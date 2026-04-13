@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
)

package com.perdonus.ruclaw.android.ui

import android.Manifest
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.perdonus.ruclaw.android.core.model.ChatMessage
import com.perdonus.ruclaw.android.core.model.ChatRole
import com.perdonus.ruclaw.android.core.model.ChatThreadSummary
import com.perdonus.ruclaw.android.core.model.ComposerAttachment
import com.perdonus.ruclaw.android.core.model.ConnectionStatus
import com.perdonus.ruclaw.android.ui.components.MarkdownMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun MainScreen(viewModel: MainViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val feedbackScope = rememberCoroutineScope()
    val notificationsPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        viewModel.onNotificationPermissionResult(granted)
    }
    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) {
            viewModel.addComposerPhoto(uri)
        }
    }
    val ggufPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
            viewModel.onLocalModelPathChanged(uri.toString())
        }
    }

    LaunchedEffect(state.bannerMessage) {
        state.bannerMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.consumeBannerMessage()
        }
    }

    LaunchedEffect(state.pendingExternalUrl) {
        state.pendingExternalUrl?.let { url ->
            runCatching { uriHandler.openUri(url) }
            viewModel.consumePendingExternalUrl()
        }
    }

    LaunchedEffect(state.pendingSystemAction) {
        val action = state.pendingSystemAction ?: return@LaunchedEffect
        when (action.type) {
            PendingSystemActionType.OPEN_UNKNOWN_SOURCES_SETTINGS -> {
                val intent = Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:" + context.packageName),
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                runCatching { context.startActivity(intent) }
            }

            PendingSystemActionType.OPEN_ALL_FILES_ACCESS_SETTINGS -> {
                val opened = runCatching {
                    val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        Intent(
                            Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                            Uri.parse("package:" + context.packageName),
                        )
                    } else {
                        Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.parse("package:" + context.packageName),
                        )
                    }.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                }.isSuccess
                if (!opened && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    runCatching {
                        context.startActivity(
                            Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    }
                }
            }

            PendingSystemActionType.OPEN_APP_NOTIFICATION_SETTINGS -> {
                val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                    putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                    putExtra("app_package", context.packageName)
                    putExtra("app_uid", context.applicationInfo.uid)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                runCatching { context.startActivity(intent) }
            }

            PendingSystemActionType.REQUEST_POST_NOTIFICATIONS -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    notificationsPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    viewModel.onNotificationPermissionResult(true)
                }
            }

            PendingSystemActionType.INSTALL_APK -> {
                action.uri?.takeIf { it.isNotBlank() }?.let { targetUri ->
                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(Uri.parse(targetUri), "application/vnd.android.package-archive")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    runCatching { context.startActivity(intent) }
                }
            }
        }
        viewModel.consumePendingSystemAction()
    }

    if (!state.isLoaded) {
        LoadingShell()
        return
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF111823)),
    ) {
        ConnectedSurface(
            state = state,
            viewModel = viewModel,
            onPathClick = { path ->
                clipboardManager.setText(AnnotatedString(path))
                viewModel.announceDownloadPath(path)
            },
            onCopyMessage = { text ->
                clipboardManager.setText(AnnotatedString(text))
                feedbackScope.launch {
                    snackbarHostState.currentSnackbarData?.dismiss()
                    snackbarHostState.showSnackbar("Текст скопирован")
                }
            },
            onAddPhoto = {
                photoPicker.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                )
            },
            onToggleRuntime = viewModel::toggleRuntimePower,
            onRestartRuntime = viewModel::restartLocalRuntime,
            onOpenAgentSheet = { viewModel.toggleAgentSheet(true) },
            modifier = Modifier.fillMaxSize(),
        )

        if (
            state.localRuntime.installState == LocalRuntimeInstallState.INSTALLING &&
            !state.localRuntime.isInstalled
        ) {
            RuntimePreparationOverlay(
                logs = state.localRuntime.installLogs,
                modifier = Modifier.fillMaxSize(),
            )
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 12.dp),
        )
    }

    if (state.showSettings) {
        SettingsSheet(
            state = state,
            viewModel = viewModel,
        )
    }

    if (state.showAgentSheet) {
        AgentSheet(
            state = state,
            viewModel = viewModel,
            onNewSession = viewModel::newSession,
            onPickGguf = { ggufPicker.launch(arrayOf("*/*")) },
        )
    }
}

@Composable
private fun LoadingShell() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF111823)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            CircularProgressIndicator(color = Color(0xFF72D8C4))
            Text(
                text = "Поднимаю RuClaw Android…",
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }
}

@Composable
private fun RuntimePreparationOverlay(
    logs: List<String>,
    modifier: Modifier = Modifier,
) {
    val transition = rememberInfiniteTransition(label = "runtime-prep")
    val panelAlpha by transition.animateFloat(
        initialValue = 0.9f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1100, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "runtime-prep-alpha",
    )

    Box(
        modifier = modifier
            .background(Color(0xF1111823))
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(34.dp),
            color = Color(0xF0182230).copy(alpha = panelAlpha),
            shadowElevation = 28.dp,
            border = BorderStroke(1.dp, Color(0x24FFFFFF)),
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 560.dp),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 22.dp, vertical = 22.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(Color(0xFF243244)),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.4.dp,
                            color = Color(0xFF72D8C4),
                        )
                    }
                    Text(
                        text = "Готовим ресурсы",
                        style = MaterialTheme.typography.headlineSmall,
                        color = Color.White,
                    )
                }

                AnimatedVisibility(visible = logs.isNotEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(22.dp),
                        color = Color(0xCC0C1118),
                        border = BorderStroke(1.dp, Color(0x18FFFFFF)),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            logs.takeLast(5).forEach { line ->
                                Text(
                                    text = line,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFFD7DEE7),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ConnectedSurface(
    state: MainUiState,
    viewModel: MainViewModel,
    onPathClick: (String) -> Unit,
    onCopyMessage: (String) -> Unit,
    onAddPhoto: () -> Unit,
    onToggleRuntime: () -> Unit,
    onRestartRuntime: () -> Unit,
    onOpenAgentSheet: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(
        modifier = modifier.background(Color(0xFF111823)),
    ) {
        val wideLayout = maxWidth >= 980.dp
        if (wideLayout) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(14.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                SidebarPanel(
                    state = state,
                    viewModel = viewModel,
                    onThreadSelected = viewModel::selectSession,
                    modifier = Modifier
                        .width(318.dp)
                        .fillMaxHeight(),
                )
                ChatStage(
                    state = state,
                    viewModel = viewModel,
                    onPathClick = onPathClick,
                    onCopyMessage = onCopyMessage,
                    onAddPhoto = onAddPhoto,
                    onToggleRuntime = onToggleRuntime,
                    onRestartRuntime = onRestartRuntime,
                    onOpenDrawer = null,
                    onOpenAgentSheet = onOpenAgentSheet,
                    modifier = Modifier
                        .fillMaxHeight()
                        .weight(1f),
                )
            }
        } else {
            val drawerState = rememberDrawerState(DrawerValue.Closed)
            val scope = rememberCoroutineScope()

            ModalNavigationDrawer(
                drawerState = drawerState,
                drawerContent = {
                    ModalDrawerSheet(
                        modifier = Modifier.width(332.dp),
                        drawerContainerColor = Color.Transparent,
                    ) {
                        SidebarPanel(
                            state = state,
                            viewModel = viewModel,
                            onThreadSelected = { sessionId ->
                                scope.launch {
                                    drawerState.close()
                                }
                                viewModel.selectSession(sessionId)
                            },
                            modifier = Modifier
                                .fillMaxHeight()
                                .padding(horizontal = 12.dp, vertical = 12.dp),
                        )
                    }
                },
            ) {
                ChatStage(
                    state = state,
                    viewModel = viewModel,
                    onPathClick = onPathClick,
                    onCopyMessage = onCopyMessage,
                    onAddPhoto = onAddPhoto,
                    onToggleRuntime = onToggleRuntime,
                    onRestartRuntime = onRestartRuntime,
                    onOpenDrawer = { scope.launch { drawerState.open() } },
                    onOpenAgentSheet = onOpenAgentSheet,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

@Composable
private fun TopOverlayBar(
    connectionStatus: ConnectionStatus,
    installState: LocalRuntimeInstallState,
    onToggleRuntime: () -> Unit,
    onRestartRuntime: () -> Unit,
    onOpenDrawer: (() -> Unit)?,
    onOpenAgentSheet: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val runtimeBusy = installState == LocalRuntimeInstallState.INSTALLING ||
        connectionStatus in setOf(
            ConnectionStatus.CONNECTING,
            ConnectionStatus.RECONNECTING,
        )
    val runtimeOnline = connectionStatus == ConnectionStatus.CONNECTED
    val powerContainerColor = when {
        runtimeBusy -> Color(0xFF5B6675)
        runtimeOnline -> Color(0xFFFF707A)
        else -> Color(0xFF58D77E)
    }
    val powerContentColor = when {
        runtimeOnline || runtimeBusy -> Color.White
        else -> Color(0xFF07110B)
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(30.dp),
        color = Color(0xD3141C27),
        shadowElevation = 20.dp,
        border = BorderStroke(1.dp, Color(0x26FFFFFF)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onOpenDrawer != null) {
                FilledIconButton(
                    onClick = onOpenDrawer,
                    modifier = Modifier.size(52.dp),
                    shape = RoundedCornerShape(18.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = Color(0xCC1E2A38),
                        contentColor = Color.White,
                    ),
                ) {
                    Icon(Icons.Rounded.Menu, contentDescription = "Открыть меню")
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            if (runtimeOnline) {
                FilledIconButton(
                    onClick = onRestartRuntime,
                    modifier = Modifier.size(52.dp),
                    shape = RoundedCornerShape(18.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = Color(0xFFE0B54C),
                        contentColor = Color(0xFF181101),
                    ),
                ) {
                    Icon(Icons.Rounded.Refresh, contentDescription = "Перезапустить локальный runtime")
                }
            }

            FilledIconButton(
                onClick = onToggleRuntime,
                enabled = !runtimeBusy,
                modifier = Modifier.size(52.dp),
                shape = RoundedCornerShape(18.dp),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = powerContainerColor,
                    contentColor = powerContentColor,
                    disabledContainerColor = powerContainerColor,
                    disabledContentColor = powerContentColor,
                ),
            ) {
                if (runtimeBusy) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = powerContentColor,
                    )
                } else {
                    Icon(
                        imageVector = if (runtimeOnline) {
                            Icons.Rounded.PowerSettingsNew
                        } else {
                            Icons.Rounded.PlayArrow
                        },
                        contentDescription = if (runtimeOnline) {
                            "Остановить локальный runtime"
                        } else {
                            "Запустить локальный runtime"
                        },
                    )
                }
            }

            FilledIconButton(
                onClick = onOpenAgentSheet,
                modifier = Modifier.size(52.dp),
                shape = RoundedCornerShape(18.dp),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = Color(0xFF72D8C4),
                    contentColor = Color(0xFF091B18),
                ),
            ) {
                Icon(Icons.Rounded.Add, contentDescription = "Открыть действия")
            }
        }
    }
}

@Composable
private fun SidebarPanel(
    state: MainUiState,
    viewModel: MainViewModel,
    onThreadSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(32.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xD9141C27),
        ),
        border = BorderStroke(1.dp, Color(0x1FFFFFFF)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            FilledTonalButton(
                onClick = { viewModel.toggleSettings(true) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
            ) {
                Icon(Icons.Rounded.Settings, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Настройки")
            }

            Text(
                text = "Треды",
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
            )

            if (state.threads.isEmpty()) {
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = Color(0xAA1E2937),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = "Диалогов пока нет",
                            color = Color.White,
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            text = "Нажми + в шапке, чтобы начать новый локальный диалог.",
                            color = Color(0xFFB8C4D2),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
                Spacer(modifier = Modifier.weight(1f))
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(state.threads, key = { it.sessionId }) { thread ->
                        ThreadRow(
                            thread = thread,
                            isSelected = thread.sessionId == state.activeSessionId,
                            onClick = { onThreadSelected(thread.sessionId) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ThreadRow(
    thread: ChatThreadSummary,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = if (isSelected) Color(0x334FAE9D) else Color(0xAA1D2938),
        border = BorderStroke(
            width = if (isSelected) 1.5.dp else 1.dp,
            color = if (isSelected) Color(0xFF72D8C4) else Color(0x18FFFFFF),
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = thread.title.ifBlank { "Новый диалог" },
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = thread.preview.ifBlank { "Ещё без сообщений" },
                color = Color(0xFFB8C4D2),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ChatStage(
    state: MainUiState,
    viewModel: MainViewModel,
    onPathClick: (String) -> Unit,
    onCopyMessage: (String) -> Unit,
    onAddPhoto: () -> Unit,
    onToggleRuntime: () -> Unit,
    onRestartRuntime: () -> Unit,
    onOpenDrawer: (() -> Unit)?,
    onOpenAgentSheet: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val topPadding = 112.dp + WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottomPadding = 186.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    LaunchedEffect(state.activeSessionId, state.messages.size, state.isTyping) {
        val extra = if (state.isTyping) 1 else 0
        val target = state.messages.lastIndex + extra
        if (target >= 0) {
            listState.animateScrollToItem(target)
        }
    }

    Box(
        modifier = modifier.background(Color(0xFF111823)),
    ) {
        if (state.messages.isNotEmpty() || state.isHistoryLoading) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = topPadding,
                    bottom = bottomPadding,
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(state.messages, key = { it.id }) { message ->
                    MessageBubble(
                        message = message,
                        onPathClick = onPathClick,
                        onExternalLink = viewModel::openExternalUrl,
                        onCopyMessage = onCopyMessage,
                    )
                }
                if (state.isTyping) {
                    item(key = "typing") {
                        TypingBubble()
                    }
                }
            }
        } else {
            EmptyStage(
                state = state,
                viewModel = viewModel,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = topPadding, bottom = bottomPadding),
            )
        }

        AnimatedVisibility(
            visible = state.isHistoryLoading,
            modifier = Modifier.align(Alignment.Center),
        ) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color(0xD91A2531),
                border = BorderStroke(1.dp, Color(0x1FFFFFFF)),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = Color(0xFF72D8C4),
                    )
                    Text(
                        text = "Подгружаю историю…",
                        color = Color.White,
                    )
                }
            }
        }

        OverlayFade(
            top = true,
            modifier = Modifier.align(Alignment.TopCenter),
        )
        OverlayFade(
            top = false,
            modifier = Modifier.align(Alignment.BottomCenter),
        )

        TopOverlayBar(
            connectionStatus = state.connectionState.status,
            installState = state.localRuntime.installState,
            onToggleRuntime = onToggleRuntime,
            onRestartRuntime = onRestartRuntime,
            onOpenDrawer = onOpenDrawer,
            onOpenAgentSheet = onOpenAgentSheet,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
        )

        ComposerDock(
            state = state,
            onValueChange = viewModel::onComposerChanged,
            onRemoveAttachment = viewModel::removeComposerAttachment,
            onAddPhoto = onAddPhoto,
            onSend = viewModel::sendMessage,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .imePadding()
                .navigationBarsPadding()
                .padding(horizontal = 12.dp, vertical = 10.dp),
        )
    }
}

@Composable
private fun OverlayFade(
    top: Boolean,
    modifier: Modifier = Modifier,
) {
    val insetPadding = if (top) {
        WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    } else {
        WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height((if (top) 188.dp else 220.dp) + insetPadding)
            .background(
                Brush.verticalGradient(
                    colors = if (top) {
                        listOf(
                            Color(0xFF111823),
                            Color(0xE6111823),
                            Color(0x8C111823),
                            Color.Transparent,
                        )
                    } else {
                        listOf(
                            Color.Transparent,
                            Color(0x66111823),
                            Color(0xD9111823),
                            Color(0xFF111823),
                        )
                    },
                ),
            ),
    )
}

@Composable
private fun MessageBubble(
    message: ChatMessage,
    onPathClick: (String) -> Unit,
    onExternalLink: (String) -> Unit,
    onCopyMessage: (String) -> Unit,
) {
    val isUser = message.role == ChatRole.USER
    val assistantTextColor = Color(0xFFF4F7FB)

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
    ) {
        Surface(
            shape = RoundedCornerShape(
                topStart = 24.dp,
                topEnd = 24.dp,
                bottomEnd = if (isUser) 10.dp else 24.dp,
                bottomStart = if (isUser) 24.dp else 10.dp,
            ),
            color = if (isUser) Color(0xFF213042) else Color(0xD9182230),
            contentColor = if (isUser) Color.White else assistantTextColor,
            border = BorderStroke(1.dp, if (isUser) Color(0x1FFFFFFF) else Color(0x18FFFFFF)),
            modifier = Modifier.widthIn(max = 760.dp),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (message.text.isNotBlank()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        FilledIconButton(
                            onClick = { onCopyMessage(message.text) },
                            modifier = Modifier.size(34.dp),
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = if (isUser) Color(0xFF32485F) else Color(0xFF243244),
                                contentColor = Color.White,
                            ),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.ContentCopy,
                                contentDescription = "Скопировать сообщение",
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }

                    if (isUser) {
                        SelectionContainer {
                            Text(
                                text = message.text,
                                style = MaterialTheme.typography.bodyLarge,
                                color = Color.White,
                            )
                        }
                    } else {
                        MarkdownMessage(
                            text = message.text,
                            onDownloadLinkClick = onPathClick,
                            onExternalLinkClick = onExternalLink,
                            textColor = assistantTextColor,
                        )
                    }
                }

                if (message.attachments.isNotEmpty()) {
                    AttachmentGallery(
                        attachments = message.attachments,
                        onPathClick = onPathClick,
                    )
                }
            }
        }
    }
}

@Composable
private fun AttachmentGallery(
    attachments: List<String>,
    onPathClick: (String) -> Unit,
) {
    val imageAttachments = attachments.filter { it.startsWith("data:image/") }
    val otherAttachments = attachments.filterNot { it.startsWith("data:image/") }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (imageAttachments.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                imageAttachments.take(3).forEach { dataUrl ->
                    DataImageTile(
                        dataUrl = dataUrl,
                        modifier = Modifier.size(96.dp),
                    )
                }
            }
        }

        if (otherAttachments.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                otherAttachments.take(3).forEach { attachment ->
                    AssistChip(
                        onClick = { onPathClick(attachment) },
                        label = {
                            Text(
                                text = attachment.takeLast(28),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun DataImageTile(
    dataUrl: String,
    modifier: Modifier = Modifier,
    onRemove: (() -> Unit)? = null,
) {
    val bitmap by produceState<androidx.compose.ui.graphics.ImageBitmap?>(
        initialValue = null,
        key1 = dataUrl,
    ) {
        value = withContext(Dispatchers.Default) {
            decodeDataImage(dataUrl)
        }
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0xFF1E2937))
            .border(BorderStroke(1.dp, Color(0x1FFFFFFF)), RoundedCornerShape(18.dp)),
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap!!,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Link,
                    contentDescription = null,
                    tint = Color.White,
                )
            }
        }

        if (onRemove != null) {
            FilledIconButton(
                onClick = onRemove,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(28.dp),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = Color(0xD90B1017),
                    contentColor = Color.White,
                ),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = "Удалить фото",
                    modifier = Modifier.size(14.dp),
                )
            }
        }
    }
}

private fun decodeDataImage(dataUrl: String) = runCatching {
    if (!dataUrl.startsWith("data:image/")) {
        null
    } else {
        val base64Part = dataUrl.substringAfter("base64,", "")
        if (base64Part.isBlank()) {
            null
        } else {
            val bytes = Base64.decode(base64Part, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
        }
    }
}.getOrNull()

@Composable
private fun TypingBubble() {
    val transition = rememberInfiniteTransition(label = "typing")
    val first by transition.animateFloat(
        initialValue = 0.2f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "typing-1",
    )
    val second by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "typing-2",
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start,
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = Color(0xD9182230),
            border = BorderStroke(1.dp, Color(0x18FFFFFF)),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Dot(alpha = first)
                Dot(alpha = second)
                Dot(alpha = first)
                Text(
                    text = "Печатает…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFFB8C4D2),
                )
            }
        }
    }
}

@Composable
private fun Dot(alpha: Float) {
    Box(
        modifier = Modifier
            .size(8.dp)
            .alpha(alpha)
            .background(Color(0xFF72D8C4), CircleShape),
    )
}

@Composable
private fun ComposerDock(
    state: MainUiState,
    onValueChange: (String) -> Unit,
    onRemoveAttachment: (Int) -> Unit,
    onAddPhoto: () -> Unit,
    onSend: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(32.dp),
        color = Color(0xD9141C27),
        shadowElevation = 20.dp,
        border = BorderStroke(1.dp, Color(0x22FFFFFF)),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (state.composer.attachments.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.composer.attachments.mapIndexed { index, attachment ->
                        ComposerAttachmentPreview(
                            attachment = attachment,
                            onRemove = { onRemoveAttachment(index) },
                        )
                    }
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                FilledIconButton(
                    onClick = onAddPhoto,
                    enabled = !state.composer.isSending,
                    modifier = Modifier.size(52.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = Color(0xCC1E2A38),
                        contentColor = Color.White,
                    ),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = "Добавить фото",
                    )
                }

                OutlinedTextField(
                    value = state.composer.text,
                    onValueChange = onValueChange,
                    modifier = Modifier.weight(1f),
                    enabled = !state.composer.isSending,
                    minLines = 1,
                    maxLines = 6,
                    placeholder = {
                        Text("Напишите задачу")
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = {
                        if (!state.composer.isSending) {
                            onSend()
                        }
                    }),
                    shape = RoundedCornerShape(24.dp),
                    colors = composerFieldColors(),
                )

                FilledIconButton(
                    onClick = onSend,
                    enabled = !state.composer.isSending &&
                        (state.composer.text.isNotBlank() || state.composer.attachments.isNotEmpty()),
                    modifier = Modifier.size(52.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = Color(0xFF72D8C4),
                        contentColor = Color(0xFF091B18),
                        disabledContainerColor = Color(0x553B4C5F),
                        disabledContentColor = Color(0x99C9D0D7),
                    ),
                ) {
                    if (state.composer.isSending) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = Color(0xFF091B18),
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.Send,
                            contentDescription = "Отправить",
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ComposerAttachmentPreview(
    attachment: ComposerAttachment,
    onRemove: () -> Unit,
) {
    DataImageTile(
        dataUrl = attachment.url,
        modifier = Modifier.size(76.dp),
        onRemove = onRemove,
    )
}

@Composable
private fun EmptyStage(
    state: MainUiState,
    viewModel: MainViewModel,
    modifier: Modifier = Modifier,
) {
    val prompts = listOf(
        "Проверь локальный поиск и web-инструменты",
        "Добавь GGUF-модель в локальный каталог",
        "Настрой Telegram-бота прямо на телефоне",
    )

    Column(
        modifier = modifier
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = when {
                state.connectionState.status == ConnectionStatus.CONNECTED -> "RuClaw запущен"
                state.localRuntime.installState == LocalRuntimeInstallState.INSTALLING -> "Поднимаю RuClaw"
                else -> "Локальный RuClaw"
            },
            style = MaterialTheme.typography.headlineSmall,
            color = Color.White,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(16.dp))
        AssistChip(
            onClick = {
                when {
                    state.connectionState.status == ConnectionStatus.CONNECTED -> viewModel.toggleSettings(true)
                    state.localRuntime.installState == LocalRuntimeInstallState.INSTALLING -> Unit
                    else -> viewModel.toggleRuntimePower()
                }
            },
            label = {
                Text(
                    text = when {
                        state.connectionState.status == ConnectionStatus.CONNECTED -> "Локальный runtime онлайн"
                        state.localRuntime.installState == LocalRuntimeInstallState.INSTALLING -> "Готовим локальный runtime"
                        state.localRuntime.installState == LocalRuntimeInstallState.FAILED -> "Подготовка сорвалась, нажми чтобы повторить"
                        state.localRuntime.isInstalled -> "Нажми зелёную кнопку сверху"
                        else -> "Ресурсы подготовятся автоматически"
                    },
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Rounded.Link,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
            },
        )
        Spacer(modifier = Modifier.height(22.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            prompts.forEach { prompt ->
                AssistChip(
                    onClick = { viewModel.applySuggestion(prompt) },
                    label = {
                        Text(
                            text = prompt,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Rounded.Link,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun SettingsSheet(
    state: MainUiState,
    viewModel: MainViewModel,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = { viewModel.toggleSettings(false) },
        sheetState = sheetState,
        containerColor = Color(0xFF111823),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "Настройки",
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
            )

            LocalRuntimeSection(
                localRuntime = state.localRuntime,
                connectionStatus = state.connectionState.status,
                hasNotificationPermission = state.hasNotificationPermission,
                onKeepAliveChanged = viewModel::onLocalKeepAliveChanged,
                onRequestNotificationPermission = viewModel::requestNotificationsPermission,
                onTelegramEnabledChanged = viewModel::onTelegramEnabledChanged,
                onTelegramBotTokenChanged = viewModel::onTelegramBotTokenChanged,
                onTelegramAllowedUsersChanged = viewModel::onTelegramAllowedUsersChanged,
                onTelegramMarkdownV2Changed = viewModel::onTelegramMarkdownV2Changed,
            )

            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
private fun LocalRuntimeSection(
    localRuntime: LocalRuntimeUiState,
    connectionStatus: ConnectionStatus,
    hasNotificationPermission: Boolean,
    onKeepAliveChanged: (Boolean) -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onTelegramEnabledChanged: (Boolean) -> Unit,
    onTelegramBotTokenChanged: (String) -> Unit,
    onTelegramAllowedUsersChanged: (String) -> Unit,
    onTelegramMarkdownV2Changed: (Boolean) -> Unit,
) {
    val installBusy = localRuntime.installState == LocalRuntimeInstallState.INSTALLING
    val launcherConnecting = connectionStatus in setOf(
        ConnectionStatus.CONNECTING,
        ConnectionStatus.RECONNECTING,
    )
    val launcherLive = connectionStatus == ConnectionStatus.CONNECTED
    val statusText = when {
        installBusy -> "Подготовка"
        launcherLive -> "Онлайн"
        launcherConnecting -> "Запуск"
        localRuntime.isInstalled -> "Оффлайн"
        else -> "Первый запуск"
    }
    val statusColor = when {
        launcherLive -> Color(0xFFFF7680)
        installBusy || launcherConnecting -> Color(0xFFB6C1CF)
        else -> Color(0xFF72D8C4)
    }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Surface(
            shape = RoundedCornerShape(26.dp),
            color = Color(0xCC182231),
            border = BorderStroke(1.dp, Color(0x18FFFFFF)),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Локальный runtime",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                    )
                    Surface(
                        shape = CircleShape,
                        color = statusColor.copy(alpha = 0.18f),
                        border = BorderStroke(1.dp, statusColor.copy(alpha = 0.34f)),
                    ) {
                        Text(
                            text = statusText,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.labelLarge,
                            color = statusColor,
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AssistChip(
                        onClick = {},
                        label = { Text(if (localRuntime.launcherEnabled) "Автостарт: да" else "Автостарт: нет") },
                    )
                    AssistChip(
                        onClick = {},
                        label = { Text(if (localRuntime.keepAliveEnabled) "Keep-alive" else "Без keep-alive") },
                    )
                }

                if (localRuntime.telegramEnabled) {
                    AssistChip(
                        onClick = {},
                        label = { Text("Telegram") },
                    )
                }

                if (localRuntime.runtimeVersion.isNotBlank()) {
                    Text(
                        text = "Версия " + localRuntime.runtimeVersion,
                        style = MaterialTheme.typography.labelMedium,
                        color = Color(0xFFB8C4D2),
                    )
                }

                Text(
                    text = "Если runtime был включён перед закрытием прилы, при следующем запуске он поднимется сам.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFD7E0EA),
                )

                if (
                    localRuntime.installState == LocalRuntimeInstallState.FAILED &&
                    localRuntime.installLogs.isNotEmpty()
                ) {
                    Text(
                        text = localRuntime.installLogs.last(),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFFFB4B9),
                    )
                }
            }
        }

        Surface(
            shape = RoundedCornerShape(24.dp),
            color = Color(0xCC182231),
            border = BorderStroke(1.dp, Color(0x18FFFFFF)),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Keep-alive",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                    )
                    Switch(
                        checked = localRuntime.keepAliveEnabled,
                        onCheckedChange = onKeepAliveChanged,
                    )
                }
                if (!hasNotificationPermission) {
                    FilledTonalButton(
                        onClick = onRequestNotificationPermission,
                        shape = RoundedCornerShape(18.dp),
                    ) {
                        Text("Разрешить уведомления")
                    }
                }
            }
        }

        Surface(
            shape = RoundedCornerShape(24.dp),
            color = Color(0xCC182231),
            border = BorderStroke(1.dp, Color(0x18FFFFFF)),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Telegram бот",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                    )
                    Switch(
                        checked = localRuntime.telegramEnabled,
                        onCheckedChange = onTelegramEnabledChanged,
                    )
                }
                if (localRuntime.telegramEnabled) {
                    OutlinedTextField(
                        value = localRuntime.telegramBotToken,
                        onValueChange = onTelegramBotTokenChanged,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Токен бота") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        colors = sheetFieldColors(),
                    )
                    OutlinedTextField(
                        value = localRuntime.telegramAllowedUsers,
                        onValueChange = onTelegramAllowedUsersChanged,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Кому можно") },
                        placeholder = { Text("12345, 67890 или *") },
                        colors = sheetFieldColors(),
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "MarkdownV2",
                            style = MaterialTheme.typography.bodyLarge,
                            color = Color.White,
                        )
                        Switch(
                            checked = localRuntime.telegramUseMarkdownV2,
                            onCheckedChange = onTelegramMarkdownV2Changed,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun UpdateSection(
    updateState: UpdateUiState,
    onCheckUpdates: () -> Unit,
    onDownloadUpdate: () -> Unit,
    onInstallUpdate: () -> Unit,
    onOpenRelease: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = "Обновления",
            style = MaterialTheme.typography.titleMedium,
            color = Color.White,
        )
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = Color(0xCC182231),
            border = BorderStroke(1.dp, Color(0x18FFFFFF)),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = "Текущая версия: " + updateState.currentVersionName.ifBlank { "dev" },
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White,
                )
                Text(
                    text = if (updateState.latestVersionName.isBlank()) {
                        if (updateState.lastCheckedAtEpochMillis > 0L) {
                            "Релиз GitHub пока не опубликован"
                        } else {
                            "Релиз GitHub ещё не запрашивался"
                        }
                    } else {
                        "Релиз GitHub: " + updateState.latestVersionName
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFFB8C4D2),
                )
                Text(
                    text = updateStatusText(updateState),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White,
                )
                updateState.releaseNotes.takeIf { it.isNotBlank() }?.let { notes ->
                    Text(
                        text = notes.take(240),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFFB8C4D2),
                        maxLines = 6,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FilledTonalButton(
                        onClick = onCheckUpdates,
                        enabled = !updateState.isChecking,
                    ) {
                        Text(if (updateState.isChecking) "Проверяю…" else "Проверить")
                    }
                    if (updateState.isUpdateAvailable && updateState.downloadState != UpdateDownloadState.READY_TO_INSTALL) {
                        FilledTonalButton(onClick = onDownloadUpdate) {
                            Text("Скачать APK")
                        }
                    }
                    if (updateState.downloadState == UpdateDownloadState.READY_TO_INSTALL) {
                        FilledTonalButton(onClick = onInstallUpdate) {
                            Text("Установить")
                        }
                    }
                    if (updateState.releaseUrl.isNotBlank()) {
                        TextButton(onClick = onOpenRelease) {
                            Text("Релиз")
                        }
                    }
                }
            }
        }
    }
}

private fun updateStatusText(updateState: UpdateUiState): String {
    return when {
        updateState.isChecking -> "Смотрю релиз GitHub…"
        updateState.downloadState == UpdateDownloadState.DOWNLOADING && updateState.downloadProgressPercent != null ->
            "APK качается: " + updateState.downloadProgressPercent + "%"
        updateState.downloadState == UpdateDownloadState.DOWNLOADING ->
            "APK качается через DownloadManager."
        updateState.downloadState == UpdateDownloadState.READY_TO_INSTALL && updateState.canInstallPackages ->
            "APK уже скачан. Можно ставить поверх текущей версии."
        updateState.downloadState == UpdateDownloadState.READY_TO_INSTALL ->
            "APK скачан. Разреши установку из этого источника и жми ещё раз."
        updateState.downloadState == UpdateDownloadState.FAILED ->
            "Предыдущая загрузка сорвалась. Запусти скачивание заново."
        updateState.isUpdateAvailable ->
            "Есть свежая версия " + updateState.latestVersionName + "."
        updateState.latestVersionName.isNotBlank() ->
            "У тебя уже актуальная версия."
        updateState.lastCheckedAtEpochMillis > 0L ->
            "Публичный релиз на GitHub пока не опубликован."
        else -> "Пока не проверял релизный канал."
    }
}

@Composable
private fun composerFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = Color.White,
    unfocusedTextColor = Color.White,
    disabledTextColor = Color(0xFFB8C4D2),
    cursorColor = Color(0xFF72D8C4),
    focusedBorderColor = Color(0xFF72D8C4),
    unfocusedBorderColor = Color(0x33FFFFFF),
    disabledBorderColor = Color(0x14FFFFFF),
    focusedContainerColor = Color.Transparent,
    unfocusedContainerColor = Color.Transparent,
    disabledContainerColor = Color.Transparent,
    focusedPlaceholderColor = Color(0x88FFFFFF),
    unfocusedPlaceholderColor = Color(0x66FFFFFF),
    disabledPlaceholderColor = Color(0x44FFFFFF),
)

@Composable
private fun sheetFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = Color.White,
    unfocusedTextColor = Color.White,
    disabledTextColor = Color(0xFFB8C4D2),
    cursorColor = Color(0xFF72D8C4),
    focusedBorderColor = Color(0xFF72D8C4),
    unfocusedBorderColor = Color(0x33FFFFFF),
    focusedContainerColor = Color.Transparent,
    unfocusedContainerColor = Color.Transparent,
    disabledContainerColor = Color.Transparent,
    focusedLabelColor = Color(0xFF72D8C4),
    unfocusedLabelColor = Color(0xFFB8C4D2),
    focusedPlaceholderColor = Color(0x88FFFFFF),
    unfocusedPlaceholderColor = Color(0x66FFFFFF),
)
