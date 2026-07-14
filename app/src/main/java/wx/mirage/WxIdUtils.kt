package wx.mirage

import wx.mirage.util.LogUtil

/**
 * 微信 ID 工具类
 *
 * 提供 wxId 格式验证和标准化处理功能。
 * wxId 格式规则：
 * - 必须以字母开头
 * - 可以包含字母、数字、下划线、连字符
 * - 长度限制：6-20 个字符
 * - 不区分大小写（存储时转为小写）
 *
 * 所有方法均为静态方法，通过 object 声明自动实现线程安全的单例。
 */
object WxIdUtils {

    private const val TAG = Constants.MODULE_TAG + ":WxIdUtils"

    /**
     * 验证 wxId 格式是否合法。
     *
     * 合法格式要求：
     * - 非空
     * - 以字母（a-z, A-Z）开头
     * - 仅包含字母、数字、下划线、连字符
     * - 长度 6-20 个字符
     *
     * @param wxId 待验证的 wxId
     * @return true 如果格式合法
     */
    fun isValidWxId(wxId: String): Boolean {
        if (wxId.isEmpty()) {
            LogUtil.w(TAG, "wxId is empty")
            return false
        }

        // 微信 ID 格式：以字母开头，可包含字母、数字、下划线、连字符
        val regex = Regex("^[a-zA-Z][a-zA-Z0-9_-]{5,19}$")

        val isValid = wxId.matches(regex)
        if (!isValid) {
            LogUtil.w(TAG, "Invalid wxId format: $wxId")
        }
        return isValid
    }

    /**
     * 标准化 wxId（转为小写、去除首尾空格）。
     *
     * 微信 ID 不区分大小写，统一转为小写以避免重复。
     *
     * @param wxId 原始 wxId
     * @return 标准化后的 wxId
     * @throws IllegalArgumentException 如果 wxId 格式不合法
     */
    fun normalize(wxId: String): String {
        val trimmed = wxId.trim()
        require(isValidWxId(trimmed)) {
            "Invalid wxId format: '$wxId'"
        }
        return trimmed.lowercase()
    }

    /**
     * 安全地标准化 wxId，格式不合法时返回 null 而不是抛出异常。
     *
     * @param wxId 原始 wxId
     * @return 标准化后的 wxId，如果格式不合法则返回 null
     */
    fun normalizeOrNull(wxId: String): String? {
        return try {
            normalize(wxId)
        } catch (e: IllegalArgumentException) {
            LogUtil.w(TAG, "Failed to normalize wxId: ${e.message}")
            null
        }
    }
}