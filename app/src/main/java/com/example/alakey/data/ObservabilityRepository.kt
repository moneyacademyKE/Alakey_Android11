package com.example.alakey.data

import com.example.alakey.system.DatabaseSystem
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Singleton
class ObservabilityRepository @Inject constructor(private val dbSystem: DatabaseSystem) {
    private val eventLogDao get() = dbSystem.db.eventLogDao()

    suspend fun getRecentLogs() = eventLogDao.getRecentEvents()
    suspend fun getLogsByType(type: String) = eventLogDao.getEventsByType(type)
    suspend fun getFailedLogs() = eventLogDao.getFailedEvents()
    suspend fun grepLogs(q: String) = eventLogDao.grepLogs(q)

    suspend fun rawQuery(sql: String): List<Map<String, Any?>> = withContext(Dispatchers.IO) {
        val cursor = dbSystem.db.query(sql, null)
        val results = mutableListOf<Map<String, Any?>>()
        cursor.use { c ->
            val columnNames = c.columnNames
            while (c.moveToNext()) {
                results.add(columnNames.mapIndexed { index, name -> name to c.valueAt(index) }.toMap())
            }
        }
        results
    }

    private fun android.database.Cursor.valueAt(index: Int): Any? {
        return when (getType(index)) {
            android.database.Cursor.FIELD_TYPE_NULL -> null
            android.database.Cursor.FIELD_TYPE_INTEGER -> getLong(index)
            android.database.Cursor.FIELD_TYPE_FLOAT -> getDouble(index)
            android.database.Cursor.FIELD_TYPE_STRING -> getString(index)
            android.database.Cursor.FIELD_TYPE_BLOB -> getBlob(index)
            else -> getString(index)
        }
    }
}
