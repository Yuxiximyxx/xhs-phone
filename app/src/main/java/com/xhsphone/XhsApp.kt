package com.xhsphone

import android.app.Application
import android.content.ContentValues
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class XhsApp : Application() {

    override fun onCreate() {
        super.onCreate()
        installCrashRecorder()
    }

    /**
     * 全局崩溃记录：未捕获异常时，在崩溃线程同步写出完整堆栈到
     * 手机"下载"文件夹（xhs-crash_*.txt），不联网、不起后台线程，
     * 写完后交还系统默认处理。
     */
    private fun installCrashRecorder() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                writeCrashFile(thread, throwable)
            } catch (e: Exception) {
                // 记录本身失败也不影响系统处理崩溃
            } finally {
                defaultHandler?.uncaughtException(thread, throwable)
            }
        }
    }

    private fun writeCrashFile(thread: Thread, throwable: Throwable) {
        val sb = StringBuilder()
        val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        sb.appendLine("time: $time")
        sb.appendLine("thread: ${thread.name}")
        sb.appendLine("app: com.xhsphone")
        sb.appendLine("device: ${Build.MANUFACTURER} ${Build.MODEL}")
        sb.appendLine("android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        sb.appendLine("---- stack ----")
        val sw = StringWriter()
        throwable.printStackTrace(PrintWriter(sw))
        sb.append(sw.toString())

        val fileName = "xhs-crash_" +
            SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date()) + ".txt"
        val bytes = sb.toString().toByteArray(Charsets.UTF_8)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android 10+：经 MediaStore 写公共下载目录，无需权限
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, "text/plain")
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }
            val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            if (uri != null) {
                contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
                return
            }
        }
        // 兜底：应用专属外部目录（无需权限，文件管理器可见）
        val dir = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: filesDir
        File(dir, fileName).writeBytes(bytes)
    }
}
