package com.workpointstracker.data.repository

import com.workpointstracker.data.local.database.SessionDao
import com.workpointstracker.data.model.Session
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

class SessionRepository(private val sessionDao: SessionDao) {

    fun getRecentSessions(): Flow<List<Session>> = sessionDao.getRecentSessions()

    fun getAllCompletedSessions(): Flow<List<Session>> = sessionDao.getAllCompletedSessions()

    fun getTotalPoints(): Flow<Double?> = sessionDao.getTotalPoints()

    suspend fun insertSession(session: Session): Long = sessionDao.insert(session)

    suspend fun updateSession(session: Session) = sessionDao.update(session)

    suspend fun getSessionById(id: Long): Session? = sessionDao.getSessionById(id)

    suspend fun getCompletedSessionsCountForDate(date: LocalDate): Int {
        return sessionDao.getCompletedSessionsCountForDate(date.toString())
    }

    suspend fun getSessionsInDateRange(startDate: LocalDate, endDate: LocalDate): List<Session> {
        return sessionDao.getSessionsInDateRange(startDate.toString(), endDate.toString())
    }

    suspend fun getTotalMinutesForDateAndType(date: LocalDate, sessionType: String): Long {
        return sessionDao.getTotalMinutesForDateAndType(date.toString(), sessionType) ?: 0L
    }
}
