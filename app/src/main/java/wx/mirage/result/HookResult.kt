package wx.mirage.result

/**
 * Hook 初始化结果密封类
 *
 * 用于 Hook 模块的 init() 方法返回明确的初始化结果，
 * 替代通过回调（onHookRegistered / onHookFailed）隐式传递状态的方式。
 *
 * 使用示例:
 * ```kotlin
 * fun init(lpparam: XC_LoadPackage.LoadPackageParam): HookResult {
 *     return try {
 *         // 注册 Hook 逻辑...
 *         HookResult.Success("ContactHook", "All hooks registered")
 *     } catch (e: Exception) {
 *         HookResult.Failure("ContactHook", e)
 *     }
 * }
 * ```
 */
sealed class HookResult {

    /**
     * 获取 Hook 模块名称。
     */
    abstract val moduleName: String

    /**
     * 获取人类可读的结果消息。
     */
    abstract val message: String

    /**
     * 判断是否为成功结果（包括降级）。
     */
    val isSuccess: Boolean get() = this is Success || this is Degraded

    /**
     * 判断是否为失败结果。
     */
    val isFailure: Boolean get() = this is Failure

    /**
     * Hook 初始化成功。
     *
     * @param moduleName Hook 模块名称
     * @param data 附加数据（如注册的 Hook 数量等）
     */
    data class Success(
        override val moduleName: String,
        val data: Any? = null
    ) : HookResult() {
        override val message: String = "Hook '$moduleName' initialized successfully" +
            if (data != null) " (data: $data)" else ""
    }

    /**
     * Hook 初始化失败。
     *
     * @param moduleName Hook 模块名称
     * @param error 导致失败的异常
     */
    data class Failure(
        override val moduleName: String,
        val error: Throwable
    ) : HookResult() {
        override val message: String = "Hook '$moduleName' initialization failed: ${error.message}"
    }

    /**
     * Hook 初始化部分成功（降级模式）。
     *
     * 例如：DexKit 不可用，使用 fallback 直接类加载策略。
     *
     * @param moduleName Hook 模块名称
     * @param data 附加数据
     * @param reason 降级原因
     */
    data class Degraded(
        override val moduleName: String,
        val data: Any? = null,
        val reason: String
    ) : HookResult() {
        override val message: String = "Hook '$moduleName' initialized in degraded mode: $reason" +
            if (data != null) " (data: $data)" else ""
    }
}