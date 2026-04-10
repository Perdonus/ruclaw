@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
)

package com.perdonus.ruclaw.android.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Cable
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
import com.perdonus.ruclaw.android.core.model.ConnectionStatus
import com.perdonus.ruclaw.android.ui.components.MarkdownMessage
import kotlinx.coroutines.launch

@Composable
fun MainScreen(viewModel: MainViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current

    LaunchedEffect(state.bannerMessage) {
        state.bannerMessage?.let {
            snackbarHostState.showSnackbar(it)
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
        val intent = when (action.type) {
            PendingSystemActionType.OPEN_UNKNOWN_SOURCES_SETTINGS -> {
                Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${context.packageName}"),
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            PendingSystemActionType.INSTALL_APK -> {
                val targetUri = action.uri?.takeIf { it.isNotBlank() } ?: return@LaunchedEffect
                Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(Uri.parse(targetUri), "application/vnd.android.package-archive")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            }
        }
        runCatching { context.startActivity(intent) }
        viewModel.consumePendingSystemAction()
    }

    if (!state.isLoaded) {
        LoadingShell()
        return
    }

    val showWelcomeGate = !state.hasConfiguredLauncher && state.threads.isEmpty() && state.messages.isEmpty()

    Box(modifier = Modifier.fillMaxSize()) {
        AnimatedBackdrop(modifier = Modifier.fillMaxSize())
        StageScrims()

        if (showWelcomeGate) {
            WelcomeGate(
                state = state,
                viewModel = viewModel,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            ConnectedSurface(
                state = state,
                viewModel = viewModel,
                onPathClick = { path ->
                    clipboardManager.setText(AnnotatedString(path))
                    viewModel.announceDownloadPath(path)
                },
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
        SettingsSheet(state = state, viewModel = viewModel)
    }
}

@Composable
private fun LoadingShell() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0E1117)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            CircularProgressIndicator()
            Text(
                text = "Поднимаю RuClaw Android…",
                color = Color(0xFFF6EFE4),
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }
}

@Composable
private fun ConnectedSurface(
    state: MainUiState,
    viewModel: MainViewModel,
    onPathClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier) {
        val wideLayout = maxWidth >= 980.dp
        if (wideLayout) {
            Column(modifier = Modifier.fillMaxSize()) {
                TopBar(
                    state = state,
                    onOpenDrawer = null,
                    onNewSession = viewModel::newSession,
                    onRefresh = viewModel::refreshThreads,
                    onToggleSettings = { viewModel.toggleSettings(true) },
                )
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 18.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    SidebarPanel(
                        state = state,
                        viewModel = viewModel,
                        modifier = Modifier
                            .width(340.dp)
                            .fillMaxHeight(),
                    )
                    ChatStage(
                        state = state,
                        viewModel = viewModel,
                        onPathClick = onPathClick,
                        modifier = Modifier
                            .fillMaxHeight()
                            .weight(1f),
                    )
                }
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
                            modifier = Modifier
                                .fillMaxHeight()
                                .padding(start = 12.dp, top = 10.dp, end = 12.dp, bottom = 12.dp),
                        )
                    }
                },
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    TopBar(
                        state = state,
                        onOpenDrawer = { scope.launch { drawerState.open() } },
                        onNewSession = viewModel::newSession,
                        onRefresh = viewModel::refreshThreads,
                        onToggleSettings = { viewModel.toggleSettings(true) },
                    )
                    ChatStage(
                        state = state,
                        viewModel = viewModel,
                        onPathClick = onPathClick,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 10.dp, vertical = 10.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun TopBar(
    state: MainUiState,
    onOpenDrawer: (() -> Unit)?,
    onNewSession: () -> Unit,
    onRefresh: () -> Unit,
    onToggleSettings: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
        tonalElevation = 3.dp,
        shadowElevation = 10.dp,
        modifier = Modifier.fillMaxWidth(),
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
                FilledIconButton(onClick = onOpenDrawer) {
                    Icon(Icons.Rounded.Menu, contentDescription = "Threads")
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "RuClaw Android",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Black,
                )
                Text(
                    text = state.activeThread?.title?.ifBlank { "Новый диалог" } ?: "Launcher chat",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            ConnectionChip(status = state.connectionState.status)
            IconButton(onClick = onNewSession) {
                Icon(Icons.Rounded.Add, contentDescription = "New session")
            }
            IconButton(onClick = onRefresh) {
                Icon(Icons.Rounded.Refresh, contentDescription = "Refresh")
            }
            IconButton(onClick = onToggleSettings) {
                Icon(Icons.Rounded.Settings, contentDescription = "Settings")
            }
        }
    }
}

@Composable
private fun SidebarPanel(
    state: MainUiState,
    viewModel: MainViewModel,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.93f),
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ConnectionOverview(state = state, viewModel = viewModel)
            HorizontalDivider()
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Треды",
                    style = MaterialTheme.typography.titleMedium,
                )
                FilledTonalButton(onClick = viewModel::newSession) {
                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Новый")
                }
            }

            if (state.threads.isEmpty()) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    shape = RoundedCornerShape(22.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = "Пока пусто",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            text = "Создай новый диалог или подключись к launcher, чтобы подтянуть историю.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(state.threads, key = { it.sessionId }) { thread ->
                        ThreadRow(
                            thread = thread,
                            isSelected = thread.sessionId == state.activeSessionId,
                            onClick = { viewModel.selectSession(thread.sessionId) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ConnectionOverview(
    state: MainUiState,
    viewModel: MainViewModel,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                    shape = CircleShape,
                    modifier = Modifier.size(42.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.Cable,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                Column {
                    Text(
                        text = "Launcher",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = state.launcherConfig.url.ifBlank { "URL не задан" },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Text(
                text = statusText(state.connectionState.status),
                style = MaterialTheme.typography.bodyLarge,
            )
            state.connectionState.message?.takeIf { it.isNotBlank() }?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ElevatedButton(onClick = viewModel::connectLauncher) {
                    Text("Подключить")
                }
                OutlinedButton(onClick = viewModel::disconnectLauncher) {
                    Text("Отключить")
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
    val shape = RoundedCornerShape(22.dp)
    Surface(
        shape = shape,
        color = if (isSelected) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.76f)
        },
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = thread.title.ifBlank { "Новый диалог" },
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (thread.isLocalOnly) {
                    AssistChip(
                        onClick = { },
                        enabled = false,
                        label = { Text("local") },
                    )
                }
            }
            Text(
                text = thread.preview.ifBlank { "Ещё без сообщений" },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()

    LaunchedEffect(state.activeSessionId, state.messages.size, state.isTyping) {
        val extra = if (state.isTyping) 1 else 0
        val target = state.messages.lastIndex + extra
        if (target >= 0) {
            listState.animateScrollToItem(target)
        }
    }

    Card(
        modifier = modifier,
        shape = RoundedCornerShape(30.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                if (state.messages.isEmpty() && !state.isHistoryLoading) {
                    EmptyStage(viewModel = viewModel, modifier = Modifier.fillMaxSize())
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(state.messages, key = { it.id }) { message ->
                            MessageBubble(
                                message = message,
                                onPathClick = onPathClick,
                                onExternalLink = viewModel::openExternalUrl,
                            )
                        }
                        if (state.isTyping) {
                            item(key = "typing") {
                                TypingBubble()
                            }
                        }
                    }
                }

                androidx.compose.animation.AnimatedVisibility(
                    visible = state.isHistoryLoading,
                    modifier = Modifier.align(Alignment.Center),
                ) {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.92f),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            Text("Подгружаю историю…")
                        }
                    }
                }
            }

            ComposerDock(
                state = state,
                onValueChange = viewModel::onComposerChanged,
                onSend = viewModel::sendMessage,
                modifier = Modifier
                    .fillMaxWidth()
                    .imePadding()
                    .navigationBarsPadding(),
            )
        }
    }
}

@Composable
private fun MessageBubble(
    message: ChatMessage,
    onPathClick: (String) -> Unit,
    onExternalLink: (String) -> Unit,
) {
    val isUser = message.role == ChatRole.USER
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
    ) {
        Surface(
            shape = RoundedCornerShape(
                topStart = 24.dp,
                topEnd = 24.dp,
                bottomEnd = if (isUser) 8.dp else 24.dp,
                bottomStart = if (isUser) 24.dp else 8.dp,
            ),
            color = if (isUser) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
            } else {
                MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.92f)
            },
            modifier = Modifier.widthIn(max = 720.dp),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (isUser) {
                    Text(
                        text = message.text,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                } else {
                    MarkdownMessage(
                        text = message.text,
                        onDownloadLinkClick = onPathClick,
                        onExternalLinkClick = onExternalLink,
                    )
                }

                if (message.attachments.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        message.attachments.take(3).forEach { attachment ->
                            AssistChip(
                                onClick = { onPathClick(attachment) },
                                label = {
                                    Text(
                                        text = attachment.takeLast(32),
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
    }
}

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
            color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.92f),
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
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
            .background(MaterialTheme.colorScheme.primary, CircleShape),
    )
}

@Composable
private fun ComposerDock(
    state: MainUiState,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.98f),
        tonalElevation = 3.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            OutlinedTextField(
                value = state.composer.text,
                onValueChange = onValueChange,
                modifier = Modifier.weight(1f),
                minLines = 1,
                maxLines = 6,
                label = { Text("Напиши задачу для RuClaw") },
                placeholder = { Text("Например: переведи этот экран на русский и собери APK") },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { onSend() }),
            )
            FilledIconButton(
                onClick = onSend,
                enabled = state.composer.text.isNotBlank(),
                modifier = Modifier.size(56.dp),
            ) {
                Icon(
                    imageVector = if (state.composer.isSending) Icons.Rounded.Stop else Icons.Rounded.Send,
                    contentDescription = "Send",
                )
            }
        }
    }
}

@Composable
private fun EmptyStage(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier,
) {
    val prompts = listOf(
        "Сделай Android-интерфейс под launcher",
        "Переведи этот проект на русский целиком",
        "Собери APK в GitHub Actions",
    )
    Column(
        modifier = modifier.padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "Пустой тред",
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = "Задай первую задачу или начни с готового промпта.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(20.dp))
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
                        Icon(Icons.Rounded.Link, contentDescription = null, modifier = Modifier.size(16.dp))
                    },
                )
            }
        }
    }
}

@Composable
private fun WelcomeGate(
    state: MainUiState,
    viewModel: MainViewModel,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 22.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "RuClaw Android",
                style = MaterialTheme.typography.headlineLarge,
                textAlign = TextAlign.Center,
            )
            Text(
                text = "Нативный GPT-style клиент к твоему launcher по локальной сети.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.84f),
                textAlign = TextAlign.Center,
            )
        }

        Spacer(modifier = Modifier.height(22.dp))

        Card(
            shape = RoundedCornerShape(34.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
            ),
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 560.dp),
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = "Подключение",
                    style = MaterialTheme.typography.titleLarge,
                )
                Text(
                    text = "По умолчанию выставлен LAN launcher. Токен не храню в коде, его вводишь тут.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = state.launcherConfig.url,
                    onValueChange = viewModel::onLauncherUrlChanged,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Launcher URL") },
                    placeholder = { Text("http://192.168.1.109:18800") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = state.launcherConfig.token,
                    onValueChange = viewModel::onLauncherTokenChanged,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Launcher access token") },
                    placeholder = { Text("Вставь access token") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ElevatedButton(onClick = viewModel::connectLauncher) {
                        Text("Подключиться")
                    }
                    ConnectionChip(status = state.connectionState.status)
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FeatureChip(label = "WS streaming")
                    FeatureChip(label = "История сессий")
                    FeatureChip(label = "Markdown + code")
                }
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
        containerColor = MaterialTheme.colorScheme.surface,
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
                text = "Настройки launcher",
                style = MaterialTheme.typography.titleLarge,
            )
            OutlinedTextField(
                value = state.launcherConfig.url,
                onValueChange = viewModel::onLauncherUrlChanged,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Launcher URL") },
                singleLine = true,
            )
            OutlinedTextField(
                value = state.launcherConfig.token,
                onValueChange = viewModel::onLauncherTokenChanged,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Launcher access token") },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ElevatedButton(onClick = viewModel::connectLauncher) {
                    Text("Применить")
                }
                OutlinedButton(onClick = viewModel::disconnectLauncher) {
                    Text("Отключить")
                }
                TextButton(onClick = { viewModel.toggleSettings(false) }) {
                    Text("Закрыть")
                }
            }

            HorizontalDivider()

            UpdateSection(
                updateState = state.updateState,
                onCheckUpdates = { viewModel.checkForUpdates() },
                onDownloadUpdate = viewModel::downloadUpdate,
                onInstallUpdate = viewModel::installDownloadedUpdate,
                onOpenRelease = {
                    state.updateState.releaseUrl
                        .takeIf { it.isNotBlank() }
                        ?.let(viewModel::openExternalUrl)
                },
            )

            if (state.diagnostics.isNotEmpty()) {
                HorizontalDivider()
                Text(
                    text = "Диагностика",
                    style = MaterialTheme.typography.titleMedium,
                )
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        state.diagnostics.takeLast(8).forEach { line ->
                            Text(
                                text = line,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
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
        )
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = "Текущая версия: ${updateState.currentVersionName.ifBlank { "dev" }}",
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    text = if (updateState.latestVersionName.isBlank()) {
                        "Latest release ещё не запрашивался"
                    } else {
                        "Latest release: ${updateState.latestVersionName}"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = updateStatusText(updateState),
                    style = MaterialTheme.typography.bodyMedium,
                )
                updateState.releaseNotes.takeIf { it.isNotBlank() }?.let { notes ->
                    Text(
                        text = notes.take(240),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 6,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ElevatedButton(
                        onClick = onCheckUpdates,
                        enabled = !updateState.isChecking,
                    ) {
                        Text(if (updateState.isChecking) "Проверяю…" else "Проверить")
                    }
                    if (updateState.isUpdateAvailable && updateState.downloadState != UpdateDownloadState.READY_TO_INSTALL) {
                        OutlinedButton(onClick = onDownloadUpdate) {
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
        updateState.isChecking -> "Смотрю latest release на GitHub…"
        updateState.downloadState == UpdateDownloadState.DOWNLOADING && updateState.downloadProgressPercent != null ->
            "APK качается: ${updateState.downloadProgressPercent}%"
        updateState.downloadState == UpdateDownloadState.DOWNLOADING ->
            "APK качается через DownloadManager."
        updateState.downloadState == UpdateDownloadState.READY_TO_INSTALL && updateState.canInstallPackages ->
            "APK уже скачан. Можно ставить поверх текущей версии."
        updateState.downloadState == UpdateDownloadState.READY_TO_INSTALL ->
            "APK скачан. Разреши установку из этого источника и жми ещё раз."
        updateState.downloadState == UpdateDownloadState.FAILED ->
            "Предыдущая загрузка сорвалась. Запусти скачивание заново."
        updateState.isUpdateAvailable ->
            "Есть свежая версия ${updateState.latestVersionName}."
        updateState.latestVersionName.isNotBlank() ->
            "У тебя уже актуальная версия."
        else -> "Пока не проверял релизный канал."
    }
}

@Composable
private fun ConnectionChip(status: ConnectionStatus) {
    val (bg, fg) = when (status) {
        ConnectionStatus.CONNECTED -> Color(0xFF15392F) to Color(0xFF78E0C8)
        ConnectionStatus.CONNECTING,
        ConnectionStatus.RECONNECTING -> Color(0xFF3A2B16) to Color(0xFFFFC36A)
        ConnectionStatus.FAILED_AUTH,
        ConnectionStatus.FAILED_NETWORK -> Color(0xFF431E22) to Color(0xFFFF9AA5)
        ConnectionStatus.DISCONNECTED -> Color(0xFF2A2F39) to Color(0xFFC8D0DA)
    }
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = bg,
    ) {
        Text(
            text = statusText(status),
            color = fg,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
        )
    }
}

@Composable
private fun FeatureChip(label: String) {
    AssistChip(
        onClick = { },
        enabled = false,
        label = { Text(label) },
    )
}

@Composable
private fun AnimatedBackdrop(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "backdrop")
    val orbA by transition.animateFloat(
        initialValue = -0.1f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(12000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "orbA",
    )
    val orbB by transition.animateFloat(
        initialValue = 1.1f,
        targetValue = -0.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(15000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "orbB",
    )
    val orbC by transition.animateFloat(
        initialValue = 0.2f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(9000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "orbC",
    )

    Canvas(modifier = modifier) {
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color(0xFF11151C),
                    Color(0xFF0E1117),
                    Color(0xFF161B22),
                ),
            ),
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0x336DD3BF), Color.Transparent),
            ),
            radius = size.minDimension * 0.36f,
            center = Offset(size.width * orbA, size.height * 0.18f),
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0x33FF9C63), Color.Transparent),
            ),
            radius = size.minDimension * 0.42f,
            center = Offset(size.width * orbB, size.height * 0.72f),
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0x22F7EAD5), Color.Transparent),
            ),
            radius = size.minDimension * 0.24f,
            center = Offset(size.width * 0.5f, size.height * orbC),
        )
    }
}

@Composable
private fun StageScrims() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0x1AF6EFE4),
                        Color.Transparent,
                        Color(0x12000000),
                    ),
                ),
            ),
    )
}

private fun statusText(status: ConnectionStatus): String {
    return when (status) {
        ConnectionStatus.CONNECTED -> "онлайн"
        ConnectionStatus.CONNECTING -> "подключение"
        ConnectionStatus.RECONNECTING -> "переподключение"
        ConnectionStatus.FAILED_AUTH -> "ошибка токена"
        ConnectionStatus.FAILED_NETWORK -> "ошибка сети"
        ConnectionStatus.DISCONNECTED -> "оффлайн"
    }
}
