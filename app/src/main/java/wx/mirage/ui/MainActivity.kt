package wx.mirage.ui

import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ListView
import android.widget.Switch
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import wx.mirage.Constants
import wx.mirage.R
import wx.mirage.config.ConfigManager
import wx.mirage.config.WxIdBlacklist
import wx.mirage.util.BackupManager
import wx.mirage.util.ConfigValidator
import wx.mirage.util.LogUtil

/**
 * Mirage 主管理界面
 *
 * 功能：
 * 1. 显示模块状态信息（DexKit 状态、进程信息、微信版本等）
 * 2. 管理隐藏好友列表（添加/删除 wxId）
 * 3. 好友标签系统（为每个隐藏好友设置自定义标签）
 * 4. 备份管理（创建/恢复/列出/删除备份）
 * 5. 模块开关控制（启用/禁用 Mirage）
 * 6. 配置验证（JSON 格式验证）
 *
 * 使用 [LabelAdapter] 为隐藏好友列表提供彩色标签显示。
 */
class MainActivity : AppCompatActivity() {

    private lateinit var wxIdInput: EditText
    private lateinit var addButton: Button
    private lateinit var hiddenList: ListView
    private lateinit var enableSwitch: Switch
    private lateinit var backupButton: Button
    private lateinit var restoreButton: Button
    private lateinit var validateButton: Button
    private lateinit var labelInput: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initViews()
        updateHiddenList()
        setupListeners()
    }

    private fun initViews() {
        wxIdInput = findViewById(R.id.wxIdInput)
        addButton = findViewById(R.id.addButton)
        hiddenList = findViewById(R.id.hiddenList)
        enableSwitch = findViewById(R.id.enableSwitch)
        backupButton = findViewById(R.id.backupButton)
        restoreButton = findViewById(R.id.restoreButton)
        validateButton = findViewById(R.id.validateButton)
        labelInput = findViewById(R.id.labelInput)

        enableSwitch.isChecked = ConfigManager.isModuleEnabled()
    }

    private fun setupListeners() {
        addButton.setOnClickListener { addWxId() }
        enableSwitch.setOnCheckedChangeListener { _, isChecked ->
            ConfigManager.setModuleEnabled(isChecked)
            LogUtil.i(Constants.MODULE_TAG, "Module enabled: $isChecked")
        }

        hiddenList.setOnItemLongClickListener { _, _, position, _ ->
            showDeleteDialog(position)
            true
        }

        hiddenList.setOnItemClickListener { _, _, position, _ ->
            showEditLabelDialog(position)
        }

        backupButton.setOnClickListener { createBackup() }
        restoreButton.setOnClickListener { showRestoreBackupDialog() }
        validateButton.setOnClickListener { validateConfig() }
    }

    private fun addWxId() {
        val wxId = wxIdInput.text.toString().trim()
        if (wxId.isEmpty()) {
            Toast.makeText(this, R.string.empty_wx_id, Toast.LENGTH_SHORT).show()
            return
        }

        if (ConfigValidator.isValidWxId(wxId)) {
            val label = labelInput.text.toString().trim()
            val success = if (label.isNotEmpty()) {
                WxIdBlacklist.addWxIdWithLabel(wxId, label)
            } else {
                WxIdBlacklist.addWxId(wxId)
            }

            if (success) {
                wxIdInput.text.clear()
                labelInput.text.clear()
                updateHiddenList()
                Toast.makeText(this, R.string.wx_id_added, Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, R.string.wx_id_exists, Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(this, R.string.invalid_wx_id, Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateHiddenList() {
        val ids = WxIdBlacklist.getWxIds()
        val labels = WxIdBlacklist.getLabels()
        val adapter = LabelAdapter(this, ids, labels)
        hiddenList.adapter = adapter
    }

    private fun showDeleteDialog(position: Int) {
        val ids = WxIdBlacklist.getWxIds()
        if (position >= ids.size) return

        val wxId = ids[position]
        val label = WxIdBlacklist.getLabel(wxId)
        val displayText = if (label.isNullOrEmpty()) wxId else "$wxId [$label]"

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.confirm_delete))
            .setMessage(getString(R.string.delete_confirm_message, displayText))
            .setPositiveButton(R.string.delete) { _, _ ->
                WxIdBlacklist.removeWxId(wxId)
                updateHiddenList()
                Toast.makeText(this, R.string.wx_id_deleted, Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showEditLabelDialog(position: Int) {
        val ids = WxIdBlacklist.getWxIds()
        if (position >= ids.size) return

        val wxId = ids[position]
        val currentLabel = WxIdBlacklist.getLabel(wxId) ?: ""

        val editText = EditText(this)
        editText.setText(currentLabel)
        editText.hint = getString(R.string.label_hint)

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.edit_label))
            .setView(editText)
            .setPositiveButton(R.string.save) { _, _ ->
                val newLabel = editText.text.toString().trim()
                WxIdBlacklist.setLabel(wxId, newLabel)
                updateHiddenList()
                Toast.makeText(this, R.string.label_saved, Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    // ========================================================================
    // 备份管理
    // ========================================================================

    private fun createBackup() {
        val result = BackupManager.createBackup(this)
        if (result.isSuccess) {
            Toast.makeText(this, getString(R.string.backup_created, result.getOrDefault("")), Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, R.string.backup_failed, Toast.LENGTH_SHORT).show()
        }
    }

    private fun showRestoreBackupDialog() {
        val backups = BackupManager.listBackups(this)
        if (backups.isEmpty()) {
            Toast.makeText(this, R.string.no_backups, Toast.LENGTH_SHORT).show()
            return
        }

        val backupNames = backups.map { it.name }
        AlertDialog.Builder(this)
            .setTitle(R.string.select_backup_to_restore)
            .setItems(backupNames.toTypedArray()) { _, which ->
                confirmRestore(backups[which])
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun confirmRestore(backup: BackupManager.BackupInfo) {
        AlertDialog.Builder(this)
            .setTitle(R.string.confirm_restore)
            .setMessage(getString(R.string.restore_confirm_message, backup.name))
            .setPositiveButton(R.string.restore) { _, _ ->
                val result = BackupManager.restoreBackup(this, backup.name)
                if (result.isSuccess) {
                    updateHiddenList()
                    Toast.makeText(this, R.string.backup_restored, Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, R.string.restore_failed, Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
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