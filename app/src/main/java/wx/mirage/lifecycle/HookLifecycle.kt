package wx.mirage.lifecycle

/**
 * Hook 生命周期监听器接口
 *
 * 定义 Hook 模块在注册、失败、卸载等生命周期事件中的回调方法。
 * 所有 Hook 类（ContactHook, ConversationHook, GroupMemberHook,
 * MomentsHook, NotificationHook, SearchHook）均应实现此接口，
 * 以便 MainHook 统一管理和追踪 Hook 状态。
 *
 * 使用方式:
 *   class MyHook : HookLifecycleListener {
 *       override fun onHookRegistered() { ... }
 *       override fun onHookFailed(error: Throwable) { ... }
 *       override fun onHookUnregistered() { ... }
 *   }
 */
interface HookLifecycleListener {

    /**
     * 当 Hook 成功注册时调用。
     *
     * 调用时机：Hook 模块的 init() 方法正常完成所有子 Hook 注册后。
     * 典型用途：更新状态计数器、记录日志、触发后续初始化。
     */
    fun onHookRegistered()

    /**
     * 当 Hook 注册失败时调用。
     *
     * 调用时机：Hook 模块的 init() 方法中任何子步骤抛出异常时。
     * 注意：此回调应在仍能捕获异常的位置调用，以便获取完整的错误信息。
     *
     * @param error 导致注册失败的 Throwable 对象
     */
    fun onHookFailed(error: Throwable)

    /**
     * 当 Hook 被卸载时调用。
     *
     * 调用时机：模块关闭或清理资源时（如 shutdown() 中）。
     * 典型用途：释放资源、清除缓存、重置状态。
     */
    fun onHookUnregistered()
}