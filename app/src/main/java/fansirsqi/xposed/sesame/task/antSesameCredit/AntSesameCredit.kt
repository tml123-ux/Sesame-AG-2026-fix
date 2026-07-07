package fansirsqi.xposed.sesame.task.antSesameCredit

import fansirsqi.xposed.sesame.data.Status
import fansirsqi.xposed.sesame.data.StatusFlags
import fansirsqi.xposed.sesame.model.ModelFields
import fansirsqi.xposed.sesame.model.ModelGroup
import fansirsqi.xposed.sesame.model.modelFieldExt.BooleanModelField
import fansirsqi.xposed.sesame.task.ModelTask
import fansirsqi.xposed.sesame.util.Log
import org.json.JSONObject

class AntSesameCredit : ModelTask() {

    companion object {
        private const val TAG = "AntSesameCredit"
    }

    private lateinit var doAllTask: BooleanModelField
    private lateinit var collectSesame: BooleanModelField
    private lateinit var exchangeGrain: BooleanModelField
    private lateinit var zmlCheckIn: BooleanModelField
    private lateinit var zhimaTree: BooleanModelField
    private lateinit var alchemyNextDay: BooleanModelField

    override fun getName(): String = "芝麻信用"

    override fun getGroup(): ModelGroup = ModelGroup.SESAME_CREDIT

    override fun getIcon(): String = "AntSesameCredit.png"

    override fun getFields(): ModelFields {
        val modelFields = ModelFields()
        modelFields.addField(
            BooleanModelField("doAllTask", "每日任务 | 开启", false).also { doAllTask = it }
        )
        modelFields.addField(
            BooleanModelField("collectSesame", "芝麻粒收集 | 开启", false).also { collectSesame = it }
        )
        modelFields.addField(
            BooleanModelField("exchangeGrain", "芝麻粒兑换 | 开启", false).also { exchangeGrain = it }
        )
        modelFields.addField(
            BooleanModelField("zmlCheckIn", "芝麻粒签到 | 开启", false).also { zmlCheckIn = it }
        )
        modelFields.addField(
            BooleanModelField("zhimaTree", "芝麻树 | 开启", false).also { zhimaTree = it }
        )
        modelFields.addField(
            BooleanModelField("alchemyNextDay", "芝麻粒炼金 | 开启", false).also { alchemyNextDay = it }
        )
        return modelFields
    }

    override fun runJava() {
        try {
            Log.record(TAG, "执行开始-${getName()}")

            if (zmlCheckIn.value == true && !Status.hasFlagToday(StatusFlags.FLAG_SESAME_ZML_CHECKIN_DONE)) {
                handleZmlCheckIn()
            }

            if (doAllTask.value == true && !Status.hasFlagToday(StatusFlags.FLAG_SESAME_DO_ALL_AVAILABLE_TASK)) {
                handleDailyTasks()
            }

            if (collectSesame.value == true && !Status.hasFlagToday(StatusFlags.FLAG_SESAME_COLLECT_DONE)) {
                handleCollectSesame()
            }

            if (exchangeGrain.value == true && !Status.hasFlagToday(StatusFlags.FLAG_SESAME_GRAIN_EXCHANGE_DONE)) {
                handleExchangeGrain()
            }

            if (zhimaTree.value == true && !Status.hasFlagToday(StatusFlags.FLAG_SESAME_ZHIMA_TREE_TASK_HANDLED_TODAY)) {
                handleZhimaTree()
            }

            if (alchemyNextDay.value == true && !Status.hasFlagToday(StatusFlags.FLAG_SESAME_ALCHEMY_NEXT_DAY_AWARD)) {
                handleAlchemyNextDay()
            }

        } catch (t: Throwable) {
            Log.printStackTrace(TAG, "执行异常", t)
        } finally {
            Log.record(TAG, "执行结束-${getName()}")
        }
    }

    private fun handleZmlCheckIn() {
        try {
            val checkInResp = AntSesameCreditRpcCall.zmlCheckInQueryTaskLists()
            val jo = JSONObject(checkInResp)
            if (!jo.optBoolean("success") && jo.optString("resultCode") != "SUCCESS") {
                Log.record(TAG, "芝麻粒签到列表查询失败: ${jo.optString("resultCode", "UNKNOWN")}")
                return
            }
            val data = jo.optJSONObject("data") ?: return
            val currentDay = data.optString("currentDate")
            if (currentDay.isNullOrBlank()) return
            val resp = AntSesameCreditRpcCall.zmCheckInCompleteTask(currentDay, "zml")
            val resultJo = JSONObject(resp)
            if (resultJo.optBoolean("success") || resultJo.optString("resultCode") == "SUCCESS") {
                Status.setFlagToday(StatusFlags.FLAG_SESAME_ZML_CHECKIN_DONE)
                Log.record(TAG, "芝麻粒签到成功")
            } else {
                val code = resultJo.optString("resultCode", resultJo.optString("errorCode", "UNKNOWN"))
                if (code == "ALREADY_SIGN_IN") {
                    Status.setFlagToday(StatusFlags.FLAG_SESAME_ZML_CHECKIN_DONE)
                    Log.record(TAG, "芝麻粒签到今日已完成")
                } else {
                    Log.record(TAG, "芝麻粒签到失败: $code")
                }
            }
        } catch (t: Throwable) {
            Log.printStackTrace(TAG, "handleZmlCheckIn err", t)
        }
    }

    private fun handleDailyTasks() {
        try {
            val taskListResponse = AntSesameCreditRpcCall.queryAvailableSesameTask()
            val jo = JSONObject(taskListResponse)
            if (!isRpcSuccess(jo)) {
                Log.record(TAG, "芝麻信用任务列表查询失败")
                return
            }

            val taskInfoList = jo.optJSONObject("data")?.optJSONArray("taskInfoList") ?: return
            var doneCount = 0
            val totalCount = taskInfoList.length()

            for (i in 0 until totalCount) {
                val task = taskInfoList.optJSONObject(i) ?: continue
                val taskTemplateId = task.optString("templateId")
                val taskStatus = task.optString("taskStatus")

                if (taskStatus == "TODO") {
                    val joinResp = AntSesameCreditRpcCall.joinSesameTask(taskTemplateId)
                    Thread.sleep(500)

                    val completeResp = AntSesameCreditRpcCall.taskFinish(taskTemplateId)
                    val completeJo = JSONObject(completeResp)

                    if (isRpcSuccess(completeJo)) {
                        doneCount++
                    }
                    Thread.sleep(300)
                }
            }

            Status.setFlagToday(StatusFlags.FLAG_SESAME_DO_ALL_AVAILABLE_TASK)
            Log.record(TAG, "芝麻信用任务处理完成: $doneCount/$totalCount")
        } catch (t: Throwable) {
            Log.printStackTrace(TAG, "handleDailyTasks err", t)
        }
    }

    private fun handleCollectSesame() {
        try {
            val response = AntSesameCreditRpcCall.collectAllCreditFeedback()
            val jo = JSONObject(response)
            if (isRpcSuccess(jo)) {
                Status.setFlagToday(StatusFlags.FLAG_SESAME_COLLECT_DONE)
                Log.record(TAG, "芝麻粒收集成功")
            } else {
                Log.record(TAG, "芝麻粒收集失败: ${jo.optString("resultCode", "UNKNOWN")}")
            }
        } catch (t: Throwable) {
            Log.printStackTrace(TAG, "handleCollectSesame err", t)
        }
    }

    private fun handleExchangeGrain() {
        try {
            val response = AntSesameCreditRpcCall.queryExchangeList(1, 20)
            val jo = JSONObject(response)
            if (isRpcSuccess(jo)) {
                Status.setFlagToday(StatusFlags.FLAG_SESAME_GRAIN_EXCHANGE_DONE)
                Log.record(TAG, "芝麻粒兑换列表已查询")
            }
        } catch (t: Throwable) {
            Log.printStackTrace(TAG, "handleExchangeGrain err", t)
        }
    }

    private fun handleZhimaTree() {
        try {
            val response = AntSesameCreditRpcCall.zhimaTreeHomePage()
            if (response != null) {
                val jo = JSONObject(response)
                if (isRpcSuccess(jo)) {
                    Status.setFlagToday(StatusFlags.FLAG_SESAME_ZHIMA_TREE_TASK_HANDLED_TODAY)
                    Log.record(TAG, "芝麻树任务已处理")
                }
            }
        } catch (t: Throwable) {
            Log.printStackTrace(TAG, "handleZhimaTree err", t)
        }
    }

    private fun handleAlchemyNextDay() {
        try {
            val response = AntSesameCreditRpcCall.Zmxy.Alchemy.claimAward()
            val jo = JSONObject(response)
            if (isRpcSuccess(jo)) {
                Status.setFlagToday(StatusFlags.FLAG_SESAME_ALCHEMY_NEXT_DAY_AWARD)
                Log.record(TAG, "芝麻粒炼金次日奖励领取成功")
            } else {
                val code = jo.optString("resultCode", jo.optString("errorCode", ""))
                if (code == "ALREADY_RECEIVED" || code == "TODAY_NOT_QUALIFIED") {
                    Status.setFlagToday(StatusFlags.FLAG_SESAME_ALCHEMY_NEXT_DAY_AWARD)
                }
            }
        } catch (t: Throwable) {
            Log.printStackTrace(TAG, "handleAlchemyNextDay err", t)
        }
    }

    private fun isRpcSuccess(jo: JSONObject): Boolean {
        return jo.optBoolean("success") || jo.optBoolean("isSuccess") ||
            jo.optString("resultCode") == "100" ||
            jo.optString("resultCode").equals("SUCCESS", ignoreCase = true) ||
            jo.optString("memo").equals("SUCCESS", ignoreCase = true)
    }
}
