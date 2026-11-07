package com.example.alakey.data

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "facts",
    primaryKeys = ["entityId", "attribute", "tx"],
    indices = [
        Index(value = ["entityId"]),
        // L72: the attribute-only index is gone — its sole consumer
        // (FactDao.getFactsUsingAttribute) was deleted in round 3; every
        // surviving query filters on entityId or groups on the PK's
        // leading columns. Dropped in MIGRATION_14_15.
        Index(value = ["tx"])
    ]
)
data class FactEntity(
    val entityId: String,
    val attribute: String,
    val value: String,
    val tx: Long = System.currentTimeMillis()
)
