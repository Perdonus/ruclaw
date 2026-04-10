package com.perdonus.ruclaw.android.core.model

import kotlinx.serialization.Serializable

@Serializable
data class PersistedAppState(
    val launcherUrl: String = "",
    val launcherToken: String = "",
    val launcherMode: LauncherMode = LauncherMode.REMOTE,
    val localRuntime: PersistedLocalRuntimeState = PersistedLocalRuntimeState(),
    val activeSessionId: String? = null,
    val threads: List<ChatThreadSummary> = emptyList(),
    val cachedSessions: List<CachedSession> = emptyList(),
    val updateState: PersistedUpdateState = PersistedUpdateState(),
)

@Serializable
enum class LauncherMode {
    REMOTE,
    LOCAL,
}

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

@Serializable
data class PersistedLocalRuntimeState(
    val isInstalled: Boolean = false,
    val runtimeVersion: String = "",
    val runtimeRoot: String = "",
    val launcherUrl: String = "",
    val launcherToken: String = "",
    val ggufPath: String = "",
    val keepAliveEnabled: Boolean = true,
)

data class ComposerState(
    val text: String = "",
    val isSending: Boolean = false,
    val attachments: List<ComposerAttachment> = emptyList(),
)

data class ComposerAttachment(
    val url: String,
    val filename: String = "",
    val mimeType: String = "",
)

data class LauncherModelItem(
    val index: Int,
    val modelName: String,
    val status: String,
    val available: Boolean,
    val isDefault: Boolean,
    val isVirtual: Boolean,
    val authMethod: String = "",
)

data class LauncherSkillItem(
    val name: String,
    val description: String,
    val source: String,
    val originKind: String,
    val registryName: String = "",
    val registryUrl: String = "",
    val installedVersion: String = "",
)

data class LauncherSkillSearchItem(
    val slug: String,
    val displayName: String,
    val summary: String,
    val version: String,
    val registryName: String,
    val url: String = "",
    val installed: Boolean = false,
    val installedName: String = "",
)

data class LauncherToolItem(
    val name: String,
    val description: String,
    val category: String,
    val status: String,
    val reasonCode: String = "",
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
