package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface CreationDao {
    @Query("SELECT * FROM creations ORDER BY creationDate DESC")
    fun getAllCreations(): Flow<List<Creation>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCreation(creation: Creation): Long

    @Delete
    suspend fun deleteCreation(creation: Creation)

    @Query("DELETE FROM creations WHERE id = :id")
    suspend fun deleteById(id: Int)
}
