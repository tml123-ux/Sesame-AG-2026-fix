package fansirsqi.xposed.sesame.task.workPoint

import fansirsqi.xposed.sesame.model.ModelFields
import fansirsqi.xposed.sesame.model.ModelGroup
import fansirsqi.xposed.sesame.model.modelFieldExt.BooleanModelField
import fansirsqi.xposed.sesame.task.ModelTask
import fansirsqi.xposed.sesame.util.Log
import fansirsqi.xposed.sesame.util.ResChecker
import fansirsqi.xposed.sesame.util.GlobalThreadPools.sleepCompat
import org.json.JSONObject

/**
 * 工分兑换模块 - 基于 IMASP programInvoke 协议
 *
 * 工分系统位于支付宝就业(job-center)右侧入口。
 * 通过 alipay.imasp.program.programInvoke 协议操作：
 *   1. 签到领工分 (independent_component_sign_in)
 *   2. 做任务赚工分 (independent_component_task_reward)
 */
class WorkPoint : ModelTask() {

    companion object {
        private const val TAG = "WorkPoint"
        const val MODULE_NAME = "工分兑换"

        @Volatile
        var instance: WorkPoint? = null
    }

    private lateinit var workPointEnable: BooleanModelField
    private lateinit var workPointSignInEnable: BooleanModelField

    override fun getName() = MODULE_NAME
    override fun getGroup() = ModelGroup.WORKPOINT
    override fun getIcon() = "Default.png"

    override fun getFields() = ModelFields().apply {
        addField(
            BooleanModelField("workPointEnable", "工分任务 | 自动完成", true)
                .also { workPointEnable = it }
        )
        addField(
            BooleanModelField("workPointSignInEnable", "工分签到 | 自动签到", true)
                .also { workPointSignInEnable = it }
        )
    }

    override fun prepare() { instance = this }
    override fun boot(clazz: ClassLoader?) { super.boot(clazz) }
    override fun destroy() { instance = null; super.destroy() }

    override suspend fun runSuspend() {
        if (!workPointEnable.value && !workPointSignInEnable.value) {
            Log.record(TAG, "工分任务和签到均未开启，跳过")
            return
        }

        Log.record(TAG, "开始工分模块处理...")

        if (workPointSignInEnable.value) {
            processSignIn()
        }

        if (workPointEnable.value) {
            processWorkPointTasks()
        }

        Log.record(TAG, "工分模块处理完成")
    }

    private fun processSignIn() {
        try {
            val recallResp = JSONObject(WorkPointRpcCall.signInRecall())
            if (!ResChecker.checkRes(TAG + "工分签到查询失败:", recallResp)) {
                Log.record(TAG, "工分签到查询失败")
                return
            }

            val signInComp = extractComponentContent(recallResp, "independent_component_sign_in_01961456")
                ?: return

            val orderList = signInComp.optJSONArray("playSignInOrderInfoList")
            if (orderList == null || orderList.length() == 0) {
                Log.record(TAG, "工分签到计划列表为空")
                return
            }

            for (i in 0 until orderList.length()) {
                val orderInfo = orderList.optJSONObject(i) ?: continue
                val cycleList = orderInfo.optJSONArray("playSignInCycleInstanceInfoList")
                    ?: continue

                for (j in 0 until cycleList.length()) {
                    val cycleInfo = cycleList.optJSONObject(j) ?: continue

                    val todayStatus = cycleInfo.optString("todaySignInStatus", "UNSIGNED")
                    val accumulateCount = cycleInfo.optInt("accumulativeSignInCount", 0)
                    val continuousCount = cycleInfo.optInt("continuousSignInCount", 0)

                    if ("SIGNED" == todayStatus) {
                        Log.record(TAG, "工分签到: 今日已签到, 连续${continuousCount}天, 累计${accumulateCount}天")
                        continue
                    }

                    // 未签到, 尝试签到
                    val signInCode = orderInfo.optString("code", "")
                    val todayDate = cycleInfo.optString("todayDate", "")
                    val todayDayOfWeek = cycleInfo.optString("todayDayOfWeek", "")

                    // 获取今日奖励
                    val rewardMap = cycleInfo.optJSONObject("dayOfWeekRewardMap")
                    val todayReward = rewardMap?.optJSONObject(todayDayOfWeek)
                    val rewardValue = todayReward
                        ?.optJSONObject("rewardInfo")
                        ?.optJSONArray("rightList")
                        ?.optJSONObject(0)
                        ?.optJSONObject("displayInfo")
                        ?.optString("value", "1") ?: "1"

                    Log.record(TAG, "工分签到: 执行签到 [${todayDate}] 预计获${rewardValue}工分")

                    if (trySignIn(signInCode)) {
                        Log.other("工分签到[成功]#${rewardValue}工分")
                    } else {
                        Log.record(TAG, "工分签到失败 (可能需要手动触发)")
                    }
                }
            }
        } catch (t: Throwable) {
            Log.printStackTrace(TAG, t)
        }
    }

    private fun trySignIn(signInCode: String): Boolean {
        try {
            val resp = JSONObject(WorkPointRpcCall.applyTask(signInCode))
            return ResChecker.checkRes(TAG + "工分签到提交:", resp)
        } catch (t: Throwable) {
            Log.printStackTrace(TAG, t)
            return false
        }
    }

    private fun processWorkPointTasks() {
        try {
            var round = 0
            while (round < 3) {
                round++
                var hasChange = false

                val s = WorkPointRpcCall.queryTaskList()
                val jo = JSONObject(s)
                if (!ResChecker.checkRes(TAG + "查询工分任务失败:", jo)) {
                    Log.record(jo.optString("resultMsg", jo.optString("resultDesc", "查询失败")))
                    break
                }

                val content = extractComponentContent(jo, "independent_component_task_reward_01961455")
                    ?: break

                val taskList = content.optJSONArray("playTaskOrderInfoList")
                if (taskList == null || taskList.length() == 0) {
                    Log.record(TAG, "当前无工分任务")
                    break
                }

                for (i in 0 until taskList.length()) {
                    val task = taskList.optJSONObject(i) ?: continue
                    val code = task.optString("code", "")
                    val displayInfo = task.optJSONObject("displayInfo")
                    val title = displayInfo?.optString("activityTitle", code) ?: code
                    val subTitle = displayInfo?.optString("activitySubTitle", "") ?: ""
                    val desc = "$title${if (subTitle.isNotEmpty()) "($subTitle)" else ""}"

                    // 检查是否已领取
                    val claimedTask = task.optJSONObject("claimedTask")
                    val processedTask = task.optJSONObject("processedTask")

                    if (processedTask != null) {
                        Log.record(TAG, "工分任务[已完成] $desc")
                        continue
                    }

                    // 未领取 → 领取任务
                    if (claimedTask == null) {
                        Log.record(TAG, "工分任务[领取] $desc")
                        val applyResult = JSONObject(WorkPointRpcCall.applyTask(code))

                        if (ResChecker.checkRes(TAG + "领取工分任务失败:", applyResult)) {
                            val applyContent = extractComponentContent(applyResult, "independent_component_task_reward_01961455")
                            val cTask = applyContent?.optJSONObject("claimedTask")
                            val recordNo = cTask?.optString("recordNo", "") ?: ""
                            val outBizNo = cTask?.optLong("outBizNo", 0) ?: 0

                            if (recordNo.isNotEmpty() && outBizNo > 0) {
                                sleepCompat((1000L..2000L).random())

                                // 完成(提交)任务
                                val processResult = JSONObject(
                                    WorkPointRpcCall.processTask(code, outBizNo, recordNo)
                                )
                                if (ResChecker.checkRes(TAG + "完成工分任务失败:", processResult)) {
                                    Log.other("工分任务[$desc]#完成")
                                    hasChange = true
                                } else {
                                    Log.record(TAG, "工分任务完成失败: $desc")
                                }
                            } else {
                                Log.record(TAG, "工分任务领取无返回 recordNo: $desc")
                            }
                        } else {
                            Log.record(TAG, "工分任务领取失败: $desc")
                        }
                        sleepCompat((1000L..2000L).random())
                        continue
                    }

                    // 已领取但未完成
                    val recordNo = claimedTask.optString("recordNo", "")
                    if (recordNo.isEmpty()) {
                        Log.record(TAG, "工分任务[已领取但无recordNo] $desc")
                        continue
                    }

                    val outBizNo = claimedTask.optLong("outBizNo",
                        claimedTask.optJSONObject("detailInfo")?.optLong("outBizNo", 0)
                            ?: System.currentTimeMillis())

                    val processResult = JSONObject(
                        WorkPointRpcCall.processTask(code, outBizNo, recordNo)
                    )
                    if (ResChecker.checkRes(TAG + "完成工分任务失败:", processResult)) {
                        Log.other("工分任务[$desc]#完成")
                        hasChange = true
                    } else {
                        Log.record(TAG, "工分任务完成失败: $desc")
                    }
                    sleepCompat((1000L..2000L).random())
                }

                if (!hasChange) break
            }
        } catch (t: Throwable) {
            Log.printStackTrace(TAG, t)
        }
    }

    /**
     * 从 programInvoke 响应中提取指定组件的 content
     */
    private fun extractComponentContent(resp: JSONObject, componentPrefix: String): JSONObject? {
        val components = resp.optJSONObject("components")
        if (components != null) {
            val keys = components.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                if (key.startsWith(componentPrefix)) {
                    val comp = components.optJSONObject(key)
                    return comp?.optJSONObject("content")
                }
            }
        }
        return null
    }
}
