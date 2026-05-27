package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "try_on_sessions")
data class TryOnSession(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val personType: String, // "GALLERY" or "PRESET_MALE" or "PRESET_FEMALE"
    val personImageUri: String?, // Local file path or content URI
    val garmentUrl: String,
    val garmentImageUrl: String?, // Scraped image URL or uploaded photo URI
    val garmentTitle: String,
    val price: String = "",
    val scale: Float = 1.0f,
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
    val rotation: Float = 0f,
    val alpha: Float = 0.85f,
    val aiResponseJson: String? = null, // JSON containing the Gemini styling analysis
    val timestamp: Long = System.currentTimeMillis()
)
