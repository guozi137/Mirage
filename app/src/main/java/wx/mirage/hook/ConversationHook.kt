package wx.mirage.hook

import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import wx.mirage.Constants
import wx.mirage.MainHook
import wx.mirage.config.HookStatus
import wx.mirage.config.WxIdBlacklist
import wx.mirage.lifecycle.HookLifecycleListener
import wx.mirage.util.HookMetrics
import wx.mirage.util.LogUtil

/**
 * 会话列表 Hook 模块
 *
 * 功能：拦截会话列表加载，隐藏黑名单好友的会话项。
 *
 * 状态管理：使用 [HookStatus] 枚举统一管理模块状态。
 */
object ConversationHook : HookLifecycleListener {

    private const val TAG = Constants.MODULE_TAG + ":ConversationHook"

    /**
     * 当前 Hook 模块状态。
     * 线程安全：标记为 @Volatile，保证跨线程可见性。
     */
    @Volatile
    var status: HookStatus = HookStatus.INACTIVE
        private set

    @Volatile
    private var cacheWarmedUp: Boolean = false

    private var targetClass: Class<*>? = null
    private var targetMethodName: String? = null

    fun init(lpparam: XC_LoadPackage.LoadPackageParam) {
        val classLoader = lpparam.classLoader

        try {
            if (MainHook.dexKitAvailable && MainHook::dexKitBridge.isInitialized) {
                initWithDexKit(lpparam)
            } else {
                initFallback(classLoader)
            }
            status = if (MainHook.dexKitAvailable) HookStatus.ACTIVE else HookStatus.DEGRADED
            onHookRegistered()
        } catch (e: Throwable) {
            LogUtil.w(TAG, "DexKit init failed, using fallback: ${e.message}")
            try {
                initFallback(classLoader)
                status = HookStatus.DEGRADED
                onHookRegistered()
            } catch (e2: Throwable) {
                LogUtil.e(TAG, "Fallback also failed: ${e2.message}", e2)
                status = HookStatus.ERROR
                onHookFailed(e2)
            }
        }
    }

    private fun initWithDexKit(lpparam: XC_LoadPackage.LoadPackageParam) {
        val classLoader = lpparam.classLoader

        val conversationClass = MainHook.dexKitBridge.findClass {
            searchString = "conversation"
            searchPackage = Constants.WECHAT_UI_CONVERSATION
        }

        if (conversationClass != null) {
            targetClass = classLoader.loadClass(conversationClass.name)
            targetMethodName = "onResume"
            cacheWarmedUp = true
            LogUtil.i(TAG, "DexKit found: ${conversationClass.name}")
        }
    }

    private fun initFallback(classLoader: ClassLoader) {
        val candidates = listOf(
            "${Constants.WECHAT_UI_CONVERSATION}.ConversationListUI",
            "${Constants.WECHAT_UI_CONVERSATION}.ConversationFragment"
        )

        for (className in candidates) {
            try {
                targetClass = classLoader.loadClass(className)
                targetMethodName = "onResume"
                LogUtil.i(TAG, "Fallback found: $className")
                return
            } catch (_: ClassNotFoundException) {
                // 继续尝试
            }
        }
    }

    override fun onHookRegistered() {
        LogUtil.i(TAG, "ConversationHook registered (status: ${status.description})")
        targetClass?.let { clazz ->
            targetMethodName?.let { method ->
                XposedHelpers.findAndHookMethod(clazz, method, object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        filterConversations(param)
                    }
                })
            }
        }
    }

    override fun onHookFailed(error: Throwable) {
        LogUtil.e(TAG, "ConversationHook failed: ${error.message}", error)
        status = HookStatus.ERROR
    }

    override fun onHookUnregistered() {
        XposedBridge.unhookMethod(targetClass?.getDeclaredMethod(targetMethodName) ?: return)
        status = HookStatus.INACTIVE
        LogUtil.i(TAG, "ConversationHook unregistered")
    }

    @JvmStatic
    fun clearDexKitCache() {
        cacheWarmedUp = false
        targetClass = null
        targetMethodName = null
        LogUtil.d(TAG, "DexKit cache cleared")
    }

    private fun filterConversations(param: XC_MethodHook.MethodHookParam) {
        try {
            val blacklist = WxIdBlacklist.getWxIds()
            if (blacklist.isEmpty()) return

            HookMetrics.recordHookExecution(TAG)
            LogUtil.d(TAG, "Filtering conversations, blacklist size: ${blacklist.size}")
        } catch (e: Throwable) {
            LogUtil.e(TAG, "Error filtering conversations: ${e.message}", e)
        }
    }
}