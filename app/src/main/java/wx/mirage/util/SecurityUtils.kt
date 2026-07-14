package wx.mirage.util

import java.io.File
import java.security.MessageDigest

/**
 * Mirage 安全工具类
 *
 * 提供输入清理、路径验证、wxId 哈希等安全相关功能。
 * 所有方法均为纯函数，无状态，天然线程安全。
 *
 * ## 功能列表
 * - [sanitizeWxId]: 清理 wxId 中的危险字符，防止注入攻击
 * - [sanitizeLabel]: 清理标签/备注中的危险字符
 * - [sanitizeFileName]: 清理文件名中的路径遍历字符
 * - [isValidPath]: 验证文件路径是否在预期目录内，防止路径遍历攻击
 * - [hashWxId]: 对 wxId 进行 SHA-256 哈希，用于日志输出（避免泄露真实 wxId）
 * - [stripControlChars]: 移除字符串中的所有控制字符
 * - [limitLength]: 限制字符串长度，超过则截断
 *
 * ## 线程安全
 * 本类为纯工具类，所有方法均为无状态静态方法，天然线程安全，无需额外同步。
 *
 * ## 使用示例
 * ```kotlin
 * // 清理用户输入
 * val cleanWxId = SecurityUtils.sanitizeWxId(rawInput)
 *
 * // 验证文件路径
 * if (!SecurityUtils.isValidPath(filePath, baseDir)) {
 *     throw SecurityException("Path traversal detected")
 * }
 *
 * // 日志中安全输出 wxId
 * LogUtil.i("MyHook", "Processing wxId: ${SecurityUtils.hashWxId(wxId)}")
 * ```
 */
object SecurityUtils {

    /** wxId 中允许的字符正则：字母、数字、下划线、连字符 */
    private val WXID_SAFE_CHARS = Regex("[^a-zA-Z0-9_-]")

    /** 标签中允许的字符正则：允许可见字符、中文、空格，排除控制字符 */
    private val LABEL_SAFE_CHARS = Regex("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F\\x7F]")

    /** 文件名中允许的字符正则：排除路径遍历和危险字符 */
    private val FILENAME_SAFE_CHARS = Regex("[^a-zA-Z0-9._\\-]")

    /** wxId 最大长度 */
    const val WXID_MAX_LENGTH = 64

    /** 标签最大长度 */
    const val LABEL_MAX_LENGTH = 50

    /** 文件名最大长度 */
    const val FILENAME_MAX_LENGTH = 255

    /** 路径最大深度（防止深层递归攻击） */
    const val MAX_PATH_DEPTH = 10

    /**
     * 清理 wxId 输入，移除所有危险字符。
     *
     * 执行以下操作：
     * 1. 去除前后空白字符
     * 2. 移除所有非字母、数字、下划线、连字符的字符
     * 3. 限制长度不超过 [WXID_MAX_LENGTH]
     *
     * @param input 原始 wxId 输入
     * @return 清理后的 wxId 字符串，如果输入为空或清理后为空则返回空字符串
     */
    fun sanitizeWxId(input: String?): String {
        if (input.isNullOrBlank()) return ""
        return input
            .trim()
            .replace(WXID_SAFE_CHARS, "")
            .let { limitLength(it, WXID_MAX_LENGTH) }
    }

    /**
     * 清理标签/备注输入，移除危险字符。
     *
     * 执行以下操作：
     * 1. 去除前后空白字符
     * 2. 移除所有控制字符（ASCII 0x00-0x1F, 0x7F，保留换行和制表符）
     * 3. 限制长度不超过 [LABEL_MAX_LENGTH]
     *
     * @param input 原始标签输入
     * @return 清理后的标签字符串，如果输入为空则返回空字符串
     */
    fun sanitizeLabel(input: String?): String {
        if (input.isNullOrBlank()) return ""
        return input
            .trim()
            .replace(LABEL_SAFE_CHARS, "")
            .let { limitLength(it, LABEL_MAX_LENGTH) }
    }

    /**
     * 清理文件名，移除路径遍历和危险字符。
     *
     * 执行以下操作：
     * 1. 去除前后空白字符
     * 2. 移除路径分隔符（/、\）和路径遍历字符（..）
     * 3. 移除所有非字母、数字、点、下划线、连字符的字符
     * 4. 限制长度不超过 [FILENAME_MAX_LENGTH]
     *
     * @param input 原始文件名
     * @return 清理后的文件名字符串
     */
    fun sanitizeFileName(input: String?): String {
        if (input.isNullOrBlank()) return "untitled"
        return input
            .trim()
            .replace("..", "")       // 移除路径遍历
            .replace("/", "")        // 移除 Unix 路径分隔符
            .replace("\\", "")       // 移除 Windows 路径分隔符
            .replace(FILENAME_SAFE_CHARS, "_")
            .let { limitLength(it, FILENAME_MAX_LENGTH) }
            .ifEmpty { "untitled" }
    }

    /**
     * 验证文件路径是否在预期的目录内，防止路径遍历攻击。
     *
     * 验证逻辑：
     * 1. 检查路径是否为空
     * 2. 将路径规范化为绝对路径
     * 3. 检查规范化后的路径是否以基准目录开头
     * 4. 检查路径中是否包含 ".." 路径遍历序列
     * 5. 检查路径深度是否超过 [MAX_PATH_DEPTH]
     *
     * @param path 待验证的文件路径
     * @param baseDir 预期的基准目录
     * @return true 如果路径安全且在预期目录内
     */
    fun isValidPath(path: String?, baseDir: File): Boolean {
        if (path.isNullOrBlank()) return false

        return try {
            val file = File(path).canonicalFile
            val base = baseDir.canonicalFile

            // 检查规范化后的路径是否以基准目录开头
            if (!file.path.startsWith(base.path + File.separator) && file.path != base.path) {
                LogUtil.w("SecurityUtils", "Path traversal attempt detected: $path (resolved to ${file.path}, base=${base.path})")
                return false
            }

            // 二次检查：原始路径中是否包含路径遍历序列
            if (path.contains("..")) {
                LogUtil.w("SecurityUtils", "Path contains '..' sequence: $path")
                return false
            }

            // 检查路径深度
            val depth = file.path.count { it == File.separatorChar }
            val baseDepth = base.path.count { it == File.separatorChar }
            if (depth - baseDepth > MAX_PATH_DEPTH) {
                LogUtil.w("SecurityUtils", "Path too deep: $path (depth=$depth, baseDepth=$baseDepth)")
                return false
            }

            true
        } catch (e: Exception) {
            LogUtil.e("SecurityUtils", "Path validation error: ${e.message}", e)
            false
        }
    }

    /**
     * 对 wxId 进行 SHA-256 哈希，用于日志输出。
     *
     * 在日志中直接输出真实 wxId 存在隐私泄露风险。此方法生成 wxId 的
     * SHA-256 哈希值的前 16 位十六进制字符，用于在日志中标识不同的 wxId，
     * 同时保护用户隐私。
     *
     * 注意：哈希是确定性的，相同的 wxId 始终产生相同的哈希值，
     * 因此可以用于追踪和调试，但无法从哈希值反推原始 wxId。
     *
     * @param wxId 原始 wxId
     * @return wxId 的 SHA-256 哈希值前 16 位十六进制字符，如果输入为空则返回 "null"
     */
    fun hashWxId(wxId: String?): String {
        if (wxId.isNullOrBlank()) return "null"
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            val hashBytes = digest.digest(wxId.toByteArray(Charsets.UTF_8))
            hashBytes.take(8).joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            // 降级：如果 SHA-256 不可用，使用 hashCode 的十六进制表示
            LogUtil.w("SecurityUtils", "SHA-256 not available, falling back to hashCode: ${e.message}")
            "%08x".format(wxId.hashCode())
        }
    }

    /**
     * 移除字符串中的所有控制字符。
     *
     * 保留可见字符、空格、换行符(\\n)和制表符(\\t)，
     * 移除其他所有 ASCII 控制字符（0x00-0x08, 0x0B, 0x0C, 0x0E-0x1F, 0x7F）。
     *
     * @param input 原始字符串
     * @return 清理后的字符串
     */
    fun stripControlChars(input: String?): String {
        if (input.isNullOrEmpty()) return input ?: ""
        return input.replace(LABEL_SAFE_CHARS, "")
    }

    /**
     * 限制字符串长度，超过则截断。
     *
     * @param input 输入字符串
     * @param maxLength 最大长度
     * @return 截断后的字符串
     */
    fun limitLength(input: String, maxLength: Int): String {
        if (input.length <= maxLength) return input
        return input.substring(0, maxLength)
    }
}