package wx.mirage.config

/**
 * Hook 模块状态枚举
 *
 * 用于统一表示 Hook 模块的当前运行状态，替代分散的 boolean 标志。
 *
 * 状态说明:
 * - [ACTIVE]: Hook 已成功注册并正常运行
 * - [INACTIVE]: Hook 未注册或已卸载
 * - [DEGRADED]: Hook 已注册但部分功能降级（如 DexKit 不可用，使用 fallback 策略）
 * - [ERROR]: Hook 注册失败或运行中出现严重错误
 */
enum class HookStatus {
    /** Hook 已成功注册并正常运行 */
    ACTIVE,
    /** Hook 未注册或已卸载 */
    INACTIVE,
    /** Hook 已注册但部分功能降级（如 DexKit 不可用，使用 fallback 策略） */
    DEGRADED,
    /** Hook 注册失败或运行中出现严重错误 */
    ERROR;

    /**
     * 判断 Hook 是否处于可用状态（ACTIVE 或 DEGRADED）。
     */
    val isAvailable: Boolean get() = this == ACTIVE || this == DEGRADED

    /**
     * 判断 Hook 是否已完全失败。
     */
    val isFailed: Boolean get() = this == ERROR

    /**
     * 返回人类可读的状态描述。
     */
    val description: String
        get() = when (this) {
            ACTIVE -> "Active"
            INACTIVE -> "Inactive"
            DEGRADED -> "Degraded"
            ERROR -> "Error"
        }
}