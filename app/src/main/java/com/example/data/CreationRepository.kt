package com.example.data

import kotlinx.coroutines.flow.Flow

class CreationRepository(private val creationDao: CreationDao) {
    val allCreations: Flow<List<Creation>> = creationDao.getAllCreations()

    suspend fun insert(creation: Creation): Long {
        return creationDao.insertCreation(creation)
    }

    suspend fun delete(creation: Creation) {
        creationDao.deleteCreation(creation)
    }

    suspend fun deleteById(id: Int) {
        creationDao.deleteById(id)
    }
}
