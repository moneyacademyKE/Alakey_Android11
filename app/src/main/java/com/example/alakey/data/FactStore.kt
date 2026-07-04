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

    suspend fun hydrateAll(items: List<PodcastEntity>): List<PodcastEntity> {
        return items.map { hydrate(it) }
    }

    suspend fun getFacts(entityId: String): List<FactEntity> = factDao.getFactsUsingEntity(entityId)
    suspend fun getAllFacts(): List<FactEntity> = factDao.getAllFacts()

    suspend fun getAttribute(entityId: String, attribute: String): String? {
        return factDao.getFactsUsingEntity(entityId)
            .filter { it.attribute == attribute }
            .maxByOrNull { it.tx }
            ?.value
    }
}
