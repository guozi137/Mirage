# Mirage

> 微信好友隐身 Xposed 模块 -- 不删除、不屏蔽，好友仍可发消息打电话

[![Build Status](https://img.shields.io/github/actions/workflow/status/HdShare/Mirage/build.yml?branch=master&label=Build)](https://github.com/HdShare/Mirage/actions)
[![Version](https://img.shields.io/badge/version-1.0.1-blue)](https://github.com/HdShare/Mirage/releases)
[![Xposed API](https://img.shields.io/badge/Xposed_API-93-green)](https://api.xposed.info/)
[![Android](https://img.shields.io/badge/Android-8.0%2B-brightgreen)](https://developer.android.com/)
[![License](https://img.shields.io/badge/license-Research%20%26%20Study-lightgrey)](./README.md)

---

## 目录

- [简介](#简介)
- [功能特性](#功能特性)
- [兼容性](#兼容性)
- [架构](#架构)
- [配置](#配置)
- [使用方法](#使用方法)
- [构建](#构建)
- [依赖](#依赖)
- [故障排除](#故障排除)
- [贡献指南](#贡献指南)
- [许可证](#许可证)
- [免责声明](#免责声明)

---

## 简介

Mirage 是一个基于 Xposed/LSPosed 框架的微信辅助模块，能够在微信前端 UI 层面隐藏指定好友，使其在联系人列表、会话列表、朋友圈、通知、搜索和群成员列表中"隐身"。

**核心策略：不删除、不屏蔽好友**，好友仍然可以正常发消息和打电话，只是在前端界面不可见。

---

## 功能特性

Mirage 提供 **6 大 Hook 模块**，覆盖微信好友可见性的所有场景：

### 1. ContactHook -- 联系人列表隐身

在微信联系人列表中隐藏指定好友。通过 DexKit 动态查找联系人 Adapter 类，Hook `getCount`、`getItem`、`getItemCount`、`onBindViewHolder` 等方法，配合 Cursor 层的 `moveToNext` / `moveToFirst` 拦截，实现多层过滤。支持 10+ 种 wxId 提取策略，兼容不同微信混淆版本。

### 2. ConversationHook -- 会话列表隐身

在聊天列表（主界面）中隐藏指定好友的会话条目。Hook 会话列表 Adapter 的 `getCount`、`getItem`、`onBindViewHolder` 方法，同时在数据库 Cursor 层（`moveToNext`、`moveToFirst`、`getString`）进行二次拦截。支持 13 种 talker 字段提取策略，涵盖 `talker`、`field_talker`、`username` 等常见混淆字段名。

### 3. MomentsHook -- 朋友圈隐身

在朋友圈 Feed 流中隐藏指定好友发布的动态。Hook SnsTimeLineBaseAdapter（`rs` 类）的 `getView`、`getItem`、`getCursor`、`getCount` 方法，以及 SnsObject / TimeLineObject 等 protobuf 数据类。支持 17 种 SNS 作者 wxId 提取策略，覆盖 `userName`、`snsId`、`feedId`、`talker` 等字段。

### 4. NotificationHook -- 通知拦截

拦截隐藏好友的消息通知，防止通知栏弹出隐藏好友的消息。Hook 微信通知管理器，在通知显示前对消息发送者进行校验，若发送者处于隐藏列表中则拦截该通知。

### 5. SearchHook -- 搜索防泄漏

防止通过微信全局搜索功能（`com.tencent.mm.plugin.fts`）找到隐藏好友。Hook 搜索索引构建和搜索结果显示方法，在搜索结果返回前过滤掉隐藏好友的条目，确保搜索功能不会暴露隐藏好友的存在。

### 6. GroupMemberHook -- 群成员列表隐身

在群聊成员列表中隐藏指定好友，同时覆盖 `@` 提及列表中的成员搜索。Hook 群成员列表 Adapter 及 `@` 列表相关方法，采用与 ContactHook 类似的多层过滤策略，确保隐藏好友在群聊场景中也不可见。

---

## 兼容性

| 项目 | 要求 |
|------|------|
| 框架 | Xposed / LSPosed / LSPatch |
| 最低 Android 版本 | 8.0 (API 26) |
| 目标微信版本 | 配合 DexKit 动态适配微信混淆，理论上兼容多种微信版本 |

---

## 架构

### 三层策略 Hook 架构

Mirage 采用**三层策略 Hook 架构**，确保在各种微信版本中都能正常工作：

```
第一层: DexKit 动态查找
  +-- 按真实微信字符串特征（如 "username", "talker", "rcontact" 等）
  |   搜索混淆后的类和方法，精确定位目标
  |
  +-- 优势: 对微信版本升级适应性强，不依赖硬编码类名
  |
第二层: 已知类直接 Hook
  +-- 基于微信 APK 分析结果，直接 Hook 已知类名
  |   （如 MMApplicationLike, SelectContactUI, rs 等）
  |
  +-- 优势: 快速、稳定，无需运行时搜索
  |
第三层: 降级兜底 (Cursor/Adapter/数据库层)
  +-- 在 Cursor 层 (moveToNext/moveToFirst/getString)
  |   和 Adapter 层 (onBindViewHolder/getItem/getCount)
  |   进行底层数据拦截
  |
  +-- 优势: 即使前两层失败，也能在数据层面保证隐身效果
```

### 模块结构

```
Mirage (Xposed Module)
+-- 入口: MainHook
|   +-- Zygote 阶段 -- 获取 modulePath
|   +-- handleLoadPackage -- 仅微信主进程
|       +-- DexKit 初始化
|       +-- ConfigManager 初始化
|       +-- Hook MMApplicationLike.onCreate
|           +-- registerAllHooks()
|               +-- ContactHook       (联系人列表隐身)
|               +-- ConversationHook  (会话列表隐身)
|               +-- MomentsHook       (朋友圈隐身)
|               +-- NotificationHook  (通知拦截)
|               +-- SearchHook        (搜索防泄漏)
|               +-- GroupMemberHook   (群成员列表隐身)
+-- 工具: WxIdUtils (5 策略 wxId 提取)
+-- 配置: ConfigManager (SharedPreferences)
+-- UI: MainActivity (管理隐藏好友列表)
```

### 多进程安全

Mirage 仅在微信主进程（`com.tencent.mm`）中工作，自动跳过 `:push`、`:tools`、`:support`、`:appbrand`、`:finder` 等 14 个子进程，避免重复注入和不必要的性能开销。

---

## 配置

### 添加隐藏好友

通过 Mirage 应用界面管理隐藏好友列表：

1. 打开 Mirage 应用
2. 点击"添加隐藏好友"
3. 输入要隐藏的好友微信 ID（如 `wxid_xxxxx`）
4. 返回微信，该好友即被隐藏

### 移除隐藏好友

1. 在 Mirage 应用中找到已隐藏的好友
2. 长按该好友条目
3. 点击"确定"移除

### 配置存储

配置数据存储在 SharedPreferences 中（`wx_mirage_config`），支持：

- **隐藏好友列表**：Set 类型存储，支持批量添加/删除
- **好友备注标签**：JSON 格式存储，方便识别不同好友
- **模块开关**：一键启用/禁用模块
- **导入/导出**：JSON 格式导出配置，方便备份和迁移

### 配置文件格式

```json
{
  "hiddenIds": ["wxid_abc123", "wxid_def456"],
  "labels": {
    "wxid_abc123": "同事A",
    "wxid_def456": "同学B"
  }
}
```

---

## 使用方法

### 安装

1. 确保已安装 Xposed/LSPosed 框架
2. 下载并安装 Mirage APK
3. 在 Xposed/LSPosed 管理器中启用 Mirage 模块
4. 勾选作用域：**微信 (com.tencent.mm)**
5. 重启微信生效

### 验证

安装完成后，可在 Xposed/LSPosed 日志中搜索 `Mirage` 标签确认模块已正常加载。成功加载的日志示例：

```
Mirage: [initZygote] modulePath=/data/app/.../base.apk
Mirage: [DexKit] Initialization SUCCESS
Mirage: [MMApplicationLike.onCreate] ========== All hooks registered SUCCESS ==========
```

---

## 构建

### 环境要求

| 工具 | 版本 |
|------|------|
| Android Studio | Hedgehog (2023.1.1) 或更高版本 |
| JDK | 17 |
| Gradle | 8.2+ |

### 编译命令

```bash
# 克隆项目
git clone https://github.com/HdShare/Mirage.git
cd Mirage

# 编译 Debug APK
./gradlew assembleDebug

# 编译 Release APK
./gradlew assembleRelease

# 清理构建产物
./gradlew clean
```

编译产物位于 `app/build/outputs/apk/release/`。

### GitHub Actions

项目配置了 GitHub Actions 自动编译流程，在 GitHub 仓库的 **Actions** 标签页中可以手动触发编译：

1. 进入仓库的 **Actions** 页面
2. 选择 **Build APK** 工作流
3. 点击 **Run workflow** 按钮
4. 编译完成后下载生成的 APK

---

## 依赖

| 依赖 | 用途 |
|------|------|
| [Xposed API](https://github.com/rovo89/XposedBridge) | Xposed 框架 Hook API |
| [DexKit](https://github.com/LuckyPray/DexKit) | 动态查找微信混淆类和方法 |
| [Gson](https://github.com/google/gson) | JSON 序列化 |
| AndroidX | Android 支持库 |

---

## 故障排除

### 模块未生效

1. 确认 Xposed/LSPosed 框架已正确安装并激活
2. 检查 Mirage 模块在 LSPosed 中是否已启用
3. 确认作用域已勾选 **微信 (com.tencent.mm)**
4. 重启微信（完全退出后重新打开，不是切后台）

### DexKit 初始化失败

DexKit 需要从微信 APK 中加载，如果初始化失败：

1. 确认微信已正常安装
2. 检查 Xposed/LSPosed 日志中是否有 `[DexKit] Initialization FAILED` 信息
3. DexKit 失败时模块会自动降级使用已知类名 Hook，大部分功能仍可正常工作

### 部分好友未隐藏

1. 确认输入的好友微信 ID 格式正确（如 `wxid_xxxxx`）
2. 不同微信版本中的混淆字段名可能不同，模块已覆盖 10+ 种提取策略
3. 查看 Xposed 日志中是否有关键词 `[Mirage]` 的错误信息

### 获取微信 ID

可以通过以下方式获取好友的微信 ID：

1. 在微信中长按好友头像，查看详情
2. 使用第三方工具获取
3. 注意：微信 ID 与微信号（alias）不同，微信 ID 通常以 `wxid_` 开头

---

## 贡献指南

欢迎贡献代码、提交 Issue 或提出改进建议。

### 贡献方式

1. **Fork** 本仓库
2. 创建特性分支 (`git checkout -b feature/amazing-feature`)
3. 提交更改 (`git commit -m 'Add amazing feature'`)
4. 推送到分支 (`git push origin feature/amazing-feature`)
5. 创建 Pull Request

### 代码规范

- 使用 Kotlin 编写
- 遵循 Android Kotlin 代码风格指南
- 每个 Hook 模块独立文件，保持单一职责
- 添加适当的注释和文档

### 提交 Issue

提交 Issue 时请提供以下信息：

- 使用的框架及版本（Xposed / LSPosed / LSPatch）
- 微信版本号
- Android 版本
- 相关 Xposed 日志（搜索 `Mirage` 标签）

---

## 许可证

本项目仅用于**学习和研究**目的，探讨 Android Hook 技术在客户端应用中的实现方式。

**禁止用于任何违法违规用途。** 使用本模块产生的一切后果由使用者自行承担。

---

## 免责声明

本模块仅修改微信客户端的前端展示，不涉及任何数据篡改或协议攻击。使用本模块产生的一切后果由使用者自行承担。