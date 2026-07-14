package wx.mirage.config

/**
 * Mirage 微信 ID 黑名单
 *
 * 定义一组永远不应该被隐藏的系统级 wxId。
 * 这些 wxId 对应微信的核心系统功能账户，隐藏它们可能导致微信功能异常。
 *
 * ## 黑名单列表
 * | wxId | 说明 |
 * |------|------|
 * | filehelper | 文件传输助手 |
 * | weixin | 微信团队官方账号 |
 * | qqmail | QQ 邮箱提醒 |
 * | medianote | 语音记事本 |
 * | newsapp | 腾讯新闻 |
 * | floatbottle | 漂流瓶 |
 * | fmessage | 朋友验证消息 |
 * | tmessage | 群助手消息 |
 * | qmessage | QQ 离线消息 |
 * | qqsync | QQ 同步助手 |
 *
 * ## 使用方式
 * ```kotlin
 * if (WxIdBlacklist.isBlacklisted("filehelper")) {
 *     // 拒绝添加到隐藏列表
 * }
 * ```
 *
 * 注意：黑名单匹配是大小写不敏感的，即 "FileHelper" 和 "filehelper" 被视为相同。
 */
object WxIdBlacklist {

    /**
     * 预定义的永远不应被隐藏的系统 wxId 集合。
     * 使用 HashSet 存储，匹配时大小写不敏感。
     */
    private val blacklistedIds: Set<String> = setOf(
        "filehelper",   // 文件传输助手
        "weixin",       // 微信团队官方账号
        "qqmail",       // QQ 邮箱提醒
        "medianote",    // 语音记事本
        "newsapp",      // 腾讯新闻
        "floatbottle",  // 漂流瓶
        "fmessage",     // 朋友验证消息
        "tmessage",     // 群助手消息
        "qmessage",     // QQ 离线消息
        "qqsync"        // QQ 同步助手
    )

    /**
     * 检查指定 wxId 是否在黑名单中。
     *
     * 匹配规则：
     * 1. 首先进行精确匹配（大小写敏感）
     * 2. 如果未匹配，则转换为小写后再匹配（大小写不敏感）
     * 3. 如果 wxId 以 "gh_" 开头（公众号），也视为系统账户，返回 true
     *
     * @param wxId 待检查的微信 ID
     * @return true 如果该 wxId 在黑名单中，不应被隐藏
     */
    fun isBlacklisted(wxId: String?): Boolean {
        if (wxId.isNullOrBlank()) return false

        // 1. 精确匹配
        if (wxId in blacklistedIds) return true

        // 2. 大小写不敏感匹配
        if (wxId.lowercase() in blacklistedIds) return true

        // 3. 公众号前缀检查（gh_ 开头的系统账户）
        if (wxId.startsWith("gh_")) return true

        return false
    }

    /**
     * 获取黑名单中的所有 wxId。
     *
     * @return 黑名单 wxId 的只读集合
     */
    fun getBlacklistedIds(): Set<String> {
        return blacklistedIds
    }

    /**
     * 获取黑名单 wxId 的数量。
     *
     * @return 黑名单中的 wxId 数量
     */
    fun blacklistSize(): Int {
        return blacklistedIds.size
    }

    /**
     * 生成黑名单的格式化描述信息。
     *
     * @return 格式化的黑名单列表字符串
     */
    fun getBlacklistDescription(): String = buildString {
        appendLine("=== WxId Blacklist (${blacklistedIds.size} entries) ===")
        for ((index, id) in blacklistedIds.withIndex()) {
            appendLine("  ${index + 1}. $id")
        }
        appendLine("=== End of Blacklist ===")
    }
}