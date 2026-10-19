package io.github.yedidyatob.telegramnarrator

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import io.github.yedidyatob.telegramnarrator.core.diagnostics.AutoLog

@HiltAndroidApp
class TelegramNarratorApp : Application() {
    override fun onCreate() {
        // Before Hilt injection, so a crash while Android Auto starts the service is recorded too
        AutoLog.init(this)
        AutoLog.installCrashHandler()
        super.onCreate()
    }
}
