package com.perdonus.ruclaw.android.data.local

import android.content.Context
import com.perdonus.ruclaw.android.core.model.CachedSession
import com.perdonus.ruclaw.android.core.model.ChatThreadSummary
import com.perdonus.ruclaw.android.core.model.PersistedAppState
import com.perdonus.ruclaw.android.core.model.PersistedUpdateState
import com.perdonus.ruclaw.android.core.util.JsonFileStore
import kotlinx.coroutines.flow.StateFlow

class LocalStateRepository(context: Context) {
    private val store = JsonFileStore(
        context = context,
        fileName = "ruclaw-android-state.json",
        serializer = PersistedAppState.serializer(),
        defaultValue = PersistedAppState(),
    )

    fun state(): StateFlow<PersistedAppState> = store.state()

    fun snapshot(): PersistedAppState = store.snapshot()

    suspend fun load(): PersistedAppState = store.load()

    suspend fun saveLauncherConfig(url: String, token: String): PersistedAppState {
        val normalizedUrl = url.trim().trimEnd('/')
        return store.update { current ->
            current.copy(
                launcherUrl = normalizedUrl,
                launcherToken = token.trim(),
            )
        }
    }

    suspend fun replaceThreads(threads: List<ChatThreadSummary>): PersistedAppState {
        return store.update { current ->
            val remoteIds = threads.map { it.sessionId }.toSet()
            val localOnly = current.threads.filter { it.isLocalOnly && it.sessionId !in remoteIds }
            current.copy(
                threads = sortThreads(threads + localOnly),
            )
        }
    }

    suspend fun upsertThread(thread: ChatThreadSummary): PersistedAppState {
        return store.update { current ->
            current.copy(
                threads = upsertThreadList(current.threads, thread),
            )
        }
    }

    suspend fun selectSession(sessionId: String?): PersistedAppState {
        return store.update { it.copy(activeSessionId = sessionId) }
    }

    suspend fun cacheSession(session: CachedSession): PersistedAppState {
        val summary = ChatThreadSummary(
            sessionId = session.sessionId,
            title = session.title,
            preview = session.preview,
            updatedAtEpochMillis = session.updatedAtEpochMillis,
            messageCount = session.messages.size,
            isLocalOnly = false,
        )
        return store.update { current ->
            current.copy(
                threads = upsertThreadList(current.threads, summary),
                cachedSessions = upsertSessionList(current.cachedSessions, session),
            )
        }
    }

    suspend fun updateUpdateState(transform: (PersistedUpdateState) -> PersistedUpdateState): PersistedAppState {
        return store.update { current ->
            current.copy(updateState = transform(current.updateState))
        }
    }

    private fun upsertThreadList(
        current: List<ChatThreadSummary>,
        thread: ChatThreadSummary,
    ): List<ChatThreadSummary> {
        return sortThreads(current.filterNot { it.sessionId == thread.sessionId } + thread)
    }

    private fun upsertSessionList(
        current: List<CachedSession>,
        session: CachedSession,
    ): List<CachedSession> {
        return (current.filterNot { it.sessionId == session.sessionId } + session)
            .sortedByDescending { it.updatedAtEpochMillis }
    }

    private fun sortThreads(threads: List<ChatThreadSummary>): List<ChatThreadSummary> {
        return threads.sortedByDescending { it.updatedAtEpochMillis }
    }
}
