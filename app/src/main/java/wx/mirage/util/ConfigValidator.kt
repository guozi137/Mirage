package wx.mirage.util

import wx.mirage.WxIdUtils

/**
 * Mirage 配置验证器
 *
 * 提供对配置值的格式校验，包括：
 * - wxId 格式验证（长度、字符集、前缀格式）
 * - 标签/备注长度验证
 * - JSON 导入数据格式验证
 * - 配置完整性检查
 * - 列表大小限制检查
 *
 * 在 ConfigManager 的写入操作和 MainActivity 的用户输入处理中使用，
 * 确保只有合法数据被持久化。
 *
 * ## 线程安全
 * 本类为纯验证逻辑，无内部状态，所有方法均为无副作用纯函数，天然线程安全。
 * 可在任何线程中安全调用。
 */
object ConfigValidator {

    /** wxId 最小长度 */
    const val WXID_MIN_LENGTH = 6

    /** wxId 最大长度 */
    const val WXID_MAX_LENGTH = 64

    /** 标签/备注最大长度 */
    const val LABEL_MAX_LENGTH = 50

    /** 导入 JSON 最大字符数（防止过大输入） */
    const val IMPORT_JSON_MAX_LENGTH = 1024 * 1024 // 1MB

    /** 隐藏好友列表最大数量 */
    const val MAX_HIDDEN_FRIENDS = 10000

    /** 标签映射最大数量 */
    const val MAX_LABELS_COUNT = 20000

    /** 单个 wxId 在 JSON 中的最大长度（防止恶意超长字符串） */
    const val IMPORT_WXID_MAX_LENGTH = 128

    /**
     * 验证 wxId 格式是否合法。
     *
     * 校验规则：
     * 1. 非空且非空白
     * 2. 长度在 [WXID_MIN_LENGTH, WXID_MAX_LENGTH] 之间
     * 3. 以字母开头
     * 4. 只包含字母、数字、下划线、连字符
     * 5. 特殊格式：wxid_ 前缀后跟至少 10 个字母数字
     *
     * @param wxId 待验证的 wxId 字符串
     * @return 验证结果，包含是否合法及错误信息
     */
    fun validateWxId(wxId: String?): ValidationResult {
        if (wxId.isNullOrBlank()) {
            return ValidationResult(false, "wxId cannot be empty")
        }

        val trimmed = wxId.trim()

        if (trimmed.length < WXID_MIN_LENGTH) {
            return ValidationResult(false, "wxId too short (min $WXID_MIN_LENGTH characters)")
        }

        if (trimmed.length > WXID_MAX_LENGTH) {
            return ValidationResult(false, "wxId too long (max $WXID_MAX_LENGTH characters)")
        }

        if (!WxIdUtils.isValidWxId(trimmed)) {
            return ValidationResult(
                false,
                "wxId format invalid: must start with a letter, followed by letters/digits/underscores/hyphens (min 6 chars), or 'wxid_' prefix followed by 10+ alphanumeric characters"
            )
        }

        return ValidationResult(true, "OK")
    }

    /**
     * 简化版 wxId 验证，仅返回布尔值。
     *
     * 等同于 validateWxId(wxId).valid。
     *
     * @param wxId 待验证的 wxId 字符串
     * @return true 如果格式合法
     */
    fun isWxIdValid(wxId: String?): Boolean {
        return validateWxId(wxId).valid
    }

    /**
     * 验证标签/备注字符串是否合法。
     *
     * 校验规则：
     * 1. 可以为空（空字符串表示移除标签）
     * 2. 长度不超过 [LABEL_MAX_LENGTH]
     * 3. 不包含控制字符
     *
     * @param label 待验证的标签字符串
     * @return 验证结果
     */
    fun validateLabel(label: String?): ValidationResult {
        if (label.isNullOrEmpty()) {
            return ValidationResult(true, "OK") // 空标签是合法的（表示移除）
        }

        if (label.length > LABEL_MAX_LENGTH) {
            return ValidationResult(false, "Label too long (max $LABEL_MAX_LENGTH characters)")
        }

        // 检查是否包含控制字符（除了换行和制表符）
        if (label.any { it.isISOControl() && it != '\n' && it != '\t' }) {
            return ValidationResult(false, "Label contains invalid control characters")
        }

        return ValidationResult(true, "OK")
    }

    /**
     * 验证导入 JSON 数据格式。
     *
     * 检查：
     * 1. 非空
     * 2. 长度不超过 [IMPORT_JSON_MAX_LENGTH]
     * 3. 包含 hiddenIds 字段（数组）
     * 4. 每个 wxId 都通过 validateWxId 验证
     * 5. hiddenIds 列表大小不超过 [MAX_HIDDEN_FRIENDS]
     * 6. labels 映射大小不超过 [MAX_LABELS_COUNT]
     * 7. 单个 wxId 字符串长度不超过 [IMPORT_WXID_MAX_LENGTH]（防止恶意超长字符串）
     *
     * @param json 导入的 JSON 字符串
     * @return 验证结果
     */
    fun validateImportJson(json: String?): ValidationResult {
        if (json.isNullOrBlank()) {
            return ValidationResult(false, "Import JSON cannot be empty")
        }

        if (json.length > IMPORT_JSON_MAX_LENGTH) {
            return ValidationResult(false, "Import JSON too large (max ${IMPORT_JSON_MAX_LENGTH / 1024}KB)")
        }

        try {
            val gson = com.google.gson.Gson()
            val type = object : com.google.gson.reflect.TypeToken<Map<String, Any>>() {}.type
            val data: Map<String, Any> = gson.fromJson(json, type)

            // 检查 hiddenIds 字段
            val hiddenIds = data["hiddenIds"]
            if (hiddenIds == null) {
                return ValidationResult(false, "Import JSON missing 'hiddenIds' field")
            }

            if (hiddenIds !is List<*>) {
                return ValidationResult(false, "Import JSON 'hiddenIds' must be an array")
            }

            // 检查列表大小限制
            if (hiddenIds.size > MAX_HIDDEN_FRIENDS) {
                return ValidationResult(false, "Too many wxIds in import (${hiddenIds.size}, max $MAX_HIDDEN_FRIENDS)")
            }

            val ids = hiddenIds.mapNotNull { it as? String }
            if (ids.size > MAX_HIDDEN_FRIENDS) {
                return ValidationResult(false, "Too many wxIds in import (max $MAX_HIDDEN_FRIENDS)")
            }

            // 验证每个 wxId 的长度和格式
            for (wxId in ids) {
                // 检查单个 wxId 长度（防止恶意超长字符串）
                if (wxId.length > IMPORT_WXID_MAX_LENGTH) {
                    return ValidationResult(false, "wxId in import too long: ${wxId.take(20)}... (max $IMPORT_WXID_MAX_LENGTH characters)")
                }
                // 验证 wxId 格式
                val result = validateWxId(wxId)
                if (!result.valid) {
                    return ValidationResult(false, "Invalid wxId in import: $result")
                }
            }

            // 检查 labels 字段（可选）
            val labels = data["labels"]
            if (labels != null) {
                if (labels !is Map<*, *>) {
                    return ValidationResult(false, "Import JSON 'labels' must be an object")
                }
                // 检查 labels 映射大小限制
                if (labels.size > MAX_LABELS_COUNT) {
                    return ValidationResult(false, "Too many labels in import (${labels.size}, max $MAX_LABELS_COUNT)")
                }
                // 验证每个 label 的格式
                @Suppress("UNCHECKED_CAST")
                val labelMap = labels as? Map<String, String>
                if (labelMap != null) {
                    for ((key, value) in labelMap) {
                        // 检查 label key 长度
                        if (key.length > IMPORT_WXID_MAX_LENGTH) {
                            return ValidationResult(false, "Label key in import too long: ${key.take(20)}... (max $IMPORT_WXID_MAX_LENGTH)")
                        }
                        // 检查 label value 长度
                        if (value.length > LABEL_MAX_LENGTH) {
                            return ValidationResult(false, "Label value in import too long: ${value.take(20)}... (max $LABEL_MAX_LENGTH)")
                        }
                        // 检查 label value 是否包含控制字符
                        val labelValidation = validateLabel(value)
                        if (!labelValidation.valid) {
                            return ValidationResult(false, "Invalid label in import: $labelValidation")
                        }
                    }
                }
            }

            return ValidationResult(true, "OK")
        } catch (e: com.google.gson.JsonSyntaxException) {
            return ValidationResult(false, "Invalid JSON syntax: ${e.message}")
        } catch (e: Exception) {
            return ValidationResult(false, "Import JSON parse error: ${e.message}")
        }
    }

    /**
     * 验证隐藏好友列表是否在合理范围内。
     *
     * @param count 当前隐藏好友数量
     * @return 验证结果
     */
    fun validateHiddenCount(count: Int): ValidationResult {
        if (count < 0) {
            return ValidationResult(false, "Hidden count cannot be negative")
        }
        if (count > MAX_HIDDEN_FRIENDS) {
            return ValidationResult(false, "Too many hidden friends (max $MAX_HIDDEN_FRIENDS)")
        }
        return ValidationResult(true, "OK")
    }

    /**
     * 验证标签映射大小是否在合理范围内。
     *
     * @param count 当前标签映射数量
     * @return 验证结果
     */
    fun validateLabelsCount(count: Int): ValidationResult {
        if (count < 0) {
            return ValidationResult(false, "Labels count cannot be negative")
        }
        if (count > MAX_LABELS_COUNT) {
            return ValidationResult(false, "Too many labels (max $MAX_LABELS_COUNT)")
        }
        return ValidationResult(true, "OK")
    }

    /**
     * 验证结果数据类。
     *
     * @param valid 是否通过验证
     * @param message 验证消息（通过时为 "OK"，失败时为错误描述）
     */
    data class ValidationResult(
        val valid: Boolean,
        val message: String
    ) {
        override fun toString(): String {
            return if (valid) "OK" else message
        }
    }
}