package io.github.yedidyatob.telegramnarrator.domain.about

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppVersionInfoTest {
    @Test
    fun `CI builds show run number and short sha`() {
        val info = AppVersionInfo("1.0.0-debug+42.abc1234", 10000, "debug", "42", "abc1234")
        assertEquals("1.0.0-debug+42.abc1234 (10000)", info.version)
        assertEquals("CI run 42 · abc1234", info.ciBuild)
    }

    @Test
    fun `local builds show no CI line`() {
        assertNull(AppVersionInfo("1.2.3", 10203, "release", "", "").ciBuild)
        assertEquals("abc1234", AppVersionInfo("1.2.3", 10203, "release", "", "abc1234").ciBuild)
    }
}
