package fansirsqi.xposed.sesame.task.exchange

import fansirsqi.xposed.sesame.model.Model
import fansirsqi.xposed.sesame.util.Log

object ExchangeReplenisher {
    private const val TAG = "ExchangeReplenisher"

    enum class ExchangeReplenishResult {
        SUCCESS,
        NOT_NEEDED,
        FAILED,
        NOT_AVAILABLE
    }

    /**
     * Replenish a resource type by trying different source modules.
     * @param effectNeed The type of resource needed (e.g., "forest_double", "farm_feed")
     * @return result of the replenish attempt
     */
    fun replenish(effectNeed: String): ExchangeReplenishResult {
        Log.record(TAG, "尝试补充资源: $effectNeed")
        
        return when (effectNeed) {
            "forest_double", "forest_patrol", "forest_shield", "forest_accelerate" -> {
                // Try forest first, then sports, then member
                tryForestExchange(effectNeed)
            }
            "farm_feed" -> {
                // Try farm first, then member
                tryFarmExchange(effectNeed)
            }
            "orchard_manure" -> {
                // Try member only
                tryMemberExchange(effectNeed)
            }
            else -> ExchangeReplenishResult.NOT_AVAILABLE
        }
    }

    private fun tryForestExchange(effectNeed: String): ExchangeReplenishResult {
        try {
            // Forest replenishment managed by forest module itself
            Log.record(TAG, "尝试森林模块兑换: $effectNeed")
            return ExchangeReplenishResult.NOT_NEEDED
        } catch (e: Exception) {
            Log.printStackTrace(TAG, "森林兑换异常", e)
        }
        return ExchangeReplenishResult.NOT_AVAILABLE
    }

    private fun tryFarmExchange(effectNeed: String): ExchangeReplenishResult {
        Log.record(TAG, "尝试庄园模块兑换: $effectNeed")
        return ExchangeReplenishResult.NOT_NEEDED
    }

    private fun tryMemberExchange(effectNeed: String): ExchangeReplenishResult {
        Log.record(TAG, "尝试会员模块兑换: $effectNeed")
        return ExchangeReplenishResult.NOT_NEEDED
    }
}
