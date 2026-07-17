package fansirsqi.xposed.sesame.hook.lsp101

import fansirsqi.xposed.sesame.data.General
import fansirsqi.xposed.sesame.hook.ApplicationHook
import fansirsqi.xposed.sesame.hook.XposedEnv
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface

class HookEntry : XposedModule() {
    val tag = "NPatchEntry"
    private var processName: String = ""
    var customHooker: ApplicationHook? = null

    override fun onModuleLoaded(param: XposedModuleInterface.ModuleLoadedParam) {
        processName = param.getProcessName()
        customHooker = ApplicationHook()
        customHooker?.xposedInterface = this
        log(android.util.Log.INFO, tag, "Initialized for process $processName")

        val baseFw = "${getFrameworkName()} ${getFrameworkVersion()} ${getFrameworkVersionCode()} target_model_process: ${getModuleApplicationInfo().processName}"
        log(android.util.Log.INFO, "NPatchEntry", "Framework from base: $baseFw")
    }

    override fun onPackageLoaded(param: XposedModuleInterface.PackageLoadedParam) {
        try {
            if (General.PACKAGE_NAME != param.getPackageName()) return
            XposedEnv.classLoader = param.getDefaultClassLoader()
            XposedEnv.appInfo = param.getApplicationInfo()
            XposedEnv.packageName = param.getPackageName()
            XposedEnv.processName = processName
            customHooker?.loadPackage(param)
            log(android.util.Log.INFO, tag, "Hooking ${param.getPackageName()} in process $processName")
        } catch (e: Throwable) {
            log(android.util.Log.ERROR, tag, "Hook failed - ${e.message}")
            log(android.util.Log.ERROR, tag, "Hook failed", e)
        }
    }
}
