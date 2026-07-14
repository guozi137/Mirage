package wx.mirage.config

import android.content.Context
import android.os.Environment
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import java.io.File
import wx.mirage.util.ConfigValidator
import wx.mirage.util.LogUtil

/**
 * Mirage 配置管理器
 *
 * 负责管理所有持久化配置，包括隐藏好友列表、模块开关、标签等。
 * 使用 SharedPreferences 作为底层存储，使用 Gson 进行 JSON 序列化。
 *
 * ## 默认值一览
 * | 配置项 | 键名 | 默认值 | 说明 |
 * |--------|------|--------|------|
 * | 隐藏好友列表 | hidden_wx_ids | 空集合 (emptySet) | 字符串集合，默认不隐藏任何人 |
 * | 模块开关 | module_enabled | true | 模块默认启用 |
 * | 标签/备注 | hidden_labels | 空 Map (emptyMap) | wxId 到标签的映射，默认为空 |
 * | 缓存 | hiddenIdsCache | 空集合 | 从 SP 懒加载，写操作后失效 |
 *
 * ## 缓存机制
 * 使用 dirty-flag 缓存模式：
 * - 读操作：先检查缓存是否有效（dirty=false），有效则直接返回，否则从 SP 重新加载
 * - 写操作：写入 SP 后调用 invalidateCache() 将 dirty 标记为 true
 * - Context 切换检测：如果 Context hashCode 变化，自动重新加载
 *
 * ## 线程安全
 * - 缓存变量使用 @Volatile 注解保证多线程可见性
 * - 写操作通过 SharedPreferences.edit() 保证原子性
 * - 注意：addHiddenWxId 和 removeHiddenWxId 存在经典的 read-modify-write 竞态条件，
 *   对于高并发场景（如同时从多个线程添加/删除），建议使用更精细的同步策略
 */
object ConfigManager {
    private const val SP_NAME = "wx_mirage_config"
    private const val KEY_HIDDEN_IDS = "hidden_wx_ids"
    private const val KEY_ENABLED = "module_enabled"
    private const val KEY_LABELS = "hidden_labels"

    private var spName: String = SP_NAME
    private var configuredPkg: String? = null

    // ===== Gson 单例，避免每次调用重复创建 =====
    private val gson: Gson = GsonBuilder().create()

    // ===== hiddenIds 缓存（带 dirty flag 模式） =====

    /**
     * 缓存的隐藏好友 ID 集合。
     * 当 dirty 为 true 时，下次读取会从 SP 重新加载。
     *
     * 线程安全：标记为 @Volatile，保证多线程写入后的可见性。
     * 写入在 getCachedHiddenIds() 和 setHiddenWxIds() 中发生，
     * 读取在 getHiddenWxIds() 和 isHidden() 中频繁发生。
     * 注意：dirty flag 检查（hiddenIdsCacheDirty）和后续的缓存加载之间
     * 存在 TOCTOU 竞态窗口，但在最坏情况下仅导致重复加载缓存，
     * 不会产生数据不一致。这是性能与一致性之间的有意权衡。
     */
    @Volatile
    private var hiddenIdsCache: Set<String> = emptySet()

    /**
     * 缓存是否已失效，需要从 SP 重新加载。
     * 线程安全：标记为 @Volatile，保证 invalidation 对其他线程立即可见。
     * 写入通过 invalidateCache() 进行，读取在 getCachedHiddenIds() 中。
     */
    @Volatile
    private var hiddenIdsCacheDirty: Boolean = true

    /**
     * 记录上次加载缓存时使用的 Context hashCode，用于检测 Context 切换。
     * 线程安全：标记为 @Volatile。当 Context 变化时（如模块重载），
     * 此值会更新，触发缓存重新加载。
     */
    @Volatile
    private var lastContextHash: Int = 0

    /**
     * 使缓存失效。所有写操作都必须调用此方法。
     */
    private fun invalidateCache() {
        hiddenIdsCacheDirty = true
    }

    /**
     * 获取缓存的 hiddenIds，若缓存失效则从 SP 重新加载。
     */
    private fun getCachedHiddenIds(context: Context): Set<String> {
        val ctxHash = context.hashCode()
        if (hiddenIdsCacheDirty || ctxHash != lastContextHash) {
            hiddenIdsCache = getSP(context).getStringSet(KEY_HIDDEN_IDS, emptySet()) ?: emptySet()
            hiddenIdsCacheDirty = false
            lastContextHash = ctxHash
        }
        return hiddenIdsCache
    }

    // ===== 初始化 =====

    /**
     * 初始化 ConfigManager。
     *
     * 由 MainHook 在 Xposed 注入流程中调用，设置 SharedPreferences 命名空间。
     * 必须在任何读写操作之前调用。
     *
     * @param packageName 微信包名，用于构造 SP 名称
     */
    fun init(packageName: String) {
        configuredPkg = packageName
        spName = "${SP_NAME}_${packageName}"
        invalidateCache()
    }

    /**
     * 判断 ConfigManager 是否已经初始化（即是否已由 Xposed 注入流程调用过 init）。
     *
     * @return true 如果已调用 init()
     */
    fun hasContext(): Boolean = configuredPkg != null

    /**
     * 获取 SharedPreferences。
     *
     * 由于 MainActivity 可能在 Xposed 初始化之前运行（Mirage 自身进程），
     * 此时 MainHook.appContext 为 null，MainActivity 会回退到自身的 Context，
     * 导致使用错误的 SP 名称。此方法提供双路径回退：
     *   1. 优先使用 init 配置的 SP 名称（Xposed 注入路径）
     *   2. 如果传入的 context 包名等于 configuredPkg，使用配置的 SP 名称
     *   3. 否则回退到默认 SP_NAME
     */
    private fun getSP(context: Context) =
        context.getSharedPreferences(resolveSpName(context), Context.MODE_PRIVATE)

    /**
     * 解析实际使用的 SharedPreferences 名称。
     * 如果 context 的包名匹配 configuredPkg，使用配置的 SP 名称；
     * 否则使用默认 SP_NAME，确保数据一致性。
     */
    private fun resolveSpName(context: Context): String {
        val ctxPkg = context.packageName
        return if (configuredPkg != null && ctxPkg == configuredPkg) {
            spName
        } else {
            SP_NAME
        }
    }

    // ===== 隐藏好友列表 =====

    /**
     * 获取隐藏好友的 wxId 集合。
     *
     * 默认返回空集合（没有任何好友被隐藏）。
     * 使用缓存机制，仅在缓存失效时从 SharedPreferences 重新加载。
     *
     * @param context Android Context，用于访问 SharedPreferences
     * @return 隐藏好友的 wxId 集合（不可变），默认值为空集合
     */
    fun getHiddenWxIds(context: Context): Set<String> {
        return getCachedHiddenIds(context)
    }

    /**
     * 设置隐藏好友的 wxId 集合（替换全部）。
     *
     * 此操作会完全替换现有的隐藏列表，并使缓存失效。
     *
     * @param context Android Context
     * @param ids 新的隐藏好友 wxId 集合
     */
    fun setHiddenWxIds(context: Context, ids: Set<String>) {
        getSP(context).edit().putStringSet(KEY_HIDDEN_IDS, ids).apply()
        invalidateCache()
    }

    /**
     * 添加单个隐藏好友 wxId。
     *
     * 注意：此方法存在 read-modify-write 竞态条件。
     * wxId 会经过 ConfigValidator 验证，格式不合法时静默忽略。
     * 黑名单中的 wxId（如 filehelper、weixin 等系统账户）会被拒绝添加。
     *
     * @param context Android Context
     * @param wxId 要添加的微信 ID
     * @return true 如果成功添加，false 如果被拒绝（格式无效或黑名单）
     */
    fun addHiddenWxId(context: Context, wxId: String): Boolean {
        // 验证 wxId 格式
        val validation = ConfigValidator.validateWxId(wxId)
        if (!validation.valid) {
            LogUtil.w("ConfigManager", "addHiddenWxId: invalid wxId '$wxId' - ${validation.message}")
            return false
        }

        // 检查黑名单
        if (WxIdBlacklist.isBlacklisted(wxId)) {
            LogUtil.w("ConfigManager", "addHiddenWxId: wxId '$wxId' is in the blacklist, cannot be hidden")
            return false
        }

        val current = getCachedHiddenIds(context).toMutableSet()
        current.add(wxId)
        setHiddenWxIds(context, current)
        return true
    }

    /**
     * 移除单个隐藏好友 wxId。
     *
     * 注意：此方法存在 read-modify-write 竞态条件。
     *
     * @param context Android Context
     * @param wxId 要移除的微信 ID
     */
    fun removeHiddenWxId(context: Context, wxId: String) {
        val current = getCachedHiddenIds(context).toMutableSet()
        current.remove(wxId)
        setHiddenWxIds(context, current)
    }

    /**
     * 判断指定 wxId 是否在隐藏列表中。
     *
     * 使用缓存机制，避免每次都从 SP 读取。
     *
     * @param context Android Context
     * @param wxId 要检查的微信 ID
     * @return true 如果该 wxId 被隐藏
     */
    fun isHidden(context: Context, wxId: String): Boolean {
        return wxId in getCachedHiddenIds(context)
    }

    // ===== 模块开关 =====

    /**
     * 获取模块是否启用。
     *
     * 默认值为 true（模块默认启用）。
     *
     * @param context Android Context
     * @return true 如果模块已启用，默认值为 true
     */
    fun isEnabled(context: Context): Boolean {
        return getSP(context).getBoolean(KEY_ENABLED, true)
    }

    /**
     * 设置模块启用/禁用状态。
     *
     * @param context Android Context
     * @param enabled true 启用模块，false 禁用模块
     */
    fun setEnabled(context: Context, enabled: Boolean) {
        getSP(context).edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    // ===== 备注/标签 =====

    data class FriendLabel(val wxId: String, val label: String)

    /**
     * 获取所有标签/备注映射。
     *
     * 默认返回空 Map（没有任何标签）。
     * 使用 Gson 从 SharedPreferences 中的 JSON 字符串反序列化。
     *
     * @param context Android Context
     * @return wxId 到标签的映射，默认值为空 Map
     */
    fun getLabels(context: Context): Map<String, String> {
        val json = getSP(context).getString(KEY_LABELS, null) ?: return emptyMap()
        return try {
            val type = object : TypeToken<Map<String, String>>() {}.type
            gson.fromJson(json, type)
        } catch (e: Exception) {
            LogUtil.w("ConfigManager", "Failed to parse labels JSON: ${e.message}")
            emptyMap()
        }
    }

    /**
     * 设置指定好友的标签/备注。
     *
     * 标签会经过 ConfigValidator 验证，格式不合法时静默忽略。
     * 空字符串视为移除标签。
     *
     * @param context Android Context
     * @param wxId 微信 ID
     * @param label 标签文本，最大长度 50 字符
     */
    fun setLabel(context: Context, wxId: String, label: String) {
        // 验证标签格式
        val validation = ConfigValidator.validateLabel(label)
        if (!validation.valid) {
            LogUtil.w("ConfigManager", "setLabel: invalid label '$label' - ${validation.message}")
            return
        }
        val labels = getLabels(context).toMutableMap()
        labels[wxId] = label
        getSP(context).edit().putString(KEY_LABELS, gson.toJson(labels)).apply()
    }

    /**
     * 移除指定好友的标签/备注。
     *
     * @param context Android Context
     * @param wxId 微信 ID
     */
    fun removeLabel(context: Context, wxId: String) {
        val labels = getLabels(context).toMutableMap()
        labels.remove(wxId)
        getSP(context).edit().putString(KEY_LABELS, gson.toJson(labels)).apply()
    }

    // ========================================================================
    // 批量操作
    // ========================================================================

    /**
     * 获取所有配置的 JSON 表示
     *
     * 包含 enabled, hiddenIds, labels 三个字段。
     * 用于批量读取和导出。
     *
     * @return 包含所有配置的 Map
     */
    fun getAllConfig(context: Context): Map<String, Any> {
        return mapOf(
            "enabled" to isEnabled(context),
            "hiddenIds" to getCachedHiddenIds(context).toList(),
            "labels" to getLabels(context)
        )
    }

    /**
     * 批量设置所有配置
     *
     * 接受一个 Map，一次性设置所有配置项。
     * 支持的 key: "enabled" (Boolean), "hiddenIds" (List<String>), "labels" (Map<String, String>)
     *
     * @return 成功设置的配置项数量
     */
    fun setAllConfig(context: Context, config: Map<String, Any>): Int {
        var count = 0
        val editor = getSP(context).edit()

        config["enabled"]?.let { value ->
            if (value is Boolean) {
                editor.putBoolean(KEY_ENABLED, value)
                count++
            }
        }

        config["hiddenIds"]?.let { value ->
            @Suppress("UNCHECKED_CAST")
            val ids = (value as? List<*>)?.mapNotNull { it as? String }?.toSet()
            if (ids != null) {
                editor.putStringSet(KEY_HIDDEN_IDS, ids)
                count++
            }
        }

        config["labels"]?.let { value ->
            @Suppress("UNCHECKED_CAST")
            val labels = value as? Map<String, String>
            if (labels != null) {
                editor.putString(KEY_LABELS, gson.toJson(labels))
                count++
            }
        }

        editor.apply()
        invalidateCache()
        return count
    }

    /**
     * 重置所有配置到默认值
     *
     * 清除所有隐藏好友、标签，并将模块开关重置为 true。
     */
    fun clear(context: Context) {
        getSP(context).edit()
            .clear()
            .putBoolean(KEY_ENABLED, true)
            .apply()
        invalidateCache()
    }

    /**
     * 将当前配置备份到外部存储的 JSON 文件
     *
     * 文件保存路径: {外部存储}/Mirage/backup/mirage_backup_{时间戳}.json
     *
     * @param context Android Context
     * @return 备份文件的绝对路径，失败返回 null
     */
    fun backup(context: Context): String? {
        return try {
            val backupDir = File(
                Environment.getExternalStorageDirectory(),
                "Mirage/backup"
            )
            if (!backupDir.exists()) {
                backupDir.mkdirs()
            }

            val timestamp = System.currentTimeMillis()
            val backupFile = File(backupDir, "mirage_backup_$timestamp.json")

            val data = mapOf(
                "version" to 1,
                "timestamp" to timestamp,
                "enabled" to isEnabled(context),
                "hiddenIds" to getCachedHiddenIds(context).toList(),
                "labels" to getLabels(context)
            )

            backupFile.writeText(gson.toJson(data))
            backupFile.absolutePath
        } catch (e: Exception) {
            LogUtil.e("ConfigManager", "Failed to backup config: ${e.message}", e)
            null
        }
    }

    // ===== 导入/导出 =====

    /**
     * 导出隐藏好友列表为 JSON 字符串。
     *
     * 包含 hiddenIds 和 labels 两个字段。
     * 默认导出空列表和空 Map 的 JSON 表示。
     *
     * @param context Android Context
     * @return JSON 格式的配置字符串
     */
    fun exportToJson(context: Context): String {
        val data = mapOf(
            "hiddenIds" to getCachedHiddenIds(context).toList(),
            "labels" to getLabels(context)
        )
        return gson.toJson(data)
    }

    /**
     * 从 JSON 字符串导入隐藏好友列表。
     *
     * JSON 会经过 ConfigValidator 验证格式。
     * 默认返回 -1 表示导入失败。
     *
     * @param context Android Context
     * @param json JSON 格式的配置字符串
     * @return 成功导入的条目数量，失败返回 -1
     */
    fun importFromJson(context: Context, json: String): Int {
        return try {
            // 验证导入 JSON 格式
            val validation = ConfigValidator.validateImportJson(json)
            if (!validation.valid) {
                LogUtil.w("ConfigManager", "importFromJson: invalid JSON - ${validation.message}")
                return -1
            }

            val type = object : TypeToken<Map<String, Any>>() {}.type
            val data: Map<String, Any> = gson.fromJson(json, type)

            // 导入隐藏 ID
            val ids = (data["hiddenIds"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
            if (ids.isNotEmpty()) {
                setHiddenWxIds(context, ids.toSet())
            }

            // 导入标签
            @Suppress("UNCHECKED_CAST")
            val labels = (data["labels"] as? Map<String, String>) ?: emptyMap()
            if (labels.isNotEmpty()) {
                getSP(context).edit().putString(KEY_LABELS, gson.toJson(labels)).apply()
            }

            ids.size
        } catch (e: Exception) {
            LogUtil.e("ConfigManager", "Failed to import from JSON: ${e.message}", e)
            -1
        }
    }
}