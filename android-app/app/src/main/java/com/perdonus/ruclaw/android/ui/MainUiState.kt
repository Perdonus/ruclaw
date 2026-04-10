package com.perdonus.ruclaw.android.ui

import com.perdonus.ruclaw.android.core.model.ChatMessage
import com.perdonus.ruclaw.android.core.model.ChatThreadSummary
import com.perdonus.ruclaw.android.core.model.ComposerState
import com.perdonus.ruclaw.android.core.model.ConnectionState
import com.perdonus.ruclaw.android.core.model.LauncherMode
import com.perdonus.ruclaw.android.core.model.LauncherConfigDraft
import com.perdonus.ruclaw.android.core.model.LauncherModelItem
import com.perdonus.ruclaw.android.core.model.LauncherSkillItem
import com.perdonus.ruclaw.android.core.model.LauncherSkillSearchItem
import com.perdonus.ruclaw.android.core.model.LauncherToolItem

data class MainUiState(
    val isLoaded: Boolean = false,
    val launcherMode: LauncherMode = LauncherMode.REMOTE,
    val launcherConfig: LauncherConfigDraft = LauncherConfigDraft(),
    val localRuntime: LocalRuntimeUiState = LocalRuntimeUiState(),
    val connectionState: ConnectionState = ConnectionState(),
    val threads: List<ChatThreadSummary> = emptyList(),
    val activeSessionId: String? = null,
    val messages: List<ChatMessage> = emptyList(),
    val composer: ComposerState = ComposerState(),
    val isTyping: Boolean = false,
    val isRefreshing: Boolean = false,
    val isHistoryLoading: Boolean = false,
    val showSettings: Boolean = false,
    val showAgentSheet: Boolean = false,
    val launcherCatalog: LauncherCatalogState = LauncherCatalogState(),
    val bannerMessage: String? = null,
    val pendingExternalUrl: String? = null,
    val pendingSystemAction: PendingSystemAction? = null,
    val updateState: UpdateUiState = UpdateUiState(),
    val diagnostics: List<String> = emptyList(),
)

data class LocalRuntimeUiState(
    val isInstalled: Boolean = false,
    val runtimeVersion: String = "",
    val runtimeRoot: String = "",
    val launcherUrl: String = DEFAULT_LOCAL_LAUNCHER_URL,
    val launcherToken: String = DEFAULT_LOCAL_LAUNCHER_TOKEN,
    val ggufPath: String = "",
    val keepAliveEnabled: Boolean = true,
    val installState: LocalRuntimeInstallState = LocalRuntimeInstallState.IDLE,
    val installLogs: List<String> = emptyList(),
)

enum class LocalRuntimeInstallState {
    IDLE,
    INSTALLING,
    READY,
    FAILED,
}

data class LauncherCatalogState(
    val isLoading: Boolean = false,
    val isLoaded: Boolean = false,
    val models: List<LauncherModelItem> = emptyList(),
    val skills: List<LauncherSkillItem> = emptyList(),
    val tools: List<LauncherToolItem> = emptyList(),
    val skillSearchQuery: String = "",
    val skillSearchResults: List<LauncherSkillSearchItem> = emptyList(),
    val isSearchingSkills: Boolean = false,
    val updatingModelName: String? = null,
    val updatingToolName: String? = null,
    val installingSkillSlug: String? = null,
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
    get() = when (launcherMode) {
        LauncherMode.LOCAL -> localRuntime.isInstalled
        LauncherMode.REMOTE -> launcherConfig.url.trim().isNotBlank() && launcherConfig.token.trim().isNotBlank()
    }

const val DEFAULT_LOCAL_LAUNCHER_URL = "http://127.0.0.1:18800"
const val DEFAULT_LOCAL_LAUNCHER_TOKEN = "ruclaw-local"
