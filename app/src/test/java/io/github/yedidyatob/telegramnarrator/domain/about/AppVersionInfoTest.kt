package io.github.yedidyatob.telegramnarrator.domain.about

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppVersionInfoTest {
    @Test
    fun `CI builds show run number and short sha`() {
        val info = AppVersionInfo("1.0.0-debug+42.abc1234", 10000, "debug", "42", "abc1234")
        assertEquals("1.0.0-debug+42.abc1234 (10000)", info.version)
        assertEquals("CI run 42 · abc1234", info.buildLine)
    }

    @Test
    fun `local builds show the git sha`() {
        assertEquals("Local build · abc1234", AppVersionInfo("1.2.3", 10203, "release", "", "abc1234").buildLine)
        assertEquals("Local build · abc1234-dirty", AppVersionInfo("1.0.0-debug+local.abc1234-dirty", 10000, "debug", "", "abc1234-dirty").buildLine)
    }

    @Test
    fun `builds without git show no build line`() {
        assertNull(AppVersionInfo("1.2.3", 10203, "release", "", "").buildLine)
    }
}
