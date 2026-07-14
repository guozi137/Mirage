package wx.mirage.util

import wx.mirage.Constants

/**
 * 调试工具类
 *
 * 提供限制调试日志输出频率的功能，防止在 Hook 回调中产生大量重复日志。
 * 使用 lastLogTimestamps 和 MIN_LOG_INTERVAL 控制日志输出频率。
 *
 * 所有方法均为静态方法，通过 object 声明自动实现线程安全的单例。
 */
object DebugUtils {

    /** 调试标签 */
    private const val TAG = Constants.MODULE_TAG + ":DebugUtils"

    /**
     * 同一标签的日志最小间隔（毫秒）。
     * 如果同一标签的日志在 MIN_LOG_INTERVAL 内被调用多次，
     * 只有第一次会输出，后续调用会被跳过。
     */
    private const val MIN_LOG_INTERVAL = 1000L

    /** 上次日志输出的时间戳映射（标签 -> 时间戳） */
    private val lastLogTimestamps = mutableMapOf<String, Long>()

    /**
     * 限制频率的调试日志输出
     *
     * 如果同一 tag 在 [MIN_LOG_INTERVAL] 毫秒内已输出过，则跳过本次输出。
     * 使用场景：在 Hook 回调中输出调试信息，避免因回调频率过高
     * 产生大量日志影响性能。
     *
     * @param tag 日志标签，用于区分不同来源的日志
     * @param message 日志消息
     */
    fun throttledDebug(tag: String, message: String) {
        val now = System.currentTimeMillis()
        val lastLog = lastLogTimestamps[tag]

        if (lastLog == null || (now - lastLog) >= MIN_LOG_INTERVAL) {
            lastLogTimestamps[tag] = now
            LogUtil.d(TAG, "[$tag] $message")
        }
    }

    /**
     * 清除所有限流日志记录
     *
     * 在模块重新加载时调用，重置所有日志限流状态。
     */
    fun clearThrottleHistory() {
        lastLogTimestamps.clear()
        LogUtil.d(TAG, "Throttle history cleared")
    }
}