package com.example.alakey.data

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * L72: the declared migration chain must actually reach the @Database
 * version. A version bump that forgets its Migration object would
 * otherwise fall to the C1 wipe path (legacyDecision backs up and
 * rebuilds — data survives, but progress is lost) and nothing would
 * fail at build time.
 */
class SchemaVersionTest {

    @Test
    fun `schema version is pinned`() = assertEquals(15, RoomSchema.VERSION)

    @Test
    fun `database annotation and RoomSchema agree`() =
        assertEquals(RoomSchema.VERSION, AppDatabase.SCHEMA_VERSION)

    @Test
    fun `migration chain is gapless from the C1 floor to the head`() {
        val chain = listOf(MIGRATION_13_14, MIGRATION_14_15)
        assertEquals(13, chain.first().startVersion)
        assertEquals(RoomSchema.VERSION, chain.last().endVersion)
        chain.zipWithNext().forEach { (left, right) ->
            assertEquals(
                "gap between v${left.endVersion} and v${right.startVersion}",
                left.endVersion,
                right.startVersion,
            )
        }
    }
}
