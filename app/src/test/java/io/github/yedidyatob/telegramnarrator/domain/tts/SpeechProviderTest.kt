package io.github.yedidyatob.telegramnarrator.domain.tts

import org.junit.Test
import org.junit.Assert.*

/**
 * Unit tests for [SpeechProvider] enum: provider resolution, migration logic,
 * and fallback behavior.
 */
class SpeechProviderTest {

    // ===== Basic Provider Tests =====

    @Test
    fun enum_hasThreeProviders() {
        val providers = SpeechProvider.values()
        assertEquals(3, providers.size)
        assertNotNull(providers.find { it == SpeechProvider.SYSTEM })
        assertNotNull(providers.find { it == SpeechProvider.GEMINI })
        assertNotNull(providers.find { it == SpeechProvider.EDGE })
    }

    @Test
    fun enum_idsAreUnique() {
        val ids = SpeechProvider.values().map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun system_id_is_system() {
        assertEquals("system", SpeechProvider.SYSTEM.id)
    }

    @Test
    fun gemini_id_is_gemini() {
        assertEquals("gemini", SpeechProvider.GEMINI.id)
    }

    @Test
    fun edge_id_is_edge() {
        assertEquals("edge", SpeechProvider.EDGE.id)
    }

    // ===== fromId Tests =====

    @Test
    fun fromId_resolvesValidIds() {
        assertEquals(SpeechProvider.SYSTEM, SpeechProvider.fromId("system"))
        assertEquals(SpeechProvider.GEMINI, SpeechProvider.fromId("gemini"))
        assertEquals(SpeechProvider.EDGE, SpeechProvider.fromId("edge"))
    }

    @Test
    fun fromId_fallsBackToSystemOnUnknownId() {
        assertEquals(SpeechProvider.SYSTEM, SpeechProvider.fromId("unknown"))
        assertEquals(SpeechProvider.SYSTEM, SpeechProvider.fromId("openai"))  // old id should not match
        assertEquals(SpeechProvider.SYSTEM, SpeechProvider.fromId(""))
        assertEquals(SpeechProvider.SYSTEM, SpeechProvider.fromId(null))
    }

    // ===== Default Behavior Tests =====

    @Test
    fun default_isEdge() {
        assertEquals(SpeechProvider.EDGE, SpeechProvider.DEFAULT)
    }

    @Test
    fun upgradeDefault_isSystem() {
        assertEquals(SpeechProvider.SYSTEM, SpeechProvider.UPGRADE_DEFAULT)
    }

    @Test
    fun defaultFor_freshInstall_isEdge() {
        assertEquals(SpeechProvider.EDGE, SpeechProvider.defaultFor(freshInstall = true))
    }

    @Test
    fun defaultFor_upgrade_isSystem() {
        assertEquals(SpeechProvider.SYSTEM, SpeechProvider.defaultFor(freshInstall = false))
    }

    // ===== Migration Tests (fromStored) =====

    @Test
    fun fromStored_migratesStoredOpenAiToGemini() {
        // Old stored id "openai" → GEMINI
        assertEquals(SpeechProvider.GEMINI, SpeechProvider.fromStored(id = "openai", legacyOpenAiEnabled = false, freshInstall = false))
    }

    @Test
    fun fromStored_migratesLegacyOpenAiEnabledFlagToGemini() {
        // Old openai_enabled flag true → GEMINI
        assertEquals(SpeechProvider.GEMINI, SpeechProvider.fromStored(id = null, legacyOpenAiEnabled = true, freshInstall = false))
    }

    @Test
    fun fromStored_respectsExplicitGeminiId() {
        assertEquals(SpeechProvider.GEMINI, SpeechProvider.fromStored(id = "gemini", legacyOpenAiEnabled = false, freshInstall = false))
    }

    @Test
    fun fromStored_respectsExplicitEdgeId() {
        assertEquals(SpeechProvider.EDGE, SpeechProvider.fromStored(id = "edge", legacyOpenAiEnabled = false, freshInstall = false))
    }

    @Test
    fun fromStored_respectsExplicitSystemId() {
        assertEquals(SpeechProvider.SYSTEM, SpeechProvider.fromStored(id = "system", legacyOpenAiEnabled = false, freshInstall = false))
    }

    @Test
    fun fromStored_usesDefaultForFreshInstallWhenNoIdStored() {
        // Fresh install, no id, flag false → DEFAULT (EDGE)
        assertEquals(SpeechProvider.EDGE, SpeechProvider.fromStored(id = null, legacyOpenAiEnabled = false, freshInstall = true))
    }

    @Test
    fun fromStored_usesUpgradeDefaultWhenNoIdStored() {
        // Upgrade, no id, flag false → UPGRADE_DEFAULT (SYSTEM)
        assertEquals(SpeechProvider.SYSTEM, SpeechProvider.fromStored(id = null, legacyOpenAiEnabled = false, freshInstall = false))
    }

    @Test
    fun fromStored_legacyFlagTrumpsDefaultOnUpgrade() {
        // Upgrade with legacy openai_enabled=true → GEMINI (even though default would be SYSTEM)
        assertEquals(SpeechProvider.GEMINI, SpeechProvider.fromStored(id = null, legacyOpenAiEnabled = true, freshInstall = false))
    }

    @Test
    fun fromStored_storedIdTrumpsLegacyFlag() {
        // If both id and flag are set, id wins
        // Scenario: stored id="edge" but flag says openai_enabled=true
        assertEquals(SpeechProvider.EDGE, SpeechProvider.fromStored(id = "edge", legacyOpenAiEnabled = true, freshInstall = false))
    }

    @Test
    fun fromStored_storedOpenAiIdTrumpsDefaultEvenIfFlagFalse() {
        // Stored "openai" id → GEMINI (migration), even if flag is false
        assertEquals(SpeechProvider.GEMINI, SpeechProvider.fromStored(id = "openai", legacyOpenAiEnabled = false, freshInstall = false))
    }

    @Test
    fun fromStored_unknownIdFallsBackToUpgradeDefault() {
        // Unknown id, flag false, not fresh → SYSTEM
        assertEquals(SpeechProvider.SYSTEM, SpeechProvider.fromStored(id = "future_engine", legacyOpenAiEnabled = false, freshInstall = false))
    }

    @Test
    fun fromStored_unknownIdCanBeMigratedByFlag() {
        // Unknown id but flag true → GEMINI
        assertEquals(SpeechProvider.GEMINI, SpeechProvider.fromStored(id = "future_engine", legacyOpenAiEnabled = true, freshInstall = false))
    }

    // ===== Complex Migration Scenarios =====

    @Test
    fun migration_scenario_oldOpenAiUser() {
        // User had OpenAI stored in very old version: id="openai", flag=true
        assertEquals(SpeechProvider.GEMINI, SpeechProvider.fromStored(id = "openai", legacyOpenAiEnabled = true, freshInstall = false))
    }

    @Test
    fun migration_scenario_corruptPrefs() {
        // Prefs corrupted: id=null, flag=false, assume it's an upgrade
        assertEquals(SpeechProvider.SYSTEM, SpeechProvider.fromStored(id = null, legacyOpenAiEnabled = false, freshInstall = false))
    }

    @Test
    fun migration_scenario_freshInstallDefaultsToEdge() {
        // Fresh install: no stored choice, flag false
        assertEquals(SpeechProvider.EDGE, SpeechProvider.fromStored(id = null, legacyOpenAiEnabled = false, freshInstall = true))
    }

    @Test
    fun migration_scenario_userSelectedSystemExplicitly() {
        // User chose SYSTEM: stored id="system"
        assertEquals(SpeechProvider.SYSTEM, SpeechProvider.fromStored(id = "system", legacyOpenAiEnabled = false, freshInstall = false))
    }

    @Test
    fun migration_scenario_userSelectedGeminiExplicitly() {
        // User chose GEMINI after upgrade: stored id="gemini"
        assertEquals(SpeechProvider.GEMINI, SpeechProvider.fromStored(id = "gemini", legacyOpenAiEnabled = false, freshInstall = false))
    }
}
