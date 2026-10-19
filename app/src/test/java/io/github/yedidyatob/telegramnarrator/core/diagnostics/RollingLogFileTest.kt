package io.github.yedidyatob.telegramnarrator.core.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class RollingLogFileTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test
    fun `appends lines and creates the directory`() {
        val log = RollingLogFile(tmp.root.resolve("diagnostics/tn-auto.log"))
        log.append("one")
        log.append("two\n")
        assertEquals("one\ntwo\n", log.file.readText())
    }

    @Test
    fun `stays under the cap, keeps the newest lines whole`() {
        val log = RollingLogFile(tmp.root.resolve("x.log"), maxBytes = 1_000)
        repeat(500) { log.append("line $it") }
        val text = log.file.readText()
        assertTrue(log.file.length() <= 1_000)
        assertTrue(text.endsWith("line 499\n"))
        assertTrue(text.lines().first().startsWith("line "))
        assertFalse(text.contains("line 0\n"))
    }

    @Test
    fun `default cap is about 200 KB`() {
        assertEquals(200 * 1024, RollingLogFile.MAX_BYTES)
    }

    @Test
    fun `stack traces carry class names and frames but never the exception message`() {
        val e = IllegalStateException("chat Secret Family +972501234567", RuntimeException("Hello from Dana"))
        val trace = AutoLog.stackTrace(e)
        assertTrue(trace.startsWith("java.lang.IllegalStateException"))
        assertTrue(trace.contains("Caused by: java.lang.RuntimeException"))
        assertTrue(trace.contains("    at "))
        assertFalse(trace.contains("Secret"))
        assertFalse(trace.contains("972"))
        assertFalse(trace.contains("Dana"))
    }
}
