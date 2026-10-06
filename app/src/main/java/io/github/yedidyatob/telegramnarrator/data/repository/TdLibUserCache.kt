package io.github.yedidyatob.telegramnarrator.data.repository

import android.util.Log
import io.github.yedidyatob.telegramnarrator.data.tdlib.TdLibClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.drinkless.tdlib.TdApi
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TdLibUserCache @Inject constructor(
    private val client: TdLibClient
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val users = MutableStateFlow<Map<Long, TdApi.User>>(emptyMap())

    init {
        scope.launch {
            client.updates.collect { update ->
                if (update is TdApi.UpdateUser) {
                    Log.d("UserCache", "Updating user: ${update.user.id}")
                    users.update { it + (update.user.id to update.user) }
                }
            }
        }
    }

    suspend fun getUser(userId: Long): TdApi.User? {
        users.value[userId]?.let { return it }
        
        return try {
            Log.d("UserCache", "Fetching user from TDLib: $userId")
            val user = client.send(TdApi.GetUser(userId))
            users.update { it + (userId to user) }
            user
        } catch (e: Exception) {
            Log.e("UserCache", "Failed to fetch user $userId", e)
            null
        }
    }

    suspend fun getUserName(userId: Long): String {
        if (userId == 0L) return "System"
        val user = getUser(userId) ?: return "Unknown User"
        val firstName = user.firstName ?: ""
        val lastName = user.lastName ?: ""
        return if (lastName.isBlank()) {
            firstName.ifBlank { "User $userId" }
        } else {
            "$firstName $lastName".trim()
        }
    }
}
