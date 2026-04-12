package com.perdonus.ruclaw.android.data.localruntime

import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.storage.StorageManager
import android.provider.DocumentsContract
import com.perdonus.ruclaw.android.BuildConfig
import com.perdonus.ruclaw.android.core.util.AppDiagnostics
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class LocalRuntimeManager(context: Context) {
    private val appContext = context.applicationContext
    private val defaultRuntimeRoot = File(appContext.filesDir, "local-runtime")
    private val nativeLibDir = appContext.applicationInfo.nativeLibraryDir
        ?.let(::File)
        ?: File(appContext.applicationInfo.dataDir, "lib")
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    @Volatile
    private var launcherProcess: Process? = null

    @Volatile
    private var modelServerProcess: Process? = null

    suspend fun install(
        dataDirectory: String,
        log: suspend (String) -> Unit,
    ): LocalRuntimeInstallation = withContext(Dispatchers.IO) {
        ensureSupportedAndroidVersion()
        val workspace = prepareWorkspace(dataDirectory, log)

        log("Сеть не нужна: локальный runtime уже встроен в APK.")
        log("Проверяю встроенные бинарники Android runtime…")
        val coreBinary = requireBundledBinary(coreBinaryLibraryFileName, log)
        val launcherBinary = requireBundledBinary(launcherBinaryLibraryFileName, log)
        val modelServerBinary = requireOptionalBundledBinary(modelServerLibraryFileName, log)
        mirrorBundledBinaries(workspace, coreBinary, launcherBinary, modelServerBinary, log)
        writeBundledBuildInfo(workspace, log)
        writeRuntimeInfo(workspace, coreBinary, launcherBinary, modelServerBinary, log)

        File(workspace.root, "VERSION.txt").writeText(BuildConfig.VERSION_NAME + "\n")
        log("Локальный runtime готов.")

        LocalRuntimeInstallation(
            runtimeVersion = BuildConfig.VERSION_NAME,
            runtimeRoot = workspace.root.absolutePath,
            launcherUrl = launcherUrl,
            launcherToken = launcherToken,
            coreBinaryPath = coreBinary.absolutePath,
            launcherBinaryPath = launcherBinary.absolutePath,
            modelServerBinaryPath = modelServerBinary?.absolutePath.orEmpty(),
        )
    }

    suspend fun startLocalRuntime(config: LocalRuntimeConfig): LocalRuntimeConnection = withContext(Dispatchers.IO) {
        ensureSupportedAndroidVersion()
        val workspace = resolveWorkspace(config.dataDirectory)
        ensureWorkspaceDirectories(workspace)
        verifyWorkspaceWritable(workspace.root)

        val ggufFile = config.ggufPath.trim()
            .takeIf { it.isNotBlank() }
            ?.let { resolveGgufFile(it, workspace.modelsDir) }

        val coreBinary = requireInstalledBinary(coreBinaryLibraryFileName)
        val launcherBinary = requireInstalledBinary(launcherBinaryLibraryFileName)
        val modelServerBinary = resolveInstalledBinary(modelServerLibraryFileName)

        if (ggufFile != null && modelServerBinary == null) {
            throw IOException(
                "В этом APK нет локального model server для GGUF. " +
                    "Либо очисти путь до GGUF, либо поставь Android-релиз с bundled llama-server.",
            )
        }

        val reuseLauncher = !config.forceRestart && isLocalLauncherReady()
        val reuseModelServer = ggufFile != null && !config.forceRestart && isModelServerReady()
        if (config.forceRestart || !reuseLauncher || (ggufFile != null && !reuseModelServer)) {
            stopLocalRuntime(config.dataDirectory)
        }
        writeLauncherConfig(workspace.homeDir)
        updateAppConfig(workspace.homeDir, ggufFile, config.telegram)

        val modelServer = if (ggufFile != null && modelServerBinary != null) {
            if (reuseModelServer) {
                AppDiagnostics.log("Reusing existing local llama-server process")
                null
            } else {
                startProcess(
                    name = "llama-server",
                    command = listOf(
                        modelServerBinary.absolutePath,
                        "-m",
                        ggufFile.absolutePath,
                        "--host",
                        "127.0.0.1",
                        "--port",
                        "1234",
                    ),
                    workspace = workspace,
                    coreBinary = coreBinary,
                )
            }
        } else {
            null
        }
        modelServerProcess = modelServer?.process
        if (ggufFile != null) {
            waitForModelServer(modelServer)
        }

        if (reuseLauncher) {
            AppDiagnostics.log("Reusing existing local RuClaw launcher on $launcherUrl")
            launcherProcess = null
        } else {
            val launcher = startProcess(
                name = "ruclaw-launcher",
                command = listOf(
                    launcherBinary.absolutePath,
                    "-no-browser",
                    "-lang",
                    "ru",
                    "-port",
                    "18800",
                ),
                workspace = workspace,
                coreBinary = coreBinary,
            )
            launcherProcess = launcher.process
            waitForLauncher(launcher)
        }

        LocalRuntimeConnection(
            launcherUrl = launcherUrl,
            launcherToken = launcherToken,
            runtimeVersion = BuildConfig.VERSION_NAME,
            runtimeRoot = workspace.root.absolutePath,
        )
    }

    suspend fun stopLocalRuntime(dataDirectory: String = "") = withContext(Dispatchers.IO) {
        val workspace = runCatching { resolveWorkspace(dataDirectory) }
            .getOrElse { resolveWorkspace("") }
        stopManagedProcess(
            name = "ruclaw-launcher",
            process = launcherProcess,
            workspace = workspace,
            markerFileName = launcherProcessMarkerFileName,
            fallbackMarkerFileName = launcherPidFileName,
            commandMarker = "ruclaw_launcher_exec",
        )
        launcherProcess = null
        stopManagedProcess(
            name = "llama-server",
            process = modelServerProcess,
            workspace = workspace,
            markerFileName = modelServerProcessMarkerFileName,
            commandMarker = "llama_server_exec",
        )
        modelServerProcess = null
    }

    fun hasAllFilesAccess(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.R || Environment.isExternalStorageManager()
    }

    fun requiresAllFilesAccess(dataDirectory: String): Boolean {
        val selector = dataDirectory.trim()
        if (selector.isBlank()) {
            return false
        }
        val root = try {
            resolveWorkspaceRoot(selector).absoluteFile
        } catch (_: Throwable) {
            return false
        }
        return requiresAllFilesAccess(root)
    }

    private suspend fun prepareWorkspace(
        dataDirectory: String,
        log: suspend (String) -> Unit,
    ): LocalRuntimeWorkspace {
        val workspace = resolveWorkspace(dataDirectory)
        log("Готовлю папку данных локального RuClaw…")
        log("Путь: ${workspace.root.absolutePath}")
        ensureWorkspaceDirectories(workspace)
        verifyWorkspaceWritable(workspace.root)
        return workspace
    }

    private fun resolveWorkspace(dataDirectory: String): LocalRuntimeWorkspace {
        val root = resolveWorkspaceRoot(dataDirectory).absoluteFile
        return LocalRuntimeWorkspace(
            root = root,
            binDir = File(root, "bin"),
            homeDir = File(root, "home"),
            logsDir = File(root, "logs"),
            modelsDir = File(root, "models"),
            tmpDir = File(root, "tmp"),
        )
    }

    private fun ensureWorkspaceDirectories(workspace: LocalRuntimeWorkspace) {
        workspace.root.mkdirs()
        workspace.binDir.mkdirs()
        workspace.homeDir.mkdirs()
        workspace.logsDir.mkdirs()
        workspace.modelsDir.mkdirs()
        workspace.tmpDir.mkdirs()
    }

    private fun verifyWorkspaceWritable(root: File) {
        if (requiresAllFilesAccess(root) && !hasAllFilesAccess()) {
            throw IOException(
                "Папка данных лежит в общем Android-хранилище: ${root.absolutePath}. " +
                    "Для Download, Documents и других общих папок локальному RuClaw нужен системный доступ " +
                    "«Все файлы». Выдай его для RuClaw в настройках Android или очисти поле папки данных.",
            )
        }
        if (!root.exists() && !root.mkdirs()) {
            throw IOException("Не удалось создать папку данных: ${root.absolutePath}")
        }
        if (!root.isDirectory) {
            throw IOException("Папка данных не похожа на каталог: ${root.absolutePath}")
        }

        val probe = File(root, ".ruclaw-write-test")
        runCatching {
            probe.writeText("ok\n")
            probe.delete()
        }.getOrElse { error ->
            throw IOException(
                "Выбранная папка недоступна для прямой записи: ${root.absolutePath}. " +
                    "Выбери обычную папку во внутреннем накопителе устройства или оставь встроенную.",
                error,
            )
        }
    }

    private fun resolveWorkspaceRoot(dataDirectory: String): File {
        val selector = dataDirectory.trim()
        if (selector.isBlank()) {
            return defaultRuntimeRoot
        }
        if (selector.startsWith("content://", ignoreCase = true)) {
            return resolveTreeUriToFile(Uri.parse(selector))
        }
        return File(selector)
    }

    private fun resolveTreeUriToFile(uri: Uri): File {
        if (!DocumentsContract.isTreeUri(uri)) {
            throw IOException("Android picker вернул не папку, а другой URI.")
        }
        val authority = uri.authority.orEmpty()
        if (authority != externalStorageDocumentsAuthority) {
            throw IOException(
                "Этот Android storage provider не даёт прямой файловый путь для local runtime. " +
                    "Выбери папку во внутреннем накопителе устройства.",
            )
        }

        val documentId = DocumentsContract.getTreeDocumentId(uri)
        if (documentId.startsWith("raw:", ignoreCase = true)) {
            return File(documentId.removePrefix("raw:"))
        }

        val volumeId = documentId.substringBefore(':')
        val relativePath = documentId.substringAfter(':', "")
        val volumeRoot = when {
            volumeId.equals("primary", ignoreCase = true) -> externalStorageRoot()
            volumeId.equals("home", ignoreCase = true) -> File(externalStorageRoot(), "Documents")
            else -> resolveStorageVolumeRoot(volumeId)
        }
        return if (relativePath.isBlank()) {
            volumeRoot
        } else {
            File(volumeRoot, relativePath)
        }
    }

    @Suppress("DEPRECATION")
    private fun externalStorageRoot(): File = Environment.getExternalStorageDirectory()

    private fun resolveStorageVolumeRoot(volumeId: String): File {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val manager = appContext.getSystemService(StorageManager::class.java)
            val volume = manager?.storageVolumes?.firstOrNull { it.uuid.equals(volumeId, ignoreCase = true) }
            val directory = volume?.directory
            if (directory != null) {
                return directory
            }
        }
        return File("/storage/$volumeId")
    }

    private fun requiresAllFilesAccess(root: File): Boolean {
        val target = root.absoluteFile
        if (isInsideAppOwnedStorage(target)) {
            return false
        }
        if (isSameOrDescendant(target, externalStorageRoot())) {
            return true
        }
        return isSameOrDescendant(target, File("/storage"))
    }

    private fun isInsideAppOwnedStorage(target: File): Boolean {
        val appOwnedRoots = buildList {
            add(File(appContext.applicationInfo.dataDir))
            add(appContext.filesDir)
            add(appContext.cacheDir)
            add(appContext.codeCacheDir)
            add(appContext.noBackupFilesDir)
            appContext.getExternalFilesDir(null)?.let(::add)
            appContext.externalCacheDir?.let(::add)
            appContext.obbDir?.let(::add)
            appContext.externalMediaDirs.forEach { mediaDir ->
                mediaDir?.let(::add)
            }
        }
        return appOwnedRoots.any { root -> isSameOrDescendant(target, root) }
    }

    private fun isSameOrDescendant(
        target: File,
        root: File,
    ): Boolean {
        val normalizedTarget = normalizedPath(target)
        val normalizedRoot = normalizedPath(root)
        return normalizedTarget == normalizedRoot || normalizedTarget.startsWith("$normalizedRoot/")
    }

    private fun normalizedPath(file: File): String {
        return runCatching { file.canonicalPath }
            .getOrElse { file.absolutePath }
            .trimEnd('/')
    }

    private suspend fun requireBundledBinary(
        libraryFileName: String,
        log: suspend (String) -> Unit,
    ): File {
        val file = requireInstalledBinary(libraryFileName)
        log("Подключаю ${file.name} из native libs…")
        return file
    }

    private suspend fun requireOptionalBundledBinary(
        libraryFileName: String,
        log: suspend (String) -> Unit,
    ): File? {
        val file = resolveInstalledBinary(libraryFileName)
        return if (file != null) {
            log("Подключаю ${file.name} из native libs…")
            file
        } else {
            log("Пропускаю ${libraryFileName.removePrefix("lib").removeSuffix(".so")}: GGUF останется опциональным.")
            null
        }
    }

    private fun requireInstalledBinary(libraryFileName: String): File {
        return resolveInstalledBinary(libraryFileName)
            ?: throw IOException(
                "В этом APK нет встроенного локального runtime ($libraryFileName). " +
                    "Нужен Android-релиз с bundled native runtime.",
            )
    }

    private fun resolveInstalledBinary(libraryFileName: String): File? {
        val binary = File(nativeLibDir, libraryFileName)
        if (!binary.exists()) {
            return null
        }
        binary.setReadable(true, false)
        binary.setExecutable(true, false)
        return binary
    }

    private suspend fun writeBundledBuildInfo(
        workspace: LocalRuntimeWorkspace,
        log: suspend (String) -> Unit,
    ) {
        val target = File(workspace.root, buildInfoAssetName)
        try {
            appContext.assets.open("$assetDir/$buildInfoAssetName").use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            }
            log("Сохраняю $buildInfoAssetName…")
        } catch (_: FileNotFoundException) {
            // Older APKs may not bundle build info.
        }
    }

    private suspend fun mirrorBundledBinaries(
        workspace: LocalRuntimeWorkspace,
        coreBinary: File,
        launcherBinary: File,
        modelServerBinary: File?,
        log: suspend (String) -> Unit,
    ) {
        mirrorBundledBinary(coreBinary, File(workspace.binDir, "ruclaw"), log)
        mirrorBundledBinary(launcherBinary, File(workspace.binDir, "ruclaw-launcher"), log)
        if (modelServerBinary != null) {
            mirrorBundledBinary(modelServerBinary, File(workspace.binDir, "llama-server"), log)
        }
    }

    private suspend fun mirrorBundledBinary(
        source: File,
        target: File,
        log: suspend (String) -> Unit,
    ) {
        source.inputStream().use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        }
        target.setReadable(true, false)
        target.setExecutable(true, false)
        log("Копирую ${target.name} в ${target.parentFile?.absolutePath}…")
    }

    private suspend fun writeRuntimeInfo(
        workspace: LocalRuntimeWorkspace,
        coreBinary: File,
        launcherBinary: File,
        modelServerBinary: File?,
        log: suspend (String) -> Unit,
    ) {
        val infoFile = File(workspace.root, runtimeInfoFileName)
        infoFile.writeText(
            json.encodeToString(
                buildJsonObject {
                    put("runtime_mode", JsonPrimitive("bundled_in_apk"))
                    put("version", JsonPrimitive(BuildConfig.VERSION_NAME))
                    put("workspace_root", JsonPrimitive(workspace.root.absolutePath))
                    put("data_dirs", buildJsonObject {
                        put("bin", JsonPrimitive(workspace.binDir.absolutePath))
                        put("home", JsonPrimitive(workspace.homeDir.absolutePath))
                        put("logs", JsonPrimitive(workspace.logsDir.absolutePath))
                        put("models", JsonPrimitive(workspace.modelsDir.absolutePath))
                        put("tmp", JsonPrimitive(workspace.tmpDir.absolutePath))
                    })
                    put("execution_binaries", buildJsonObject {
                        put("ruclaw", JsonPrimitive(coreBinary.absolutePath))
                        put("ruclaw_launcher", JsonPrimitive(launcherBinary.absolutePath))
                        if (modelServerBinary != null) {
                            put("llama_server", JsonPrimitive(modelServerBinary.absolutePath))
                        }
                    })
                    put("mirrored_binaries", buildJsonObject {
                        put("ruclaw", JsonPrimitive(File(workspace.binDir, "ruclaw").absolutePath))
                        put("ruclaw_launcher", JsonPrimitive(File(workspace.binDir, "ruclaw-launcher").absolutePath))
                        if (modelServerBinary != null) {
                            put("llama_server", JsonPrimitive(File(workspace.binDir, "llama-server").absolutePath))
                        }
                    })
                    put(
                        "note",
                        JsonPrimitive(
                            "RuClaw runs from APK native libs. The selected folder stores data and mirrored binaries for visibility/debugging.",
                        ),
                    )
                },
            ) + "\n",
        )

        File(workspace.root, runtimeReadmeFileName).writeText(
            """
            |Локальный RuClaw уже встроен в APK приложения.
            |
            |Что лежит в этой папке:
            |- bin/ — видимые копии бинарников для проверки и отладки.
            |- home/ — config, база, сессии и данные launcher-а.
            |- logs/ — логи локального runtime.
            |- models/ — локальные GGUF-модели.
            |- tmp/ — временные файлы.
            |
            |Что реально запускает приложение:
            |- ${coreBinary.absolutePath}
            |- ${launcherBinary.absolutePath}
            |${modelServerBinary?.absolutePath?.let { "- $it" } ?: "- llama-server в этом APK не встроен"}
            |
            |Почему так:
            |Android надёжнее запускает встроенные native libs из APK, чем бинарники из обычной папки данных.
            |Поэтому в bin/ лежат зеркала для наглядности, а реальный запуск идёт из APK.
            """.trimMargin() + "\n",
        )
        log("Сохраняю ${runtimeInfoFileName} и ${runtimeReadmeFileName}…")
    }

    private fun writeLauncherConfig(homeDir: File) {
        val file = File(homeDir, "launcher-config.json")
        file.writeText(
            """
            {
              "port": 18800,
              "public": false,
              "launcher_token": "$launcherToken"
            }
            """.trimIndent() + "\n",
        )
    }

    private fun updateAppConfig(
        homeDir: File,
        ggufFile: File?,
        telegram: LocalTelegramConfig,
    ) {
        val file = File(homeDir, "config.json")
        if (!file.exists()) {
            file.parentFile?.mkdirs()
            file.writeText("{}\n")
        }

        val root = runCatching { json.decodeFromString<JsonObject>(file.readText()) }
            .getOrElse { JsonObject(emptyMap()) }
        val existingModels = root["model_list"]?.jsonArray?.toList().orEmpty()
        val filteredModels = existingModels.filterNot { element ->
            element.jsonObject["model_name"]?.jsonPrimitive?.content == localModelName
        }
        val currentAgents = root["agents"]?.jsonObject ?: JsonObject(emptyMap())
        val currentDefaults = currentAgents["defaults"]?.jsonObject ?: JsonObject(emptyMap())
        val currentChannels = root["channels"]?.jsonObject ?: JsonObject(emptyMap())
        val nextDefaultsBase = buildJsonObject {
            currentDefaults.forEach { (key, value) ->
                put(key, value)
            }
            val configuredMaxToolIterations = currentDefaults["max_tool_iterations"]
                ?.jsonPrimitive
                ?.contentOrNull
                ?.toIntOrNull()
            if (configuredMaxToolIterations == null || configuredMaxToolIterations < localDefaultMaxToolIterations) {
                put("max_tool_iterations", JsonPrimitive(localDefaultMaxToolIterations))
            }
        }
        val nextChannels = buildChannelsConfig(currentChannels, telegram)

        val nextRoot = if (ggufFile == null) {
            val nextDefaults = buildJsonObject {
                nextDefaultsBase.forEach { (key, value) ->
                    if (!(key == "model_name" && value.jsonPrimitive.contentOrNull == localModelName)) {
                        put(key, value)
                    }
                }
            }
            buildJsonObject {
                root.forEach { (key, value) ->
                    if (key != "model_list" && key != "agents" && key != "channels") {
                        put(key, value)
                    }
                }
                if (filteredModels.isNotEmpty() || root.containsKey("model_list")) {
                    put("model_list", JsonArray(filteredModels))
                }
                put(
                    "agents",
                    buildJsonObject {
                        currentAgents.forEach { (key, value) ->
                            if (key != "defaults") {
                                put(key, value)
                            }
                        }
                        put("defaults", nextDefaults)
                    },
                )
                if (nextChannels.isNotEmpty() || root.containsKey("channels")) {
                    put("channels", nextChannels)
                }
            }
        } else {
            val modelId = sanitizeModelId(ggufFile.nameWithoutExtension.ifBlank { "local-gguf" })
            val nextModels = filteredModels + buildJsonObject {
                put("model_name", JsonPrimitive(localModelName))
                put("model", JsonPrimitive("lmstudio/openai/$modelId"))
                put("api_base", JsonPrimitive(localModelApiBase))
            }

            buildJsonObject {
                root.forEach { (key, value) ->
                    if (key != "model_list" && key != "agents" && key != "channels") {
                        put(key, value)
                    }
                }
                put("model_list", JsonArray(nextModels))
                put(
                    "agents",
                    buildJsonObject {
                        currentAgents.forEach { (key, value) ->
                            if (key != "defaults") {
                                put(key, value)
                            }
                        }
                        put(
                            "defaults",
                            buildJsonObject {
                                nextDefaultsBase.forEach { (key, value) ->
                                    put(key, value)
                                }
                                put("model_name", JsonPrimitive(localModelName))
                            },
                        )
                    },
                )
                if (nextChannels.isNotEmpty() || root.containsKey("channels")) {
                    put("channels", nextChannels)
                }
            }
        }

        file.parentFile?.mkdirs()
        file.writeText(json.encodeToString(nextRoot) + "\n")
    }

    private fun buildChannelsConfig(
        currentChannels: JsonObject,
        telegram: LocalTelegramConfig,
    ): JsonObject {
        val token = telegram.botToken.trim()
        if (telegram.enabled && token.isBlank()) {
            throw IOException("Для Telegram сначала укажи токен бота.")
        }
        return buildJsonObject {
            currentChannels.forEach { (key, value) ->
                if (key != "telegram") {
                    put(key, value)
                }
            }
            if (telegram.enabled && token.isNotBlank()) {
                put(
                    "telegram",
                    buildTelegramChannelConfig(
                        current = currentChannels["telegram"]?.jsonObject ?: JsonObject(emptyMap()),
                        token = token,
                        telegram = telegram,
                    ),
                )
            }
        }
    }

    private fun buildTelegramChannelConfig(
        current: JsonObject,
        token: String,
        telegram: LocalTelegramConfig,
    ): JsonObject {
        val allowedUsers = parseAllowFrom(telegram.allowedUsers)
        if (allowedUsers.isEmpty()) {
            throw IOException(
                "Для Telegram укажи хотя бы один user/chat ID в Allow from или явно поставь * для открытого бота.",
            )
        }
        return buildJsonObject {
            current.forEach { (key, value) ->
                if (key !in setOf("enabled", "token", "allow_from", "use_markdown_v2")) {
                    put(key, value)
                }
            }
            put("enabled", JsonPrimitive(true))
            put("token", JsonPrimitive(token))
            put("allow_from", JsonArray(allowedUsers.map(::JsonPrimitive)))
            put("use_markdown_v2", JsonPrimitive(telegram.useMarkdownV2))
        }
    }

    private fun parseAllowFrom(value: String): List<String> {
        return value
            .split(Regex("[,\\n\\r\\t ]+"))
            .map(String::trim)
            .filter(String::isNotBlank)
            .distinct()
    }

    private fun resolveGgufFile(
        pathOrUri: String,
        modelsDir: File,
    ): File {
        return if (pathOrUri.startsWith("content://", ignoreCase = true)) {
            materializeGgufUri(Uri.parse(pathOrUri), modelsDir)
        } else {
            File(pathOrUri).also { file ->
                if (!file.exists() || !file.isFile) {
                    throw IOException("GGUF файл не найден: $pathOrUri")
                }
            }
        }
    }

    private fun materializeGgufUri(
        uri: Uri,
        modelsDir: File,
    ): File {
        modelsDir.mkdirs()
        val cachedModel = File(modelsDir, "selected-model.gguf")
        val sourceMarker = File(modelsDir, "selected-model.uri")
        if (cachedModel.exists() && sourceMarker.exists() && sourceMarker.readText() == uri.toString()) {
            return cachedModel
        }

        val resolver = appContext.contentResolver
        resolver.openInputStream(uri)?.use { input ->
            val temp = File(modelsDir, "selected-model.gguf.tmp")
            temp.outputStream().use { output -> input.copyTo(output) }
            if (!temp.renameTo(cachedModel)) {
                temp.copyTo(cachedModel, overwrite = true)
                temp.delete()
            }
        } ?: throw IOException("Не удалось открыть GGUF URI: $uri")

        sourceMarker.writeText(uri.toString())
        return cachedModel
    }

    private suspend fun waitForModelServer(process: ManagedLocalProcess?) {
        var lastError: Throwable? = null
        repeat(120) {
            val ready = runCatching { isModelServerReady() }.getOrElse { error ->
                lastError = error
                false
            }
            if (ready) {
                return
            }
            process?.exitCodeOrNull()?.let { code ->
                throw process.buildStartupFailure(
                    name = "llama-server",
                    exitCode = code,
                    fallback = lastError?.message ?: "Локальная GGUF модель завершилась до готовности.",
                )
            }
            delay(1000)
        }
        throw process?.buildStartupFailure(
            name = "llama-server",
            exitCode = process.exitCodeOrNull(),
            fallback = lastError?.message ?: "Локальная GGUF модель не успела подняться.",
        ) ?: IOException(lastError?.message ?: "Локальная GGUF модель не успела подняться.")
    }

    private suspend fun waitForLauncher(process: ManagedLocalProcess) {
        var lastError: Throwable? = null
        repeat(24) {
            val ready = runCatching { isLocalLauncherReady() }.getOrElse { error ->
                lastError = error
                false
            }
            if (ready) {
                return
            }

            process.exitCodeOrNull()?.let { code ->
                if (process.looksLikePortConflict()) {
                    repeat(6) {
                        delay(250)
                        val conflictedReady = runCatching { isLocalLauncherReady() }.getOrElse { error ->
                            lastError = error
                            false
                        }
                        if (conflictedReady) {
                            launcherProcess = null
                            AppDiagnostics.log("Attached to already running local RuClaw launcher after port conflict")
                            return
                        }
                    }
                }
                throw process.buildStartupFailure(
                    name = "ruclaw-launcher",
                    exitCode = code,
                    fallback = lastError?.message ?: "Локальный RuClaw завершился до готовности.",
                )
            }
            delay(500)
        }
        throw process.buildStartupFailure(
            name = "ruclaw-launcher",
            exitCode = process.exitCodeOrNull(),
            fallback = lastError?.message ?: "Локальный RuClaw не поднялся.",
        )
    }

    private fun isModelServerReady(): Boolean {
        return probeHttpReady(localModelApiBase + "/models") || probeHttpReady("http://127.0.0.1:1234/health")
    }

    private fun isLocalLauncherReady(): Boolean {
        return probeAuthorizedReady("$launcherUrl/api/pico/token", launcherToken)
    }

    private fun probeHttpReady(url: String): Boolean {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 1000
            readTimeout = 1000
        }
        return try {
            connection.connect()
            connection.responseCode in 200..299
        } finally {
            connection.disconnect()
        }
    }

    private fun probeAuthorizedReady(
        url: String,
        bearerToken: String,
    ): Boolean {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 1000
            readTimeout = 1000
            setRequestProperty("Authorization", "Bearer ${bearerToken.trim()}")
        }
        return try {
            connection.connect()
            connection.responseCode in 200..299
        } finally {
            connection.disconnect()
        }
    }

    private fun startProcess(
        name: String,
        command: List<String>,
        workspace: LocalRuntimeWorkspace,
        coreBinary: File,
    ): ManagedLocalProcess {
        val process = ProcessBuilder(command)
            .directory(workspace.root)
            .redirectErrorStream(true)
            .apply {
                environment()["HOME"] = workspace.homeDir.absolutePath
                environment()["TMPDIR"] = workspace.tmpDir.absolutePath
                environment()["PICOCLAW_HOME"] = workspace.homeDir.absolutePath
                environment()["PICOCLAW_CONFIG"] = File(workspace.homeDir, "config.json").absolutePath
                environment()["PICOCLAW_BINARY"] = coreBinary.absolutePath
                environment()["PICOCLAW_LAUNCHER_TOKEN"] = launcherToken
                environment()["LD_LIBRARY_PATH"] = nativeLibDir.absolutePath
            }
            .start()

        val recentOutput = RecentProcessOutput()
        watchProcessOutput(name, process, recentOutput)
        resolveProcessPid(process)?.let { pid ->
            persistProcessMarker(workspace, name, pid)
        }
        AppDiagnostics.log("Spawned local runtime process: $name")
        return ManagedLocalProcess(process, recentOutput)
    }

    private fun stopProcess(name: String, process: Process?) {
        if (process == null) {
            return
        }
        runCatching {
            process.destroy()
            if (!process.waitFor(2, TimeUnit.SECONDS)) {
                process.destroyForcibly()
                process.waitFor(2, TimeUnit.SECONDS)
            }
            AppDiagnostics.log("Local runtime process stopped: $name")
        }
    }

    private fun stopManagedProcess(
        name: String,
        process: Process?,
        workspace: LocalRuntimeWorkspace,
        markerFileName: String,
        fallbackMarkerFileName: String? = null,
        commandMarker: String,
    ) {
        stopProcess(name, process)

        val pidCandidates = buildList {
            readPersistedPid(File(workspace.root, markerFileName))?.let(::add)
            if (fallbackMarkerFileName != null) {
                readPersistedPid(File(workspace.homeDir, fallbackMarkerFileName))?.let(::add)
            }
        }.distinct()

        pidCandidates.forEach { pid ->
            if (pidMatchesCommand(pid, commandMarker)) {
                killPid(name, pid)
            }
        }

        runCatching { File(workspace.root, markerFileName).delete() }
    }

    private fun persistProcessMarker(
        workspace: LocalRuntimeWorkspace,
        name: String,
        pid: Long,
    ) {
        val markerFileName = when (name) {
            "ruclaw-launcher" -> launcherProcessMarkerFileName
            "llama-server" -> modelServerProcessMarkerFileName
            else -> return
        }
        File(workspace.root, markerFileName).writeText(pid.toString() + "\n")
    }

    private fun resolveProcessPid(process: Process): Long? {
        return runCatching {
            Process::class.java.getMethod("pid").invoke(process)
        }.getOrNull()
            ?.let { value ->
                when (value) {
                    is Long -> value
                    is Int -> value.toLong()
                    is Number -> value.toLong()
                    else -> null
                }
            }
            ?: runCatching {
                val field = process.javaClass.getDeclaredField("pid").apply {
                    isAccessible = true
                }
                when (val value = field.get(process)) {
                    is Long -> value
                    is Int -> value.toLong()
                    is Number -> value.toLong()
                    else -> null
                }
            }.getOrNull()
    }

    private fun readPersistedPid(file: File): Long? {
        if (!file.exists()) {
            return null
        }
        val raw = runCatching { file.readText() }.getOrNull()?.trim().orEmpty()
        if (raw.isBlank()) {
            return null
        }
        raw.toLongOrNull()?.let { return it }
        return runCatching {
            json.decodeFromString<JsonObject>(raw)["pid"]
                ?.jsonPrimitive
                ?.contentOrNull
                ?.toLongOrNull()
        }.getOrNull()
    }

    private fun pidMatchesCommand(
        pid: Long,
        commandMarker: String,
    ): Boolean {
        if (pid <= 0) {
            return false
        }
        val cmdline = runCatching {
            File("/proc/$pid/cmdline")
                .readBytes()
                .decodeToString()
                .replace('\u0000', ' ')
                .trim()
        }.getOrNull().orEmpty()
        return cmdline.contains(commandMarker, ignoreCase = true)
    }

    private fun killPid(
        name: String,
        pid: Long,
    ) {
        if (pid <= 0) {
            return
        }
        runCatching {
            val kill = ProcessBuilder("/system/bin/kill", "-9", pid.toString())
                .redirectErrorStream(true)
                .start()
            kill.waitFor(2, TimeUnit.SECONDS)
            AppDiagnostics.log("Killed stale $name process via PID marker: $pid")
        }
    }

    private fun watchProcessOutput(
        name: String,
        process: Process,
        recentOutput: RecentProcessOutput,
    ) {
        thread(name = "ruclaw-$name-log", isDaemon = true) {
            runCatching {
                process.inputStream.bufferedReader().useLines { lines ->
                    lines.forEach { line ->
                        if (line.isNotBlank()) {
                            recentOutput.add(line)
                            AppDiagnostics.log("$name: $line")
                        }
                    }
                }
            }
        }
        thread(name = "ruclaw-$name-exit", isDaemon = true) {
            runCatching {
                val code = process.waitFor()
                AppDiagnostics.log("$name exited with code $code")
            }
        }
    }

    private fun ManagedLocalProcess.exitCodeOrNull(): Int? {
        return if (process.isAlive) {
            null
        } else {
            runCatching { process.exitValue() }.getOrNull()
        }
    }

    private fun ManagedLocalProcess.looksLikePortConflict(): Boolean {
        val haystack = recentOutput.snapshot().joinToString("\n").lowercase()
        return haystack.contains("address already in use") ||
            haystack.contains("port already in use") ||
            haystack.contains("bind: address in use") ||
            haystack.contains("listen tcp") && haystack.contains("in use")
    }

    private fun ManagedLocalProcess.buildStartupFailure(
        name: String,
        exitCode: Int?,
        fallback: String,
    ): IOException {
        val tail = recentOutput.snapshot()
        val message = buildString {
            append(name)
            if (exitCode != null) {
                append(" завершился до готовности (exit code ")
                append(exitCode)
                append(").")
            } else {
                append(" не успел стать готовым.")
            }
            if (fallback.isNotBlank()) {
                append(' ')
                append(fallback.trim().removeSuffix("."))
                append('.')
            }
            if (tail.isNotEmpty()) {
                append(" Последние строки:\n")
                tail.forEachIndexed { index, line ->
                    if (index > 0) {
                        append('\n')
                    }
                    append(line)
                }
            }
        }
        return IOException(message)
    }

    private fun sanitizeModelId(value: String): String {
        return value.replace(Regex("[^A-Za-z0-9._-]+"), "-").trim('-').ifBlank { "local-gguf" }
    }

    private fun ensureSupportedAndroidVersion() {
        if (Build.VERSION.SDK_INT < minimumLocalRuntimeSdk) {
            throw IOException(
                "Локальный RuClaw на устройстве требует Android 9 (API 28) или новее.",
            )
        }
    }

    companion object {
        const val launcherUrl = "http://127.0.0.1:18800"
        const val launcherToken = "ruclaw-local"

        private const val assetDir = "runtime"
        private const val buildInfoAssetName = "BUILD_INFO.txt"
        private const val runtimeInfoFileName = "RUNTIME_INFO.json"
        private const val runtimeReadmeFileName = "README.txt"
        private const val coreBinaryLibraryFileName = "libruclaw_exec.so"
        private const val launcherBinaryLibraryFileName = "libruclaw_launcher_exec.so"
        private const val modelServerLibraryFileName = "libllama_server_exec.so"
        private const val localModelName = "local-gguf"
        private const val localModelApiBase = "http://127.0.0.1:1234/v1"
        private const val localDefaultMaxToolIterations = 64
        private const val minimumLocalRuntimeSdk = 28
        private const val externalStorageDocumentsAuthority = "com.android.externalstorage.documents"
        private const val launcherPidFileName = ".picoclaw.pid"
        private const val launcherProcessMarkerFileName = ".ruclaw-launcher.pid"
        private const val modelServerProcessMarkerFileName = ".llama-server.pid"
    }
}

private data class LocalRuntimeWorkspace(
    val root: File,
    val binDir: File,
    val homeDir: File,
    val logsDir: File,
    val modelsDir: File,
    val tmpDir: File,
)

data class LocalRuntimeInstallation(
    val runtimeVersion: String,
    val runtimeRoot: String,
    val launcherUrl: String,
    val launcherToken: String,
    val coreBinaryPath: String,
    val launcherBinaryPath: String,
    val modelServerBinaryPath: String,
)

data class LocalRuntimeConfig(
    val ggufPath: String,
    val dataDirectory: String,
    val forceRestart: Boolean = false,
    val telegram: LocalTelegramConfig = LocalTelegramConfig(),
)

data class LocalTelegramConfig(
    val enabled: Boolean = false,
    val botToken: String = "",
    val allowedUsers: String = "",
    val useMarkdownV2: Boolean = false,
)

data class LocalRuntimeConnection(
    val launcherUrl: String,
    val launcherToken: String,
    val runtimeVersion: String,
    val runtimeRoot: String,
)

private data class ManagedLocalProcess(
    val process: Process,
    val recentOutput: RecentProcessOutput,
)

private class RecentProcessOutput(
    private val limit: Int = 12,
) {
    private val lines = ArrayDeque<String>()

    @Synchronized
    fun add(line: String) {
        while (lines.size >= limit) {
            lines.removeFirst()
        }
        lines.addLast(line)
    }

    @Synchronized
    fun snapshot(): List<String> = lines.toList()
}
