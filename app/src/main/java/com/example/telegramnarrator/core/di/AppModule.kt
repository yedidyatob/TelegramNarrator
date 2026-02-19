package com.example.telegramnarrator.core.di

import android.content.Context
import com.example.telegramnarrator.core.audio.TtsManager
import com.example.telegramnarrator.data.repository.TdLibAuthRepository
import com.example.telegramnarrator.data.repository.TdLibChatRepository
import com.example.telegramnarrator.data.tdlib.TdLibClient
import com.example.telegramnarrator.domain.repository.AuthRepository
import com.example.telegramnarrator.domain.repository.ChatRepository
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
    @Singleton
    fun provideFilesDir(@ApplicationContext context: Context): File {
        return context.filesDir
    }

    @Provides
    @Singleton
    fun provideTdLibClient(@ApplicationContext context: Context): TdLibClient {
        return TdLibClient(context)
    }

    @Provides
    @Singleton
    fun provideAuthRepository(
        client: TdLibClient,
        filesDir: File
    ): AuthRepository {
        return TdLibAuthRepository(client, filesDir)
    }

    @Provides
    @Singleton
    fun provideChatRepository(
        client: TdLibClient
    ): ChatRepository {
        return TdLibChatRepository(client)
    }

    @Provides
    @Singleton
    fun provideTtsManager(@ApplicationContext context: Context): TtsManager {
        return TtsManager(context)
    }
}
