package com.example.alakey.system

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * C1 floor semantics, pinned. The v14 bump briefly made "wipe-worthy" mean
 * "below CURRENT version" instead of "below the LOWEST MIGRATABLE version" —
 * a v13 install would have been backed up + wiped even though v13→v14 has a
 * real data migration. These pins keep the floor honest.
 */
class LegacyDecisionTest {

    private val floor = 13 // MIGRATION_FLOOR: lowest version with a real Migration

    @Test
    fun `no database file - absent, nothing to do`() {
        assertEquals(LegacyDecision.Absent, legacyDecision(dbExists = false, version = null, migrationFloor = floor))
    }

    @Test
    fun `unreadable version - pass through, never wipe on a read failure`() {
        assertEquals(LegacyDecision.PassThrough, legacyDecision(dbExists = true, version = null, migrationFloor = floor))
    }

    @Test
    fun `below the floor - backup and rebuild, version reported`() {
        val decision = legacyDecision(dbExists = true, version = 12, migrationFloor = floor)
        assertTrue(decision is LegacyDecision.BackupAndRebuild)
        assertEquals(12, (decision as LegacyDecision.BackupAndRebuild).fromVersion)
    }

    @Test
    fun `at the floor - migrate in place, not wiped`() {
        assertEquals(LegacyDecision.PassThrough, legacyDecision(dbExists = true, version = 13, migrationFloor = floor))
    }

    @Test
    fun `above the floor toward current - migrate in place`() {
        assertEquals(LegacyDecision.PassThrough, legacyDecision(dbExists = true, version = 14, migrationFloor = floor))
    }
}
