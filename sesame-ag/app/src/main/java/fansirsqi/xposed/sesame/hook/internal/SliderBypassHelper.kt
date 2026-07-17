package fansirsqi.xposed.sesame.hook.internal

import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.widget.TextView
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XC_MethodReplacement
import de.robv.android.xposed.XposedHelpers
import fansirsqi.xposed.sesame.hook.ApplicationHook
import fansirsqi.xposed.sesame.model.BaseModel
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

    fun init(loader: ClassLoader) {
        classLoader = loader
        Log.record(TAG, "滑块绕过助手初始化完成")
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
     * Hook NebulaTransActivity + 通用Activity - 滑块页面自动滑动验证
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
                            val isNebulaActivity = className.contains("NebulaTransActivity")
                            val isSchemeActivity = className.contains("SchemeStartActivity")
                            val isWebViewActivity = className.contains("WebViewActivity") || 
                                className.contains("H5Activity") || className.contains("BrowserActivity")

                            val intent = activity.intent
                            val url = intent?.dataString ?: intent?.getStringExtra("url") ?: ""

                            val isSliderPage = url.contains("slider") || url.contains("captcha") ||
                                url.contains("slideVerify") || url.contains("dragVerify") ||
                                url.contains("antcaptcha") || url.contains("slidingVerify") ||
                                url.contains("nc_verify") || url.contains("alipayVerify")

                            val isOtherVerify = url.contains("security") || url.contains("verify") ||
                                url.contains("risk") || url.contains("safePay")

                            val isAccessDenied = url.contains("accessDeny") || url.contains("denied") ||
                                url.contains("forbidden") || url.contains("requestfailure") ||
                                url.contains("noPermission") || url.contains("networkBlock") ||
                                url.contains("errorpage") || url.contains("vpnDetect") ||
                                url.contains("proxyCheck") || url.contains("accessRefuse")

                            // NebulaTransActivity 中的滑块检测
                            if (isNebulaActivity) {
                                if (isSliderPage && BaseModel.enableAutoSlideCaptcha.value) {
                                    Log.record(TAG, "NebulaTransActivity检测到滑块: $className")
                                    scheduleAutoSlide(activity, 800)
                                } else if (isSliderPage) {
                                    Log.record(TAG, "检测到滑块验证页面，自动滑动已关闭: $className")
                                } else if (isOtherVerify) {
                                    Log.record(TAG, "拦截非滑块验证页面: $className url=$url")
                                    activity.finish()
                                } else if (isAccessDenied) {
                                    Log.record(TAG, "拦截访问被拒绝/VPN检测页面: $className url=$url")
                                    activity.finish()
                                } else {
                                    // 兜底：检测页面标题/内容中的滑块关键词
                                    if (BaseModel.enableAutoSlideCaptcha.value) {
                                        checkActivityForSliderContent(activity, className)
                                    }
                                }
                                return
                            }

                            // 通用Activity滑块检测（非Nebula）
                            if (BaseModel.enableAutoSlideCaptcha.value) {
                                if (isSliderPage) {
                                    Log.record(TAG, "通用Activity检测到滑块页面: $className url=$url")
                                    scheduleAutoSlide(activity, 1500)
                                } else if (className.contains("Verify") || className.contains("Captcha")) {
                                    Log.record(TAG, "验证相关Activity: $className")
                                    scheduleAutoSlide(activity, 1200)
                                }
                                // 每次Activity打开都检测嵌入容器
                                checkEmbeddedContainer(activity)
                            }
                        } catch (_: Throwable) {}
                    }
                })
            Log.record(TAG, "Hook Activity.onResume (滑块检测) 成功")
        } catch (e: Throwable) {
            Log.record(TAG, "Hook Activity.onResume 失败: ${e.message}")
        }
    }

    /** 检测 Activity 内容中的滑块关键词 */
    private fun checkActivityForSliderContent(activity: android.app.Activity, className: String) {
        try {
            val decorView = activity.window?.decorView ?: return
            // 扫描视图树中的文本内容
            val texts = mutableListOf<String>()
            collectViewTexts(decorView, texts)
            val content = texts.joinToString(" ").lowercase()
            if (content.contains("滑块") || content.contains("滑动") ||
                content.contains("slider") || content.contains("slide") ||
                content.contains("drag") || content.contains("captcha") ||
                content.contains("人机验证") || content.contains("安全验证")) {
                Log.record(TAG, "Activity内容检测到滑块关键词: $className")
                scheduleAutoSlide(activity, 1000)
            }
        } catch (_: Throwable) {}
    }

    private fun collectViewTexts(view: View, result: MutableList<String>) {
        try {
            val contentDesc = view.contentDescription?.toString()
            if (!contentDesc.isNullOrBlank() && contentDesc.length < 100) {
                result.add(contentDesc)
            }
            if (view is android.widget.TextView) {
                val text = view.text?.toString()
                if (!text.isNullOrBlank() && text.length < 100) {
                    result.add(text)
                }
            }
        } catch (_: Throwable) {}
        if (result.size >= 50) return
        if (view is ViewGroup) {
            for (i in 0 until Math.min(view.childCount, 30)) {
                val child = view.getChildAt(i) ?: continue
                collectViewTexts(child, result)
            }
        }
    }

    private fun checkEmbeddedContainer(activity: android.app.Activity) {
        try {
            val decorView = activity.window?.decorView ?: return
            val containerId = decorView.context.resources.getIdentifier(
                "embeded_fragment_container", "id",
                "com.alipay.multiplatform.phone.xriver_integration"
            )
            if (containerId != 0 && decorView.findViewById<View>(containerId) != null) {
                if (BaseModel.enableAutoSlideCaptcha.value) {
                    Log.record(TAG, "检测到验证码容器，尝试自动滑动")
                    scheduleAutoSlide(activity, 1000)
                } else {
                    Log.record(TAG, "检测到验证码容器，自动滑动已关闭，保持页面等待手动操作")
                }
            }
        } catch (_: Throwable) {}
    }

    /**
     * 调度自动滑动任务（带多轮重试）
     * @param delayMs 延迟毫秒数，等待页面渲染完成
     */
    private fun scheduleAutoSlide(activity: android.app.Activity, delayMs: Long) {
        slideExecutor.schedule({
            tryAutoSlideWithRetry(activity, 3)
        }, delayMs, TimeUnit.MILLISECONDS)
    }

    /**
     * 多轮重试自动滑动：视图查找 -> 坐标估算 -> WebView检测
     */
    private fun tryAutoSlideWithRetry(activity: android.app.Activity, maxAttempts: Int) {
        for (attempt in 1..maxAttempts) {
            try {
                Log.record(TAG, "自动滑动尝试 $attempt/$maxAttempts")
                val result = performAutoSlideGesture(activity)
                if (result) {
                    Log.record(TAG, "自动滑动成功 (第${attempt}次)")
                    return
                }
                if (attempt < maxAttempts) {
                    Log.record(TAG, "自动滑动失败，等待 ${attempt * 1200}ms 后重试")
                    Thread.sleep((attempt * 1200).toLong())
                }
            } catch (e: Throwable) {
                Log.record(TAG, "自动滑动异常(第${attempt}次): ${e.message}")
                if (attempt < maxAttempts) {
                    Thread.sleep((attempt * 1200).toLong())
                }
            }
        }
        Log.record(TAG, "自动滑动全部尝试失败，保持页面等待手动操作")
    }

    /**
     * 执行自动滑动验证手势 - 多策略尝试
     * 策略1: 视图层级查找滑块控件
     * 策略2: WebView JavaScript 检测并操作
     * 策略3: 坐标推算回退
     * @return true if slide was performed, false if all strategies failed
     */
    private fun performAutoSlideGesture(activity: android.app.Activity): Boolean {
        val decorView = activity.window?.decorView ?: return false

        // 策略1: 视图层级查找
        val sliderView = findSliderThumbView(decorView)
        if (sliderView != null) {
            val containerView = findSliderContainer(decorView, sliderView)
            val slideDistance = if (containerView != null) {
                computeSlideDistance(containerView, sliderView)
            } else {
                decorView.width * 0.62f
            }
            if (slideDistance > 0) {
                Log.record(TAG, "策略1-视图查找: 找到滑块, distance=$slideDistance")
                executeTouchGesture(sliderView, slideDistance)
                return true
            }
        }

        // 策略2: 尝试 WebView 内部滑块
        if (tryWebViewSliderGesture(decorView)) {
            return true
        }

        // 策略3: 坐标推算回退 - 尝试多个可能位置
        Log.record(TAG, "策略1/2失败，使用坐标推算回退")
        for (yRatio in listOf(0.68f, 0.72f, 0.65f, 0.75f, 0.70f)) {
            val result = performCoordinateSlide(decorView, yRatio)
            if (result) return true
            Thread.sleep(40)
        }

        return false
    }

    /**
     * 在视图树中递归搜索滑块控件
     */
    private fun findSliderThumbView(root: View): View? {
        val sliderClassKeywords = arrayOf(
            "Slider", "Slide", "Drag", "Thumb",
            "Captcha", "Verify", "Seek"
        )
        val sliderIdKeywords = arrayOf(
            "slider", "slide", "thumb", "drag",
            "captcha", "verify", "seek", "btn"
        )
        val candidateViews = mutableListOf<View>()

        collectSliderCandidates(root, sliderClassKeywords, sliderIdKeywords, candidateViews)

        if (candidateViews.isEmpty()) {
            Log.record(TAG, "无候选滑块视图，尝试全树搜索")
            collectAllClickableViews(root, candidateViews)
            candidateViews.sortBy { v ->
                val loc = IntArray(2)
                v.getLocationOnScreen(loc)
                loc[0]
            }
        } else {
            candidateViews.sortBy { v ->
                val loc = IntArray(2)
                v.getLocationOnScreen(loc)
                loc[0]
            }
            val firstX = IntArray(2).also { candidateViews.first().getLocationOnScreen(it) }[0]
            if (firstX > root.width * 0.4f) {
                candidateViews.sortBy { v ->
                    val loc = IntArray(2)
                    v.getLocationOnScreen(loc)
                    -loc[0]
                }
            }
        }

        return candidateViews.firstOrNull { v ->
            val loc = IntArray(2)
            v.getLocationOnScreen(loc)
            v.width > 0 && v.height > 0 && loc[1] > 0
        }
    }

    private fun collectSliderCandidates(
        view: View,
        classKeywords: Array<String>,
        idKeywords: Array<String>,
        candidates: MutableList<View>
    ) {
        val className = view.javaClass.name.lowercase()
        val idName = try {
            view.resources.getResourceEntryName(view.id).lowercase()
        } catch (_: Throwable) { "" }

        val matchesClass = classKeywords.any { className.contains(it.lowercase()) }
        val matchesId = idKeywords.any { idName.contains(it.lowercase()) }
        val isClickable = view.isClickable || view.isFocusable

        if ((matchesClass || matchesId) && isClickable) {
            candidates.add(view)
        }

        if (candidates.size >= 20) return

        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                val child = view.getChildAt(i) ?: continue
                collectSliderCandidates(child, classKeywords, idKeywords, candidates)
            }
        }
    }

    private fun collectAllClickableViews(view: View, candidates: MutableList<View>) {
        if (view.isClickable && view.width in 40..200 && view.height in 40..200) {
            candidates.add(view)
        }
        if (candidates.size >= 50) return
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                val child = view.getChildAt(i) ?: continue
                collectAllClickableViews(child, candidates)
            }
        }
    }

    /**
     * 查找滑块容器（滑轨区域）
     */
    private fun findSliderContainer(root: View, sliderView: View): View? {
        var parent = sliderView.parent
        while (parent != null && parent is View) {
            val p = parent as View
            if (p.width in 200..(root.width) && p.height in 40..200) {
                val bg = p.background
                if (bg != null) return p
            }
            parent = p.parent
        }
        return root
    }

    /**
     * 计算滑块需要滑动的距离
     */
    private fun computeSlideDistance(container: View, sliderView: View): Float {
        val containerLoc = IntArray(2)
        val sliderLoc = IntArray(2)
        container.getLocationOnScreen(containerLoc)
        sliderView.getLocationOnScreen(sliderLoc)

        val containerRight = containerLoc[0] + container.width
        val sliderRight = sliderLoc[0] + sliderView.width
        val rawDistance = (containerRight - sliderRight).toFloat()

        return when {
            rawDistance <= 0 -> container.width * 0.65f
            rawDistance > container.width -> container.width * 0.85f
            else -> rawDistance + sliderView.width * 0.3f
        }
    }

    /**
     * 策略2: 尝试在 WebView 中查找滑块并执行滑动
     */
    private fun tryWebViewSliderGesture(root: View): Boolean {
        try {
            val webViews = mutableListOf<View>()
            collectWebViews(root, webViews)
            if (webViews.isEmpty()) return false

            for (webView in webViews) {
                if (webView !is ViewGroup) continue
                val thumbView = findSliderInWebViewContainer(webView)
                if (thumbView != null) {
                    Log.record(TAG, "策略2-WebView: 在WebView容器中找到滑块控件")
                    val slideDistance = root.width * 0.62f
                    executeTouchGesture(thumbView, slideDistance)
                    return true
                }
            }

            // WebView 存在但无可见滑块，尝试 WebView 坐标滑动
            if (webViews.isNotEmpty()) {
                val wv = webViews.first()
                Log.record(TAG, "策略2-WebView: 尝试WebView坐标滑动")
                return performCoordinateSlide(wv, 0.68f)
            }
        } catch (e: Throwable) {
            Log.record(TAG, "WebView滑块检测异常: ${e.message}")
        }
        return false
    }

    /**
     * 收集视图树中所有 WebView
     */
    private fun collectWebViews(view: View, result: MutableList<View>) {
        if (view.javaClass.name.contains("WebView") || view.javaClass.name.contains("NebulaWebView")) {
            result.add(view)
        }
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                val child = view.getChildAt(i) ?: continue
                collectWebViews(child, result)
            }
        }
    }

    /**
     * 在 WebView 容器内部递归查找滑块控件
     */
    private fun findSliderInWebViewContainer(container: ViewGroup): View? {
        val sliderClassKeywords = arrayOf("Slider", "Slide", "Drag", "Thumb", "Captcha", "Verify", "Seek")
        val sliderIdKeywords = arrayOf("slider", "slide", "thumb", "drag", "captcha", "verify", "seek", "btn")

        val candidates = mutableListOf<View>()
        collectSliderCandidates(container, sliderClassKeywords, sliderIdKeywords, candidates)
        if (candidates.isNotEmpty()) {
            return candidates.firstOrNull { v ->
                val loc = IntArray(2)
                v.getLocationOnScreen(loc)
                v.width in 40..200 && v.height in 40..200
            }
        }

        // 回退：找 WebView 中任意位置在屏幕中下部区域的可点击视图
        val allViews = mutableListOf<View>()
        collectSliderCandidatesInWebView(container, allViews)
        return allViews.firstOrNull { v ->
            val loc = IntArray(2)
            v.getLocationOnScreen(loc)
            v.width in 40..200 && v.height in 40..200 &&
                loc[1] > container.height * 0.5f && loc[1] < container.height * 0.85f
        }
    }

    private fun collectSliderCandidatesInWebView(view: View, result: MutableList<View>) {
        if (view.isClickable && view.width in 40..200 && view.height in 40..200) {
            result.add(view)
        }
        if (result.size >= 30) return
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                val child = view.getChildAt(i) ?: continue
                collectSliderCandidatesInWebView(child, result)
            }
        }
    }

    /**
     * 策略3: 坐标推算回退滑动
     * 在指定 Y 位置执行水平滑动手势
     */
    private fun performCoordinateSlide(view: View, yRatio: Float): Boolean {
        try {
            val screenWidth = view.width.toFloat()
            val screenHeight = view.height.toFloat()
            if (screenWidth <= 0 || screenHeight <= 0) return false

            val startX = screenWidth * 0.12f
            val endX = screenWidth * 0.88f
            val y = screenHeight * yRatio
            val slideDistance = endX - startX

            if (slideDistance <= 0) return false

            Log.record(TAG, "坐标滑动: startX=$startX endX=$endX y=$y yRatio=$yRatio")
            executeCoordinateTouchGesture(view, startX, y, slideDistance)
            return true
        } catch (e: Throwable) {
            Log.record(TAG, "坐标滑动异常: ${e.message}")
        }
        return false
    }

    /**
     * 基于绝对坐标执行触摸滑动手势（用于无视图引用的场景）
     */
    private fun executeCoordinateTouchGesture(view: View, startX: Float, startY: Float, totalDistance: Float) {
        val downTime = SystemClock.uptimeMillis()
        val endX = startX + totalDistance

        val downEvent = MotionEvent.obtain(
            downTime, downTime, MotionEvent.ACTION_DOWN, startX, startY, 0
        )
        view.dispatchTouchEvent(downEvent)
        downEvent.recycle()

        val totalSteps = 18 + (5..10).random()
        val baseStepMs = 6L + (0..4).random()
        var currentX = startX
        var eventTime = downTime + 50

        for (i in 1..totalSteps) {
            val progress = i.toFloat() / totalSteps
            val easeProgress = easeProgress(progress)
            currentX = startX + totalDistance * easeProgress
            val jitter = ((Math.random() - 0.5) * 4).toFloat()

            val stepDuration = when {
                progress < 0.15f -> baseStepMs + (5..12).random()
                progress < 0.85f -> baseStepMs + (2..6).random()
                else -> baseStepMs + (10..20).random()
            }
            eventTime += stepDuration

            val moveEvent = MotionEvent.obtain(
                downTime, eventTime,
                MotionEvent.ACTION_MOVE, currentX, startY + jitter, 0
            )
            view.dispatchTouchEvent(moveEvent)
            moveEvent.recycle()

            try { Thread.sleep(stepDuration) } catch (_: Throwable) {}
        }

        val overShoot = 3f + (0..5).random()
        eventTime += 40
        val lastMoveEvent = MotionEvent.obtain(
            downTime, eventTime,
            MotionEvent.ACTION_MOVE, endX + overShoot, startY, 0
        )
        view.dispatchTouchEvent(lastMoveEvent)
        lastMoveEvent.recycle()

        eventTime += 20 + (0..30).random()
        val upEvent = MotionEvent.obtain(
            downTime, eventTime,
            MotionEvent.ACTION_UP, endX + overShoot, startY, 0
        )
        view.dispatchTouchEvent(upEvent)
        upEvent.recycle()
    }

    /**
     * 执行模拟触摸手势滑动
     * 模拟人类滑动特征: 加速启动 -> 匀速 -> 微调 → 释放
     */
    private fun executeTouchGesture(view: View, totalDistance: Float) {
        val loc = IntArray(2)
        view.getLocationOnScreen(loc)
        val startX = loc[0] + view.width / 2f
        val startY = loc[1] + view.height / 2f
        val endX = startX + totalDistance
        val downTime = SystemClock.uptimeMillis()

        val downEvent = MotionEvent.obtain(
            downTime, downTime,
            MotionEvent.ACTION_DOWN, startX, startY, 0
        )
        view.dispatchTouchEvent(downEvent)
        downEvent.recycle()

        val totalSteps = 15 + (5..15).random()
        val baseStepMs = 6L + (0..4).random()
        var currentX = startX
        var currentY = startY
        var eventTime = downTime + 50

        for (i in 1..totalSteps) {
            val progress = i.toFloat() / totalSteps
            val easeProgress = easeProgress(progress)

            currentX = startX + totalDistance * easeProgress

            val jitter = ((Math.random() - 0.5) * 4).toFloat()
            currentY = startY + jitter

            val stepDuration = when {
                progress < 0.15f -> baseStepMs + (5..12).random()
                progress < 0.85f -> baseStepMs + (2..6).random()
                else -> baseStepMs + (10..20).random()
            }
            eventTime += stepDuration

            val moveEvent = MotionEvent.obtain(
                downTime, eventTime,
                MotionEvent.ACTION_MOVE, currentX, currentY, 0
            )
            view.dispatchTouchEvent(moveEvent)
            moveEvent.recycle()

            try { Thread.sleep(stepDuration) } catch (_: Throwable) {}
        }

        val overShoot = 3f + (0..5).random()
        eventTime += 40
        val lastMoveEvent = MotionEvent.obtain(
            downTime, eventTime,
            MotionEvent.ACTION_MOVE, endX + overShoot, startY, 0
        )
        view.dispatchTouchEvent(lastMoveEvent)
        lastMoveEvent.recycle()

        eventTime += 20 + (0..30).random()
        val upEvent = MotionEvent.obtain(
            downTime, eventTime,
            MotionEvent.ACTION_UP, endX + overShoot, startY, 0
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
            // 使用 H5BasePage 具体实现类（H5Page 是接口，不能直接 Hook）
            var hooked = false
            // 尝试 H5BasePage
            try {
                XposedHelpers.findAndHookMethod(
                    "com.alipay.mobile.nebula.basebridge.H5BasePage",
                    loader,
                    "loadUrl",
                    String::class.java,
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam) {
                            val url = param.args[0] as? String ?: return
                            if (url.contains("securityVerify") ||
                                url.contains("slidingVerify") ||
                                url.contains("captcha") ||
                                url.contains("riskVerify") ||
                                url.contains("ariver/verify") ||
                                url.contains("accessDeny") ||
                                url.contains("denied") ||
                                url.contains("forbidden") ||
                                url.contains("requestfailure") ||
                                url.contains("noPermission") ||
                                url.contains("networkBlock") ||
                                url.contains("errorpage") ||
                                url.contains("vpnDetect") ||
                                url.contains("proxyCheck") ||
                                url.contains("accessRefuse")
                            ) {
                                Log.record(TAG, "H5BasePage 拦截安全验证/拒绝访问页面: $url")
                                param.result = null
                            }
                        }
                    })
                hooked = true
                Log.record(TAG, "Hook H5BasePage.loadUrl 成功")
            } catch (_: Throwable) {}

            // 备用：尝试 H5WebView 的 loadUrl
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
                                if (url.contains("securityVerify") ||
                                    url.contains("slidingVerify") ||
                                    url.contains("captcha") ||
                                    url.contains("riskVerify") ||
                                    url.contains("ariver/verify") ||
                                    url.contains("accessDeny") ||
                                    url.contains("denied") ||
                                    url.contains("forbidden") ||
                                    url.contains("requestfailure") ||
                                    url.contains("noPermission") ||
                                    url.contains("networkBlock") ||
                                    url.contains("errorpage") ||
                                    url.contains("vpnDetect") ||
                                    url.contains("proxyCheck") ||
                                    url.contains("accessRefuse")
                                ) {
                                    Log.record(TAG, "H5WebView 拦截安全验证/拒绝访问页面: $url")
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
            // Hook 新版 RPC 通道: RpcBridgeExtension.rpc()
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
                        val method = param.args[0] as? String ?: return
                        if (!method.contains("antcaptcha")) return
                        Log.record(TAG, "拦截 antcaptcha: $method")
                        try {
                            val cb = param.args[15]
                            if (cb != null) {
                                // 直接调用 sendJSONResponse 返回假成功
                                val fakeJson = jsonClass.newInstance()
                                jsonClass.getMethod("put", String::class.java, Object::class.java)
                                    .invoke(fakeJson, "success", true)
                                jsonClass.getMethod("put", String::class.java, Object::class.java)
                                    .invoke(fakeJson, "data", "{}")
                                XposedHelpers.callMethod(cb, "sendJSONResponse", fakeJson)
                                param.result = null // 阻止原始调用
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
