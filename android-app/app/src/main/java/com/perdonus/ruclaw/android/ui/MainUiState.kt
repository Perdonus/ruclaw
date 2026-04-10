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
    val diagnostics: List<String> = emptyList(),
)

val MainUiState.activeThread: ChatThreadSummary?
    get() = threads.firstOrNull { it.sessionId == activeSessionId }

val MainUiState.hasConfiguredLauncher: Boolean
    get() = launcherConfig.url.trim().isNotBlank() && launcherConfig.token.trim().isNotBlank()
