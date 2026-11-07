package com.example.alakey.data

import com.example.alakey.domain.InformationModel
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FactStore @Inject constructor(private val dbSystem: com.example.alakey.system.DatabaseSystem) {
    private val factDao get() = dbSystem.db.factDao()

    val facts = factDao.getAllFactsFlow()

    suspend fun assert(entityId: String, attribute: String, value: String) {
        factDao.insert(FactEntity(entityId, attribute, value))
    }

    suspend fun hydrate(base: PodcastEntity): PodcastEntity {
        return InformationModel.hydrate(base, factDao.getLatestFacts(base.id))
    }

    /**
     * Batched hydration (M13): one query for all facts, grouped in memory.
     * [InformationModel.hydrate] resolves the latest fact per attribute
     * itself, so this is semantically identical to per-entity
     * [getLatestFacts] calls — minus the N+1 JOINs.
     */
    suspend fun hydrateAll(items: List<PodcastEntity>): List<PodcastEntity> {
        if (items.isEmpty()) return items
        val factsByEntity = factDao.getAllFacts().groupBy { it.entityId }
        return items.map { InformationModel.hydrate(it, factsByEntity[it.id].orEmpty()) }
    }

    suspend fun getFacts(entityId: String): List<FactEntity> = factDao.getFactsUsingEntity(entityId)
    suspend fun getAllFacts(): List<FactEntity> = factDao.getAllFacts()

    /** M52: facts die with their entity — see FactDao.deleteByEntityIds. */
    suspend fun deleteFacts(ids: List<String>) = factDao.deleteByEntityIds(ids)

    suspend fun getAttribute(entityId: String, attribute: String): String? {
        return factDao.getFactsUsingEntity(entityId)
            .filter { it.attribute == attribute }
            .maxByOrNull { it.tx }
            ?.value
    }

    /** Retention: collapse history to the latest fact per (entityId, attribute). */
    suspend fun compact() = factDao.compact()
}
