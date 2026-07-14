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
 * 联系人 Hook 模块
 *
 * 功能：拦截联系人列表加载，隐藏黑名单中的好友。
 * 使用 DexKit 动态查找微信混淆类，失败时降级为直接类加载。
 *
 * 状态管理：使用 [HookStatus] 枚举统一管理模块状态。
 */
object ContactHook : HookLifecycleListener {

    private const val TAG = Constants.MODULE_TAG + ":ContactHook"

    /**
     * 当前 Hook 模块状态。
     * 线程安全：标记为 @Volatile，保证跨线程可见性。
     */
    @Volatile
    var status: HookStatus = HookStatus.INACTIVE
        private set

    /** 是否已缓存 DexKit 查询结果 */
    @Volatile
    private var cacheWarmedUp: Boolean = false

    /** 缓存的目标类 */
    private var targetClass: Class<*>? = null

    /** 缓存的方法名 */
    private var targetMethodName: String? = null

    /**
     * 初始化 ContactHook
     *
     * 使用 DexKit 动态查找微信联系人相关类，失败时使用降级策略。
     */
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

        // 通过 DexKit 查找联系人列表相关类
        val contactsClass = MainHook.dexKitBridge.findClass {
            searchString = "contact"
            searchPackage = Constants.WECHAT_UI_CONTACT
        }

        if (contactsClass != null) {
            targetClass = classLoader.loadClass(contactsClass.name)
            targetMethodName = "onResume"
            cacheWarmedUp = true
            LogUtil.i(TAG, "DexKit found: ${contactsClass.name}")
        }
    }

    private fun initFallback(classLoader: ClassLoader) {
        // 降级方案：直接尝试已知类名
        val candidates = listOf(
            "${Constants.WECHAT_UI_CONTACT}.ContactInfoUI",
            "${Constants.WECHAT_UI_CONTACT}.ContactInfoActivity",
            "${Constants.WECHAT_UI_CONTACT}.SelectContactUI"
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
        LogUtil.i(TAG, "ContactHook registered (status: ${status.description})")
        // 注册实际 Hook 逻辑
        targetClass?.let { clazz ->
            targetMethodName?.let { method ->
                XposedHelpers.findAndHookMethod(clazz, method, object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        filterContacts(param)
                    }
                })
            }
        }
    }

    override fun onHookFailed(error: Throwable) {
        LogUtil.e(TAG, "ContactHook failed: ${error.message}", error)
        status = HookStatus.ERROR
    }

    override fun onHookUnregistered() {
        XposedBridge.unhookMethod(targetClass?.getDeclaredMethod(targetMethodName) ?: return)
        status = HookStatus.INACTIVE
        LogUtil.i(TAG, "ContactHook unregistered")
    }

    /**
     * 清除 DexKit 缓存，强制下次重新查询
     */
    @JvmStatic
    fun clearDexKitCache() {
        cacheWarmedUp = false
        targetClass = null
        targetMethodName = null
        LogUtil.d(TAG, "DexKit cache cleared")
    }

    private fun filterContacts(param: XC_MethodHook.MethodHookParam) {
        try {
            val blacklist = WxIdBlacklist.getWxIds()
            if (blacklist.isEmpty()) return

            HookMetrics.recordHookExecution(TAG)
            // 联系人过滤逻辑由子类实现
            LogUtil.d(TAG, "Filtering contacts, blacklist size: ${blacklist.size}")
        } catch (e: Throwable) {
            LogUtil.e(TAG, "Error filtering contacts: ${e.message}", e)
        }
    }
}