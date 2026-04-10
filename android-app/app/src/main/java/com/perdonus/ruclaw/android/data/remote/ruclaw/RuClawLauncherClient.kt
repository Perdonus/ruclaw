package com.perdonus.ruclaw.android.data.remote.ruclaw

import com.perdonus.ruclaw.android.core.model.CachedSession
import com.perdonus.ruclaw.android.core.model.ChatMessage
import com.perdonus.ruclaw.android.core.model.ChatRole
import com.perdonus.ruclaw.android.core.model.ChatThreadSummary
import com.perdonus.ruclaw.android.core.model.LauncherModelItem
import com.perdonus.ruclaw.android.core.model.LauncherSkillItem
import com.perdonus.ruclaw.android.core.model.LauncherSkillSearchItem
import com.perdonus.ruclaw.android.core.model.LauncherToolItem
import com.perdonus.ruclaw.android.core.model.MessageStatus
import java.io.IOException
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

class RuClawLauncherClient(
    private val httpClient: OkHttpClient,
) {
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    suspend fun ensurePico(
        baseUrl: String,
        launcherToken: String,
    ): PicoHandshake {
        val normalizedBaseUrl = normalizeBaseUrl(baseUrl)
        val initial = requestJson<PicoTokenResponse>(
            requestBuilder(normalizedBaseUrl, "/api/pico/token", launcherToken).get().build(),
        )
        if (initial.token.isNotBlank() && initial.enabled) {
            return PicoHandshake(
                picoToken = initial.token,
                wsUrl = initial.wsUrl,
                enabled = initial.enabled,
            )
        }

        val setup = requestJson<PicoTokenResponse>(
            requestBuilder(normalizedBaseUrl, "/api/pico/setup", launcherToken)
                .post(ByteArray(0).toRequestBody())
                .header("Origin", normalizedBaseUrl)
                .build(),
        )
        return PicoHandshake(
            picoToken = setup.token,
            wsUrl = setup.wsUrl,
            enabled = setup.enabled,
        )
    }

    suspend fun listSessions(
        baseUrl: String,
        launcherToken: String,
        limit: Int = 80,
    ): List<ChatThreadSummary> {
        val normalizedBaseUrl = normalizeBaseUrl(baseUrl)
        val response = requestJson<List<RemoteSessionSummary>>(
            requestBuilder(normalizedBaseUrl, "/api/sessions?limit=$limit", launcherToken)
                .get()
                .build(),
        )
        return response.map { dto ->
            ChatThreadSummary(
                sessionId = dto.id,
                title = dto.title.ifBlank { dto.preview.ifBlank { "Новый диалог" } }.trim(),
                preview = dto.preview.trim(),
                updatedAtEpochMillis = parseTimestamp(dto.updated),
                messageCount = dto.messageCount,
                isLocalOnly = false,
            )
        }.sortedByDescending { it.updatedAtEpochMillis }
    }

    suspend fun getSession(
        baseUrl: String,
        launcherToken: String,
        sessionId: String,
    ): CachedSession {
        val normalizedBaseUrl = normalizeBaseUrl(baseUrl)
        val response = requestJson<RemoteSessionDetails>(
            requestBuilder(normalizedBaseUrl, "/api/sessions/$sessionId", launcherToken)
                .get()
                .build(),
        )
        val messages = response.messages.mapIndexed { index, message ->
            ChatMessage(
                id = "${sessionId}_${index}_${message.role}",
                role = if (message.role == "user") ChatRole.USER else ChatRole.ASSISTANT,
                text = message.content,
                attachments = message.media,
                status = MessageStatus.COMPLETE,
                createdAtEpochMillis = parseTimestamp(response.updated),
            )
        }
        val preview = response.messages.firstOrNull { it.role == "user" }?.content?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: messages.firstOrNull()?.text?.trim().orEmpty()
        return CachedSession(
            sessionId = response.id,
            title = preview.ifBlank { response.summary.ifBlank { "Новый диалог" } }.take(60),
            preview = preview.take(120),
            updatedAtEpochMillis = parseTimestamp(response.updated),
            messages = messages,
        )
    }

    suspend fun listModels(
        baseUrl: String,
        launcherToken: String,
    ): List<LauncherModelItem> {
        val normalizedBaseUrl = normalizeBaseUrl(baseUrl)
        val response = requestJson<RemoteModelsResponse>(
            requestBuilder(normalizedBaseUrl, "/api/models", launcherToken)
                .get()
                .build(),
        )
        return response.models
            .sortedWith(
                compareByDescending<RemoteModelItem> { it.isDefault }
                    .thenByDescending { it.available }
                    .thenBy { it.modelName.lowercase() },
            )
            .map { dto ->
                LauncherModelItem(
                    index = dto.index,
                    modelName = dto.modelName,
                    status = dto.status,
                    available = dto.available,
                    isDefault = dto.isDefault,
                    isVirtual = dto.isVirtual,
                    authMethod = dto.authMethod,
                )
            }
    }

    suspend fun setDefaultModel(
        baseUrl: String,
        launcherToken: String,
        modelName: String,
    ) {
        val normalizedBaseUrl = normalizeBaseUrl(baseUrl)
        requestText(
            requestBuilder(normalizedBaseUrl, "/api/models/default", launcherToken)
                .post(("{\"model_name\":" + jsonQuote(modelName) + "}").toRequestBody(jsonMediaType))
                .build(),
        )
    }

    suspend fun listSkills(
        baseUrl: String,
        launcherToken: String,
    ): List<LauncherSkillItem> {
        val normalizedBaseUrl = normalizeBaseUrl(baseUrl)
        val response = requestJson<RemoteSkillsResponse>(
            requestBuilder(normalizedBaseUrl, "/api/skills", launcherToken)
                .get()
                .build(),
        )
        return response.skills
            .sortedBy { it.name.lowercase() }
            .map { dto ->
                LauncherSkillItem(
                    name = dto.name,
                    description = dto.description,
                    source = dto.source,
                    originKind = dto.originKind,
                    registryName = dto.registryName,
                    registryUrl = dto.registryUrl,
                    installedVersion = dto.installedVersion,
                )
            }
    }

    suspend fun searchSkills(
        baseUrl: String,
        launcherToken: String,
        query: String,
        limit: Int = 8,
        offset: Int = 0,
    ): List<LauncherSkillSearchItem> {
        val normalizedBaseUrl = normalizeBaseUrl(baseUrl)
        val encodedQuery = URLEncoder.encode(query.trim(), StandardCharsets.UTF_8.toString())
        val response = requestJson<RemoteSkillSearchResponse>(
            requestBuilder(
                normalizedBaseUrl,
                "/api/skills/search?q=$encodedQuery&limit=$limit&offset=$offset",
                launcherToken,
            )
                .get()
                .build(),
        )
        return response.results.map { dto ->
            LauncherSkillSearchItem(
                slug = dto.slug,
                displayName = dto.displayName,
                summary = dto.summary,
                version = dto.version,
                registryName = dto.registryName,
                url = dto.url,
                installed = dto.installed,
                installedName = dto.installedName,
            )
        }
    }

    suspend fun installSkill(
        baseUrl: String,
        launcherToken: String,
        slug: String,
        registryName: String,
        version: String = "",
    ): LauncherSkillItem? {
        val normalizedBaseUrl = normalizeBaseUrl(baseUrl)
        val body = buildString {
            append("{\"slug\":")
            append(jsonQuote(slug))
            append(",\"registry\":")
            append(jsonQuote(registryName))
            if (version.isNotBlank()) {
                append(",\"version\":")
                append(jsonQuote(version))
            }
            append("}")
        }
        val response = requestJson<RemoteInstallSkillResponse>(
            requestBuilder(normalizedBaseUrl, "/api/skills/install", launcherToken)
                .post(body.toRequestBody(jsonMediaType))
                .build(),
        )
        val skill = response.skill ?: return null
        return LauncherSkillItem(
            name = skill.name,
            description = skill.description,
            source = skill.source,
            originKind = skill.originKind,
            registryName = skill.registryName,
            registryUrl = skill.registryUrl,
            installedVersion = skill.installedVersion,
        )
    }

    suspend fun listTools(
        baseUrl: String,
        launcherToken: String,
    ): List<LauncherToolItem> {
        val normalizedBaseUrl = normalizeBaseUrl(baseUrl)
        val response = requestJson<RemoteToolsResponse>(
            requestBuilder(normalizedBaseUrl, "/api/tools", launcherToken)
                .get()
                .build(),
        )
        return response.tools
            .sortedWith(compareBy<RemoteToolItem> { it.category }.thenBy { it.name })
            .map { dto ->
                LauncherToolItem(
                    name = dto.name,
                    description = dto.description,
                    category = dto.category,
                    status = dto.status,
                    reasonCode = dto.reasonCode,
                )
            }
    }

    suspend fun setToolEnabled(
        baseUrl: String,
        launcherToken: String,
        name: String,
        enabled: Boolean,
    ) {
        val normalizedBaseUrl = normalizeBaseUrl(baseUrl)
        requestText(
            requestBuilder(
                normalizedBaseUrl,
                "/api/tools/" + URLEncoder.encode(name, StandardCharsets.UTF_8.toString()) + "/state",
                launcherToken,
            )
                .put(("{\"enabled\":" + enabled + "}").toRequestBody(jsonMediaType))
                .build(),
        )
    }

    fun openSocket(
        baseUrl: String,
        launcherToken: String,
        handshake: PicoHandshake,
        sessionId: String,
    ): PicoSocket {
        return PicoSocket(
            httpClient = httpClient,
            json = json,
            wsUrl = handshake.wsUrl,
            origin = normalizeBaseUrl(baseUrl),
            launcherToken = launcherToken.trim(),
            picoToken = handshake.picoToken,
            sessionId = sessionId,
        )
    }

    private suspend inline fun <reified T> requestJson(request: Request): T {
        val body = requestText(request)
        return json.decodeFromString(body)
    }

    private suspend fun requestText(request: Request): String = withContext(Dispatchers.IO) {
        httpClient.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw toApiException(response, body)
            }
            body
        }
    }

    private fun requestBuilder(
        baseUrl: String,
        path: String,
        launcherToken: String,
    ): Request.Builder {
        return Request.Builder()
            .url(baseUrl + path)
            .header("Authorization", "Bearer ${launcherToken.trim()}")
    }

    private fun jsonQuote(value: String): String {
        return buildString(value.length + 8) {
            append('"')
            value.forEach { char ->
                when (char) {
                    '\\' -> append("\\\\")
                    '"' -> append("\\\"")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    else -> append(char)
                }
            }
            append('"')
        }
    }

    private fun toApiException(response: Response, body: String): RuClawApiException {
        val parsedMessage = runCatching {
            json.parseToJsonElement(body).jsonObject["error"]?.jsonPrimitive?.contentOrNull
                ?: json.parseToJsonElement(body).jsonObject["message"]?.jsonPrimitive?.contentOrNull
        }.getOrNull()
        val message = parsedMessage?.takeIf { it.isNotBlank() }
            ?: body.trim().ifBlank { "HTTP ${response.code}" }
        return RuClawApiException(response.code, message)
    }

    private fun normalizeBaseUrl(value: String): String {
        return value.trim().trimEnd('/')
    }

    private fun parseTimestamp(value: String?): Long {
        if (value.isNullOrBlank()) return System.currentTimeMillis()
        return runCatching { Instant.parse(value).toEpochMilli() }
            .getOrDefault(System.currentTimeMillis())
    }
}

data class PicoHandshake(
    val picoToken: String,
    val wsUrl: String,
    val enabled: Boolean,
)

class RuClawApiException(
    val statusCode: Int,
    override val message: String,
) : IOException(message)

class PicoSocket(
    private val httpClient: OkHttpClient,
    private val json: Json,
    private val wsUrl: String,
    private val origin: String,
    private val launcherToken: String,
    private val picoToken: String,
    private val sessionId: String,
) {
    private val _events = MutableSharedFlow<PicoEvent>(extraBufferCapacity = 128)
    private var socket: WebSocket? = null

    val events: SharedFlow<PicoEvent> = _events.asSharedFlow()

    suspend fun connect() = withContext(Dispatchers.IO) {
        close()
        val opened = CompletableDeferred<Unit>()
        val failed = CompletableDeferred<Throwable>()
        val request = Request.Builder()
            .url(wsUrlWithSession())
            .header("Authorization", "Bearer $launcherToken")
            .header("Origin", origin)
            .header("Sec-WebSocket-Protocol", "token.$picoToken")
            .build()
        socket = httpClient.newWebSocket(
            request,
            object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    opened.complete(Unit)
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    handleMessage(text)
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    socket = null
                    val failure = response?.let { upgradeResponse ->
                        val body = runCatching { upgradeResponse.body?.string().orEmpty() }.getOrDefault("")
                        toApiException(upgradeResponse, body)
                    } ?: t
                    failed.complete(failure)
                    _events.tryEmit(
                        PicoEvent.SocketFailure(
                            reason = failure.message ?: "WebSocket failure",
                            statusCode = (failure as? RuClawApiException)?.statusCode,
                        ),
                    )
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    socket = null
                    _events.tryEmit(PicoEvent.SocketClosed(reason.ifBlank { "Соединение закрыто" }))
                }
            },
        )
        withTimeout(10_000L) {
            select<Unit> {
                opened.onAwait { }
                failed.onAwait { throw it }
            }
        }
    }

    suspend fun close() = withContext(Dispatchers.IO) {
        socket?.close(1000, "closing")
        socket = null
    }

    fun sendMessage(
        requestId: String,
        content: String,
        media: List<String> = emptyList(),
    ): Boolean {
        val currentSocket = socket ?: return false
        val payload = buildJsonObject(
            requestId = requestId,
            content = content,
            media = media,
        )
        return currentSocket.send(payload)
    }

    private fun wsUrlWithSession(): String {
        val separator = if (wsUrl.contains("?")) "&" else "?"
        return "$wsUrl${separator}session_id=$sessionId"
    }

    private fun buildJsonObject(
        requestId: String,
        content: String,
        media: List<String>,
    ): String {
        val payload = buildString {
            append("{\"type\":\"message.send\",\"id\":\"")
            append(escape(requestId))
            append("\",\"payload\":{\"content\":\"")
            append(escape(content))
            append("\"")
            if (media.isNotEmpty()) {
                append(",\"media\":[")
                append(media.joinToString(",") { "\"${escape(it)}\"" })
                append("]")
            }
            append("}}")
        }
        return payload
    }

    private fun handleMessage(text: String) {
        val root = runCatching { json.parseToJsonElement(text).jsonObject }.getOrElse {
            _events.tryEmit(PicoEvent.SocketFailure("Некорректный ответ от launcher"))
            return
        }
        val type = root["type"]?.jsonPrimitive?.contentOrNull.orEmpty()
        val payload = root["payload"]?.jsonObject ?: JsonObject(emptyMap())
        val timestamp = root["timestamp"]?.jsonPrimitive?.contentOrNull?.toLongOrNull()
            ?: System.currentTimeMillis()

        when (type) {
            "message.create" -> {
                val messageId = payload["message_id"]?.jsonPrimitive?.contentOrNull
                    ?: root["id"]?.jsonPrimitive?.contentOrNull
                    ?: "assistant-$timestamp"
                val content = payload["content"]?.jsonPrimitive?.contentOrNull.orEmpty()
                _events.tryEmit(
                    PicoEvent.MessageCreate(
                        messageId = messageId,
                        content = content,
                        createdAtEpochMillis = timestamp,
                    ),
                )
            }

            "message.update" -> {
                val messageId = payload["message_id"]?.jsonPrimitive?.contentOrNull ?: return
                val content = payload["content"]?.jsonPrimitive?.contentOrNull.orEmpty()
                _events.tryEmit(
                    PicoEvent.MessageUpdate(
                        messageId = messageId,
                        content = content,
                    ),
                )
            }

            "typing.start" -> _events.tryEmit(PicoEvent.Typing(active = true))
            "typing.stop" -> _events.tryEmit(PicoEvent.Typing(active = false))
            "error" -> {
                _events.tryEmit(
                    PicoEvent.ProtocolError(
                        requestId = payload["request_id"]?.jsonPrimitive?.contentOrNull,
                        message = payload["message"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                    ),
                )
            }

            "pong" -> _events.tryEmit(PicoEvent.Pong)
        }
    }

    private fun escape(value: String): String {
        return buildString(value.length + 8) {
            value.forEach { char ->
                when (char) {
                    '\\' -> append("\\\\")
                    '"' -> append("\\\"")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    else -> append(char)
                }
            }
        }
    }
}

sealed interface PicoEvent {
    data class MessageCreate(
        val messageId: String,
        val content: String,
        val createdAtEpochMillis: Long,
    ) : PicoEvent

    data class MessageUpdate(
        val messageId: String,
        val content: String,
    ) : PicoEvent

    data class Typing(
        val active: Boolean,
    ) : PicoEvent

    data class ProtocolError(
        val requestId: String?,
        val message: String,
    ) : PicoEvent

    data class SocketClosed(
        val reason: String,
    ) : PicoEvent

    data class SocketFailure(
        val reason: String,
        val statusCode: Int? = null,
    ) : PicoEvent

    data object Pong : PicoEvent
}

@Serializable
private data class PicoTokenResponse(
    val token: String = "",
    @SerialName("ws_url")
    val wsUrl: String = "",
    val enabled: Boolean = false,
)

@Serializable
private data class RemoteSessionSummary(
    val id: String,
    val title: String = "",
    val preview: String = "",
    @SerialName("message_count")
    val messageCount: Int = 0,
    val updated: String = "",
)

@Serializable
private data class RemoteSessionDetails(
    val id: String,
    val summary: String = "",
    val updated: String = "",
    val messages: List<RemoteSessionMessage> = emptyList(),
)

@Serializable
private data class RemoteSessionMessage(
    val role: String = "",
    val content: String = "",
    val media: List<String> = emptyList(),
)

@Serializable
private data class RemoteModelsResponse(
    val models: List<RemoteModelItem> = emptyList(),
)

@Serializable
private data class RemoteModelItem(
    val index: Int = 0,
    @SerialName("model_name")
    val modelName: String = "",
    val status: String = "",
    val available: Boolean = false,
    @SerialName("is_default")
    val isDefault: Boolean = false,
    @SerialName("is_virtual")
    val isVirtual: Boolean = false,
    @SerialName("auth_method")
    val authMethod: String = "",
)

@Serializable
private data class RemoteSkillsResponse(
    val skills: List<RemoteSkillItem> = emptyList(),
)

@Serializable
private data class RemoteSkillItem(
    val name: String = "",
    val description: String = "",
    val source: String = "",
    @SerialName("origin_kind")
    val originKind: String = "",
    @SerialName("registry_name")
    val registryName: String = "",
    @SerialName("registry_url")
    val registryUrl: String = "",
    @SerialName("installed_version")
    val installedVersion: String = "",
)

@Serializable
private data class RemoteSkillSearchResponse(
    val results: List<RemoteSkillSearchItem> = emptyList(),
)

@Serializable
private data class RemoteSkillSearchItem(
    val slug: String = "",
    @SerialName("display_name")
    val displayName: String = "",
    val summary: String = "",
    val version: String = "",
    @SerialName("registry_name")
    val registryName: String = "",
    val url: String = "",
    val installed: Boolean = false,
    @SerialName("installed_name")
    val installedName: String = "",
)

@Serializable
private data class RemoteInstallSkillResponse(
    val skill: RemoteSkillItem? = null,
)

@Serializable
private data class RemoteToolsResponse(
    val tools: List<RemoteToolItem> = emptyList(),
)

@Serializable
private data class RemoteToolItem(
    val name: String = "",
    val description: String = "",
    val category: String = "",
    val status: String = "",
    @SerialName("reason_code")
    val reasonCode: String = "",
)
