package com.perdonus.ruclaw.android.core.model

import kotlinx.serialization.Serializable

@Serializable
data class PersistedAppState(
    val launcherUrl: String = "",
    val launcherToken: String = "",
    val activeSessionId: String? = null,
    val threads: List<ChatThreadSummary> = emptyList(),
    val cachedSessions: List<CachedSession> = emptyList(),
    val updateState: PersistedUpdateState = PersistedUpdateState(),
)

@Serializable
data class ChatThreadSummary(
    val sessionId: String,
    val title: String,
    val preview: String,
    val updatedAtEpochMillis: Long,
    val messageCount: Int = 0,
    val isLocalOnly: Boolean = false,
)

@Serializable
data class CachedSession(
    val sessionId: String,
    val title: String,
    val preview: String,
    val updatedAtEpochMillis: Long,
    val messages: List<ChatMessage> = emptyList(),
)

@Serializable
data class ChatMessage(
    val id: String,
    val role: ChatRole,
    val text: String,
    val attachments: List<String> = emptyList(),
    val status: MessageStatus = MessageStatus.COMPLETE,
    val createdAtEpochMillis: Long = 0L,
)

@Serializable
enum class ChatRole {
    USER,
    ASSISTANT,
    SYSTEM,
}

@Serializable
enum class MessageStatus {
    SENDING,
    STREAMING,
    COMPLETE,
    ERROR,
}

data class LauncherConfigDraft(
    val url: String = "",
    val token: String = "",
)

data class ComposerState(
    val text: String = "",
    val isSending: Boolean = false,
)

data class ConnectionState(
    val status: ConnectionStatus = ConnectionStatus.DISCONNECTED,
    val message: String? = null,
)

enum class ConnectionStatus {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    RECONNECTING,
    FAILED_AUTH,
    FAILED_NETWORK,
}

@Serializable
data class PersistedUpdateState(
    val latestVersionName: String = "",
    val releaseTag: String = "",
    val releaseUrl: String = "",
    val releaseNotes: String = "",
    val apkUrl: String = "",
    val apkSha256Url: String = "",
    val publishedAtEpochMillis: Long = 0L,
    val lastCheckedAtEpochMillis: Long = 0L,
    val downloadId: Long? = null,
    val downloadedUri: String = "",
    val downloadState: PersistedDownloadState = PersistedDownloadState.IDLE,
)

@Serializable
enum class PersistedDownloadState {
    IDLE,
    DOWNLOADING,
    READY_TO_INSTALL,
    FAILED,
}
