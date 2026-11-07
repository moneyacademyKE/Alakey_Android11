package com.example.alakey.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface FactDao {
    // L11: same-ms re-assert of the same (entityId, attribute, tx) previously
    // hit INSERT IGNORE and was silently dropped; REPLACE makes it
    // last-write-wins, which is what an assert means.
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(fact: FactEntity)

    @Query("SELECT * FROM facts ORDER BY tx DESC")
    fun getAllFactsFlow(): kotlinx.coroutines.flow.Flow<List<FactEntity>>

    @Query("SELECT * FROM facts WHERE entityId = :entityId")
    suspend fun getFactsUsingEntity(entityId: String): List<FactEntity>

    
    @Query("SELECT * FROM facts")
    suspend fun getAllFacts(): List<FactEntity>

    /**
     * M52: unsubscribe must delete the episodes' facts. Episode ids are a pure
     * function of feed data, so surviving facts RESURRECT on re-subscription —
     * the fresh rows hydrate the old queue/progress/download state back in.
     */
    @Query("DELETE FROM facts WHERE entityId IN (:ids)")
    suspend fun deleteByEntityIds(ids: List<String>)

    /**
     * Retention sweep: drop every superseded fact, keeping the latest tx per
     * (entityId, attribute) — exactly the rows getLatestFacts/hydrate consume.
     * Lossless for values: same-ms re-asserts collide on the composite PK and
     * are resolved by REPLACE (last-write-wins, see insert); differing tx
     * values are distinct rows resolved here by max(tx).
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

    /**
     * M72: tree-wide latest-per-(entityId, attribute) — getLatestFacts without
     * the entityId WHERE. Every write previously re-read and re-emitted the
     * FULL lifetime fact history to three separate collectors (library, queue,
     * inbox) that each re-hydrate every entity; these emit exactly the rows
     * hydrate consumes. [InformationModel.hydrate] resolves latest-per-attribute
     * itself, so one row per key is semantically identical to full history.
     */
    @Query("""
        SELECT f1.* FROM facts f1
        INNER JOIN (
            SELECT entityId, attribute, MAX(tx) as maxTx
            FROM facts
            GROUP BY entityId, attribute
        ) f2 ON f1.entityId = f2.entityId AND f1.attribute = f2.attribute AND f1.tx = f2.maxTx
    """)
    fun getLatestFactsFlow(): kotlinx.coroutines.flow.Flow<List<FactEntity>>

    /** M72's suspend twin: batched hydration reads, not lifetime history. */
    @Query("""
        SELECT f1.* FROM facts f1
        INNER JOIN (
            SELECT entityId, attribute, MAX(tx) as maxTx
            FROM facts
            GROUP BY entityId, attribute
        ) f2 ON f1.entityId = f2.entityId AND f1.attribute = f2.attribute AND f1.tx = f2.maxTx
    """)
    suspend fun getLatestFactsAll(): List<FactEntity>
}
