package io.github.yedidyatob.telegramnarrator.core.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.github.yedidyatob.telegramnarrator.BuildConfig
import io.github.yedidyatob.telegramnarrator.core.audio.TtsManager
import io.github.yedidyatob.telegramnarrator.data.connection.TdLibConnectionMonitor
import io.github.yedidyatob.telegramnarrator.data.demo.DemoAwareAuthRepository
import io.github.yedidyatob.telegramnarrator.data.demo.DemoAwareChatRepository
import io.github.yedidyatob.telegramnarrator.data.demo.DemoAwareConnectionMonitor
import io.github.yedidyatob.telegramnarrator.data.demo.DemoMode
import io.github.yedidyatob.telegramnarrator.data.demo.FakeChatRepository
import io.github.yedidyatob.telegramnarrator.data.repository.TdLibAuthRepository
import io.github.yedidyatob.telegramnarrator.data.repository.TdLibChatRepository
import io.github.yedidyatob.telegramnarrator.data.repository.TdLibUserCache
import io.github.yedidyatob.telegramnarrator.data.sponsored.TdLibSponsoredMessagesSource
import io.github.yedidyatob.telegramnarrator.data.tdlib.TdGateway
import io.github.yedidyatob.telegramnarrator.data.tdlib.TdLibClient
import io.github.yedidyatob.telegramnarrator.data.tdlib.TdLibDatabaseKeyStore
import io.github.yedidyatob.telegramnarrator.data.tts.TtsPreferences
import io.github.yedidyatob.telegramnarrator.domain.connection.ConnectionMonitor
import io.github.yedidyatob.telegramnarrator.domain.repository.AuthRepository
import io.github.yedidyatob.telegramnarrator.domain.repository.ChatRepository
import io.github.yedidyatob.telegramnarrator.domain.sponsored.SponsoredMessagesSource
import java.io.File
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    fun provideSpeechCacheWiper(
        @ApplicationContext context: Context,
        languages: io.github.yedidyatob.telegramnarrator.domain.language.ChatLanguageResolver,
    ): io.github.yedidyatob.telegramnarrator.data.speech.SpeechCacheWiper =
        io.github.yedidyatob.telegramnarrator.data.speech.SpeechCacheWiper {
            io.github.yedidyatob.telegramnarrator.data.speech.SpeechCaches
                .wipe(context.cacheDir)
            // Detected chat languages are per-account data too (#89)
            languages.resetAll()
        }

    @Provides
    @Singleton
    fun provideChatLanguageResolver(
        @ApplicationContext context: Context,
    ): io.github.yedidyatob.telegramnarrator.domain.language.ChatLanguageResolver =
        io.github.yedidyatob.telegramnarrator.domain.language.ChatLanguageResolver(
            store =
                io.github.yedidyatob.telegramnarrator.data.language
                    .EncryptedChatLanguageStore(context),
            identifier =
                io.github.yedidyatob.telegramnarrator.data.language
                    .MlKitLanguageIdentifier(),
            deviceLanguage = {
                java.util.Locale
                    .getDefault()
                    .language
            },
            log = {
                io.github.yedidyatob.telegramnarrator.core.diagnostics.AutoLog
                    .event(it)
            },
        )

    @Provides
    @Singleton
    fun provideFilesDir(
        @ApplicationContext context: Context,
    ): File = context.filesDir

    // The real TDLib client (@Inject constructor, singleton) behind the gateway interface (#13)
    @Provides
    @Singleton
    fun provideTdGateway(client: TdLibClient): TdGateway = client

    @Provides
    @Singleton
    fun provideUserCache(client: TdGateway): TdLibUserCache = TdLibUserCache(client)

    @Provides
    @Singleton
    fun provideAuthRepository(
        client: TdGateway,
        filesDir: File,
        databaseKeyStore: TdLibDatabaseKeyStore,
        demo: DemoMode,
        fakeChats: FakeChatRepository,
    ): AuthRepository = DemoAwareAuthRepository(TdLibAuthRepository(client, filesDir, databaseKeyStore), demo, fakeChats)

    // Play review demo login: sample channels instead of Telegram while active (no TDLib calls)
    @Provides
    @Singleton
    fun provideDemoMode(): DemoMode = DemoMode()

    @Provides
    @Singleton
    fun provideFakeChatRepository(): FakeChatRepository = FakeChatRepository()

    @Provides
    @Singleton
    fun provideChatRepository(
        client: TdGateway,
        userCache: TdLibUserCache,
        ttsPreferences: TtsPreferences,
        demo: DemoMode,
        fakeChats: FakeChatRepository,
    ): ChatRepository =
        DemoAwareChatRepository(
            TdLibChatRepository(client, userCache, { ttsPreferences.settings.value.markAsReadEnabled(BuildConfig.DEBUG) }),
            fakeChats,
            demo,
        )

    @Provides
    @Singleton
    fun provideConnectionMonitor(
        monitor: TdLibConnectionMonitor,
        demo: DemoMode,
    ): ConnectionMonitor = DemoAwareConnectionMonitor(monitor, demo)

    @Provides
    @Singleton
    fun provideSponsoredMessagesSource(
        source: TdLibSponsoredMessagesSource,
        demo: DemoMode,
    ): SponsoredMessagesSource =
        object : SponsoredMessagesSource by source {
            // No ads for the demo channels
            override suspend fun chatKind(chatId: Long) = if (demo.isActive) null else source.chatKind(chatId)
        }

    @Provides
    @Singleton
    fun provideTtsManager(
        @ApplicationContext context: Context,
        ttsPreferences: TtsPreferences,
    ): TtsManager = TtsManager(context, ttsPreferences)
}
