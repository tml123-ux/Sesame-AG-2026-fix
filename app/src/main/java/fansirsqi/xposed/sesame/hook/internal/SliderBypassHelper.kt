package fansirsqi.xposed.sesame.hook.internal

import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XC_MethodReplacement
import de.robv.android.xposed.XposedHelpers
import fansirsqi.xposed.sesame.hook.ApplicationHook
import fansirsqi.xposed.sesame.util.Log
import java.lang.reflect.Proxy
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * 绕过新版支付宝滑块验证和安全检测
 * 针对 RpcSecurityCountersignHandleProxy 和新版安全机制
 */
object SliderBypassHelper {

    private const val TAG = "SliderBypass"
    private var classLoader: ClassLoader? = null
    private var hookInstalled = false
    @Volatile
    private var sliderBypassEnabled = false
    @Volatile
    private var captchaUIHookEnabled = false

    fun init(loader: ClassLoader) {
        classLoader = loader
        Log.record(TAG, "滑块绕过助手初始化完成")
    }

    /**
     * 设置功能开关
     * @param sliderEnabled 是否启用滑块弹窗自动关闭
     * @param captchaEnabled 是否启用安全弹窗拦截（VPN/访问拒绝等）
     */
    fun setConfig(sliderEnabled: Boolean, captchaEnabled: Boolean) {
        sliderBypassEnabled = sliderEnabled
        captchaUIHookEnabled = captchaEnabled
        Log.record(TAG, "配置更新: sliderBypass=$sliderEnabled, captchaHook=$captchaEnabled")
    }

    /**
     * 安装所有滑块绕过 Hook
     */
    fun installAllHooks() {
        if (hookInstalled) {
            Log.record(TAG, "Hook 已安装，跳过")
            return
        }
        val loader = classLoader ?: return

        try {
            // 1. Hook RPC 安全联署 - 自动提供有效签名
            hookRpcSecurityCountersign(loader)

            // 2. Hook NebulaTransActivity - 自动关闭滑块/验证码页面
            hookNebulaTransActivity(loader)

            // 3. Hook 安全验证 H5 - H5BasePage 实现类
            hookSecurityVerification(loader)

            // 4. Hook antcaptcha.verify - 绕过滑块验证 RPC
            hookAntCaptcha(loader)

            // 以下弹窗自动关闭已禁用（用户要求保留支付宝原生弹窗行为）
            // 5. Hook 风险提示弹窗
            // hookRiskDialog(loader)

            // 6. Hook 广告/促销弹窗自动关闭
            // hookAdPromotionDialog(loader)

            // 7. Hook 权限请求弹窗自动关闭
            // hookPermissionDialog(loader)

            // 8. Hook 版本更新弹窗自动关闭
            // hookUpdateDialog(loader)

            // 9. Hook 通用Dialog.show()自动关闭已知弹窗
            // hookGeneralDialogDismiss(loader)

            hookInstalled = true
            Log.record(TAG, "所有滑块绕过 Hook 安装成功")
        } catch (e: Throwable) {
            Log.printStackTrace(TAG, "安装滑块绕过 Hook 失败", e)
        }
    }

    /**
     * Hook NebulaTransActivity - 滑块页面自动滑动验证，其他验证页面自动关闭
     */
    private fun hookNebulaTransActivity(loader: ClassLoader) {
        try {
            XposedHelpers.findAndHookMethod(
                android.app.Activity::class.java,
                "onResume",
                android.os.Bundle::class.java ?: Any::class.java,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        try {
                            val activity = param.thisObject as? android.app.Activity ?: return
                            val className = activity.javaClass.name
                            if (!className.contains("NebulaTransActivity")) return

                            val intent = activity.intent
                            val url = intent?.dataString ?: intent?.getStringExtra("url") ?: ""

                            val isSliderPage = url.contains("slider") || url.contains("slideVerify") ||
                                url.contains("dragVerify")
                            val isOtherVerify = url.contains("security") || url.contains("verify") ||
                                url.contains("risk") || url.contains("safePay")
                            val isAccessDenied = url.contains("accessDeny") || url.contains("denied") ||
                                url.contains("forbidden") || url.contains("requestfailure") ||
                                url.contains("noPermission") || url.contains("networkBlock") ||
                                url.contains("errorpage") || url.contains("vpnDetect") ||
                                url.contains("proxyCheck") || url.contains("accessRefuse")

                            if (isSliderPage && sliderBypassEnabled) {
                                Log.record(TAG, "检测到滑块验证页面，启动自动滑动: $className")
                                scheduleAutoSlideOnActivity(activity, 1000)
                            } else if (isOtherVerify && captchaUIHookEnabled) {
                                Log.record(TAG, "拦截非滑块验证页面: $className url=$url")
                                activity.finish()
                            } else if (isAccessDenied && captchaUIHookEnabled) {
                                Log.record(TAG, "拦截访问被拒绝/VPN检测页面: $className url=$url")
                                activity.finish()
                            }
                        } catch (_: Throwable) {}
                    }
                })
            Log.record(TAG, "Hook NebulaTransActivity (auto-slide) 成功")
        } catch (e: Throwable) {
            Log.record(TAG, "Hook NebulaTransActivity 失败: ${e.message}")
        }
    }

    /**
     * 调度自动滑动任务（基于Activity窗口坐标，兼容WebView渲染的滑块）
     */
    private fun scheduleAutoSlideOnActivity(activity: android.app.Activity, delayMs: Long) {
        slideExecutor.schedule({
            try {
                val view = activity.window?.decorView ?: return@schedule
                val screenWidth = view.width.toFloat()
                val screenHeight = view.height.toFloat()
                if (screenWidth <= 0 || screenHeight <= 0) return@schedule

                val startX = screenWidth * 0.15f
                val endX = screenWidth * 0.85f
                val y = screenHeight * 0.72f
                val slideDistance = endX - startX

                if (slideDistance <= 0) return@schedule

                Log.record(TAG, "窗口级触摸滑动: startX=$startX endX=$endX y=$y distance=$slideDistance")
                executeTouchGestureOnView(view, startX, y, slideDistance)
                Log.record(TAG, "窗口级自动滑动完成")
            } catch (e: Throwable) {
                Log.record(TAG, "窗口级自动滑动异常: ${e.message}，保持页面等待手动操作")
            }
        }, delayMs, TimeUnit.MILLISECONDS)
    }

    /**
     * 在指定View上执行水平触摸滑动手势（适用于Activity/DecorView级别分派）
     */
    private fun executeTouchGestureOnView(view: View, startX: Float, startY: Float, totalDistance: Float) {
        val downTime = SystemClock.uptimeMillis()

        val downEvent = MotionEvent.obtain(
            downTime, downTime,
            MotionEvent.ACTION_DOWN, startX, startY, 0
        )
        view.dispatchTouchEvent(downEvent)
        downEvent.recycle()

        val totalSteps = 18 + (8..12).random()
        val baseStepMs = 8L + (0..4).random()
        var currentX = startX
        var currentY = startY
        var eventTime = downTime + 60

        for (i in 1..totalSteps) {
            val progress = i.toFloat() / totalSteps
            val easeProgress = easeProgress(progress)

            currentX = startX + totalDistance * easeProgress
            val jitter = ((Math.random() - 0.5) * 3).toFloat()
            currentY = startY + jitter

            val stepDuration = when {
                progress < 0.15f -> baseStepMs + (8..15).random()
                progress < 0.85f -> baseStepMs + (3..7).random()
                else -> baseStepMs + (12..25).random()
            }
            eventTime += stepDuration

            val moveEvent = MotionEvent.obtain(
                downTime, eventTime,
                MotionEvent.ACTION_MOVE, currentX, currentY, 0
            )
            view.dispatchTouchEvent(moveEvent)
            moveEvent.recycle()

            Thread.sleep(stepDuration)
        }

        eventTime += 50
        val upEvent = MotionEvent.obtain(
            downTime, eventTime,
            MotionEvent.ACTION_UP, startX + totalDistance, startY, 0
        )
        view.dispatchTouchEvent(upEvent)
        upEvent.recycle()
    }

    /**
     * 缓动函数: 模拟人类加速→匀速→减速的滑动特征
     */
    private fun easeProgress(t: Float): Float {
        return when {
            t < 0.2f -> 2.5f * t * t
            t < 0.8f -> 0.5f + (t - 0.2f) * 5f / 6f
            else -> 1f - 2f * (1f - t) * (1f - t)
        }
    }

    /**
     * Hook RpcSecurityCountersignHandleProxy#handleRpcSecurityCountersign
     * 拦截安全联署请求，返回有效的安全头部
     */
    private fun hookRpcSecurityCountersign(loader: ClassLoader) {
        if (!captchaUIHookEnabled) {
            Log.record(TAG, "RPC 安全联署绕过未启用，跳过安装")
            return
        }
        try {
            val proxyClass = XposedHelpers.findClass(
                "com.alibaba.ariver.commonability.network.rpc.RpcSecurityCountersignHandleProxy",
                loader
            )

            // Hook RVProxy.get() 返回代理，拦截 get 调用以返回我们的自定义实现
            val rvProxyClass = XposedHelpers.findClass(
                "com.alibaba.ariver.kernel.common.RVProxy",
                loader
            )

            XposedHelpers.findAndHookMethod(
                rvProxyClass,
                "get",
                Class::class.java,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val serviceClass = param.args[0] as? Class<*>
                        if (serviceClass?.name?.contains("RpcSecurityCountersignHandleProxy") == true) {
                            val original = param.result
                            if (original == null) {
                                // 创建代理实现，返回空的 countersign map（绕过签名检查）
                                val proxy = Proxy.newProxyInstance(
                                    loader,
                                    arrayOf(proxyClass),
                                    { _, method, args ->
                                        when (method.name) {
                                            "handleRpcSecurityCountersign" -> {
                                                Log.record(TAG, "绕过 RPC 安全联署请求")
                                                emptyMap<String, String>()
                                            }
                                            "getPriority" -> 0
                                            else -> null
                                        }
                                    }
                                )
                                param.result = proxy
                                Log.record(TAG, "已注入 RpcSecurityCountersignHandleProxy 代理")
                            }
                        }
                    }
                })

            Log.record(TAG, "Hook RpcSecurityCountersignHandleProxy 成功")
        } catch (e: Throwable) {
            Log.record(TAG, "Hook RpcSecurityCountersignHandleProxy 失败: ${e.message}")
            Log.printStackTrace(TAG, e)
        }
    }

    /**
     * Hook 安全验证页面 - 自动跳过滑块/H5验证
     * 拦截 SchemeStartActivity 和安全验证相关 Activity
     */
    private fun hookSecurityVerification(loader: ClassLoader) {
        try {
            var hooked = false
            try {
                XposedHelpers.findAndHookMethod(
                    "com.alipay.mobile.nebula.basebridge.H5BasePage",
                    loader,
                    "loadUrl",
                    String::class.java,
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam) {
                            val url = param.args[0] as? String ?: return
                            val isSliderUrl = url.contains("slidingVerify") ||
                                url.contains("slideVerify") ||
                                url.contains("dragVerify")
                            val isExplicitCaptcha = url.contains("antcaptcha") &&
                                (url.contains("verify") || url.contains("captcha"))
                            val isBlockUrl = url.contains("accessDeny") ||
                                url.contains("denied") ||
                                url.contains("forbidden") ||
                                url.contains("requestfailure") ||
                                url.contains("noPermission") ||
                                url.contains("networkBlock") ||
                                url.contains("errorpage") ||
                                url.contains("vpnDetect") ||
                                url.contains("proxyCheck") ||
                                url.contains("accessRefuse")

                            if (isSliderUrl && sliderBypassEnabled) {
                                Log.record(TAG, "H5BasePage 拦截滑块验证页面: $url")
                                param.result = null
                            } else if (isExplicitCaptcha && sliderBypassEnabled) {
                                Log.record(TAG, "H5BasePage 拦截antcaptcha验证页面: $url")
                                param.result = null
                            } else if (isBlockUrl && captchaUIHookEnabled) {
                                Log.record(TAG, "H5BasePage 拦截拒绝访问页面: $url")
                                param.result = null
                            }
                        }
                    })
                hooked = true
                Log.record(TAG, "Hook H5BasePage.loadUrl 成功")
            } catch (_: Throwable) {}

            if (!hooked) {
                try {
                    XposedHelpers.findAndHookMethod(
                        "com.alipay.mobile.nebulacore.web.H5WebView",
                        loader,
                        "loadUrl",
                        String::class.java,
                        object : XC_MethodHook() {
                            override fun beforeHookedMethod(param: MethodHookParam) {
                                val url = param.args[0] as? String ?: return
                                val isSliderUrl = url.contains("slidingVerify") ||
                                    url.contains("slideVerify") ||
                                    url.contains("dragVerify")
                                val isExplicitCaptcha = url.contains("antcaptcha") &&
                                    (url.contains("verify") || url.contains("captcha"))
                                val isBlockUrl = url.contains("accessDeny") ||
                                    url.contains("denied") ||
                                    url.contains("forbidden") ||
                                    url.contains("requestfailure") ||
                                    url.contains("noPermission") ||
                                    url.contains("networkBlock") ||
                                    url.contains("errorpage") ||
                                    url.contains("vpnDetect") ||
                                    url.contains("proxyCheck") ||
                                    url.contains("accessRefuse")

                                if (isSliderUrl && sliderBypassEnabled) {
                                    Log.record(TAG, "H5WebView 拦截滑块验证页面: $url")
                                    param.result = null
                                } else if (isExplicitCaptcha && sliderBypassEnabled) {
                                    Log.record(TAG, "H5WebView 拦截antcaptcha验证页面: $url")
                                    param.result = null
                                } else if (isBlockUrl && captchaUIHookEnabled) {
                                    Log.record(TAG, "H5WebView 拦截拒绝访问页面: $url")
                                    param.result = null
                                }
                            }
                        })
                    hooked = true
                    Log.record(TAG, "Hook H5WebView.loadUrl 成功")
                } catch (_: Throwable) {}
            }

            if (!hooked) {
                Log.record(TAG, "Hook 安全验证页面失败: 未找到可用的实现类")
            }
        } catch (e: Throwable) {
            Log.record(TAG, "Hook 安全验证页面失败: ${e.message}")
        }
    }

    /**
     * Hook alipay.security.antcaptcha.verify - 滑块验证 RPC 绕过
     * 拦截 RpcBridgeExtension.rpc() 中 method=antcaptcha.verify 的调用
     */
    private fun hookAntCaptcha(loader: ClassLoader) {
        try {
            val bridgeClass = XposedHelpers.findClass(
                "com.alibaba.ariver.commonability.network.rpc.RpcBridgeExtension", loader
            )
            val jsonClass = Class.forName("com.alibaba.fastjson.JSONObject", false, loader)
            XposedHelpers.findAndHookMethod(
                bridgeClass, "rpc",
                String::class.java, java.lang.Boolean.TYPE, java.lang.Boolean.TYPE,
                String::class.java, jsonClass, String::class.java, jsonClass,
                java.lang.Boolean.TYPE, java.lang.Boolean.TYPE,
                Integer.TYPE, java.lang.Boolean.TYPE, String::class.java,
                XposedHelpers.findClass("com.alibaba.ariver.app.api.App", loader),
                XposedHelpers.findClass("com.alibaba.ariver.app.api.Page", loader),
                XposedHelpers.findClass("com.alibaba.ariver.engine.api.bridge.model.ApiContext", loader),
                XposedHelpers.findClass("com.alibaba.ariver.engine.api.bridge.extension.BridgeCallback", loader),
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        if (!sliderBypassEnabled) return
                        val method = param.args[0] as? String ?: return
                        if (!method.contains("antcaptcha")) return
                        Log.record(TAG, "拦截 antcaptcha: $method")
                        try {
                            val cb = param.args[15]
                            if (cb != null) {
                                val fakeJson = jsonClass.newInstance()
                                jsonClass.getMethod("put", String::class.java, Object::class.java)
                                    .invoke(fakeJson, "success", true)
                                jsonClass.getMethod("put", String::class.java, Object::class.java)
                                    .invoke(fakeJson, "data", "{}")
                                XposedHelpers.callMethod(cb, "sendJSONResponse", fakeJson)
                                param.result = null
                            }
                        } catch (_: Throwable) {}
                    }
                })
            Log.record(TAG, "Hook antcaptcha.verify (新RPC通道) 成功")
        } catch (e: Throwable) {
            Log.record(TAG, "Hook antcaptcha.verify 失败: ${e.message}")
        }
    }

    /**
     * Hook 风险提示弹窗 - 防止弹窗干扰
     */
    private fun hookRiskDialog(loader: ClassLoader) {
        try {
            // Hook 风险提示对话框
            XposedHelpers.findAndHookMethod(
                "com.alipay.mobile.quinox.LauncherActivity",
                loader,
                "onResume",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        // 检查是否有安全验证弹窗
                        try {
                            val activity = param.thisObject
                            // 尝试关闭可能的安全验证 Fragment/Dialog
                            val fm = XposedHelpers.callMethod(activity, "getSupportFragmentManager")
                            val fragments = XposedHelpers.callMethod(fm, "getFragments") as? List<*>
                            fragments?.forEach { fragment ->
                                val fragClass = fragment?.javaClass?.name ?: ""
                                if (fragClass.contains("Verify") ||
                                    fragClass.contains("Risk") ||
                                    fragClass.contains("SecurityDialog") ||
                                    fragClass.contains("SafePay")
                                ) {
                                    Log.record(TAG, "自动关闭安全验证弹窗: $fragClass")
                                    try {
                                        XposedHelpers.callMethod(fm, "beginTransaction")
                                            .let { transaction ->
                                                XposedHelpers.callMethod(transaction, "remove", fragment)
                                                XposedHelpers.callMethod(transaction, "commitAllowingStateLoss")
                                            }
                                    } catch (_: Throwable) {}
                                }
                            }
                        } catch (_: Throwable) {}
                    }
                })
            Log.record(TAG, "Hook 风险提示弹窗成功")
        } catch (e: Throwable) {
            Log.record(TAG, "Hook 风险提示弹窗失败: ${e.message}")
        }
    }

    /**
     * Hook 广告/促销弹窗 - 自动关闭
     */
    private fun hookAdPromotionDialog(loader: ClassLoader) {
        try {
            val dialogKeywords = arrayOf(
                "AdDialog", "PromotionDialog", "PopAd", "SplashAd",
                "OperationDialog", "ActivityDialog", "CouponDialog",
                "RecommendDialog", "MarketingDialog", "BannerDialog"
            )
            XposedHelpers.findAndHookMethod(
                "android.app.Dialog",
                loader,
                "show",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        try {
                            val dialog = param.thisObject as? android.app.Dialog ?: return
                            val className = dialog.javaClass.name
                            for (kw in dialogKeywords) {
                                if (className.contains(kw)) {
                                    Log.record(TAG, "自动关闭广告弹窗: $className")
                                    scheduleDismiss(dialog, 0)
                                    param.result = null
                                    return
                                }
                            }
                            val window = dialog.window
                            val decorView = window?.decorView
                            if (decorView != null) {
                                checkAndDismissPromotionContent(dialog, decorView)
                            }
                        } catch (_: Throwable) {}
                    }
                })
            Log.record(TAG, "Hook 广告/促销弹窗成功")
        } catch (e: Throwable) {
            Log.record(TAG, "Hook 广告/促销弹窗失败: ${e.message}")
        }
    }

    private fun checkAndDismissPromotionContent(dialog: android.app.Dialog, view: android.view.View) {
        try {
            val promotionKwds = arrayOf(
                "领红包", "限时优惠", "新人专享", "立即领取",
                "去看看", "去逛逛", "福利", "红包雨", "优惠券",
                "专享价", "限时抢", "秒杀", "特价", "超值",
                "热门推荐", "为您推荐", "猜你喜欢"
            )
            val viewStr = view.toString().lowercase()
            for (kw in promotionKwds) {
                if (viewStr.contains(kw.lowercase())) {
                    Log.record(TAG, "检测到促销内容弹窗，自动关闭: $kw")
                    scheduleDismiss(dialog, 0)
                    return
                }
            }
        } catch (_: Throwable) {}
    }

    /**
     * Hook 权限请求弹窗 - 自动关闭
     */
    private fun hookPermissionDialog(loader: ClassLoader) {
        try {
            XposedHelpers.findAndHookMethod(
                "com.alipay.mobile.nebula.basebridge.H5BasePage",
                loader,
                "loadUrl",
                String::class.java,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val url = param.args[0] as? String ?: return
                        if (url.contains("permission") ||
                            url.contains("authorize") ||
                            url.contains("grantAuth") ||
                            url.contains("userAuthorization") ||
                            url.contains("protocolSign") ||
                            url.contains("agreementSign")
                        ) {
                            Log.record(TAG, "拦截权限请求页面: $url")
                            param.result = null
                        }
                    }
                })
            Log.record(TAG, "Hook 权限请求弹窗成功")
        } catch (e: Throwable) {
            Log.record(TAG, "Hook 权限请求弹窗失败: ${e.message}")
        }
    }

    /**
     * Hook 版本更新弹窗 - 自动关闭
     */
    private fun hookUpdateDialog(loader: ClassLoader) {
        try {
            XposedHelpers.findAndHookMethod(
                "android.app.Dialog",
                loader,
                "show",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        try {
                            val dialog = param.thisObject as? android.app.Dialog ?: return
                            val className = dialog.javaClass.name
                            if (className.contains("UpdateDialog") ||
                                className.contains("UpgradeDialog") ||
                                className.contains("VersionDialog") ||
                                className.contains("NewVersion")
                            ) {
                                Log.record(TAG, "自动关闭版本更新弹窗: $className")
                                scheduleDismiss(dialog, 0)
                                param.result = null
                            }
                        } catch (_: Throwable) {}
                    }
                })
            Log.record(TAG, "Hook 版本更新弹窗成功")
        } catch (e: Throwable) {
            Log.record(TAG, "Hook 版本更新弹窗失败: ${e.message}")
        }
    }

    /**
     * Hook 通用Dialog.show() - 自动关闭已知干扰弹窗
     * 基于弹窗标题/内容文本检测
     */
    private fun hookGeneralDialogDismiss(loader: ClassLoader) {
        try {
            val dialogBlacklist = arrayOf(
                "PrivacyDialog", "AgreementDialog", "ProtocolDialog",
                "GuideDialog", "TutorialDialog", "RatingDialog",
                "FeedbackDialog", "SurveyDialog", "InviteDialog",
                "ShareDialog", "BindCardDialog", "RealNameDialog",
                "FaceVerifyDialog", "IdCardDialog"
            )
            XposedHelpers.findAndHookMethod(
                "android.app.AlertDialog",
                loader,
                "show",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        try {
                            val dialog = param.thisObject as? android.app.AlertDialog ?: return
                            val className = dialog.javaClass.name
                            for (kw in dialogBlacklist) {
                                if (className.contains(kw)) {
                                    Log.record(TAG, "自动关闭干扰弹窗: $className")
                                    scheduleDismiss(dialog, 0)
                                    param.result = null
                                    return
                                }
                            }
                        } catch (_: Throwable) {}
                    }
                })

            XposedHelpers.findAndHookMethod(
                "android.app.AlertDialog\$Builder",
                loader,
                "show",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        try {
                            val dialog = param.result as? android.app.AlertDialog ?: return
                            val className = dialog.javaClass.name
                            for (kw in dialogBlacklist) {
                                if (className.contains(kw)) {
                                    Log.record(TAG, "自动关闭AlertDialog.Builder干扰弹窗: $className")
                                    scheduleDismiss(dialog, 0)
                                    return
                                }
                            }
                        } catch (_: Throwable) {}
                    }
                })
            Log.record(TAG, "Hook 通用弹窗自动关闭成功")
        } catch (e: Throwable) {
            Log.record(TAG, "Hook 通用弹窗自动关闭失败: ${e.message}")
        }
    }

    private val dismissExecutor = Executors.newSingleThreadScheduledExecutor()
    private val slideExecutor = Executors.newSingleThreadScheduledExecutor()

    private fun scheduleDismiss(dialog: android.app.Dialog, delayMs: Long) {
        dismissExecutor.schedule({
            try {
                if (dialog.isShowing) {
                    dialog.dismiss()
                }
            } catch (_: Throwable) {}
        }, delayMs, TimeUnit.MILLISECONDS)
    }

    /**
     * 卸载所有 Hook（如果需要）
     */
    fun unload() {
        hookInstalled = false
        dismissExecutor.shutdownNow()
        slideExecutor.shutdownNow()
        Log.record(TAG, "滑块绕过 Hook 已卸载")
    }
}
