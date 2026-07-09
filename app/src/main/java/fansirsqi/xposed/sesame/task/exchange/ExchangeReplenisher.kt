package fansirsqi.xposed.sesame.task.exchange

import fansirsqi.xposed.sesame.data.Status
import fansirsqi.xposed.sesame.task.antForest.Vitality
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
     * 补充资源 - 通过各种渠道自动兑换道具
     * @param effectNeed 需要的资源类型
     * @return 补充结果
     */
    fun replenish(effectNeed: String): ExchangeReplenishResult {
        Log.record(TAG, "尝试补充资源: $effectNeed")

        return when (effectNeed) {
            "forest_double" -> tryExchangeDoubleCard()
            "forest_patrol" -> tryExchangePatrol()
            "forest_shield" -> tryExchangeShield()
            "forest_stealth" -> tryExchangeStealthCard()
            "forest_accelerate" -> tryExchangeAccelerate()
            "farm_feed" -> tryExchangeFarmFeed()
            "forest_bomb" -> tryExchangeEnergyBombCard()
            else -> ExchangeReplenishResult.NOT_AVAILABLE
        }
    }

    private fun tryExchangeDoubleCard(): ExchangeReplenishResult {
        try {
            Vitality.initVitality("SC_ASSETS")
            if (Vitality.handleVitalityExchange("SK20240805004754")) {
                Log.forest("活力兑换🍃[双击卡]兑换成功")
                return ExchangeReplenishResult.SUCCESS
            }
            if (Vitality.handleVitalityExchange("CR20230516000363")) {
                Log.forest("活力兑换🍃[限时双击卡]兑换成功")
                return ExchangeReplenishResult.SUCCESS
            }
            return ExchangeReplenishResult.FAILED
        } catch (e: Exception) {
            Log.printStackTrace(TAG, "双击卡兑换异常", e)
        }
        return ExchangeReplenishResult.FAILED
    }

    private fun tryExchangePatrol(): ExchangeReplenishResult {
        try {
            Vitality.initVitality("SC_ASSETS")
            val skuId = "CR20230516000371"
            val spuId = "CR20230517000497"
            if (Status.canVitalityExchangeToday(skuId, 1) &&
                Vitality.VitalityExchange(spuId, skuId, "巡护次数")
            ) {
                Log.forest("活力兑换🍃[巡护次数]兑换成功")
                return ExchangeReplenishResult.SUCCESS
            }
            return ExchangeReplenishResult.FAILED
        } catch (e: Exception) {
            Log.printStackTrace(TAG, "巡护次数兑换异常", e)
        }
        return ExchangeReplenishResult.FAILED
    }

    private fun tryExchangeShield(): ExchangeReplenishResult {
        try {
            Vitality.initVitality("SC_ASSETS")
            val skuId = "CR20230516000370"
            val spuId = "CR20230517000497"
            if (Status.canVitalityExchangeToday(skuId, 1) &&
                Vitality.VitalityExchange(spuId, skuId, "保护罩")
            ) {
                Log.forest("活力兑换🍃[保护罩]兑换成功")
                return ExchangeReplenishResult.SUCCESS
            }
            return ExchangeReplenishResult.FAILED
        } catch (e: Exception) {
            Log.printStackTrace(TAG, "保护罩兑换异常", e)
        }
        return ExchangeReplenishResult.FAILED
    }

    private fun tryExchangeStealthCard(): ExchangeReplenishResult {
        try {
            Vitality.initVitality("SC_ASSETS")
            val skuId = "SK20230521000206"
            val spuId = "SP20230521000082"
            if (Status.canVitalityExchangeToday(skuId, 1) &&
                Vitality.VitalityExchange(spuId, skuId, "隐身卡")
            ) {
                Log.forest("活力兑换🍃[隐身卡]兑换成功")
                return ExchangeReplenishResult.SUCCESS
            }
            return ExchangeReplenishResult.FAILED
        } catch (e: Exception) {
            Log.printStackTrace(TAG, "隐身卡兑换异常", e)
        }
        return ExchangeReplenishResult.FAILED
    }

    private fun tryExchangeAccelerate(): ExchangeReplenishResult {
        try {
            Vitality.initVitality("SC_ASSETS")
            if (Vitality.handleVitalityExchange("SK20240806000201")) {
                Log.forest("活力兑换🍃[加速卡]兑换成功")
                return ExchangeReplenishResult.SUCCESS
            }
            return ExchangeReplenishResult.FAILED
        } catch (e: Exception) {
            Log.printStackTrace(TAG, "加速卡兑换异常", e)
        }
        return ExchangeReplenishResult.FAILED
    }

    private fun tryExchangeEnergyBombCard(): ExchangeReplenishResult {
        try {
            Vitality.initVitality("SC_ASSETS")
            val skuId = "CR20240806000311"
            val spuId = "CR20240806000310"
            if (Status.canVitalityExchangeToday(skuId, 1) &&
                Vitality.VitalityExchange(spuId, skuId, "能量炸弹卡")
            ) {
                Log.forest("活力兑换🍃[能量炸弹卡]兑换成功")
                return ExchangeReplenishResult.SUCCESS
            }
            return ExchangeReplenishResult.FAILED
        } catch (e: Exception) {
            Log.printStackTrace(TAG, "能量炸弹卡兑换异常", e)
        }
        return ExchangeReplenishResult.FAILED
    }

    private fun tryExchangeFarmFeed(): ExchangeReplenishResult {
        Log.record(TAG, "饲料兑换需通过庄园乐园币兑换实现")
        return ExchangeReplenishResult.NOT_AVAILABLE
    }
}
