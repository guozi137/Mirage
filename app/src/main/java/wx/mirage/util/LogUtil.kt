package wx.mirage.util

import de.robv.android.xposed.XposedBridge
import wx.mirage.Constants

/**
 * Mirage 集中式日志工具类
 *
 * 封装 XposedBridge.log，提供统一的 TAG 格式和日志级别过滤。
 * 所有日志输出格式: "Mirage: [TAG] message"
 *
 * 使用示例:
 *   LogUtil.i("ContactHook", "Initializing...")
 *   LogUtil.e("MainHook", "Failed to initialize", exception)
 */
object LogUtil {

    /** 全局 TAG，统一此前缀，引用自 [Constants.MODULE_TAG] */
    const val GLOBAL_TAG = Constants.MODULE_TAG

    /**
     * 格式化日志消息为统一格式
     */
    private fun format(tag: String, message: String): String {
        return "$GLOBAL_TAG: [$tag] $message"
    }

    /**
     * Debug 级别日志 - 用于详细的调试信息
     */
    fun d(tag: String, message: String) {
        XposedBridge.log("[DEBUG] ${format(tag, message)}")
    }

    /**
     * Info 级别日志 - 用于常规信息输出
     */
    fun i(tag: String, message: String) {
        XposedBridge.log("[INFO] ${format(tag, message)}")
    }

    /**
     * Warning 级别日志 - 用于警告信息
     */
    fun w(tag: String, message: String) {
        XposedBridge.log("[WARN] ${format(tag, message)}")
    }

    /**
     * Error 级别日志 - 用于错误信息，可附带异常堆栈
     */
    fun e(tag: String, message: String, throwable: Throwable? = null) {
        XposedBridge.log("[ERROR] ${format(tag, message)}")
        throwable?.let { XposedBridge.log(it) }
    }
}