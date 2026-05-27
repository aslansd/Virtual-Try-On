package com.example.data

import kotlinx.coroutines.flow.Flow

class TryOnRepository(private val tryOnDao: TryOnDao) {
    val allSessions: Flow<List<TryOnSession>> = tryOnDao.getAllSessions()

    suspend fun getSessionById(id: Int): TryOnSession? {
        return tryOnDao.getSessionById(id)
    }

    suspend fun insertSession(session: TryOnSession): Long {
        return tryOnDao.insertSession(session)
    }

    suspend fun updateSession(session: TryOnSession) {
        tryOnDao.updateSession(session)
    }

    suspend fun deleteSession(session: TryOnSession) {
        tryOnDao.deleteSession(session)
    }

    suspend fun deleteSessionById(id: Int) {
        tryOnDao.deleteSessionById(id)
    }
}
