package fansirsqi.xposed.sesame.hook

import org.json.JSONObject

object ApplicationHookConstants {
    private var offline: Boolean = false

    @JvmStatic
    fun isOffline(): Boolean = offline

    @JvmStatic
    fun enterOffline() {
        offline = true
    }

    @JvmStatic
    fun exitOffline() {
        offline = false
    }

    @JvmStatic
    fun enterOfflineIfNeeded(response: JSONObject) {
        val code = response.optString("resultCode").ifBlank {
            response.optString("code").ifBlank {
                response.optString("errorCode")
            }
        }
        if (code == "1009" || code == "I07") {
            enterOffline()
        }
    }
}
