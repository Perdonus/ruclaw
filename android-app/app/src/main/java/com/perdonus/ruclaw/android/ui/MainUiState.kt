package com.perdonus.ruclaw.android.ui

import com.perdonus.ruclaw.android.core.model.ChatMessage
import com.perdonus.ruclaw.android.core.model.ChatThreadSummary
import com.perdonus.ruclaw.android.core.model.ComposerState
import com.perdonus.ruclaw.android.core.model.ConnectionState
import com.perdonus.ruclaw.android.core.model.LauncherConfigDraft

data class MainUiState(
    val isLoaded: Boolean = false,
    val launcherConfig: LauncherConfigDraft = LauncherConfigDraft(),
    val connectionState: ConnectionState = ConnectionState(),
    val threads: List<ChatThreadSummary> = emptyList(),
    val activeSessionId: String? = null,
    val messages: List<ChatMessage> = emptyList(),
    val composer: ComposerState = ComposerState(),
    val isTyping: Boolean = false,
    val isRefreshing: Boolean = false,
    val isHistoryLoading: Boolean = false,
    val showSettings: Boolean = false,
    val bannerMessage: String? = null,
    val pendingExternalUrl: String? = null,
    val pendingSystemAction: PendingSystemAction? = null,
    val updateState: UpdateUiState = UpdateUiState(),
    val diagnostics: List<String> = emptyList(),
)

data class UpdateUiState(
    val currentVersionName: String = "",
    val latestVersionName: String = "",
    val releaseTag: String = "",
    val releaseUrl: String = "",
    val releaseNotes: String = "",
    val apkUrl: String = "",
    val apkSha256Url: String = "",
    val isChecking: Boolean = false,
    val isUpdateAvailable: Boolean = false,
    val canInstallPackages: Boolean = false,
    val downloadId: Long? = null,
    val downloadState: UpdateDownloadState = UpdateDownloadState.IDLE,
    val downloadedUri: String = "",
    val downloadProgressPercent: Int? = null,
    val lastCheckedAtEpochMillis: Long = 0L,
)

enum class UpdateDownloadState {
    IDLE,
    DOWNLOADING,
    READY_TO_INSTALL,
    FAILED,
}

data class PendingSystemAction(
    val type: PendingSystemActionType,
    val uri: String? = null,
)

enum class PendingSystemActionType {
    OPEN_UNKNOWN_SOURCES_SETTINGS,
    INSTALL_APK,
}

val MainUiState.activeThread: ChatThreadSummary?
    get() = threads.firstOrNull { it.sessionId == activeSessionId }

val MainUiState.hasConfiguredLauncher: Boolean
    get() = launcherConfig.url.trim().isNotBlank() && launcherConfig.token.trim().isNotBlank()
