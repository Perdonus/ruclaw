@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
)

package com.perdonus.ruclaw.android.ui

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
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
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.AssistChip
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
    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) {
            viewModel.addComposerPhoto(uri)
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
        val intent = when (action.type) {
            PendingSystemActionType.OPEN_UNKNOWN_SOURCES_SETTINGS -> {
                Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:" + context.packageName),
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
            onAddPhoto = {
                photoPicker.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                )
            },
            onOpenAgentSheet = { viewModel.toggleAgentSheet(true) },
            modifier = Modifier.fillMaxSize(),
        )

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
private fun ConnectedSurface(
    state: MainUiState,
    viewModel: MainViewModel,
    onPathClick: (String) -> Unit,
    onAddPhoto: () -> Unit,
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
                    onAddPhoto = onAddPhoto,
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
                    onAddPhoto = onAddPhoto,
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
    onOpenDrawer: (() -> Unit)?,
    onOpenAgentSheet: () -> Unit,
    modifier: Modifier = Modifier,
) {
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
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = Color(0xCC1E2A38),
                        contentColor = Color.White,
                    ),
                ) {
                    Icon(Icons.Rounded.Menu, contentDescription = "Открыть меню")
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            FilledIconButton(
                onClick = onOpenAgentSheet,
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
                            text = "Нажми + в шапке или подключись к launcher, чтобы подтянуть историю.",
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
    onAddPhoto: () -> Unit,
    onOpenDrawer: (() -> Unit)?,
    onOpenAgentSheet: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val topPadding = 112.dp
    val bottomPadding = 186.dp

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
                    )
                }
                if (state.isTyping) {
                    item(key = "typing") {
                        TypingBubble()
                    }
                }
            }
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
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding(),
        )

        TopOverlayBar(
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
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(if (top) 188.dp else 220.dp)
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
                bottomEnd = if (isUser) 10.dp else 24.dp,
                bottomStart = if (isUser) 24.dp else 10.dp,
            ),
            color = if (isUser) Color(0xFF213042) else Color(0xD9182230),
            border = BorderStroke(1.dp, if (isUser) Color(0x1FFFFFFF) else Color(0x18FFFFFF)),
            modifier = Modifier.widthIn(max = 760.dp),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (message.text.isNotBlank()) {
                    if (isUser) {
                        Text(
                            text = message.text,
                            style = MaterialTheme.typography.bodyLarge,
                            color = Color.White,
                        )
                    } else {
                        MarkdownMessage(
                            text = message.text,
                            onDownloadLinkClick = onPathClick,
                            onExternalLinkClick = onExternalLink,
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
        "Сделай Android-интерфейс под launcher",
        "Покажи где ломается API и как исправить",
        "Подготовь новый релиз APK",
    )

    Column(
        modifier = modifier
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "Готов к задаче",
            style = MaterialTheme.typography.headlineSmall,
            color = Color.White,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = if (state.hasConfiguredLauncher) {
                "Выбери тред слева или начни новый диалог."
            } else {
                "Открой настройки, укажи launcher URL и access token, потом можно писать прямо сюда."
            },
            style = MaterialTheme.typography.bodyLarge,
            color = Color(0xFFB8C4D2),
            textAlign = TextAlign.Center,
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
                text = "Настройки launcher",
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
            )

            OutlinedTextField(
                value = state.launcherConfig.url,
                onValueChange = viewModel::onLauncherUrlChanged,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Launcher URL") },
                placeholder = { Text("http://192.168.1.109:18800") },
                singleLine = true,
                colors = sheetFieldColors(),
            )

            OutlinedTextField(
                value = state.launcherConfig.token,
                onValueChange = viewModel::onLauncherTokenChanged,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Launcher access token") },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                colors = sheetFieldColors(),
            )

            FilledTonalButton(
                onClick = {
                    viewModel.toggleSettings(false)
                    viewModel.connectLauncher()
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
            ) {
                Text("Подключить")
            }

            HorizontalDivider(color = Color(0x1FFFFFFF))

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
                        "Latest release ещё не запрашивался"
                    } else {
                        "Latest release: " + updateState.latestVersionName
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
        updateState.isChecking -> "Смотрю latest release на GitHub…"
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
                    Color(0xFF05080D),
                    Color(0xFF09111A),
                    Color(0xFF0F1824),
                ),
            ),
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0x446DD3BF), Color.Transparent),
            ),
            radius = size.minDimension * 0.36f,
            center = Offset(size.width * orbA, size.height * 0.18f),
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0x35FF9C63), Color.Transparent),
            ),
            radius = size.minDimension * 0.44f,
            center = Offset(size.width * orbB, size.height * 0.74f),
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0x1EF7EAD5), Color.Transparent),
            ),
            radius = size.minDimension * 0.25f,
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
                        Color(0x10000000),
                        Color.Transparent,
                        Color(0x12000000),
                    ),
                ),
            ),
    )
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
