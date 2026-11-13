package io.github.yedidyatob.telegramnarrator

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import io.github.yedidyatob.telegramnarrator.core.diagnostics.AutoLog
import io.github.yedidyatob.telegramnarrator.domain.repository.ChatRepository
import javax.inject.Inject

@HiltAndroidApp
class TelegramNarratorApp : Application() {
    // Created with the app, before anything starts TDLib's login (#131): the chat list sees every chat update.
    // Created lazily by Home instead, the chats TDLib sent before then were missing.
    @Inject lateinit var chatRepository: ChatRepository

    override fun onCreate() {
        // Before Hilt injection, so a crash while Android Auto starts the service is recorded too
        AutoLog.init(this)
        AutoLog.installCrashHandler()
        super.onCreate()
    }
}
