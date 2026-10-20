package io.github.yedidyatob.telegramnarrator.core.di

import android.content.Context
import io.github.yedidyatob.telegramnarrator.core.audio.TtsManager
import io.github.yedidyatob.telegramnarrator.data.connection.TdLibConnectionMonitor
import io.github.yedidyatob.telegramnarrator.domain.connection.ConnectionMonitor
import io.github.yedidyatob.telegramnarrator.data.repository.TdLibAuthRepository
import io.github.yedidyatob.telegramnarrator.data.repository.TdLibChatRepository
import io.github.yedidyatob.telegramnarrator.data.repository.TdLibUserCache
import io.github.yedidyatob.telegramnarrator.data.sponsored.TdLibSponsoredMessagesSource
import io.github.yedidyatob.telegramnarrator.data.tdlib.TdGateway
import io.github.yedidyatob.telegramnarrator.data.tdlib.TdLibClient
import io.github.yedidyatob.telegramnarrator.BuildConfig
import io.github.yedidyatob.telegramnarrator.data.tdlib.TdLibDatabaseKeyStore
import io.github.yedidyatob.telegramnarrator.data.tts.TtsPreferences
import io.github.yedidyatob.telegramnarrator.domain.repository.AuthRepository
import io.github.yedidyatob.telegramnarrator.domain.repository.ChatRepository
import io.github.yedidyatob.telegramnarrator.domain.sponsored.SponsoredMessagesSource
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    fun provideSpeechCacheWiper(@ApplicationContext context: Context): io.github.yedidyatob.telegramnarrator.data.speech.SpeechCacheWiper =
        io.github.yedidyatob.telegramnarrator.data.speech.SpeechCacheWiper {
            io.github.yedidyatob.telegramnarrator.data.speech.SpeechCaches.wipe(context.cacheDir)
        }

    @Provides
    @Singleton
    fun provideFilesDir(@ApplicationContext context: Context): File {
        return context.filesDir
    }

    @Provides
    @Singleton
    // The real TDLib client (@Inject constructor, singleton) behind the gateway interface (#13)
    fun provideTdGateway(client: TdLibClient): TdGateway = client

    @Provides
    @Singleton
    fun provideUserCache(client: TdGateway): TdLibUserCache {
        return TdLibUserCache(client)
    }

    @Provides
    @Singleton
    fun provideAuthRepository(
        client: TdGateway,
        filesDir: File,
        databaseKeyStore: TdLibDatabaseKeyStore
    ): AuthRepository {
        return TdLibAuthRepository(client, filesDir, databaseKeyStore)
    }

    @Provides
    @Singleton
    fun provideChatRepository(
        client: TdGateway,
        userCache: TdLibUserCache,
        ttsPreferences: TtsPreferences
    ): ChatRepository {
        return TdLibChatRepository(client, userCache, { ttsPreferences.settings.value.markAsReadEnabled(BuildConfig.DEBUG) })
    }

    @Provides
    @Singleton
    fun provideConnectionMonitor(monitor: TdLibConnectionMonitor): ConnectionMonitor = monitor

    @Provides
    @Singleton
    fun provideSponsoredMessagesSource(source: TdLibSponsoredMessagesSource): SponsoredMessagesSource = source

    @Provides
    @Singleton
    fun provideTtsManager(@ApplicationContext context: Context, ttsPreferences: TtsPreferences): TtsManager {
        return TtsManager(context, ttsPreferences)
    }
}
