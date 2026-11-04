package com.example.util

import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.StatFs
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Privacy-preserving ring buffer logger (stores last 300 diagnostic events, NEVER logs question text)
 * and uncaught crash trace recorder for easy resident reporting («کپی گزارش»).
 */
object DiagnosticsLogger {

    private const val MAX_LINES = 300
    private const val PREFS_NAME = "medo_diagnostics_prefs"
    private const val KEY_LAST_CRASH = "last_crash_trace"

    private val ringBuffer = ArrayDeque<String>(MAX_LINES)
    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    @Synchronized
    fun logEvent(category: String, message: String) {
        // Safety guard: never log long strings or HTML that could contain question text
        val sanitized = message
            .replace(Regex("<[^>]+>"), "[html]")
            .take(140)
        val ts = timeFormat.format(Date())
        val entry = "[$ts] [$category] $sanitized"
        if (ringBuffer.size >= MAX_LINES) {
            ringBuffer.removeFirst()
        }
        ringBuffer.addLast(entry)
    }

    @Synchronized
    fun getRecentLines(): List<String> = ringBuffer.toList()

    @Synchronized
    fun clearLogs() {
        ringBuffer.clear()
    }

    fun installCrashHandler(context: Context) {
        val appContext = context.applicationContext
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val sw = StringWriter()
                throwable.printStackTrace(PrintWriter(sw))
                val trace = "Thread: ${thread.name}\nTime: ${Date()}\n${sw.toString().take(4000)}"
                appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .edit()
                    .putString(KEY_LAST_CRASH, trace)
                    .commit()
            } catch (_: Exception) {
            }
            previousHandler?.uncaughtException(thread, throwable)
        }
    }

    fun getLastCrashTrace(context: Context): String? {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_LAST_CRASH, null)
    }

    fun clearLastCrashTrace(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_LAST_CRASH)
            .apply()
    }

    fun buildDiagnosticsReport(
        context: Context,
        appVersion: String,
        totalQuestions: Int,
        totalCategories: Int,
        installedPacksSummary: String,
        answeredCount: Int
    ): String {
        val statFs = StatFs(Environment.getDataDirectory().path)
        val freeMb = (statFs.availableBytes / (1024L * 1024L))
        val totalMb = (statFs.totalBytes / (1024L * 1024L))
        val lastCrash = getLastCrashTrace(context) ?: "بدون خطای ثبت‌شده (No crashes)"
        val logs = getRecentLines().joinToString("\n")

        return buildString {
            appendLine("=== گزارش عیب‌یابی Medo Next ===")
            appendLine("نسخه برنامه: $appVersion")
            appendLine("نسخه اندروید: API ${Build.VERSION.SDK_INT} (${Build.VERSION.RELEASE})")
            appendLine("مدل دستگاه: ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("تعداد کل سوالات بانک: $totalQuestions")
            appendLine("تعداد دسته‌بندی‌ها: $totalCategories")
            appendLine("سوالات پاسخ‌داده‌شده: $answeredCount")
            appendLine("بسته‌های نصب‌شده: $installedPacksSummary")
            appendLine("فضای آزاد داخلی: $freeMb MB از $totalMb MB")
            appendLine("--- آخرین گزارش کرش ---")
            appendLine(lastCrash)
            appendLine("--- آخرین رویدادهای سیستم (${getRecentLines().size} خط) ---")
            appendLine(logs)
        }
    }
}
