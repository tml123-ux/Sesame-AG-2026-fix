package fansirsqi.xposed.sesame.task.exchange

enum class ExchangeSafety {
    AUTO,
    LOG_ONLY,
    UNAVAILABLE
}

data class ExchangeCost(
    val type: String = "",
    val amount: Int = 0
)

data class ExchangeLimit(
    val dailyLimit: Int = 0,
    val weeklyLimit: Int = 0
)

data class ExchangeItem(
    val id: String = "",
    val title: String = "",
    val cost: ExchangeCost = ExchangeCost(),
    val limit: ExchangeLimit = ExchangeLimit(),
    val available: Boolean = true,
    val safety: ExchangeSafety = ExchangeSafety.AUTO
)

data class ExchangeOptionRow(
    val label: String = "",
    val category: String = "",
    val item: ExchangeItem? = null
)

object ExchangeSafetyRules {
    fun isAutoExchangeAllowed(item: ExchangeItem): Boolean {
        return item.safety == ExchangeSafety.AUTO && item.available
    }
}

object ExchangeOptionsCache {
    private val cache = mutableMapOf<String, List<ExchangeOptionRow>>()

    fun getOptions(moduleName: String): List<ExchangeOptionRow> {
        return cache[moduleName] ?: emptyList()
    }

    fun setOptions(moduleName: String, options: List<ExchangeOptionRow>) {
        cache[moduleName] = options
    }

    fun clear() {
        cache.clear()
    }
}
