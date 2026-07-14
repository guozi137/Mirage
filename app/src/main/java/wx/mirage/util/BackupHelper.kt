package wx.mirage.util

import android.content.Context
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import wx.mirage.R

/**
 * 备份管理 UI 辅助类
 *
 * 从 SettingsActivity 和 MainActivity 中提取公共的备份管理 UI 逻辑，
 * 提供创建备份、恢复备份、列出备份、删除旧备份的对话框方法。
 *
 * 所有方法均为静态方法，不持有状态，通过 object 声明实现。
 *
 * @see BackupManager 底层备份数据操作
 */
object BackupHelper {

    /**
     * 创建备份并显示结果提示
     *
     * @param context Android Context
     * @param onBackupCreated 备份创建成功后的回调（用于刷新 UI）
     */
    fun createBackup(context: Context, onBackupCreated: () -> Unit = {}) {
        val result = BackupManager.createBackup(context)
        if (result.isSuccess) {
            Toast.makeText(
                context,
                context.getString(R.string.backup_created, result.getOrDefault("")),
                Toast.LENGTH_SHORT
            ).show()
            onBackupCreated()
        } else {
            Toast.makeText(context, R.string.backup_failed, Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 显示恢复备份对话框
     *
     * 列出所有可用备份，用户选择后确认恢复。
     *
     * @param context Android Context
     * @param onRestoreCompleted 恢复完成后的回调（用于刷新 UI）
     */
    fun showRestoreBackupDialog(context: Context, onRestoreCompleted: () -> Unit = {}) {
        val backups = BackupManager.listBackups(context)
        if (backups.isEmpty()) {
            Toast.makeText(context, R.string.no_backups, Toast.LENGTH_SHORT).show()
            return
        }

        val backupNames = backups.map { it.name }
        AlertDialog.Builder(context)
            .setTitle(R.string.select_backup_to_restore)
            .setItems(backupNames.toTypedArray()) { _, which ->
                confirmRestore(context, backups[which], onRestoreCompleted)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    /**
     * 确认恢复备份对话框
     */
    private fun confirmRestore(
        context: Context,
        backup: BackupManager.BackupInfo,
        onRestoreCompleted: () -> Unit
    ) {
        AlertDialog.Builder(context)
            .setTitle(R.string.confirm_restore)
            .setMessage(context.getString(R.string.restore_confirm_message, backup.name))
            .setPositiveButton(R.string.restore) { _, _ ->
                val result = BackupManager.restoreBackup(context, backup.name)
                if (result.isSuccess) {
                    onRestoreCompleted()
                    Toast.makeText(context, R.string.backup_restored, Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, R.string.restore_failed, Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    /**
     * 显示所有备份列表对话框
     *
     * 列出所有备份的基本信息（名称、时间、大小），支持删除操作。
     *
     * @param context Android Context
     */
    fun showListBackupsDialog(context: Context) {
        val backups = BackupManager.listBackups(context)
        if (backups.isEmpty()) {
            Toast.makeText(context, R.string.no_backups, Toast.LENGTH_SHORT).show()
            return
        }

        val sb = StringBuilder()
        for ((index, backup) in backups.withIndex()) {
            sb.append("${index + 1}. ${backup.name}\n")
            sb.append("   ${backup.timestamp}\n")
            sb.append("   ${backup.size}\n\n")
        }

        AlertDialog.Builder(context)
            .setTitle(R.string.backup_list)
            .setMessage(sb.toString().trim())
            .setPositiveButton(R.string.ok, null)
            .show()
    }

    /**
     * 显示删除旧备份对话框
     *
     * 列出所有备份并允许用户选择删除。
     *
     * @param context Android Context
     * @param onDeleted 删除完成后的回调
     */
    fun showDeleteOldBackupsDialog(context: Context, onDeleted: () -> Unit = {}) {
        val backups = BackupManager.listBackups(context)
        if (backups.isEmpty()) {
            Toast.makeText(context, R.string.no_backups, Toast.LENGTH_SHORT).show()
            return
        }

        val backupNames = backups.map { it.name }
        AlertDialog.Builder(context)
            .setTitle(R.string.select_backup_to_delete)
            .setItems(backupNames.toTypedArray()) { _, which ->
                val name = backups[which].name
                AlertDialog.Builder(context)
                    .setTitle(R.string.confirm_delete)
                    .setMessage(context.getString(R.string.delete_backup_confirm, name))
                    .setPositiveButton(R.string.delete) { _, _ ->
                        val result = BackupManager.deleteBackup(context, name)
                        if (result.isSuccess) {
                            onDeleted()
                            Toast.makeText(context, R.string.backup_deleted, Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, R.string.delete_failed, Toast.LENGTH_SHORT).show()
                        }
                    }
                    .setNegativeButton(R.string.cancel, null)
                    .show()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
}