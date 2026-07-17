package fansirsqi.xposed.sesame.task.exchange

import fansirsqi.xposed.sesame.model.Model
import fansirsqi.xposed.sesame.task.antForest.Vitality
import fansirsqi.xposed.sesame.util.Log
import org.json.JSONObject

object ExchangeReplenisher {
    private const val TAG = "ExchangeReplenisher"

    enum class ExchangeReplenishResult {
        SUCCESS,
        NOT_NEEDED,
        FAILED,
        NOT_AVAILABLE
    }

    fun replenish(effectNeed: String): ExchangeReplenishResult {
        Log.record(TAG, "尝试补充资源: $effectNeed")

        return when (effectNeed) {
            "forest_double", "forest_patrol", "forest_shield", "forest_accelerate" -> {
                tryForestExchange(effectNeed)
            }
            "farm_feed" -> {
                tryFarmExchange(effectNeed)
            }
            "orchard_manure" -> {
                tryMemberExchange(effectNeed)
            }
            else -> ExchangeReplenishResult.NOT_AVAILABLE
        }
    }

    private fun tryForestExchange(effectNeed: String): ExchangeReplenishResult {
        try {
            Log.record(TAG, "尝试森林模块兑换: $effectNeed")

            Vitality.initVitality("SC_ASSETS")

            val skuIds = when (effectNeed) {
                "forest_double" -> listOf("CR20240805004754", "SK20240805004754")
                "forest_shield" -> listOf("CR20230516000363", "SK20230516000363")
                "forest_patrol" -> listOf("SK20240805004754", "CR20240805004754")
                "forest_accelerate" -> listOf("CR20240805004754", "SK20240805004754")
                else -> listOf()
            }

            for (skuId in skuIds) {
                if (Vitality.handleVitalityExchange(skuId)) {
                    Log.farm("ExchangeReplenisher: 成功兑换森林道具 $effectNeed (SKU: $skuId)")
                    return ExchangeReplenishResult.SUCCESS
                }
                Thread.sleep(500)
            }

            Log.record(TAG, "ExchangeReplenisher: 森林兑换未找到可用SKU: $effectNeed")
            return ExchangeReplenishResult.NOT_NEEDED
        } catch (e: Exception) {
            Log.printStackTrace(TAG, "森林兑换异常", e)
        }
        return ExchangeReplenishResult.NOT_AVAILABLE
    }

    private fun tryFarmExchange(effectNeed: String): ExchangeReplenishResult {
        try {
            Log.record(TAG, "尝试庄园模块兑换: $effectNeed")

            val response = fansirsqi.xposed.sesame.task.antFarm.AntFarmRpcCall.listFarmTask()
            if (response.isNullOrEmpty()) {
                Log.record(TAG, "ExchangeReplenisher: 庄园任务列表为空")
                return ExchangeReplenishResult.NOT_AVAILABLE
            }

            val jo = JSONObject(response)
            if (!fansirsqi.xposed.sesame.util.ResChecker.checkRes(TAG, jo)) {
                return ExchangeReplenishResult.NOT_AVAILABLE
            }

            val farmTaskList = jo.getJSONArray("farmTaskList")
            var claimed = false
            for (i in 0 until farmTaskList.length()) {
                val task = farmTaskList.getJSONObject(i)
                val taskStatus = task.getString("taskStatus")
                if ("FINISHED" == taskStatus) {
                    val taskId = task.optString("taskId")
                    val resultJo = JSONObject(fansirsqi.xposed.sesame.task.antFarm.AntFarmRpcCall.receiveFarmTaskAward(taskId))
                    if (fansirsqi.xposed.sesame.util.ResChecker.checkRes(TAG, resultJo)) {
                        Log.farm("ExchangeReplenisher: 成功领取庄园任务奖励")
                        claimed = true
                    }
                }
            }

            return if (claimed) ExchangeReplenishResult.SUCCESS else ExchangeReplenishResult.NOT_NEEDED
        } catch (e: Exception) {
            Log.printStackTrace(TAG, "庄园兑换异常", e)
        }
        return ExchangeReplenishResult.NOT_AVAILABLE
    }

    private fun tryMemberExchange(effectNeed: String): ExchangeReplenishResult {
        Log.record(TAG, "尝试会员模块兑换: $effectNeed")
        return ExchangeReplenishResult.NOT_NEEDED
    }
}
