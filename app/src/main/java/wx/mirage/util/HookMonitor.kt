package wx.mirage.util

import wx.mirage.Constants
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Mirage Hook 调用频率监控器
 *
 * 监控每个 Hook 模块的调用频率，检测潜在的性能问题。
 * 当某个 Hook 在 1 秒内被调用超过 100 次时，通过 Xposed 日志发出告警。
 *
 * ## 功能列表
 * - [recordInvocation]: 记录一次 Hook 调用，自动跟踪频率和健康状态
 * - [getInvocationRate]: 获取指定 Hook 的每秒调用频率
 * - [getHealthStatus]: 获取指定 Hook 的健康状态
 * - [getAllHealthStatuses]: 获取所有 Hook 的健康状态汇总
 * - [reset]: 重置所有计数器
 * - [getHealthReport]: 获取格式化的健康报告
 *
 * ## 健康状态
 * - HEALTHY: 正常（调用频率 < 50/s）
 * - WARNING: 警告（调用频率 50-100/s）
 * - CRITICAL: 严重（调用频率 > 100/s，会触发 Xposed 日志告警）
 * - UNKNOWN: 未知（尚无调用记录）
 *
 * ## 频率计算
 * 使用滑动窗口方式计算每秒调用频率：
 * - 记录每个 Hook 在当前秒内的调用次数
 * - 每秒开始时重置计数器
 * - 频率 = 当前秒内调用次数 / 1秒
 *
 * ## 使用示例
 * ```kotlin
 * // 在 Hook 回调中记录调用
 * HookMonitor.recordInvocation("ContactHook.filterContactList")
 *
 * // 检查健康状态
 * val status = HookMonitor.getHealthStatus("ContactHook.filterContactList")
 * if (status == HookMonitor.HealthStatus.CRITICAL) {
 *     // 处理性能问题
 * }
 * ```
 */
object HookMonitor {

    private const val TAG = Constants.MODULE_TAG + ":HookMonitor"

    /** 每秒调用次数阈值，超过此值触发告警 */
    const val ALERT_THRESHOLD_PER_SECOND = 100L

    /** 警告阈值（每秒调用次数） */
    const val WARNING_THRESHOLD_PER_SECOND = 50L

    /** 频率计算窗口大小（毫秒），用于计算每秒调用频率 */
    private const val FREQUENCY_WINDOW_MS = 1000L

    /**
     * Hook 健康状态枚举。
     */
    enum class HealthStatus {
        /** 正常 - 调用频率低于警告阈值 */
        HEALTHY,
        /** 警告 - 调用频率在警告阈值和告警阈值之间 */
        WARNING,
        /** 严重 - 调用频率超过告警阈值，需要关注 */
        CRITICAL,
        /** 未知 - 尚无调用记录 */
        UNKNOWN
    }

    /**
     * 每个 Hook 的调用统计信息。
     *
     * 线程安全：所有字段均考虑了并发访问：
     * - [count] 和 [totalCount] 使用 AtomicLong，保证原子递增/递减
     * - [windowStart], [lastInvocationTime], [alertTriggered] 标记为 @Volatile，
     *   保证跨线程可见性。这些字段的读写操作不需要原子性（读取-修改-写入
     *   之间的竞态条件在监控场景中是可接受的）。
     */
    private data class HookInvocationStats(
        /** 调用次数计数器（当前窗口内），使用 AtomicLong 保证原子性 */
        val count: AtomicLong = AtomicLong(0),
        /** 窗口开始时间戳，@Volatile 保证跨线程可见性 */
        @Volatile var windowStart: Long = System.currentTimeMillis(),
        /** 总调用次数（累计），使用 AtomicLong 保证原子性 */
        val totalCount: AtomicLong = AtomicLong(0),
        /** 上一次调用时间戳，@Volatile 保证跨线程可见性 */
        @Volatile var lastInvocationTime: Long = 0L,
        /** 是否已触发告警（用于避免重复告警），@Volatile 保证跨线程可见性 */
        @Volatile var alertTriggered: Boolean = false
    )

    /**
     * 各 Hook 的调用统计。
     * 线程安全：使用 ConcurrentHashMap，保证并发读写安全。
     * getOrPut 操作是原子的，但内部的统计更新（count.incrementAndGet）
     * 与窗口重置（count.getAndSet）之间存在竞态窗口，在监控场景中可接受。
     */
    private val hookStats = ConcurrentHashMap<String, HookInvocationStats>()

    /**
     * 记录一次 Hook 调用。
     *
     * 自动跟踪调用频率，当检测到频率超过 [ALERT_THRESHOLD_PER_SECOND] 时，
     * 通过 Xposed 日志发出告警。每个 Hook 的告警只触发一次，避免日志刷屏。
     * 告警状态在频率恢复正常后自动重置。
     *
     * @param hookName Hook 名称，用于标识和追踪
     */
    fun recordInvocation(hookName: String) {
        val stats = hookStats.getOrPut(hookName) { HookInvocationStats() }
        val now = System.currentTimeMillis()

        // 检查是否需要重置窗口
        if (now - stats.windowStart >= FREQUENCY_WINDOW_MS) {
            // 窗口过期，重置
            val previousCount = stats.count.getAndSet(1)
            stats.windowStart = now

            // 如果之前触发了告警且现在频率恢复正常，重置告警标志
            if (stats.alertTriggered && previousCount < ALERT_THRESHOLD_PER_SECOND) {
                stats.alertTriggered = false
                LogUtil.d(TAG, "Alert reset for $hookName (frequency normalized)")
            }
        } else {
            stats.count.incrementAndGet()
        }

        stats.totalCount.incrementAndGet()
        stats.lastInvocationTime = now

        // 检查是否需要告警
        val currentCount = stats.count.get()
        if (currentCount > ALERT_THRESHOLD_PER_SECOND && !stats.alertTriggered) {
            stats.alertTriggered = true
            LogUtil.w(
                TAG,
                "HIGH FREQUENCY ALERT: $hookName invoked $currentCount times in the last second! " +
                "This may indicate a performance issue. Total invocations: ${stats.totalCount.get()}"
            )
        }
    }

    /**
     * 获取指定 Hook 的每秒调用频率。
     *
     * 根据当前时间窗口内的调用次数计算频率。
     *
     * @param hookName Hook 名称
     * @return 每秒调用次数（double），无记录返回 0.0
     */
    fun getInvocationRate(hookName: String): Double {
        val stats = hookStats[hookName] ?: return 0.0
        val now = System.currentTimeMillis()
        val elapsed = now - stats.windowStart

        if (elapsed <= 0) return 0.0

        val count = stats.count.get()
        return (count.toDouble() / elapsed) * 1000.0
    }

    /**
     * 获取指定 Hook 的健康状态。
     *
     * 根据当前调用频率判断：
     * - 频率 > 100/s: CRITICAL
     * - 频率 > 50/s: WARNING
     * - 频率 <= 50/s: HEALTHY
     * - 无调用记录: UNKNOWN
     *
     * @param hookName Hook 名称
     * @return 健康状态
     */
    fun getHealthStatus(hookName: String): HealthStatus {
        val stats = hookStats[hookName] ?: return HealthStatus.UNKNOWN
        val rate = getInvocationRate(hookName)

        return when {
            rate > ALERT_THRESHOLD_PER_SECOND -> HealthStatus.CRITICAL
            rate > WARNING_THRESHOLD_PER_SECOND -> HealthStatus.WARNING
            stats.totalCount.get() > 0 -> HealthStatus.HEALTHY
            else -> HealthStatus.UNKNOWN
        }
    }

    /**
     * 获取所有 Hook 的健康状态汇总。
     *
     * @return Map，key 为 Hook 名称，value 为对应的健康状态
     */
    fun getAllHealthStatuses(): Map<String, HealthStatus> {
        return hookStats.keys.associateWith { getHealthStatus(it) }
    }

    /**
     * 获取指定 Hook 的累计调用次数。
     *
     * @param hookName Hook 名称
     * @return 累计调用次数
     */
    fun getTotalInvocationCount(hookName: String): Long {
        return hookStats[hookName]?.totalCount?.get() ?: 0L
    }

    /**
     * 获取指定 Hook 的上次调用时间。
     *
     * @param hookName Hook 名称
     * @return 上次调用时间戳（毫秒），无记录返回 0
     */
    fun getLastInvocationTime(hookName: String): Long {
        return hookStats[hookName]?.lastInvocationTime ?: 0L
    }

    /**
     * 获取所有已追踪的 Hook 名称列表。
     *
     * @return Hook 名称集合
     */
    fun getTrackedHooks(): Set<String> {
        return hookStats.keys.toSet()
    }

    /**
     * 获取格式化的健康报告。
     *
     * 包含每个 Hook 的调用频率、累计调用次数、健康状态等信息。
     *
     * @return 格式化的健康报告字符串
     */
    fun getHealthReport(): String = buildString {
        appendLine("=== Mirage Hook Health Report ===")
        appendLine()

        val hooks = getTrackedHooks().sorted()
        if (hooks.isEmpty()) {
            appendLine("  (No hooks tracked)")
            return@buildString
        }

        appendLine("  Hook Name                          Rate/s    Total Calls    Status")
        appendLine("  -------------------------------------------------------------------")
        for (hook in hooks) {
            val rate = "%.1f".format(getInvocationRate(hook))
            val total = getTotalInvocationCount(hook)
            val status = getHealthStatus(hook)
            val statusStr = when (status) {
                HealthStatus.HEALTHY -> "HEALTHY"
                HealthStatus.WARNING -> "WARNING"
                HealthStatus.CRITICAL -> "CRITICAL"
                HealthStatus.UNKNOWN -> "UNKNOWN"
            }
            appendLine("  ${hook.padEnd(36)} ${rate.padStart(8)}  ${total.toString().padStart(12)}  $statusStr")
        }
        appendLine()
        appendLine("=== End of Report ===")
    }

    /**
     * 重置所有计数器。
     *
     * 清除所有 Hook 的调用统计，包括累计计数和告警状态。
     */
    fun reset() {
        hookStats.clear()
        LogUtil.d(TAG, "All hook monitoring stats reset")
    }

    /**
     * 重置指定 Hook 的计数器。
     *
     * @param hookName Hook 名称
     */
    fun resetHook(hookName: String) {
        hookStats.remove(hookName)
        LogUtil.d(TAG, "Hook monitoring stats reset for: $hookName")
    }
}