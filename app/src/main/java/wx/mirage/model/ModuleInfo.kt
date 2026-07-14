package wx.mirage.model

import wx.mirage.config.HookStatus

/**
 * 模块元数据信息数据类
 *
 * 用于统一描述 Mirage 各子模块的基本信息，便于统一管理和状态展示。
 *
 * @param name 模块名称
 * @param version 模块版本号
 * @param description 模块功能描述
 * @param status 模块当前状态
 */
data class ModuleInfo(
    val name: String,
    val version: String,
    val description: String,
    val status: HookStatus
) {
    companion object {
        /**
         * 创建 Mirage 主模块信息。
         */
        fun createMainModule(status: HookStatus): ModuleInfo = ModuleInfo(
            name = "Mirage",
            version = "1.0.1",
            description = "WeChat friend stealth Xposed module",
            status = status
        )

        /**
         * 创建 Hook 子模块信息。
         */
        fun createHookModule(
            name: String,
            description: String,
            status: HookStatus
        ): ModuleInfo = ModuleInfo(
            name = name,
            version = "1.0.1",
            description = description,
            status = status
        )
    }
}