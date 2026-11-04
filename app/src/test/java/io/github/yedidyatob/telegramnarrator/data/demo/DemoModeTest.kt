package io.github.yedidyatob.telegramnarrator.data.demo

import io.github.yedidyatob.telegramnarrator.data.tdlib.TdLibException
import io.github.yedidyatob.telegramnarrator.domain.auth.PhoneNumberNormalizer
import io.github.yedidyatob.telegramnarrator.domain.model.AuthState
import io.github.yedidyatob.telegramnarrator.domain.repository.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class DemoModeTest {
    private class RecordingAuth : AuthRepository {
        val calls = mutableListOf<String>()
        override val authState = MutableStateFlow<AuthState>(AuthState.WaitPhoneNumber)

        override suspend fun retryInitialization() {
            calls += "retry"
        }

        override suspend fun setPhoneNumber(phoneNumber: String) {
            calls += "phone"
        }

        override suspend fun checkAuthenticationCode(code: String) {
            calls += "code"
        }

        override suspend fun checkAuthenticationPassword(password: String) {
            calls += "password"
        }

        override suspend fun logOut() {
            calls += "logout"
        }
    }

    private val real = RecordingAuth()
    private val demo = DemoMode()
    private var deviceLanguage = "iw"
    private val fake = FakeChatRepository { deviceLanguage }
    private val auth = DemoAwareAuthRepository(real, demo, fake)

    @Test
    fun `detects the demo number in any spacing`() {
        assertTrue(DemoLogin.isDemoPhone("+999 000 0000"))
        assertTrue(DemoLogin.isDemoPhone("+9990000000"))
        assertFalse(DemoLogin.isDemoPhone("9990000000"))
        assertFalse(DemoLogin.isDemoPhone("+972521234567"))
        assertTrue(DemoLogin.isDemoCode(" 12345 "))
        assertFalse(DemoLogin.isDemoCode("12346"))
    }

    @Test
    fun `phone field accepts the demo number`() {
        assertEquals(
            PhoneNumberNormalizer.Result.Valid(DemoLogin.PHONE, null),
            PhoneNumberNormalizer().normalize("+999 000 0000", "IL"),
        )
    }

    @Test
    fun `demo login and logout never reach Telegram`() =
        runTest {
            auth.setPhoneNumber("+9990000000")
            assertEquals(AuthState.WaitCode, auth.authState.first())
            try {
                auth.checkAuthenticationCode("11111")
                fail()
            } catch (e: TdLibException) {
                assertEquals("PHONE_CODE_INVALID", e.tdMessage)
            }
            auth.checkAuthenticationCode("12345")
            assertTrue(demo.isActive)
            assertEquals(AuthState.Authenticated, auth.authState.first())
            auth.logOut()
            assertFalse(demo.isActive)
            assertEquals(AuthState.WaitPhoneNumber, auth.authState.first())
            assertEquals(emptyList<String>(), real.calls)
        }

    @Test
    fun `other numbers go to Telegram`() =
        runTest {
            auth.setPhoneNumber("+972521234567")
            auth.checkAuthenticationCode("12345")
            auth.logOut()
            assertFalse(demo.isActive)
            assertEquals(listOf("phone", "code", "logout"), real.calls)
        }

    @Test
    fun `fake repo lists channels and marks read locally`() =
        runTest {
            val chats = fake.getUnreadChats().first()
            assertEquals(DemoData.channels.size, chats.size)
            assertTrue(chats.any { chat -> chat.title.any { it in '\u05D0'..'\u05EA' } })
            val first = chats.first()
            val messages = fake.getChatMessages(first.id)
            assertEquals(first.unreadCount, messages.size)
            fake.markChatAsRead(first.id, listOf(messages.first().id))
            assertEquals(first.unreadCount - 1, fake.getChat(first.id)?.unreadCount)
            fake.markChatAsRead(first.id, messages.map { it.id })
            assertEquals(DemoData.channels.size - 1, fake.getUnreadChats().first().size)
            fake.reset()
            assertEquals(DemoData.channels.size, fake.getUnreadChats().first().size)
        }

    @Test
    fun `non-Hebrew devices only get the English demo channels`() =
        runTest {
            deviceLanguage = "en"
            fake.reset()
            val titles = fake.getUnreadChats().first().map { it.title }
            assertTrue(titles.isNotEmpty())
            assertTrue(titles.none { title -> title.any { it in '\u05D0'..'\u05EA' } })
        }
}
