package com.perdonus.ruclaw.android.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.perdonus.ruclaw.android.BuildConfig
import com.perdonus.ruclaw.android.RuClawMobileApp
import com.perdonus.ruclaw.android.SessionKeepAliveService
import com.perdonus.ruclaw.android.core.model.CachedSession
import com.perdonus.ruclaw.android.core.model.ChatMessage
import com.perdonus.ruclaw.android.core.model.ChatRole
import com.perdonus.ruclaw.android.core.model.ChatThreadSummary
import com.perdonus.ruclaw.android.core.model.ComposerState
import com.perdonus.ruclaw.android.core.model.ConnectionState
import com.perdonus.ruclaw.android.core.model.ConnectionStatus
import com.perdonus.ruclaw.android.core.model.LauncherConfigDraft
import com.perdonus.ruclaw.android.core.model.MessageStatus
import com.perdonus.ruclaw.android.core.model.PersistedDownloadState
import com.perdonus.ruclaw.android.core.model.PersistedUpdateState
import com.perdonus.ruclaw.android.core.util.AppDiagnostics
import com.perdonus.ruclaw.android.data.local.LocalStateRepository
import com.perdonus.ruclaw.android.data.remote.ruclaw.PicoEvent
import com.perdonus.ruclaw.android.data.remote.ruclaw.PicoHandshake
import com.perdonus.ruclaw.android.data.remote.ruclaw.PicoSocket
import com.perdonus.ruclaw.android.data.remote.ruclaw.RuClawApiException
import com.perdonus.ruclaw.android.data.remote.ruclaw.RuClawLauncherClient
import com.perdonus.ruclaw.android.data.remote.update.ReleaseFeedClient
import com.perdonus.ruclaw.android.data.update.ApkDownloadState
import com.perdonus.ruclaw.android.data.update.ApkUpdateManager
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
    private val container = (application as RuClawMobileApp).container
    private val localStateRepository: LocalStateRepository = container.localStateRepository
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

    init {
        viewModelScope.launch {
            val persisted = localStateRepository.load()
            val cachedSession = persisted.activeSessionId?.let { id ->
                persisted.cachedSessions.firstOrNull { it.sessionId == id }
            }
            _uiState.update {
                it.copy(
                    isLoaded = true,
                    launcherConfig = LauncherConfigDraft(
                        url = persisted.launcherUrl.ifBlank { BuildConfig.DEFAULT_LAUNCHER_URL },
                        token = persisted.launcherToken,
                    ),
                    threads = persisted.threads,
                    activeSessionId = persisted.activeSessionId,
                    messages = cachedSession?.messages ?: emptyList(),
                    updateState = persisted.updateState.toUiState(),
                    diagnostics = AppDiagnostics.snapshot(),
                )
            }
            refreshUpdateFromSystem(showFailureMessage = false)
            checkForUpdates(silent = true)
            if (persisted.launcherUrl.isNotBlank() && persisted.launcherToken.isNotBlank()) {
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
            )
        }
    }

    fun onLauncherTokenChanged(value: String) {
        _uiState.update {
            it.copy(
                launcherConfig = it.launcherConfig.copy(token = value),
            )
        }
    }

    fun onComposerChanged(value: String) {
        _uiState.update {
            it.copy(composer = it.composer.copy(text = value))
        }
    }

    fun applySuggestion(text: String) {
        _uiState.update {
            it.copy(composer = ComposerState(text = text, isSending = false))
        }
    }

    fun connectLauncher() {
        launchConnectionTask {
            connectLauncherInternal(silent = false, reconnecting = false)
        }
    }

    fun disconnectLauncher() {
        launchConnectionTask {
            shouldMaintainConnection = false
            reconnectAttempts = 0
            cancelReconnectLoop()
            closeSocket()
            cachedHandshake = null
            updateConnectionState(ConnectionStatus.DISCONNECTED, "Отключено")
        }
    }

    fun refreshThreads() {
        launchConnectionTask {
            val config = resolvedLauncherConfig() ?: run {
                showMessage("Сначала укажи launcher URL и access token.")
                return@launchConnectionTask
            }

            _uiState.update { it.copy(isRefreshing = true) }
            try {
                localStateRepository.saveLauncherConfig(config.url, config.token)
                val remoteThreads = launcherClient.listSessions(config.url, config.token)
                localStateRepository.replaceThreads(remoteThreads)
                applyStoreSnapshot()
            } catch (error: Throwable) {
                if (error !is CancellationException) {
                    showMessage(error.message ?: "Не удалось обновить список тредов.")
                } else {
                    throw error
                }
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
                val handshake = cachedHandshake ?: launcherClient.ensurePico(config.url, config.token).also {
                    cachedHandshake = it
                }
                openSocket(config.url, config.token, handshake, sessionId)
                updateConnectionState(ConnectionStatus.CONNECTED, "Подключено к launcher")
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
                        updateConnectionState(ConnectionStatus.CONNECTED, "Подключено к launcher")
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
        val rawText = _uiState.value.composer.text.trim()
        if (rawText.isBlank()) {
            return
        }

        viewModelScope.launch {
            val config = resolvedLauncherConfig() ?: run {
                showMessage("Сначала подключи launcher.")
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
                    updateConnectionState(ConnectionStatus.CONNECTED, "Подключено к launcher")
                }

                val requestId = "msg-${System.currentTimeMillis()}"
                val userMessage = ChatMessage(
                    id = requestId,
                    role = ChatRole.USER,
                    text = rawText,
                    status = MessageStatus.COMPLETE,
                    createdAtEpochMillis = System.currentTimeMillis(),
                )
                val nextMessages = _uiState.value.messages + userMessage
                _uiState.update {
                    it.copy(
                        messages = nextMessages,
                        composer = it.composer.copy(text = "", isSending = true),
                        isTyping = true,
                    )
                }
                persistSession(sessionId, nextMessages)

                val sent = socket?.sendMessage(requestId, rawText) == true
                if (!sent) {
                    _uiState.update {
                        it.copy(
                            messages = it.messages.filterNot { message -> message.id == requestId },
                            composer = it.composer.copy(text = rawText, isSending = false),
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
        _uiState.update { it.copy(showSettings = show) }
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
                showMessage("В latest release нет APK для Android.")
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
            refreshUpdateFromSystem(showFailureMessage = false)
        }
    }

    fun consumeBannerMessage() {
        _uiState.update { it.copy(bannerMessage = null) }
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
    ) {
        val config = resolvedLauncherConfig() ?: run {
            updateConnectionState(ConnectionStatus.DISCONNECTED, null)
            if (!silent) {
                showMessage("Укажи launcher URL и access token.")
            }
            return
        }

        shouldMaintainConnection = true
        cancelReconnectLoop()
        updateConnectionState(
            status = if (reconnecting) ConnectionStatus.RECONNECTING else ConnectionStatus.CONNECTING,
            message = if (reconnecting) "Восстанавливаю подключение…" else "Проверяю launcher…",
        )

        try {
            AppDiagnostics.log("Connecting to ${config.url}")
            localStateRepository.saveLauncherConfig(config.url, config.token)
            val handshake = launcherClient.ensurePico(config.url, config.token)
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
            updateConnectionState(ConnectionStatus.CONNECTED, "Подключено к launcher")
        } catch (error: Throwable) {
            shouldMaintainConnection = false
            handleConnectionError(error, silent)
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
                val filteredMessages = if (event.requestId != null) {
                    _uiState.value.messages.filterNot { it.id == event.requestId }
                } else {
                    _uiState.value.messages
                }
                _uiState.update {
                    it.copy(
                        messages = filteredMessages,
                        isTyping = false,
                        composer = it.composer.copy(isSending = false),
                    )
                }
                persistSession(expectedSessionId, filteredMessages)
                showMessage(event.message.ifBlank { "Launcher вернул ошибку." })
            }

            is PicoEvent.SocketClosed -> {
                if (shouldMaintainConnection) {
                    scheduleReconnect(expectedSessionId, event.reason)
                } else {
                    updateConnectionState(ConnectionStatus.DISCONNECTED, event.reason)
                }
            }

            is PicoEvent.SocketFailure -> {
                if (shouldMaintainConnection) {
                    scheduleReconnect(expectedSessionId, event.reason)
                } else {
                    updateConnectionState(ConnectionStatus.FAILED_NETWORK, event.reason)
                }
            }

            PicoEvent.Pong -> Unit
        }
    }

    private fun scheduleReconnect(
        sessionId: String,
        reason: String,
    ) {
        val config = resolvedLauncherConfig() ?: return
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
            runCatching {
                val handshake = launcherClient.ensurePico(config.url, config.token)
                cachedHandshake = handshake
                openSocket(config.url, config.token, handshake, sessionId)
                reconnectAttempts = 0
                updateConnectionState(ConnectionStatus.CONNECTED, "Подключено к launcher")
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
    ) {
        if (error is CancellationException) {
            throw error
        }

        closeSocket()
        cachedHandshake = null

        val (status, message) = when (error) {
            is RuClawApiException -> {
                if (error.statusCode == 401) {
                    ConnectionStatus.FAILED_AUTH to "Launcher access token не подошёл."
                } else {
                    ConnectionStatus.FAILED_NETWORK to error.message
                }
            }

            else -> ConnectionStatus.FAILED_NETWORK to (error.message ?: "Не удалось подключиться к launcher.")
        }

        updateConnectionState(status, message)
        if (!silent) {
            showMessage(message)
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
        _uiState.update {
            it.copy(
                isLoaded = true,
                threads = persisted.threads,
                activeSessionId = persisted.activeSessionId,
                messages = messagesOverride ?: activeCached?.messages ?: emptyList(),
                updateState = persisted.updateState.toUiState(),
                diagnostics = AppDiagnostics.snapshot(),
            )
        }
    }

    private fun resolvedLauncherConfig(): LauncherConfigDraft? {
        val url = _uiState.value.launcherConfig.url.trim().ifBlank { BuildConfig.DEFAULT_LAUNCHER_URL }
        val token = _uiState.value.launcherConfig.token.trim()
        if (token.isBlank()) {
            return null
        }
        return LauncherConfigDraft(url = url, token = token)
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
            active = status in setOf(
                ConnectionStatus.CONNECTING,
                ConnectionStatus.CONNECTED,
                ConnectionStatus.RECONNECTING,
            ),
            title = "RuClaw Android",
            message = message ?: "Launcher подключён",
        )
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

    override fun onCleared() {
        shouldMaintainConnection = false
        cancelReconnectLoop()
        updateJob?.cancel()
        runBlocking {
            closeSocket()
        }
        SessionKeepAliveService.sync(getApplication(), false, "RuClaw Android", "")
        super.onCleared()
    }
}
