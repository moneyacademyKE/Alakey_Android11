package com.example.alakey.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface FactDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(fact: FactEntity)

    @Query("SELECT * FROM facts ORDER BY tx DESC")
    fun getAllFactsFlow(): kotlinx.coroutines.flow.Flow<List<FactEntity>>

    @Query("SELECT * FROM facts WHERE entityId = :entityId")
    suspend fun getFactsUsingEntity(entityId: String): List<FactEntity>

    @Query("SELECT * FROM facts WHERE attribute = :attribute")
    suspend fun getFactsUsingAttribute(attribute: String): List<FactEntity>
    
    @Query("SELECT * FROM facts")
    suspend fun getAllFacts(): List<FactEntity>

    /**
     * Retention sweep: drop every superseded fact, keeping the latest tx per
     * (entityId, attribute) — exactly the rows getLatestFacts/hydrate consume.
     * Same-ms siblings are impossible (composite PK), so this is lossless.
     */
    @Query("""
        DELETE FROM facts WHERE tx < (
            SELECT MAX(f2.tx) FROM facts f2
            WHERE f2.entityId = facts.entityId AND f2.attribute = facts.attribute
        )
    """)
    suspend fun compact()

    @Query("""
        SELECT f1.* FROM facts f1
        INNER JOIN (
            SELECT entityId, attribute, MAX(tx) as maxTx
            FROM facts
            WHERE entityId = :entityId
            GROUP BY entityId, attribute
        ) f2 ON f1.entityId = f2.entityId AND f1.attribute = f2.attribute AND f1.tx = f2.maxTx
    """)
    suspend fun getLatestFacts(entityId: String): List<FactEntity>
}
