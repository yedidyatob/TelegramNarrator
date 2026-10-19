package io.github.yedidyatob.telegramnarrator.core.diagnostics

import android.content.Context
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

/**
 * Android Auto / media-session diagnostics (logcat tag [TAG], and a small rolling file the user can share from
 * Settings when testing in a car without a PC). Privacy: callers log only package names, uids, ids, counts
 * and states, never message text, chat titles or phone numbers; exceptions are written as class names and
 * stack frames without their messages ([stackTrace]).
 */
object AutoLog {
    const val TAG = "TN-Auto"
    const val DIR = "diagnostics"
    const val FILE_NAME = "tn-auto.log"

    private val writer = Executors.newSingleThreadExecutor { Thread(it, "TN-Auto-log").apply { isDaemon = true } }
    @Volatile private var log: RollingLogFile? = null

    fun init(context: Context) {
        if (log == null) log = RollingLogFile(file(context))
    }

    fun file(context: Context): File = File(File(context.filesDir, DIR), FILE_NAME)

    fun event(message: String) {
        Log.i(TAG, message)
        write("I", message)
    }

    fun error(message: String, error: Throwable? = null) {
        Log.e(TAG, message, error)
        write("E", if (error == null) message else message + "\n" + stackTrace(error))
    }

    /** Uncaught exceptions of the app process (service started by Android Auto included) go to the file first. */
    fun installCrashHandler() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, e ->
            val line = line("E", "Uncaught exception in thread ${thread.name}\n" + stackTrace(e))
            // Synchronously: the process is about to die
            runCatching { synchronized(this) { log?.append(line) } }
            previous?.uncaughtException(thread, e)
        }
    }

    private fun write(level: String, message: String) {
        val target = log ?: return
        val line = line(level, message)
        writer.execute { runCatching { synchronized(this) { target.append(line) } } }
    }

    private fun line(level: String, message: String): String {
        val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS Z", Locale.US).format(Date())
        return "$time $level $message"
    }

    /** Class names and frames only: exception messages can carry user data. */
    fun stackTrace(error: Throwable): String = buildString {
        var current: Throwable? = error
        var depth = 0
        while (current != null && depth < 5) {
            if (depth > 0) append("Caused by: ")
            append(current.javaClass.name).append('\n')
            current.stackTrace.take(25).forEach { append("    at ").append(it).append('\n') }
            current = current.cause.takeIf { it !== current }
            depth++
        }
    }.trimEnd()
}
