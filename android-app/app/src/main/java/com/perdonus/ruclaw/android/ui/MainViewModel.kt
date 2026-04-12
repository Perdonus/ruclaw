package com.perdonus.ruclaw.android.ui

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import android.util.Base64
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.perdonus.ruclaw.android.BuildConfig
import com.perdonus.ruclaw.android.RuClawMobileApp
import com.perdonus.ruclaw.android.SessionKeepAliveService
import com.perdonus.ruclaw.android.core.model.CachedSession
import com.perdonus.ruclaw.android.core.model.ChatMessage
import com.perdonus.ruclaw.android.core.model.ChatRole
import com.perdonus.ruclaw.android.core.model.ChatThreadSummary
import com.perdonus.ruclaw.android.core.model.ComposerAttachment
import com.perdonus.ruclaw.android.core.model.ComposerState
import com.perdonus.ruclaw.android.core.model.ConnectionState
import com.perdonus.ruclaw.android.core.model.ConnectionStatus
import com.perdonus.ruclaw.android.core.model.LauncherConfigDraft
import com.perdonus.ruclaw.android.core.model.LauncherMode
import com.perdonus.ruclaw.android.core.model.LauncherModelItem
import com.perdonus.ruclaw.android.core.model.LauncherSkillItem
import com.perdonus.ruclaw.android.core.model.LauncherSkillSearchItem
import com.perdonus.ruclaw.android.core.model.LauncherToolItem
import com.perdonus.ruclaw.android.core.model.MessageStatus
import com.perdonus.ruclaw.android.core.model.PersistedLocalRuntimeState
import com.perdonus.ruclaw.android.core.model.PersistedDownloadState
import com.perdonus.ruclaw.android.core.model.PersistedUpdateState
import com.perdonus.ruclaw.android.core.util.AppDiagnostics
import com.perdonus.ruclaw.android.data.local.LocalStateRepository
import com.perdonus.ruclaw.android.data.localruntime.LocalRuntimeConfig
import com.perdonus.ruclaw.android.data.localruntime.LocalRuntimeConnection
import com.perdonus.ruclaw.android.data.localruntime.LocalRuntimeManager
import com.perdonus.ruclaw.android.data.localruntime.LocalTelegramConfig
import com.perdonus.ruclaw.android.data.remote.ruclaw.PicoEvent
import com.perdonus.ruclaw.android.data.remote.ruclaw.PicoHandshake
import com.perdonus.ruclaw.android.data.remote.ruclaw.PicoSocket
import com.perdonus.ruclaw.android.data.remote.ruclaw.RuClawApiException
import com.perdonus.ruclaw.android.data.remote.ruclaw.RuClawLauncherClient
import com.perdonus.ruclaw.android.data.remote.update.NoPublishedReleaseException
import com.perdonus.ruclaw.android.data.remote.update.ReleaseFeedClient
import com.perdonus.ruclaw.android.data.update.ApkDownloadState
import com.perdonus.ruclaw.android.data.update.ApkUpdateManager
import java.io.IOException
import java.net.ConnectException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.util.UUID
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class MainViewModel(application: Application) : AndroidViewModel(application) {
    companion object {
        private const val MAX_COMPOSER_IMAGE_BYTES = 7L * 1024L * 1024L
    }

    private val container = (application as RuClawMobileApp).container
    private val localStateRepository: LocalStateRepository = container.localStateRepository
    private val localRuntimeManager: LocalRuntimeManager = container.localRuntimeManager
    private val launcherClient: RuClawLauncherClient = container.newLauncherClient()
    private val releaseFeedClient: ReleaseFeedClient = container.releaseFeedClient
    private val apkUpdateManager: ApkUpdateManager = container.apkUpdateManager

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState = _uiState.asStateFlow()

    private var socket: PicoSocket? = null
    private var socketSessionId: String? = null
    private var socketEventsJob: Job? = null
    private var connectionJob: Job? = null
    private var reconnectJob: Job? = null
    private var updateJob: Job? = null
    private var shouldMaintainConnection = false
    private var reconnectAttempts = 0
    private var cachedHandshake: PicoHandshake? = null
    private var isCheckingUpdates = false
    private var downloadProgressPercent: Int? = null
    private var pendingKeepAliveEnable = false

    init {
        viewModelScope.launch {
            val persisted = localStateRepository.load()
            val cachedSession = persisted.activeSessionId?.let { id ->
                persisted.cachedSessions.firstOrNull { it.sessionId == id }
            }
            if (persisted.launcherMode != LauncherMode.LOCAL) {
                localStateRepository.saveLauncherMode(LauncherMode.LOCAL)
            }
            _uiState.update {
                it.copy(
                    isLoaded = true,
                    launcherMode = LauncherMode.LOCAL,
                    launcherConfig = LauncherConfigDraft(
                        url = persisted.launcherUrl.ifBlank { DEFAULT_LOCAL_LAUNCHER_URL },
                        token = persisted.launcherToken,
                    ),
                    localRuntime = persisted.localRuntime.toUiState(),
                    threads = persisted.threads,
                    activeSessionId = persisted.activeSessionId,
                    messages = cachedSession?.messages ?: emptyList(),
                    hasNotificationPermission = SessionKeepAliveService.hasNotificationPermission(getApplication()),
                    updateState = persisted.updateState.toUiState(),
                    diagnostics = AppDiagnostics.snapshot(),
                )
            }
            refreshUpdateFromSystem(showFailureMessage = false)
            checkForUpdates(silent = true)
            if (persisted.localRuntime.isInstalled) {
                launchConnectionTask {
                    connectLauncherInternal(silent = true, reconnecting = false)
                }
            }
        }
    }
    fun onLauncherUrlChanged(value: String) {
        _uiState.update {
            it.copy(
                launcherConfig = it.launcherConfig.copy(url = value),
                launcherCatalog = LauncherCatalogState(),
            )
        }
    }

    fun onLauncherTokenChanged(value: String) {
        _uiState.update {
            it.copy(
                launcherConfig = it.launcherConfig.copy(token = value),
                launcherCatalog = LauncherCatalogState(),
            )
        }
    }

    fun onLauncherModeChanged(mode: LauncherMode) {
        val previousMode = _uiState.value.launcherMode
        _uiState.update {
            it.copy(
                launcherMode = mode,
                launcherCatalog = LauncherCatalogState(),
            )
        }
        viewModelScope.launch {
            localStateRepository.saveLauncherMode(mode)
        }
        if (previousMode != mode && _uiState.value.connectionState.status != ConnectionStatus.DISCONNECTED) {
            disconnectLauncher(stopLocalRuntime = previousMode == LauncherMode.LOCAL)
        }
    }

    fun onLocalModelPathChanged(value: String) {
        _uiState.update {
            it.copy(
                localRuntime = it.localRuntime.copy(ggufPath = value),
            )
        }
        viewModelScope.launch {
            localStateRepository.updateLocalRuntime { current ->
                current.copy(ggufPath = value.trim())
            }
        }
    }

    fun onLocalDataDirectoryChanged(value: String) {
        _uiState.update {
            it.copy(
                localRuntime = it.localRuntime.copy(dataDirectory = value),
            )
        }
        viewModelScope.launch {
            localStateRepository.updateLocalRuntime { current ->
                current.copy(dataDirectory = value.trim())
            }
        }
    }

    fun onLocalKeepAliveChanged(enabled: Boolean) {
        if (enabled && !_uiState.value.hasNotificationPermission) {
            pendingKeepAliveEnable = true
            requestNotificationPermission()
            showMessage("Разреши уведомления, чтобы keep alive удерживал локальный RuClaw в фоне.")
            return
        }

        pendingKeepAliveEnable = false
        _uiState.update {
            it.copy(
                localRuntime = it.localRuntime.copy(keepAliveEnabled = enabled),
            )
        }
        viewModelScope.launch {
            localStateRepository.updateLocalRuntime { current ->
                current.copy(keepAliveEnabled = enabled)
            }
            if (_uiState.value.launcherMode == LauncherMode.LOCAL) {
                val status = _uiState.value.connectionState
                updateConnectionState(status.status, status.message)
            }
        }
    }

    fun onTelegramEnabledChanged(enabled: Boolean) {
        _uiState.update {
            it.copy(
                localRuntime = it.localRuntime.copy(telegramEnabled = enabled),
            )
        }
        viewModelScope.launch {
            localStateRepository.updateLocalRuntime { current ->
                current.copy(telegramEnabled = enabled)
            }
        }
    }

    fun onTelegramBotTokenChanged(value: String) {
        _uiState.update {
            it.copy(
                localRuntime = it.localRuntime.copy(telegramBotToken = value),
            )
        }
        viewModelScope.launch {
            localStateRepository.updateLocalRuntime { current ->
                current.copy(telegramBotToken = value.trim())
            }
        }
    }

    fun onTelegramAllowedUsersChanged(value: String) {
        _uiState.update {
            it.copy(
                localRuntime = it.localRuntime.copy(telegramAllowedUsers = value),
            )
        }
        viewModelScope.launch {
            localStateRepository.updateLocalRuntime { current ->
                current.copy(telegramAllowedUsers = value.trim())
            }
        }
    }

    fun onTelegramMarkdownV2Changed(enabled: Boolean) {
        _uiState.update {
            it.copy(
                localRuntime = it.localRuntime.copy(telegramUseMarkdownV2 = enabled),
            )
        }
        viewModelScope.launch {
            localStateRepository.updateLocalRuntime { current ->
                current.copy(telegramUseMarkdownV2 = enabled)
            }
        }
    }

    fun requestNotificationsPermission() {
        requestNotificationPermission()
    }

    fun onNotificationPermissionResult(granted: Boolean) {
        _uiState.update { it.copy(hasNotificationPermission = granted) }
        if (granted) {
            if (pendingKeepAliveEnable) {
                pendingKeepAliveEnable = false
                onLocalKeepAliveChanged(true)
                return
            }
            val status = _uiState.value.connectionState
            updateConnectionState(status.status, status.message)
        } else {
            pendingKeepAliveEnable = false
            if (_uiState.value.localRuntime.keepAliveEnabled) {
                _uiState.update {
                    it.copy(
                        localRuntime = it.localRuntime.copy(keepAliveEnabled = false),
                    )
                }
                viewModelScope.launch {
                    localStateRepository.updateLocalRuntime { current ->
                        current.copy(keepAliveEnabled = false)
                    }
                }
            }
            val status = _uiState.value.connectionState
            updateConnectionState(status.status, status.message)
            showMessage("Без разрешения на уведомления foreground keep alive не удержит локальный runtime.")
        }
    }

    fun installLocalRuntime() {
        if (_uiState.value.localRuntime.installState == LocalRuntimeInstallState.INSTALLING) {
            return
        }

        viewModelScope.launch {
            val dataDirectory = _uiState.value.localRuntime.dataDirectory
            if (localRuntimeManager.requiresAllFilesAccess(dataDirectory) && !localRuntimeManager.hasAllFilesAccess()) {
                openAllFilesAccessSettings()
                showMessage(
                    "Для выбранной папки Android требует доступ «Все файлы». Сейчас открою системные настройки; после выдачи доступа повтори установку.",
                )
                return@launch
            }

            shouldMaintainConnection = false
            reconnectAttempts = 0
            cancelReconnectLoop()
            closeSocket()
            runCatching {
                localRuntimeManager.stopLocalRuntime(_uiState.value.localRuntime.dataDirectory)
            }
            cachedHandshake = null
            _uiState.update {
                it.copy(
                    showAgentSheet = false,
                    launcherCatalog = LauncherCatalogState(),
                )
            }
            updateConnectionState(ConnectionStatus.DISCONNECTED, "Локальный runtime обновляется…")

            _uiState.update {
                it.copy(
                    localRuntime = it.localRuntime.copy(
                        installState = LocalRuntimeInstallState.INSTALLING,
                        installLogs = emptyList(),
                    ),
                )
            }

            val appendLog: suspend (String) -> Unit = { line ->
                AppDiagnostics.log("local-install: $line")
                _uiState.update { state ->
                    state.copy(
                        localRuntime = state.localRuntime.copy(
                            installLogs = (state.localRuntime.installLogs + line).takeLast(10),
                        ),
                        diagnostics = AppDiagnostics.snapshot(),
                    )
                }
                delay(120)
            }

            runCatching {
                localRuntimeManager.install(
                    dataDirectory = _uiState.value.localRuntime.dataDirectory,
                    log = appendLog,
                )
            }.onSuccess { installation ->
                localStateRepository.updateLocalRuntime { current ->
                    current.copy(
                        isInstalled = true,
                        runtimeVersion = installation.runtimeVersion,
                        runtimeRoot = installation.runtimeRoot,
                        launcherUrl = installation.launcherUrl,
                        launcherToken = installation.launcherToken,
                    )
                }
                applyStoreSnapshot()
                _uiState.update {
                    it.copy(
                        localRuntime = it.localRuntime.copy(
                            installState = LocalRuntimeInstallState.READY,
                        ),
                    )
                }
                delay(700)
                _uiState.update {
                    it.copy(
                        localRuntime = it.localRuntime.copy(
                            installState = LocalRuntimeInstallState.IDLE,
                            installLogs = emptyList(),
                        ),
                    )
                }
                showMessage("Локальный runtime готов. В выбранной папке теперь есть данные и bin/, а запуск идёт из APK.")
            }.onFailure { error ->
                val message = error.message ?: "Не удалось установить локальный runtime."
                _uiState.update { state ->
                    state.copy(
                        localRuntime = state.localRuntime.copy(
                            installState = LocalRuntimeInstallState.FAILED,
                            installLogs = (state.localRuntime.installLogs + message).takeLast(12),
                        ),
                        diagnostics = AppDiagnostics.snapshot(),
                    )
                }
                showMessage(message)
            }
        }
    }

    fun onComposerChanged(value: String) {
        _uiState.update {
            it.copy(composer = it.composer.copy(text = value))
        }
    }

    fun applySuggestion(text: String) {
        _uiState.update {
            it.copy(
                composer = it.composer.copy(
                    text = text,
                    isSending = false,
                ),
            )
        }
    }

    fun connectLauncher(forceRestart: Boolean = false) {
        launchConnectionTask {
            connectLauncherInternal(
                silent = false,
                reconnecting = false,
                forceRestart = forceRestart,
            )
        }
    }

    fun disconnectLauncher(stopLocalRuntime: Boolean = _uiState.value.launcherMode == LauncherMode.LOCAL) {
        launchConnectionTask {
            shouldMaintainConnection = false
            reconnectAttempts = 0
            cancelReconnectLoop()
            closeSocket()
            if (stopLocalRuntime) {
                localRuntimeManager.stopLocalRuntime(_uiState.value.localRuntime.dataDirectory)
            }
            cachedHandshake = null
            _uiState.update {
                it.copy(
                    showAgentSheet = false,
                    launcherCatalog = LauncherCatalogState(),
                )
            }
            updateConnectionState(ConnectionStatus.DISCONNECTED, "Отключено")
        }
    }

    fun refreshThreads() {
        launchConnectionTask {
            val config = resolvedLauncherConfig() ?: run {
                showMessage("Сначала установи и запусти локальный RuClaw.")
                return@launchConnectionTask
            }

            _uiState.update { it.copy(isRefreshing = true) }
            try {
                if (_uiState.value.launcherMode == LauncherMode.REMOTE) {
                    localStateRepository.saveLauncherConfig(config.url, config.token)
                }
                val remoteThreads = launcherClient.listSessions(config.url, config.token)
                localStateRepository.replaceThreads(remoteThreads)
                applyStoreSnapshot()
            } catch (error: Throwable) {
                if (error is CancellationException) {
                    throw error
                }
                handleLauncherActionError(
                    error = error,
                    fallbackMessage = "Не удалось обновить список тредов.",
                )
            } finally {
                _uiState.update { it.copy(isRefreshing = false) }
            }
        }
    }

    fun newSession() {
        launchConnectionTask {
            cancelReconnectLoop()
            val sessionId = createLocalSession()
            applyStoreSnapshot(messagesOverride = emptyList())
            val config = resolvedLauncherConfig()
            if (config != null && shouldMaintainConnection) {
                try {
                    val handshake = cachedHandshake ?: launcherClient.ensurePico(config.url, config.token).also {
                        cachedHandshake = it
                    }
                    openSocket(config.url, config.token, handshake, sessionId)
                    updateConnectionState(ConnectionStatus.CONNECTED, "Локальный RuClaw активен")
                } catch (error: Throwable) {
                    handleConnectionError(error, silent = false)
                }
            }
        }
    }

    fun selectSession(sessionId: String) {
        launchConnectionTask {
            cancelReconnectLoop()
            localStateRepository.selectSession(sessionId)
            val cached = localStateRepository.snapshot().cachedSessions.firstOrNull { it.sessionId == sessionId }
            _uiState.update {
                it.copy(
                    activeSessionId = sessionId,
                    messages = cached?.messages ?: emptyList(),
                    isTyping = false,
                    isHistoryLoading = true,
                )
            }

            try {
                val config = resolvedLauncherConfig()
                if (config != null) {
                    val remote = loadRemoteHistory(config.url, config.token, sessionId)
                    applyStoreSnapshot(messagesOverride = remote?.messages ?: cached?.messages ?: emptyList())
                    if (shouldMaintainConnection) {
                        val handshake = cachedHandshake ?: launcherClient.ensurePico(config.url, config.token).also {
                            cachedHandshake = it
                        }
                        openSocket(config.url, config.token, handshake, sessionId)
                        updateConnectionState(ConnectionStatus.CONNECTED, "Локальный RuClaw активен")
                    }
                } else {
                    applyStoreSnapshot(messagesOverride = cached?.messages ?: emptyList())
                }
            } catch (error: Throwable) {
                handleConnectionError(error, silent = false)
            } finally {
                _uiState.update { it.copy(isHistoryLoading = false) }
            }
        }
    }

    fun sendMessage() {
        val composerState = _uiState.value.composer
        if (composerState.isSending) {
            return
        }
        val rawText = composerState.text.trim()
        val rawAttachments = composerState.attachments.map { it.url }
        if (rawText.isBlank() && rawAttachments.isEmpty()) {
            return
        }

        viewModelScope.launch {
            if (_uiState.value.launcherMode == LauncherMode.LOCAL && (socket == null || socketSessionId == null)) {
                connectLauncherInternal(silent = false, reconnecting = false)
            }
            val config = resolvedLauncherConfig() ?: run {
                showMessage(launcherRequiredMessage())
                return@launch
            }

            try {
                shouldMaintainConnection = true
                cancelReconnectLoop()

                val sessionId = _uiState.value.activeSessionId ?: createLocalSession()
                val handshake = cachedHandshake ?: launcherClient.ensurePico(config.url, config.token).also {
                    cachedHandshake = it
                }

                if (socket == null || socketSessionId != sessionId) {
                    openSocket(config.url, config.token, handshake, sessionId)
                    updateConnectionState(ConnectionStatus.CONNECTED, "Локальный RuClaw активен")
                }

                val requestId = "msg-" + System.currentTimeMillis()
                val userMessage = ChatMessage(
                    id = requestId,
                    role = ChatRole.USER,
                    text = rawText,
                    attachments = rawAttachments,
                    status = MessageStatus.COMPLETE,
                    createdAtEpochMillis = System.currentTimeMillis(),
                )
                val nextMessages = _uiState.value.messages + userMessage
                _uiState.update {
                    it.copy(
                        messages = nextMessages,
                        composer = it.composer.copy(
                            text = "",
                            isSending = true,
                            attachments = emptyList(),
                        ),
                        isTyping = true,
                    )
                }
                persistSession(sessionId, nextMessages)

                val sent = socket?.sendMessage(
                    requestId = requestId,
                    content = rawText,
                    media = rawAttachments,
                ) == true
                if (!sent) {
                    _uiState.update {
                        it.copy(
                            messages = it.messages.filterNot { message -> message.id == requestId },
                            composer = it.composer.copy(
                                text = rawText,
                                isSending = false,
                                attachments = composerState.attachments,
                            ),
                            isTyping = false,
                        )
                    }
                    persistSession(sessionId, _uiState.value.messages)
                    showMessage("Не удалось отправить сообщение.")
                    scheduleReconnect(sessionId, "Восстанавливаю websocket…")
                }
            } catch (error: Throwable) {
                handleConnectionError(error, silent = false)
            }
        }
    }

    fun toggleSettings(show: Boolean = !_uiState.value.showSettings) {
        _uiState.update {
            it.copy(
                showSettings = show,
                showAgentSheet = if (show) false else it.showAgentSheet,
            )
        }
    }

    fun toggleAgentSheet(show: Boolean = !_uiState.value.showAgentSheet) {
        _uiState.update {
            it.copy(
                showAgentSheet = show,
                showSettings = if (show) false else it.showSettings,
            )
        }
        if (show) {
            refreshLauncherCatalog(force = false, silent = true)
        }
    }

    fun refreshLauncherCatalog(
        force: Boolean = true,
        silent: Boolean = false,
    ) {
        viewModelScope.launch {
            val current = _uiState.value.launcherCatalog
            if (!force && (current.isLoaded || current.isLoading)) {
                return@launch
            }
            val config = resolvedLauncherConfig() ?: run {
                _uiState.update {
                    it.copy(launcherCatalog = LauncherCatalogState())
                }
                if (!silent) {
                    showMessage(launcherRequiredMessage())
                }
                return@launch
            }

            _uiState.update {
                it.copy(
                    launcherCatalog = it.launcherCatalog.copy(
                        isLoading = true,
                    ),
                )
            }

            try {
                val models = launcherClient.listModels(config.url, config.token)
                val skills = launcherClient.listSkills(config.url, config.token)
                val tools = launcherClient.listTools(config.url, config.token)
                _uiState.update {
                    it.copy(
                        launcherCatalog = it.launcherCatalog.copy(
                            isLoading = false,
                            isLoaded = true,
                            models = models,
                            skills = skills,
                            tools = tools,
                            updatingModelName = null,
                            updatingToolName = null,
                            installingSkillSlug = null,
                        ),
                    )
                }
            } catch (error: Throwable) {
                if (error is CancellationException) {
                    throw error
                }
                _uiState.update {
                    it.copy(
                        launcherCatalog = it.launcherCatalog.copy(
                            isLoading = false,
                            updatingModelName = null,
                            updatingToolName = null,
                            installingSkillSlug = null,
                        ),
                    )
                }
                handleLauncherActionError(
                    error = error,
                    fallbackMessage = "Не удалось загрузить модели, навыки и инструменты.",
                    silent = silent,
                )
            }
        }
    }

    fun setDefaultLauncherModel(modelName: String) {
        viewModelScope.launch {
            val config = resolvedLauncherConfig() ?: run {
                showMessage(launcherRequiredMessage())
                return@launch
            }

            _uiState.update {
                it.copy(
                    launcherCatalog = it.launcherCatalog.copy(updatingModelName = modelName),
                )
            }

            try {
                launcherClient.setDefaultModel(config.url, config.token, modelName)
                _uiState.update {
                    it.copy(
                        launcherCatalog = it.launcherCatalog.copy(
                            updatingModelName = null,
                            models = it.launcherCatalog.models.map { model ->
                                model.copy(isDefault = model.modelName == modelName)
                            },
                        ),
                    )
                }
                showMessage("Модель по умолчанию: " + modelName)
            } catch (error: Throwable) {
                _uiState.update {
                    it.copy(
                        launcherCatalog = it.launcherCatalog.copy(updatingModelName = null),
                    )
                }
                if (error is CancellationException) {
                    throw error
                }
                handleLauncherActionError(
                    error = error,
                    fallbackMessage = "Не удалось переключить модель.",
                )
            }
        }
    }

    fun onSkillSearchQueryChanged(value: String) {
        _uiState.update {
            it.copy(
                launcherCatalog = it.launcherCatalog.copy(
                    skillSearchQuery = value,
                    skillSearchResults = if (value.isBlank()) emptyList() else it.launcherCatalog.skillSearchResults,
                ),
            )
        }
    }

    fun searchLauncherSkills() {
        viewModelScope.launch {
            val config = resolvedLauncherConfig() ?: run {
                showMessage(launcherRequiredMessage())
                return@launch
            }
            val query = _uiState.value.launcherCatalog.skillSearchQuery.trim()
            if (query.isBlank()) {
                _uiState.update {
                    it.copy(
                        launcherCatalog = it.launcherCatalog.copy(
                            skillSearchResults = emptyList(),
                            isSearchingSkills = false,
                        ),
                    )
                }
                return@launch
            }

            _uiState.update {
                it.copy(
                    launcherCatalog = it.launcherCatalog.copy(isSearchingSkills = true),
                )
            }

            try {
                val results = launcherClient.searchSkills(config.url, config.token, query)
                _uiState.update {
                    it.copy(
                        launcherCatalog = it.launcherCatalog.copy(
                            isSearchingSkills = false,
                            skillSearchResults = results,
                        ),
                    )
                }
            } catch (error: Throwable) {
                _uiState.update {
                    it.copy(
                        launcherCatalog = it.launcherCatalog.copy(isSearchingSkills = false),
                    )
                }
                if (error is CancellationException) {
                    throw error
                }
                handleLauncherActionError(
                    error = error,
                    fallbackMessage = "Не удалось найти навыки.",
                )
            }
        }
    }

    fun installLauncherSkill(item: LauncherSkillSearchItem) {
        viewModelScope.launch {
            val config = resolvedLauncherConfig() ?: run {
                showMessage(launcherRequiredMessage())
                return@launch
            }

            _uiState.update {
                it.copy(
                    launcherCatalog = it.launcherCatalog.copy(installingSkillSlug = item.slug),
                )
            }

            try {
                val installedSkill = launcherClient.installSkill(
                    baseUrl = config.url,
                    launcherToken = config.token,
                    slug = item.slug,
                    registryName = item.registryName,
                    version = item.version,
                )
                val skills = launcherClient.listSkills(config.url, config.token)
                _uiState.update {
                    it.copy(
                        launcherCatalog = it.launcherCatalog.copy(
                            installingSkillSlug = null,
                            skills = skills,
                            skillSearchResults = it.launcherCatalog.skillSearchResults.map { result ->
                                if (result.slug == item.slug && result.registryName == item.registryName) {
                                    result.copy(
                                        installed = true,
                                        installedName = installedSkill?.name ?: result.installedName,
                                    )
                                } else {
                                    result
                                }
                            },
                        ),
                    )
                }
                showMessage("Навык установлен: " + (installedSkill?.name ?: item.displayName))
            } catch (error: Throwable) {
                _uiState.update {
                    it.copy(
                        launcherCatalog = it.launcherCatalog.copy(installingSkillSlug = null),
                    )
                }
                if (error is CancellationException) {
                    throw error
                }
                handleLauncherActionError(
                    error = error,
                    fallbackMessage = "Не удалось установить навык.",
                )
            }
        }
    }

    fun useLauncherSkill(skill: LauncherSkillItem) {
        _uiState.update {
            val currentText = it.composer.text.trim()
            val command = if (currentText.isBlank()) {
                "/use " + skill.name + " "
            } else if (currentText.startsWith("/use ")) {
                val message = currentText.split(" ", limit = 3).getOrNull(2).orEmpty()
                if (message.isBlank()) {
                    "/use " + skill.name + " "
                } else {
                    "/use " + skill.name + " " + message
                }
            } else {
                "/use " + skill.name + " " + currentText
            }
            it.copy(
                showAgentSheet = false,
                composer = it.composer.copy(text = command),
            )
        }
    }

    fun toggleLauncherTool(
        tool: LauncherToolItem,
        enabled: Boolean,
    ) {
        viewModelScope.launch {
            val config = resolvedLauncherConfig() ?: run {
                showMessage(launcherRequiredMessage())
                return@launch
            }

            _uiState.update {
                it.copy(
                    launcherCatalog = it.launcherCatalog.copy(updatingToolName = tool.name),
                )
            }

            try {
                launcherClient.setToolEnabled(
                    baseUrl = config.url,
                    launcherToken = config.token,
                    name = tool.name,
                    enabled = enabled,
                )
                val tools = launcherClient.listTools(config.url, config.token)
                _uiState.update {
                    it.copy(
                        launcherCatalog = it.launcherCatalog.copy(
                            updatingToolName = null,
                            tools = tools,
                        ),
                    )
                }
                showMessage(
                    if (enabled) {
                        "Инструмент включён: " + tool.name
                    } else {
                        "Инструмент выключен: " + tool.name
                    },
                )
            } catch (error: Throwable) {
                _uiState.update {
                    it.copy(
                        launcherCatalog = it.launcherCatalog.copy(updatingToolName = null),
                    )
                }
                if (error is CancellationException) {
                    throw error
                }
                handleLauncherActionError(
                    error = error,
                    fallbackMessage = "Не удалось переключить инструмент.",
                )
            }
        }
    }

    fun checkForUpdates(silent: Boolean = false) {
        updateJob?.cancel()
        updateJob = viewModelScope.launch {
            isCheckingUpdates = true
            applyStoreSnapshot()
            try {
                val release = releaseFeedClient.fetchLatestRelease()
                localStateRepository.updateUpdateState { current ->
                    val releaseChanged = current.latestVersionName.isNotBlank() && current.latestVersionName != release.versionName
                    current.copy(
                        latestVersionName = release.versionName,
                        releaseTag = release.tagName,
                        releaseUrl = release.htmlUrl,
                        releaseNotes = release.releaseNotes,
                        apkUrl = release.apkUrl,
                        apkSha256Url = release.apkSha256Url,
                        publishedAtEpochMillis = release.publishedAtEpochMillis,
                        lastCheckedAtEpochMillis = System.currentTimeMillis(),
                        downloadId = if (releaseChanged) null else current.downloadId,
                        downloadedUri = if (releaseChanged) "" else current.downloadedUri,
                        downloadState = if (releaseChanged) {
                            PersistedDownloadState.IDLE
                        } else {
                            current.downloadState
                        },
                    )
                }
                applyStoreSnapshot()
                val updateAvailable = compareVersionNames(release.versionName, BuildConfig.VERSION_NAME) > 0
                if (!silent) {
                    if (updateAvailable) {
                        showMessage("Доступно обновление ${release.versionName}.")
                    } else {
                        showMessage("Обновлений пока нет.")
                    }
                }
            } catch (error: NoPublishedReleaseException) {
                AppDiagnostics.log("Update check: latest release is not published yet")
                localStateRepository.updateUpdateState { current ->
                    current.copy(
                        latestVersionName = "",
                        releaseTag = "",
                        releaseUrl = "",
                        releaseNotes = "",
                        apkUrl = "",
                        apkSha256Url = "",
                        publishedAtEpochMillis = 0L,
                        lastCheckedAtEpochMillis = System.currentTimeMillis(),
                        downloadId = null,
                        downloadedUri = "",
                        downloadState = PersistedDownloadState.IDLE,
                    )
                }
                applyStoreSnapshot()
                if (!silent) {
                    showMessage(error.message ?: "Публичный релиз на GitHub пока не опубликован.")
                }
            } catch (error: Throwable) {
                if (error is CancellationException) {
                    throw error
                }
                AppDiagnostics.log("Update check failed: ${error.message ?: "unknown"}")
                updateDiagnostics()
                if (!silent) {
                    showMessage(error.message ?: "Не удалось проверить обновления.")
                }
            } finally {
                isCheckingUpdates = false
                applyStoreSnapshot()
            }
        }
    }

    fun downloadUpdate() {
        viewModelScope.launch {
            val updateState = _uiState.value.updateState
            if (updateState.apkUrl.isBlank()) {
                showMessage(
                    if (updateState.lastCheckedAtEpochMillis > 0L && updateState.latestVersionName.isBlank()) {
                        "Публичный релиз на GitHub пока не опубликован."
                    } else {
                        "В latest release нет APK для Android."
                    },
                )
                return@launch
            }

            try {
                val downloadId = apkUpdateManager.enqueueDownload(
                    apkUrl = updateState.apkUrl,
                    versionName = updateState.latestVersionName.ifBlank { updateState.releaseTag.ifBlank { "latest" } },
                )
                downloadProgressPercent = 0
                localStateRepository.updateUpdateState { current ->
                    current.copy(
                        downloadId = downloadId,
                        downloadedUri = "",
                        downloadState = PersistedDownloadState.DOWNLOADING,
                    )
                }
                applyStoreSnapshot()
                showMessage("APK отправлен в DownloadManager.")
            } catch (error: Throwable) {
                if (error is CancellationException) {
                    throw error
                }
                showMessage(error.message ?: "Не удалось начать загрузку APK.")
            }
        }
    }

    fun installDownloadedUpdate() {
        viewModelScope.launch {
            refreshUpdateFromSystem(showFailureMessage = false)
            val updateState = _uiState.value.updateState
            if (updateState.downloadedUri.isBlank()) {
                showMessage("APK ещё не докачался.")
                return@launch
            }

            if (!apkUpdateManager.canRequestPackageInstalls()) {
                _uiState.update {
                    it.copy(
                        pendingSystemAction = PendingSystemAction(
                            type = PendingSystemActionType.OPEN_UNKNOWN_SOURCES_SETTINGS,
                        ),
                    )
                }
                return@launch
            }

            _uiState.update {
                it.copy(
                    pendingSystemAction = PendingSystemAction(
                        type = PendingSystemActionType.INSTALL_APK,
                        uri = updateState.downloadedUri,
                    ),
                )
            }
        }
    }

    fun onAppForegrounded() {
        viewModelScope.launch {
            val previous = _uiState.value.hasNotificationPermission
            val current = SessionKeepAliveService.hasNotificationPermission(getApplication())
            _uiState.update {
                it.copy(hasNotificationPermission = current)
            }
            refreshUpdateFromSystem(showFailureMessage = false)
            if (current && pendingKeepAliveEnable) {
                onLocalKeepAliveChanged(true)
            }
            if (previous != current || _uiState.value.localRuntime.keepAliveEnabled) {
                val status = _uiState.value.connectionState
                updateConnectionState(status.status, status.message)
            }
        }
    }

    fun consumeBannerMessage() {
        _uiState.update { it.copy(bannerMessage = null) }
    }

    fun addComposerPhoto(uri: Uri) {
        viewModelScope.launch {
            val attachment = runCatching { readComposerAttachment(uri) }.getOrElse { error ->
                showMessage(error.message ?: "Не удалось прикрепить фото.")
                return@launch
            }
            _uiState.update {
                it.copy(
                    composer = it.composer.copy(
                        attachments = (it.composer.attachments + attachment).take(1),
                    ),
                )
            }
        }
    }

    fun removeComposerAttachment(index: Int) {
        _uiState.update {
            it.copy(
                composer = it.composer.copy(
                    attachments = it.composer.attachments.filterIndexed { itemIndex, _ -> itemIndex != index },
                ),
            )
        }
    }

    fun openExternalUrl(url: String) {
        _uiState.update { it.copy(pendingExternalUrl = url) }
    }

    fun consumePendingExternalUrl() {
        _uiState.update { it.copy(pendingExternalUrl = null) }
    }

    fun consumePendingSystemAction() {
        _uiState.update { it.copy(pendingSystemAction = null) }
    }

    private fun requestNotificationPermission() {
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                getApplication(),
                Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            _uiState.update {
                it.copy(
                    pendingSystemAction = PendingSystemAction(
                        type = PendingSystemActionType.REQUEST_POST_NOTIFICATIONS,
                    ),
                )
            }
        } else {
            _uiState.update {
                it.copy(
                    pendingSystemAction = PendingSystemAction(
                        type = PendingSystemActionType.OPEN_APP_NOTIFICATION_SETTINGS,
                    ),
                )
            }
        }
    }

    private fun openAllFilesAccessSettings() {
        _uiState.update {
            it.copy(
                pendingSystemAction = PendingSystemAction(
                    type = PendingSystemActionType.OPEN_ALL_FILES_ACCESS_SETTINGS,
                ),
            )
        }
    }

    fun announceDownloadPath(path: String) {
        showMessage("Путь скопирован: $path")
    }

    private fun launchConnectionTask(block: suspend () -> Unit) {
        connectionJob?.cancel()
        connectionJob = viewModelScope.launch {
            runCatching { block() }.onFailure { error ->
                if (error !is CancellationException) {
                    AppDiagnostics.log("Connection task error: ${error.message ?: "unknown"}")
                    updateDiagnostics()
                }
            }
        }
    }

    private suspend fun connectLauncherInternal(
        silent: Boolean,
        reconnecting: Boolean,
        forceRestart: Boolean = false,
    ) {
        shouldMaintainConnection = true
        cancelReconnectLoop()

        try {
            var localHandshake: PicoHandshake? = null
            if (_uiState.value.launcherMode == LauncherMode.LOCAL) {
                val localRuntime = _uiState.value.localRuntime
                if (!localRuntime.isInstalled) {
                    shouldMaintainConnection = false
                    updateConnectionState(ConnectionStatus.DISCONNECTED, null)
                    if (!silent) {
                        showMessage("Сначала нажми «Установить» для локального runtime.")
                    }
                    return
                }
                if (
                    localRuntimeManager.requiresAllFilesAccess(localRuntime.dataDirectory) &&
                    !localRuntimeManager.hasAllFilesAccess()
                ) {
                    shouldMaintainConnection = false
                    updateConnectionState(ConnectionStatus.DISCONNECTED, "Нужен доступ «Все файлы» для выбранной папки.")
                    openAllFilesAccessSettings()
                    if (!silent) {
                        showMessage(
                            "Для выбранной папки Android требует доступ «Все файлы». Сейчас открою системные настройки; после выдачи доступа повтори запуск локального RuClaw.",
                        )
                    }
                    return
                }
                if (localRuntime.keepAliveEnabled && !_uiState.value.hasNotificationPermission) {
                    requestNotificationPermission()
                }
                updateConnectionState(
                    status = if (reconnecting) ConnectionStatus.RECONNECTING else ConnectionStatus.CONNECTING,
                    message = if (reconnecting) "Перезапускаю локальный RuClaw…" else "Поднимаю локальный RuClaw…",
                )
                val (connection, handshake) = startLocalRuntimeWithRetries(
                    localRuntime = localRuntime,
                    forceRestart = forceRestart,
                )
                localHandshake = handshake
                localStateRepository.updateLocalRuntime { current ->
                    current.copy(
                        isInstalled = true,
                        runtimeVersion = connection.runtimeVersion,
                        runtimeRoot = connection.runtimeRoot,
                        launcherUrl = connection.launcherUrl,
                        launcherToken = connection.launcherToken,
                    )
                }
                applyStoreSnapshot()
                cachedHandshake = handshake
            }

            val config = resolvedLauncherConfig() ?: run {
                shouldMaintainConnection = false
                updateConnectionState(ConnectionStatus.DISCONNECTED, null)
                if (!silent) {
                    showMessage("Подготовь локальный runtime.")
                }
                return
            }

            updateConnectionState(
                status = if (reconnecting) ConnectionStatus.RECONNECTING else ConnectionStatus.CONNECTING,
                message = if (reconnecting) "Восстанавливаю подключение…" else "Проверяю локальный RuClaw…",
            )
            AppDiagnostics.log("Connecting to ${config.url}")
            val handshake = localHandshake ?: launcherClient.ensurePico(config.url, config.token)
            cachedHandshake = handshake

            val remoteThreads = launcherClient.listSessions(config.url, config.token)
            localStateRepository.replaceThreads(remoteThreads)

            var persisted = localStateRepository.snapshot()
            var sessionId = persisted.activeSessionId
            if (sessionId == null) {
                sessionId = persisted.threads.firstOrNull()?.sessionId ?: createLocalSession()
                localStateRepository.selectSession(sessionId)
                persisted = localStateRepository.snapshot()
            }

            val activeCached = loadRemoteHistory(config.url, config.token, sessionId)
                ?: persisted.cachedSessions.firstOrNull { it.sessionId == sessionId }
                ?: emptyCachedSession(sessionId)
            persistSession(sessionId, activeCached.messages, activeCached.title, activeCached.preview)
            applyStoreSnapshot(messagesOverride = activeCached.messages)

            openSocket(config.url, config.token, handshake, sessionId)
            reconnectAttempts = 0
            updateConnectionState(ConnectionStatus.CONNECTED, "Локальный RuClaw активен")
            refreshLauncherCatalog(force = true, silent = true)
        } catch (error: Throwable) {
            shouldMaintainConnection = reconnecting && _uiState.value.launcherMode == LauncherMode.LOCAL
            if (_uiState.value.launcherMode == LauncherMode.LOCAL) {
                runCatching {
                    localRuntimeManager.stopLocalRuntime(_uiState.value.localRuntime.dataDirectory)
                }
            }
            handleConnectionError(error, silent, allowLocalRecovery = false)
            if (reconnecting && _uiState.value.launcherMode == LauncherMode.LOCAL) {
                shouldMaintainConnection = true
                throw error
            }
        }
    }

    private suspend fun openSocket(
        url: String,
        token: String,
        handshake: PicoHandshake,
        sessionId: String,
    ) {
        AppDiagnostics.log("Opening websocket session=$sessionId")
        closeSocket()
        val newSocket = launcherClient.openSocket(url, token, handshake, sessionId)
        socket = newSocket
        socketSessionId = sessionId
        socketEventsJob = viewModelScope.launch {
            newSocket.events.collect { event ->
                handleSocketEvent(sessionId, event)
            }
        }
        newSocket.connect()
    }

    private suspend fun closeSocket() {
        socketEventsJob?.cancel()
        socketEventsJob = null
        socket?.close()
        socket = null
        socketSessionId = null
        _uiState.update {
            it.copy(
                isTyping = false,
                composer = it.composer.copy(isSending = false),
            )
        }
    }

    private suspend fun handleSocketEvent(
        expectedSessionId: String,
        event: PicoEvent,
    ) {
        if (_uiState.value.activeSessionId != expectedSessionId) {
            return
        }

        when (event) {
            is PicoEvent.MessageCreate -> {
                val nextMessages = upsertAssistantMessage(
                    current = _uiState.value.messages,
                    messageId = event.messageId,
                    content = event.content,
                    status = MessageStatus.COMPLETE,
                    createdAtEpochMillis = event.createdAtEpochMillis,
                )
                _uiState.update {
                    it.copy(
                        messages = nextMessages,
                        isTyping = false,
                        composer = it.composer.copy(isSending = false),
                    )
                }
                persistSession(expectedSessionId, nextMessages)
            }

            is PicoEvent.MessageUpdate -> {
                val nextMessages = upsertAssistantMessage(
                    current = _uiState.value.messages,
                    messageId = event.messageId,
                    content = event.content,
                    status = MessageStatus.STREAMING,
                    createdAtEpochMillis = System.currentTimeMillis(),
                )
                _uiState.update {
                    it.copy(
                        messages = nextMessages,
                        isTyping = true,
                        composer = it.composer.copy(isSending = false),
                    )
                }
                persistSession(expectedSessionId, nextMessages)
            }

            is PicoEvent.Typing -> {
                _uiState.update { it.copy(isTyping = event.active) }
            }

            is PicoEvent.ProtocolError -> {
                _uiState.update {
                    it.copy(
                        isTyping = false,
                        composer = it.composer.copy(isSending = false),
                    )
                }
                persistSession(expectedSessionId, _uiState.value.messages)
                showMessage(event.message.ifBlank { "Локальный RuClaw вернул ошибку." })
            }

            is PicoEvent.SocketClosed -> {
                if (shouldMaintainConnection) {
                    scheduleReconnect(expectedSessionId, event.reason)
                } else {
                    updateConnectionState(ConnectionStatus.DISCONNECTED, event.reason)
                }
            }

            is PicoEvent.SocketFailure -> {
                val failureMessage = describeLauncherFailure(event.statusCode, event.reason)
                if (event.statusCode == 401) {
                    shouldMaintainConnection = false
                    updateConnectionState(ConnectionStatus.FAILED_AUTH, failureMessage)
                    showMessage(failureMessage)
                } else if (event.statusCode == 403) {
                    shouldMaintainConnection = false
                    updateConnectionState(ConnectionStatus.FAILED_NETWORK, failureMessage)
                    showMessage(failureMessage)
                } else if (shouldMaintainConnection) {
                    scheduleReconnect(expectedSessionId, failureMessage)
                } else {
                    updateConnectionState(ConnectionStatus.FAILED_NETWORK, failureMessage)
                }
            }

            PicoEvent.Pong -> Unit
        }
    }

    private fun scheduleReconnect(
        sessionId: String,
        reason: String,
    ) {
        if (!shouldMaintainConnection) {
            return
        }

        reconnectJob?.cancel()
        val delays = listOf(1000L, 2000L, 4000L, 5000L)
        val delayMs = delays[min(reconnectAttempts, delays.lastIndex)]
        reconnectAttempts += 1
        updateConnectionState(ConnectionStatus.RECONNECTING, reason)
        reconnectJob = viewModelScope.launch {
            delay(delayMs)
            if (!shouldMaintainConnection) {
                return@launch
            }
            if (_uiState.value.launcherMode == LauncherMode.LOCAL) {
                runCatching {
                    connectLauncherInternal(silent = true, reconnecting = true)
                }.onFailure { error ->
                    if (error !is CancellationException) {
                        shouldMaintainConnection = true
                        AppDiagnostics.log("Local reconnect failed: ${error.message ?: "unknown"}")
                        updateDiagnostics()
                        scheduleReconnect(sessionId, "Перезапускаю локальный RuClaw…")
                    }
                }
                return@launch
            }

            val config = resolvedLauncherConfig() ?: return@launch
            runCatching {
                val handshake = launcherClient.ensurePico(config.url, config.token)
                cachedHandshake = handshake
                openSocket(config.url, config.token, handshake, sessionId)
                reconnectAttempts = 0
                updateConnectionState(ConnectionStatus.CONNECTED, "Локальный RuClaw активен")
            }.onFailure { error ->
                if (error !is CancellationException) {
                    AppDiagnostics.log("Reconnect failed: ${error.message ?: "unknown"}")
                    updateDiagnostics()
                    scheduleReconnect(sessionId, "Повторяю подключение…")
                }
            }
        }
    }

    private fun cancelReconnectLoop() {
        reconnectJob?.cancel()
        reconnectJob = null
    }

    private suspend fun handleConnectionError(
        error: Throwable,
        silent: Boolean,
        allowLocalRecovery: Boolean = true,
    ) {
        if (error is CancellationException) {
            throw error
        }

        closeSocket()
        cachedHandshake = null

        val (status, message) = when (error) {
            is RuClawApiException -> {
                if (error.statusCode == 401) {
                    ConnectionStatus.FAILED_AUTH to "Токен локального RuClaw не подошёл."
                } else {
                    ConnectionStatus.FAILED_NETWORK to describeLauncherFailure(error.statusCode, error.message)
                }
            }

            else -> ConnectionStatus.FAILED_NETWORK to (error.message ?: "Не удалось подключиться к локальному RuClaw.")
        }

        val shouldRecover = allowLocalRecovery && shouldRecoverLocalConnection(error)
        updateConnectionState(status, message)
        if (shouldRecover) {
            scheduleLocalRecovery("Перезапускаю локальный RuClaw…")
        }
        if (!silent) {
            if (shouldRecover) {
                showMessage("Локальный RuClaw недоступен, переподнимаю…")
            } else {
                showMessage(message)
            }
        }
    }

    private suspend fun handleLauncherActionError(
        error: Throwable,
        fallbackMessage: String,
        silent: Boolean = false,
    ) {
        if (error is CancellationException) {
            throw error
        }
        if (shouldRecoverLocalConnection(error)) {
            handleConnectionError(error, silent = silent)
            return
        }
        if (!silent) {
            showMessage(error.message ?: fallbackMessage)
        }
    }

    private fun shouldRecoverLocalConnection(error: Throwable): Boolean {
        if (_uiState.value.launcherMode != LauncherMode.LOCAL || !_uiState.value.localRuntime.isInstalled) {
            return false
        }
        return when (error) {
            is ConnectException,
            is SocketException,
            is SocketTimeoutException -> true

            is RuClawApiException -> error.statusCode in 500..599 || error.statusCode in setOf(429, 553)

            is IOException -> {
                val message = error.message.orEmpty().lowercase()
                message.contains("127.0.0.1:18800") ||
                    message.contains("failed to connect") ||
                    message.contains("connection refused") ||
                    message.contains("broken pipe") ||
                    message.contains("unexpected end of stream") ||
                    message.contains("socket closed") ||
                    message.contains("timeout")
            }

            else -> false
        }
    }

    private fun scheduleLocalRecovery(reason: String) {
        if (_uiState.value.launcherMode != LauncherMode.LOCAL || !_uiState.value.localRuntime.isInstalled) {
            return
        }
        if (reconnectJob?.isActive == true) {
            updateConnectionState(ConnectionStatus.RECONNECTING, reason)
            return
        }
        shouldMaintainConnection = true
        val existingSessionId = _uiState.value.activeSessionId ?: localStateRepository.snapshot().activeSessionId
        if (existingSessionId != null) {
            scheduleReconnect(existingSessionId, reason)
            return
        }
        viewModelScope.launch {
            val sessionId = _uiState.value.activeSessionId
                ?: localStateRepository.snapshot().activeSessionId
                ?: createLocalSession()
            scheduleReconnect(sessionId, reason)
        }
    }

    private suspend fun createLocalSession(): String {
        val sessionId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        localStateRepository.cacheSession(
            CachedSession(
                sessionId = sessionId,
                title = "Новый диалог",
                preview = "",
                updatedAtEpochMillis = now,
                messages = emptyList(),
            ),
        )
        localStateRepository.upsertThread(
            ChatThreadSummary(
                sessionId = sessionId,
                title = "Новый диалог",
                preview = "",
                updatedAtEpochMillis = now,
                messageCount = 0,
                isLocalOnly = true,
            ),
        )
        localStateRepository.selectSession(sessionId)
        return sessionId
    }

    private suspend fun loadRemoteHistory(
        url: String,
        token: String,
        sessionId: String,
    ): CachedSession? {
        return try {
            launcherClient.getSession(url, token, sessionId).also { session ->
                localStateRepository.cacheSession(session)
            }
        } catch (error: RuClawApiException) {
            if (error.statusCode == 404) {
                localStateRepository.snapshot().cachedSessions.firstOrNull { it.sessionId == sessionId }
            } else {
                throw error
            }
        }
    }

    private suspend fun refreshUpdateFromSystem(showFailureMessage: Boolean) {
        val updateState = localStateRepository.snapshot().updateState
        val downloadId = updateState.downloadId ?: run {
            downloadProgressPercent = null
            applyStoreSnapshot()
            return
        }
        val snapshot = apkUpdateManager.queryDownload(downloadId) ?: run {
            localStateRepository.updateUpdateState {
                it.copy(
                    downloadId = null,
                    downloadedUri = "",
                    downloadState = PersistedDownloadState.IDLE,
                )
            }
            downloadProgressPercent = null
            applyStoreSnapshot()
            return
        }

        downloadProgressPercent = snapshot.progressPercent
        localStateRepository.updateUpdateState { current ->
            current.copy(
                downloadId = snapshot.downloadId,
                downloadedUri = snapshot.localUri,
                downloadState = when (snapshot.state) {
                    ApkDownloadState.PENDING,
                    ApkDownloadState.DOWNLOADING -> PersistedDownloadState.DOWNLOADING
                    ApkDownloadState.READY_TO_INSTALL -> PersistedDownloadState.READY_TO_INSTALL
                    ApkDownloadState.FAILED -> PersistedDownloadState.FAILED
                    ApkDownloadState.IDLE -> current.downloadState
                },
            )
        }
        applyStoreSnapshot()

        if (showFailureMessage && snapshot.state == ApkDownloadState.FAILED) {
            showMessage(snapshot.reason ?: "Загрузка APK завершилась ошибкой.")
        }
    }

    private suspend fun persistSession(
        sessionId: String,
        messages: List<ChatMessage>,
        titleOverride: String? = null,
        previewOverride: String? = null,
    ) {
        val existing = localStateRepository.snapshot().threads.firstOrNull { it.sessionId == sessionId }
        val preview = previewOverride?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: messages.firstOrNull { it.role == ChatRole.USER && it.text.isNotBlank() }?.text?.trim()
            ?: existing?.preview.orEmpty()
        val title = titleOverride?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: preview.take(60).ifBlank { existing?.title ?: "Новый диалог" }
        val updatedAt = System.currentTimeMillis()

        localStateRepository.cacheSession(
            CachedSession(
                sessionId = sessionId,
                title = title,
                preview = preview.take(120),
                updatedAtEpochMillis = updatedAt,
                messages = messages,
            ),
        )
        localStateRepository.upsertThread(
            ChatThreadSummary(
                sessionId = sessionId,
                title = title,
                preview = preview.take(120),
                updatedAtEpochMillis = updatedAt,
                messageCount = messages.size,
                isLocalOnly = existing?.isLocalOnly ?: false,
            ),
        )
        localStateRepository.selectSession(sessionId)
        applyStoreSnapshot(messagesOverride = messages)
    }

    private fun applyStoreSnapshot(messagesOverride: List<ChatMessage>? = null) {
        val persisted = localStateRepository.snapshot()
        val activeCached = persisted.activeSessionId?.let { id ->
            persisted.cachedSessions.firstOrNull { it.sessionId == id }
        }
        val currentLocalRuntime = _uiState.value.localRuntime
        val hasNotificationPermission = _uiState.value.hasNotificationPermission
        _uiState.update {
            it.copy(
                isLoaded = true,
                launcherMode = LauncherMode.LOCAL,
                launcherConfig = it.launcherConfig.copy(
                    url = persisted.launcherUrl.ifBlank { DEFAULT_LOCAL_LAUNCHER_URL },
                    token = persisted.launcherToken,
                ),
                localRuntime = persisted.localRuntime.toUiState(currentLocalRuntime),
                threads = persisted.threads,
                activeSessionId = persisted.activeSessionId,
                messages = messagesOverride ?: activeCached?.messages ?: emptyList(),
                hasNotificationPermission = hasNotificationPermission,
                updateState = persisted.updateState.toUiState(),
                diagnostics = AppDiagnostics.snapshot(),
            )
        }
    }

    private fun resolvedLauncherConfig(): LauncherConfigDraft? {
        return when (_uiState.value.launcherMode) {
            LauncherMode.LOCAL -> {
                val localRuntime = _uiState.value.localRuntime
                val status = _uiState.value.connectionState.status
                if (
                    !localRuntime.isInstalled ||
                    status !in setOf(
                        ConnectionStatus.CONNECTING,
                        ConnectionStatus.CONNECTED,
                        ConnectionStatus.RECONNECTING,
                    )
                ) {
                    null
                } else {
                    LauncherConfigDraft(
                        url = localRuntime.launcherUrl.trim().ifBlank { DEFAULT_LOCAL_LAUNCHER_URL },
                        token = localRuntime.launcherToken.trim().ifBlank { DEFAULT_LOCAL_LAUNCHER_TOKEN },
                    )
                }
            }

            LauncherMode.REMOTE -> {
                val url = _uiState.value.launcherConfig.url.trim().ifBlank { DEFAULT_LOCAL_LAUNCHER_URL }
                val token = _uiState.value.launcherConfig.token.trim()
                if (token.isBlank()) {
                    null
                } else {
                    LauncherConfigDraft(url = url, token = token)
                }
            }
        }
    }

    private suspend fun waitForLocalLauncher(
        url: String,
        token: String,
    ): PicoHandshake {
        var lastError: Throwable? = null
        repeat(24) {
            runCatching {
                launcherClient.ensurePico(url, token)
            }.onSuccess { handshake ->
                return handshake
            }.onFailure { error ->
                lastError = error
                delay(500)
            }
        }
        throw IOException(lastError?.message ?: "Локальный RuClaw не поднялся.")
    }

    private suspend fun startLocalRuntimeWithRetries(
        localRuntime: LocalRuntimeUiState,
        forceRestart: Boolean,
    ): Pair<LocalRuntimeConnection, PicoHandshake> {
        var lastError: Throwable? = null
        repeat(3) { attempt ->
            runCatching {
                val connection = localRuntimeManager.startLocalRuntime(
                    LocalRuntimeConfig(
                        ggufPath = localRuntime.ggufPath,
                        dataDirectory = localRuntime.dataDirectory,
                        forceRestart = forceRestart || attempt > 0,
                        telegram = LocalTelegramConfig(
                            enabled = localRuntime.telegramEnabled,
                            botToken = localRuntime.telegramBotToken,
                            allowedUsers = localRuntime.telegramAllowedUsers,
                            useMarkdownV2 = localRuntime.telegramUseMarkdownV2,
                        ),
                    ),
                )
                val handshake = waitForLocalLauncher(connection.launcherUrl, connection.launcherToken)
                connection to handshake
            }.onSuccess { ready ->
                return ready
            }.onFailure { error ->
                if (error is CancellationException) {
                    throw error
                }
                lastError = error
                AppDiagnostics.log("Local runtime start attempt ${attempt + 1} failed: ${error.message ?: "unknown"}")
                runCatching {
                    localRuntimeManager.stopLocalRuntime(localRuntime.dataDirectory)
                }
                if (attempt < 2) {
                    delay((attempt + 1) * 800L)
                }
            }
        }
        throw IOException(lastError?.message ?: "Локальный RuClaw не поднялся.")
    }

    private fun emptyCachedSession(sessionId: String): CachedSession {
        return CachedSession(
            sessionId = sessionId,
            title = "Новый диалог",
            preview = "",
            updatedAtEpochMillis = System.currentTimeMillis(),
            messages = emptyList(),
        )
    }

    private fun updateConnectionState(
        status: ConnectionStatus,
        message: String?,
    ) {
        _uiState.update {
            it.copy(
                connectionState = ConnectionState(status, message),
                diagnostics = AppDiagnostics.snapshot(),
            )
        }
        SessionKeepAliveService.sync(
            context = getApplication(),
            active = shouldKeepAlive(status),
            title = "RuClaw Android",
            message = when {
                _uiState.value.launcherMode == LauncherMode.LOCAL ->
                    message ?: "Локальный RuClaw работает в фоне"
                else -> message ?: "Локальный RuClaw активен"
            },
        )
    }

    private fun shouldKeepAlive(status: ConnectionStatus): Boolean {
        val connected = status in setOf(
            ConnectionStatus.CONNECTING,
            ConnectionStatus.CONNECTED,
            ConnectionStatus.RECONNECTING,
        )
        if (!connected) {
            return false
        }
        return if (_uiState.value.launcherMode == LauncherMode.LOCAL) {
            _uiState.value.localRuntime.keepAliveEnabled && _uiState.value.hasNotificationPermission
        } else {
            true
        }
    }

    private fun launcherRequiredMessage(): String {
        return if (_uiState.value.launcherMode == LauncherMode.LOCAL) {
            "Сначала установи и запусти локальный RuClaw."
        } else {
            "Сначала подними локальный RuClaw."
        }
    }

    private fun showMessage(message: String) {
        AppDiagnostics.log(message)
        _uiState.update {
            it.copy(
                bannerMessage = message,
                diagnostics = AppDiagnostics.snapshot(),
            )
        }
    }

    private fun updateDiagnostics() {
        _uiState.update { it.copy(diagnostics = AppDiagnostics.snapshot()) }
    }

    private fun upsertAssistantMessage(
        current: List<ChatMessage>,
        messageId: String,
        content: String,
        status: MessageStatus,
        createdAtEpochMillis: Long,
    ): List<ChatMessage> {
        val existingIndex = current.indexOfFirst { it.id == messageId }
        return if (existingIndex >= 0) {
            current.map { message ->
                if (message.id == messageId) {
                    message.copy(text = content, status = status)
                } else {
                    message
                }
            }
        } else {
            current + ChatMessage(
                id = messageId,
                role = ChatRole.ASSISTANT,
                text = content,
                status = status,
                createdAtEpochMillis = createdAtEpochMillis,
            )
        }
    }

    private fun PersistedUpdateState.toUiState(): UpdateUiState {
        val latestVersion = latestVersionName.trim()
        return UpdateUiState(
            currentVersionName = BuildConfig.VERSION_NAME,
            latestVersionName = latestVersion,
            releaseTag = releaseTag,
            releaseUrl = releaseUrl,
            releaseNotes = releaseNotes,
            apkUrl = apkUrl,
            apkSha256Url = apkSha256Url,
            isChecking = isCheckingUpdates,
            isUpdateAvailable = latestVersion.isNotBlank() &&
                compareVersionNames(latestVersion, BuildConfig.VERSION_NAME) > 0,
            canInstallPackages = apkUpdateManager.canRequestPackageInstalls(),
            downloadId = downloadId,
            downloadState = when (downloadState) {
                PersistedDownloadState.IDLE -> UpdateDownloadState.IDLE
                PersistedDownloadState.DOWNLOADING -> UpdateDownloadState.DOWNLOADING
                PersistedDownloadState.READY_TO_INSTALL -> UpdateDownloadState.READY_TO_INSTALL
                PersistedDownloadState.FAILED -> UpdateDownloadState.FAILED
            },
            downloadedUri = downloadedUri,
            downloadProgressPercent = downloadProgressPercent,
            lastCheckedAtEpochMillis = lastCheckedAtEpochMillis,
        )
    }

    private fun PersistedLocalRuntimeState.toUiState(current: LocalRuntimeUiState? = null): LocalRuntimeUiState {
        return LocalRuntimeUiState(
            isInstalled = isInstalled,
            runtimeVersion = runtimeVersion,
            runtimeRoot = runtimeRoot,
            launcherUrl = launcherUrl.ifBlank { DEFAULT_LOCAL_LAUNCHER_URL },
            launcherToken = launcherToken.ifBlank { DEFAULT_LOCAL_LAUNCHER_TOKEN },
            dataDirectory = dataDirectory,
            ggufPath = ggufPath,
            keepAliveEnabled = keepAliveEnabled,
            telegramEnabled = telegramEnabled,
            telegramBotToken = telegramBotToken,
            telegramAllowedUsers = telegramAllowedUsers,
            telegramUseMarkdownV2 = telegramUseMarkdownV2,
            installState = current?.installState ?: LocalRuntimeInstallState.IDLE,
            installLogs = current?.installLogs ?: emptyList(),
        )
    }

    private fun compareVersionNames(left: String, right: String): Int {
        val leftParts = versionParts(left)
        val rightParts = versionParts(right)
        val size = max(leftParts.size, rightParts.size)
        repeat(size) { index ->
            val leftPart = leftParts.getOrElse(index) { "0" }
            val rightPart = rightParts.getOrElse(index) { "0" }
            val leftNumber = leftPart.toIntOrNull()
            val rightNumber = rightPart.toIntOrNull()
            val comparison = when {
                leftNumber != null && rightNumber != null -> leftNumber.compareTo(rightNumber)
                else -> leftPart.compareTo(rightPart, ignoreCase = true)
            }
            if (comparison != 0) {
                return comparison
            }
        }
        return 0
    }

    private fun versionParts(value: String): List<String> {
        return value.removePrefix("v")
            .split(Regex("[^A-Za-z0-9]+"))
            .filter { it.isNotBlank() }
    }

    private fun describeLauncherFailure(statusCode: Int?, fallback: String): String {
        return when (statusCode) {
            401 -> "Токен локального RuClaw не подошёл."
            403 -> "Локальный RuClaw отклонил подключение к чату. Проверь токен и права доступа."
            429 -> "Локальный RuClaw упёрся в лимит запросов. Подожди немного и отправь ещё раз."
            502 -> "Локальный RuClaw не достучался до шлюза. Подожди пару секунд и попробуй снова."
            503 -> "Локальный RuClaw временно недоступен. Рантайм ещё поднимается или занят."
            504 -> "Локальный RuClaw не дождался ответа от шлюза. Повтори запрос чуть позже."
            553 -> "Локальный RuClaw вернул нестандартную сетевую ошибку по чату. Обычно это временный сбой шлюза."
            in 500..599 -> "Локальный RuClaw временно не отвечает по чату. Повтори запрос чуть позже."
            else -> fallback.ifBlank { "Не удалось подключиться к локальному RuClaw." }
        }
    }

    private fun readComposerAttachment(uri: Uri): ComposerAttachment {
        val resolver = getApplication<Application>().contentResolver
        val mimeType = resolver.getType(uri).orEmpty().ifBlank { "image/jpeg" }
        if (!mimeType.startsWith("image/")) {
            throw IOException("Можно прикреплять только изображения.")
        }

        val meta = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)
            ?.use { cursor ->
                if (!cursor.moveToFirst()) {
                    null
                } else {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                    val fileName = if (nameIndex >= 0) cursor.getString(nameIndex) else "image"
                    val fileSize = if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) cursor.getLong(sizeIndex) else null
                    fileName to fileSize
                }
            }

        val bytes = resolver.openInputStream(uri)?.use { input -> input.readBytes() }
            ?: throw IOException("Не удалось прочитать изображение.")

        val sizeBytes = meta?.second ?: bytes.size.toLong()
        if (sizeBytes > MAX_COMPOSER_IMAGE_BYTES) {
            throw IOException("Фото больше 7 МБ, выбери файл поменьше.")
        }

        val dataUrl = "data:" + mimeType + ";base64," + Base64.encodeToString(bytes, Base64.NO_WRAP)
        return ComposerAttachment(
            url = dataUrl,
            filename = meta?.first.orEmpty(),
            mimeType = mimeType,
        )
    }

    override fun onCleared() {
        val preserveLocalRuntime = _uiState.value.launcherMode == LauncherMode.LOCAL &&
            _uiState.value.localRuntime.keepAliveEnabled &&
            _uiState.value.hasNotificationPermission &&
            _uiState.value.connectionState.status in setOf(
                ConnectionStatus.CONNECTING,
                ConnectionStatus.CONNECTED,
                ConnectionStatus.RECONNECTING,
            )

        shouldMaintainConnection = false
        cancelReconnectLoop()
        updateJob?.cancel()
        runBlocking {
            closeSocket()
            if (!preserveLocalRuntime) {
                localRuntimeManager.stopLocalRuntime(_uiState.value.localRuntime.dataDirectory)
            }
        }
        if (!preserveLocalRuntime) {
            SessionKeepAliveService.sync(getApplication(), false, "RuClaw Android", "")
        }
        super.onCleared()
    }
}
