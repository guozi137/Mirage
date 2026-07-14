package wx.mirage.ui

import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import wx.mirage.Constants
import wx.mirage.R
import wx.mirage.config.WxIdBlacklist
import wx.mirage.util.BackupHelper
import wx.mirage.util.ConfigValidator
import wx.mirage.util.HookMetrics
import wx.mirage.util.LogUtil

/**
 * Mirage 设置界面
 *
 * 功能：
 * 1. 导入/导出配置（保留现有逻辑）
 * 2. 备份管理（通过 [BackupHelper] 统一管理）
 * 3. 清除所有数据（重置黑名单和配置）
 * 4. 模块状态诊断（DexKit 状态、Hook 状态等）
 *
 * 备份管理逻辑已提取到 [BackupHelper] 工具类中，
 * 实现代码复用和关注点分离。
 */
class SettingsActivity : AppCompatActivity() {

    private lateinit var createBackupBtn: Button
    private lateinit var restoreBackupBtn: Button
    private lateinit var listBackupsBtn: Button
    private lateinit var deleteOldBackupsBtn: Button
    private lateinit var clearAllDataBtn: Button
    private lateinit var validateBtn: Button
    private lateinit var exportBtn: Button
    private lateinit var importBtn: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        initViews()
        setupListeners()
    }

    private fun initViews() {
        createBackupBtn = findViewById(R.id.createBackupBtn)
        restoreBackupBtn = findViewById(R.id.restoreBackupBtn)
        listBackupsBtn = findViewById(R.id.listBackupsBtn)
        deleteOldBackupsBtn = findViewById(R.id.deleteOldBackupsBtn)
        clearAllDataBtn = findViewById(R.id.clearAllDataBtn)
        validateBtn = findViewById(R.id.validateBtn)
        exportBtn = findViewById(R.id.exportBtn)
        importBtn = findViewById(R.id.importBtn)
    }

    private fun setupListeners() {
        createBackupBtn.setOnClickListener {
            BackupHelper.createBackup(this)
        }
        restoreBackupBtn.setOnClickListener {
            BackupHelper.showRestoreBackupDialog(this) {
                LogUtil.i(Constants.MODULE_TAG, "Backup restored, refreshing UI")
            }
        }
        listBackupsBtn.setOnClickListener {
            BackupHelper.showListBackupsDialog(this)
        }
        deleteOldBackupsBtn.setOnClickListener {
            BackupHelper.showDeleteOldBackupsDialog(this) {
                LogUtil.i(Constants.MODULE_TAG, "Backup deleted")
            }
        }
        clearAllDataBtn.setOnClickListener {
            clearAllData()
        }
        validateBtn.setOnClickListener {
            validateConfig()
        }
        exportBtn.setOnClickListener {
            exportConfig()
        }
        importBtn.setOnClickListener {
            importConfig()
        }
    }

    private fun clearAllData() {
        WxIdBlacklist.clearAll()
        wx.mirage.config.ConfigManager.clearConfig()
        HookMetrics.clearAllMetrics()
        Toast.makeText(this, R.string.data_cleared, Toast.LENGTH_SHORT).show()
        LogUtil.i(Constants.MODULE_TAG, "All data cleared")
    }

    private fun exportConfig() {
        val result = wx.mirage.util.BackupManager.createBackup(this)
        if (result.isSuccess) {
            Toast.makeText(this, R.string.config_exported, Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, R.string.export_failed, Toast.LENGTH_SHORT).show()
        }
    }

    private fun importConfig() {
        // 委托 BackupHelper 显示恢复对话框
        BackupHelper.showRestoreBackupDialog(this) {
            LogUtil.i(Constants.MODULE_TAG, "Config imported, refreshing UI")
        }
    }

    private fun validateConfig() {
        val isValid = ConfigValidator.validate()
        if (isValid) {
            Toast.makeText(this, R.string.config_valid, Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Config validation failed", Toast.LENGTH_SHORT).show()
        }
    }
}