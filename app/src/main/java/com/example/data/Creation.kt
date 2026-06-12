package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class CreationType {
    IMAGE, VIDEO, FACESWAP
}

@Entity(tableName = "creations")
data class Creation(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val prompt: String,
    val type: CreationType,
    val fileUri: String,
    val creationDate: Long = System.currentTimeMillis(),
    val configurationJson: String = ""
)
