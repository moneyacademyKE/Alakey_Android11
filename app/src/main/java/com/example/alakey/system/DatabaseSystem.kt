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
        if (!dbFile.exists()) return

        val legacyVersion = runCatching {
            SQLiteDatabase.openDatabase(dbFile.path, null, SQLiteDatabase.OPEN_READONLY).use { it.version }
        }.getOrDefault(AppDatabase.SCHEMA_VERSION)

        // Wipe floor: v13 and up have real migrations (Migrations.kt) —
        // Room upgrades them in place. Only below the floor is there no
        // path, and that is what the backup-and-rebuild is for.
        if (legacyVersion >= MIGRATION_FLOOR) return

        val backup = File(dbFile.parentFile, "${DB_NAME}.pre-v${AppDatabase.SCHEMA_VERSION}.bak")
        runCatching { dbFile.copyTo(backup, overwrite = true) }
            .onSuccess { Log.w(TAG, "Legacy DB v$legacyVersion backed up to ${backup.name}; building fresh v${AppDatabase.SCHEMA_VERSION} database") }
            .onFailure { Log.w(TAG, "Legacy DB backup failed; rebuilding without backup", it) }

        dbFile.delete()
        listOf("-wal", "-shm", "-journal").forEach { suffix ->
            File(dbFile.parentFile, "$DB_NAME$suffix").delete()
        }
    }

    private companion object {
        const val DB_NAME = "alakey-db"
        const val TAG = "DatabaseSystem"
        /** Lowest schema with a real Migration; below it: backup + rebuild (C1). */
        const val MIGRATION_FLOOR = 13
    }
}
