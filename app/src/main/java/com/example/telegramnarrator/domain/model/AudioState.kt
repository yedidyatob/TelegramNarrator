package com.example.telegramnarrator.domain.model

sealed class AudioState {
    object Idle : AudioState()
    data class Playing(val currentItemDescription: String) : AudioState()
    object Paused : AudioState()
    object Buffering : AudioState()
}
