package fansirsqi.xposed.sesame.util

import fansirsqi.xposed.sesame.hook.ApplicationHookConstants
import org.json.JSONObject

object RpcOfflineRisk {
    @JvmStatic
    fun enterOfflineIfNeeded(flowName: String, response: JSONObject) {
        ApplicationHookConstants.enterOfflineIfNeeded(response)
    }
}
