package fansirsqi.xposed.sesame.task.myBankWelfare

import fansirsqi.xposed.sesame.data.Status.Companion.hasFlagToday
import fansirsqi.xposed.sesame.data.Status.Companion.setFlagToday
import fansirsqi.xposed.sesame.data.StatusFlags
import fansirsqi.xposed.sesame.model.ModelFields
import fansirsqi.xposed.sesame.model.ModelGroup
import fansirsqi.xposed.sesame.model.modelFieldExt.BooleanModelField
import fansirsqi.xposed.sesame.task.ModelTask
import fansirsqi.xposed.sesame.util.JsonUtil
import fansirsqi.xposed.sesame.util.Log
import org.json.JSONObject
import java.math.BigDecimal
import java.math.RoundingMode

class MyBankWelfare : ModelTask() {

    companion object {
        private const val TAG = "MyBankWelfare"
        private const val DISPLAY_NAME = "网商银行"
        private const val BUSINESS_NAME = "网商银行福利金"
        private const val TASK_CENTER_ID = "AP1269301"
        private val SUPPORTED_TRIGGER_TYPES = setOf("USER_TRIGGER", "EVENT_TRIGGER")
    }

    private var myBankWelfareTask: BooleanModelField? = null
    private var myBankWelfareSign: BooleanModelField? = null

    override fun getName(): String = DISPLAY_NAME

    override fun getGroup(): ModelGroup = ModelGroup.MYBANK

    override fun getIcon(): String = "AntMember.png"

    override fun getFields(): ModelFields {
        val modelFields = ModelFields()
        modelFields.addField(
            BooleanModelField("myBankWelfareTask", "网商银行 | 福利金任务", true).also {
                myBankWelfareTask = it
            })
        modelFields.addField(
            BooleanModelField("myBankWelfareSign", "网商银行 | 福利金签到", true).also {
                myBankWelfareSign = it
            })
        return modelFields
    }

    override fun runJava() {
        try {
            Log.member("${BUSINESS_NAME}执行开始")
            logPointBalance()
            logVirtualProfits()
            if (myBankWelfareSign?.value == true && !hasFlagToday(StatusFlags.FLAG_MYBANK_WELFARE_SIGN_DONE)) {
                handleSign()
            }
            if (myBankWelfareTask?.value == true) {
                handleTasks()
            }
        } catch (t: Throwable) {
            Log.printStackTrace(TAG, "runJava err:", t)
        } finally {
            Log.member("${BUSINESS_NAME}执行结束")
        }
    }

    private fun handleSign() {
        try {
            val response = JSONObject(MyBankWelfareRpcCall.signinPlay())
            if (!response.optBoolean("success")) {
                Log.member("${BUSINESS_NAME}📅签到咨询失败#${
                    response.optString("resultDesc").ifBlank { response.optString("memo") }
                }")
                return
            }
            val result = response.optJSONObject("result")
            val signNotAdmit = result?.optBoolean("signNotAdmit", false) == true
            val canRetry = result?.optBoolean("canRetry", false) == true
            if (signNotAdmit && !canRetry) {
                Log.member("${BUSINESS_NAME}📅今日签到已处理")
            } else {
                Log.member("${BUSINESS_NAME}📅签到咨询成功")
            }
            setFlagToday(StatusFlags.FLAG_MYBANK_WELFARE_SIGN_DONE)
        } catch (t: Throwable) {
            Log.printStackTrace(TAG, "handleSign err:", t)
        }
    }

    private fun handleTasks() {
        try {
            val response = MyBankWelfareRpcCall.taskQuery(TASK_CENTER_ID)
            if (response.isBlank()) {
                Log.member("${BUSINESS_NAME}🎯任务查询返回空")
                return
            }
            val jo = JsonUtil.parseJSONObject(response)
            if (!jo.optBoolean("success")) {
                Log.member("${BUSINESS_NAME}🎯任务查询失败#${jo.optString("resultDesc")}")
                return
            }
            val taskDetailList = jo.optJSONObject("result")?.optJSONArray("taskDetailList") ?: return
            for (i in 0 until taskDetailList.length()) {
                val taskDetail = taskDetailList.optJSONObject(i) ?: continue
                val taskId = taskDetail.optString("taskId").trim()
                val taskTitle = taskDetail.optString("taskTitle").trim().ifBlank { taskId }
                val status = taskDetail.optString("taskProcessStatus").trim()
                val triggerType = taskDetail.optString("sendCampTriggerType").trim()
                if (triggerType !in SUPPORTED_TRIGGER_TYPES) {
                    continue
                }
                when (status.uppercase()) {
                    "TO_RECEIVE" -> {
                        triggerAndLog(taskId, taskTitle, "receive")
                    }
                    "NONE_SIGNUP", "SIGNUP_COMPLETE" -> {
                        if (status.uppercase() == "NONE_SIGNUP") {
                            triggerAndLog(taskId, taskTitle, "signup")
                        }
                        triggerAndLog(taskId, taskTitle, "send")
                    }
                    else -> { /* skip already completed or unknown status */ }
                }
            }
        } catch (t: Throwable) {
            Log.printStackTrace(TAG, "handleTasks err:", t)
        }
    }

    private fun triggerAndLog(taskId: String, taskTitle: String, stageCode: String) {
        try {
            val response = MyBankWelfareRpcCall.taskTrigger(taskId, stageCode, TASK_CENTER_ID)
            if (response.isBlank()) {
                Log.member("${BUSINESS_NAME}🎯[$taskTitle]$stageCode 返回空")
                return
            }
            val jo = JsonUtil.parseJSONObject(response)
            if (jo.optBoolean("success")) {
                val actionText = when (stageCode) {
                    "signup" -> "报名完成"
                    "send" -> "奖励发放完成"
                    "receive" -> "奖励领取完成"
                    else -> "处理成功"
                }
                Log.member("${BUSINESS_NAME}🎯[$taskTitle]$actionText")
            } else {
                val code = jo.optString("resultDesc").ifBlank { jo.optString("memo") }
                if (code in setOf("已领取", "重复领取", "已完成", "已报名", "已经报名", "已处理")) {
                    Log.member("${BUSINESS_NAME}🎯[$taskTitle]${code}")
                } else {
                    Log.member("${BUSINESS_NAME}🎯[$taskTitle]$stageCode 失败#$code")
                }
            }
        } catch (t: Throwable) {
            Log.printStackTrace(TAG, "triggerAndLog err:", t)
        }
    }

    private fun logPointBalance() {
        try {
            val response = JSONObject(MyBankWelfareRpcCall.queryPointBalance())
            if (!response.optBoolean("success")) {
                return
            }
            val pointBalance = response.optJSONObject("result")
                ?.opt("pointBalance")?.toString().orEmpty()
                .ifBlank { response.opt("pointBalance")?.toString().orEmpty() }
            if (pointBalance.isNotBlank()) {
                Log.member("${BUSINESS_NAME}💰当前可用福利金${formatDecimalAmount(pointBalance)}")
            }
        } catch (t: Throwable) {
            Log.printStackTrace(TAG, "logPointBalance err:", t)
        }
    }

    private fun logVirtualProfits() {
        try {
            val response = JSONObject(MyBankWelfareRpcCall.queryEnableVirtualProfitV2())
            if (!response.optBoolean("success")) {
                return
            }
            val profitList = response.optJSONObject("result")
                ?.optJSONArray("virtualProfitList") ?: return
            if (profitList.length() == 0) {
                return
            }
            val logs = mutableListOf<String>()
            for (i in 0 until profitList.length()) {
                val profit = profitList.optJSONObject(i) ?: continue
                val sceneDesc = profit.optString("sceneDesc").ifBlank {
                    profit.optJSONObject("sceneDTO")?.optString("sceneDesc").orEmpty()
                }
                val reward = profit.optString("reward").ifBlank {
                    profit.optString("pointShowValue")
                }.ifBlank {
                    extractAmountText(profit.opt("point"))
                }
                logs.add(
                    listOf(
                        sceneDesc.ifBlank { "奖励场景" },
                        reward.takeIf { it.isNotBlank() }?.let {
                            "${formatDecimalAmount(it)}福利金"
                        }.orEmpty()
                    ).filter { it.isNotBlank() }.joinToString("#")
                )
            }
            logs.filter { it.isNotBlank() }.forEach {
                Log.member("${BUSINESS_NAME}🎁$it")
            }
        } catch (t: Throwable) {
            Log.printStackTrace(TAG, "logVirtualProfits err:", t)
        }
    }

    private fun formatDecimalAmount(raw: String): String {
        return try {
            BigDecimal(raw).setScale(2, RoundingMode.HALF_UP).toPlainString()
        } catch (_: Exception) {
            raw
        }
    }

    private fun extractAmountText(value: Any?): String {
        return when (value) {
            is String -> value.trim()
            is Number -> formatDecimalAmount(value.toString())
            else -> ""
        }
    }
}
