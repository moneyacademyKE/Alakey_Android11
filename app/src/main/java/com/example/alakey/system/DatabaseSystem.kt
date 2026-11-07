package com.example.alakey.system

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import androidx.room.Room
import com.example.alakey.data.AppDatabase
import com.example.alakey.data.MIGRATION_13_14
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * C1 decision: which pre-open path an existing database file takes. Pure so
 * the floor semantics are unit-testable — the v14 bump once made a v13
 * install wipe-worthy by accident (floor was "current version" instead of
 * "lowest migratable version").
 */
internal sealed interface LegacyDecision {
    /** No database file — nothing to do. */
    data object Absent : LegacyDecision
    /** Room handles it: real migration path exists, or version unreadable
     *  (never wipe on a read failure). */
    data object PassThrough : LegacyDecision
    /** Below the migration floor: no path — back up, then rebuild. */
    data class BackupAndRebuild(val fromVersion: Int) : LegacyDecision
}

internal fun legacyDecision(dbExists: Boolean, version: Int?, migrationFloor: Int): LegacyDecision = when {
    !dbExists -> LegacyDecision.Absent
    version == null -> LegacyDecision.PassThrough
    version < migrationFloor -> LegacyDecision.BackupAndRebuild(version)
    else -> LegacyDecision.PassThrough
}

@Singleton
class DatabaseSystem @Inject constructor(
    @ApplicationContext private val context: Context
) : Component {

    private var _db: AppDatabase? = null
    val db: AppDatabase get() = _db ?: throw IllegalStateException("DatabaseSystem not started")

    override fun start() {
        if (_db != null) return
        preserveLegacyDatabase()
        _db = Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            DB_NAME
        )
            // Real migrations from the floor up (C1 + M14); only a downgrade
            // may fall back destructively.
            .addMigrations(MIGRATION_13_14)
            .fallbackToDestructiveMigrationOnDowngrade()
            .build()
    }

    override fun stop() {
        _db?.close()
        _db = null
    }

    /**
     * C1 remediation: installs on a pre-v13 schema have no migration path
     * (schemas were never exported). Before Room opens, snapshot the old
     * database so a future migration can recover it, then build fresh —
     * no crash loop, no silent wipe. Backup failure is logged loudly but
     * still rebuilds: a bricked launch is worse than a logged loss.
     */
    private fun preserveLegacyDatabase() {
        val dbFile = context.getDatabasePath(DB_NAME)
        val legacyVersion = runCatching {
            SQLiteDatabase.openDatabase(dbFile.path, null, SQLiteDatabase.OPEN_READONLY).use { it.version }
        }.getOrNull()

        when (val decision = legacyDecision(dbFile.exists(), legacyVersion, MIGRATION_FLOOR)) {
            LegacyDecision.Absent, LegacyDecision.PassThrough -> return
            is LegacyDecision.BackupAndRebuild -> {
                val backup = File(dbFile.parentFile, "${DB_NAME}.pre-v${AppDatabase.SCHEMA_VERSION}.bak")
                runCatching { dbFile.copyTo(backup, overwrite = true) }
                    .onSuccess { Log.w(TAG, "Legacy DB v${decision.fromVersion} backed up to ${backup.name}; building fresh v${AppDatabase.SCHEMA_VERSION} database") }
                    .onFailure { Log.w(TAG, "Legacy DB backup failed; rebuilding without backup", it) }

                dbFile.delete()
                listOf("-wal", "-shm", "-journal").forEach { suffix ->
                    File(dbFile.parentFile, "$DB_NAME$suffix").delete()
                }
            }
        }
    }

    private companion object {
        const val DB_NAME = "alakey-db"
        const val TAG = "DatabaseSystem"
        /** Lowest schema with a real Migration; below it: backup + rebuild (C1). */
        const val MIGRATION_FLOOR = 13
    }
}
