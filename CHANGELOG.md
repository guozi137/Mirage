# Mirage 更新日志

本文档记录 Mirage Xposed 模块的所有版本变更历史。

## [v1.0.1] - 2026-07-15

### 新增
- 新增 `HookLifecycleListener` 接口，定义 Hook 生命周期回调（`onHookRegistered`、`onHookFailed`、`onHookUnregistered`），所有 Hook 模块均已实现该接口
- 新增 `ConfigValidator.kt` 配置验证器，提供 wxId 格式、标签长度、导入 JSON 格式的验证功能
- 新增微信版本兼容性检查机制，在模块加载时对比已知兼容版本列表，对未测试版本输出警告日志
- 新增 `ConfigReceiver.cleanup()` 方法，用于清理 BroadcastReceiver 资源

### 改进
- 增强 Hook 模块错误边界处理：每个 Hook 的 `init()` 方法中，各子策略（DexKit、已知类、缓存预热）均独立 try-catch，防止单个策略失败导致整个模块初始化失败
- 所有 Hook 模块现输出详细的堆栈跟踪日志（`getStackTraceString`），便于问题排查
- `MainHook.shutdown()` 流程优化：新增 `notifyHookUnregistered()` 步骤，在关闭时通知所有 Hook 模块执行卸载回调
- `MainHook.registerAllHooks()` 现返回 `Boolean` 值表示是否有至少一个 Hook 注册成功，并调用生命周期回调
- ConfigManager 新增默认值文档说明（所有方法均标注默认值），并在 `addHiddenWxId`、`setLabel`、`importFromJson` 中集成配置验证
- MainActivity 的添加好友和导入对话框新增输入验证，格式不合法时显示 Toast 提示
- `LogUtil` 导入已添加到所有需要日志的模块中

### 修复
- 修复 `SettingsActivity` 缺少 `onDestroy()` 资源清理的问题
- 修复 BroadcastReceiver 未正确清理可能导致内存泄漏的问题
- 修复 `MainHook` 中 `MODULE_TAG` 常量定义冲突的问题

### 文档
- 新增 `CHANGELOG.md` 版本变更日志
- 为 `ConfigManager.kt` 所有公共方法添加完整 KDoc（含 `@param` 和 `@return` 标签）
- 为 `MainHook.kt` 所有公共方法添加完整 KDoc（含 `@param` 和 `@return` 标签）
- 为 `HookLifecycle.kt` 接口添加完整使用文档

## [v1.0.0] - 2026-06-01

### 新增
- 首个公开发布版本
- 核心功能：隐藏微信好友（联系人列表、会话列表、朋友圈、群成员列表、通知、搜索结果）
- 基于 Xposed/LSPosed 框架的 DexKit 动态方法查找
- 配置管理界面（MainActivity），支持添加/删除/导入/导出隐藏好友
- 设置页面（SettingsActivity），支持模块开关和 Hook 状态查看
- 单个好友设置自定义标签/备注
- 广播接收器（ConfigReceiver）实现跨进程配置同步
- 多策略 Hook 机制：DexKit 查找 + 已知类直接 Hook + 回退方案
- 异步 DexKit 缓存预热
- 完整的日志系统（LogUtil）
- 性能监控（HookMetrics）
- 调试工具（DebugUtils）