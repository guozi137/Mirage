package wx.mirage.util

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Mirage Hook 性能指标追踪工具
 *
 * 提供:
 * - hookInvocationCount: 各 Hook 模块的调用次数统计
 * - averageExecutionTime: 各操作的加权平均执行时间 (纳秒)
 * - errorRates: 各操作的错误率
 * - totalHookTime: 各 Hook 模块的累计执行时间
 *
 * 使用 AtomicLong 保证线程安全的计数器。
 * 使用 System.nanoTime() 进行高精度计时。
 *
 * 使用示例:
 *   val startTime = HookMetrics.beginTiming("ContactHook.filterContactList")
 *   try {
 *       // 执行操作
 *       HookMetrics.recordSuccess("ContactHook.filterContactList")
 *   } catch (e: Exception) {
 *       HookMetrics.recordError("ContactHook.filterContactList")
 *   } finally {
 *       HookMetrics.endTiming("ContactHook.filterContactList", startTime)
 *   }
 */
object HookMetrics {

    /**
     * 各操作的调用次数。
     * 线程安全：使用 ConcurrentHashMap + AtomicLong，保证并发安全。
     * ConcurrentHashMap 保证 put/get 的线程安全，AtomicLong 保证计数器的原子性。
     */
    private val invocationCounts = ConcurrentHashMap<String, AtomicLong>()

    /**
     * 各操作的累计执行时间 (纳秒)。
     * 线程安全：使用 ConcurrentHashMap + AtomicLong。
     */
    private val totalExecutionTimes = ConcurrentHashMap<String, AtomicLong>()

    /**
     * 各操作的错误次数。
     * 线程安全：使用 ConcurrentHashMap + AtomicLong。
     */
    private val errorCounts = ConcurrentHashMap<String, AtomicLong>()

    /**
     * 各操作的执行次数 (用于加权平均)。
     * 线程安全：使用 ConcurrentHashMap + AtomicLong。
     */
    private val executionCounts = ConcurrentHashMap<String, AtomicLong>()

    /**
     * 开始计时，返回当前纳秒时间戳。
     * 调用者需要在 finally 块中调用 endTiming()。
     *
     * @param operationName 操作名称
     * @return 开始时间 (nanoTime)
     */
    fun beginTiming(operationName: String): Long {
        invocationCounts.getOrPut(operationName) { AtomicLong(0) }.incrementAndGet()
        return System.nanoTime()
    }

    /**
     * 结束计时，累加执行时间。
     *
     * @param operationName 操作名称
     * @param startNano beginTiming 返回的开始时间
     */
    fun endTiming(operationName: String, startNano: Long) {
        val elapsed = System.nanoTime() - startNano
        totalExecutionTimes.getOrPut(operationName) { AtomicLong(0) }.addAndGet(elapsed)
        executionCounts.getOrPut(operationName) { AtomicLong(0) }.incrementAndGet()
    }

    /**
     * 记录一次成功执行。
     *
     * @param operationName 操作名称
     */
    fun recordSuccess(operationName: String) {
        // 成功计数已通过 invocationCount 跟踪，此方法用于显式标记
        executionCounts.getOrPut(operationName) { AtomicLong(0) }.incrementAndGet()
    }

    /**
     * 记录一次错误。
     *
     * @param operationName 操作名称
     */
    fun recordError(operationName: String) {
        errorCounts.getOrPut(operationName) { AtomicLong(0) }.incrementAndGet()
    }

    /**
     * 获取指定操作的调用次数。
     */
    fun getInvocationCount(operationName: String): Long {
        return invocationCounts[operationName]?.get() ?: 0L
    }

    /**
     * 获取指定操作的平均执行时间 (微秒)。
     */
    fun getAverageExecutionTimeUs(operationName: String): Double {
        val totalTime = totalExecutionTimes[operationName]?.get() ?: 0L
        val count = executionCounts[operationName]?.get() ?: 0L
        if (count == 0L) return 0.0
        return (totalTime.toDouble() / count) / 1000.0 // 纳秒转微秒
    }

    /**
     * 获取指定操作的错误率 (0.0 ~ 1.0)。
     */
    fun getErrorRate(operationName: String): Double {
        val total = invocationCounts[operationName]?.get() ?: 0L
        val errors = errorCounts[operationName]?.get() ?: 0L
        if (total == 0L) return 0.0
        return errors.toDouble() / total.toDouble()
    }

    /**
     * 获取所有已追踪的操作名称列表。
     */
    fun getTrackedOperations(): Set<String> {
        return invocationCounts.keys.toSet()
    }

    /**
     * 获取格式化的性能报告。
     */
    fun getPerformanceReport(): String = buildString {
        appendLine("=== Mirage Hook Performance Report ===")
        appendLine()

        val operations = getTrackedOperations().sorted()
        if (operations.isEmpty()) {
            appendLine("  (No tracked operations)")
            return@buildString
        }

        appendLine("  Operation               Calls    AvgTime(us)   ErrorRate")
        appendLine("  ---------------------------------------------------------")
        for (op in operations) {
            val calls = getInvocationCount(op)
            val avgTime = getAverageExecutionTimeUs(op)
            val errorRate = getErrorRate(op)
            appendLine("  ${op.padEnd(24)} ${calls.toString().padStart(8)}  ${"%.2f".format(avgTime).padStart(12)}  ${"%.2f%%".format(errorRate * 100).padStart(8)}")
        }
        appendLine()
        appendLine("=== End of Report ===")
    }

    /**
     * 清除所有指标数据。
     */
    fun reset() {
        invocationCounts.clear()
        totalExecutionTimes.clear()
        errorCounts.clear()
        executionCounts.clear()
    }
}