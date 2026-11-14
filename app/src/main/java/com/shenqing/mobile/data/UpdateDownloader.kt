package com.shenqing.mobile.data

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.content.FileProvider
import java.io.File

/** 应用内下载 APK，并在完成后交给系统包安装器。 */
object UpdateDownloader {
    private const val APK_MIME = "application/vnd.android.package-archive"

    fun canInstall(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return true
        return context.packageManager.canRequestPackageInstalls()
    }

    fun openInstallSettings(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            runCatching {
                context.startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:" + context.packageName),
                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
        }
    }

    fun enqueue(context: Context, info: UpdateInfo): Long {
        if (info.assetUrl.isBlank()) throw IllegalArgumentException("Release 没有 APK 资产")
        val fileName = info.assetName.ifBlank { "shenqing-" + info.latest + ".apk" }
        val request = DownloadManager.Request(Uri.parse(info.assetUrl))
            .setTitle("深清手机端 " + info.latest)
            .setDescription("正在下载更新安装包")
            .setMimeType(APK_MIME)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(false)
            .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, fileName)
        val manager = context.getSystemService(DownloadManager::class.java)
        return manager.enqueue(request)
    }

    /** 下载完成后用 FileProvider 打开系统安装器。返回给 UI 的提示文本。 */
    fun installDownloaded(context: Context, downloadId: Long): String {
        if (!canInstall(context)) {
            openInstallSettings(context)
            return "请允许深清安装未知应用后，再点一次下载"
        }
        val manager = context.getSystemService(DownloadManager::class.java)
        val query = DownloadManager.Query().setFilterById(downloadId)
        manager.query(query).use { cursor ->
            if (cursor == null || !cursor.moveToFirst()) return "找不到下载任务"
            val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
            if (status != DownloadManager.STATUS_SUCCESSFUL) {
                val reason = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
                return "下载未完成（状态 " + status + " / " + reason + "）"
            }
            val localUri = cursor.getString(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_LOCAL_URI))
                ?: return "下载文件路径不可用"
            val path = Uri.parse(localUri).path ?: return "下载文件路径不可用"
            val file = File(path)
            if (!file.isFile) return "下载文件不存在"
            return installFile(context, file)
        }
    }

    fun installFile(context: Context, file: File): String {
        val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, APK_MIME)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(intent)
        return "已打开系统安装器"
    }
}
