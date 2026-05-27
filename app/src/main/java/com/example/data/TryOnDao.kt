package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface TryOnDao {
    @Query("SELECT * FROM try_on_sessions ORDER BY timestamp DESC")
    fun getAllSessions(): Flow<List<TryOnSession>>

    @Query("SELECT * FROM try_on_sessions WHERE id = :id LIMIT 1")
    suspend fun getSessionById(id: Int): TryOnSession?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: TryOnSession): Long

    @Update
    suspend fun updateSession(session: TryOnSession)

    @Delete
    suspend fun deleteSession(session: TryOnSession)

    @Query("DELETE FROM try_on_sessions WHERE id = :id")
    suspend fun deleteSessionById(id: Int)
}
