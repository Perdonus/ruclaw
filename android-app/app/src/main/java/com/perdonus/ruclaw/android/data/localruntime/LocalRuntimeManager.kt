package com.perdonus.ruclaw.android.data.localruntime

import android.content.Context
import android.net.Uri
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
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromString
import kotlinx.serialization.json.encodeToString
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class LocalRuntimeManager(context: Context) {
    private val appContext = context.applicationContext
    private val runtimeRoot = File(appContext.filesDir, "local-runtime")
    private val binDir = File(runtimeRoot, "bin")
    private val homeDir = File(runtimeRoot, "home")
    private val logsDir = File(runtimeRoot, "logs")
    private val modelsDir = File(runtimeRoot, "models")
    private val tmpDir = File(runtimeRoot, "tmp")
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    @Volatile
    private var launcherProcess: Process? = null

    @Volatile
    private var modelServerProcess: Process? = null

    suspend fun install(log: suspend (String) -> Unit): LocalRuntimeInstallation = withContext(Dispatchers.IO) {
        log("Готовлю sandbox для локального RuClaw…")
        runtimeRoot.mkdirs()
        binDir.mkdirs()
        homeDir.mkdirs()
        logsDir.mkdirs()
        modelsDir.mkdirs()
        tmpDir.mkdirs()

        val coreBinary = extractBundledBinary(coreBinaryAssetName, File(binDir, coreBinaryAssetName), log)
        val launcherBinary = extractBundledBinary(launcherBinaryAssetName, File(binDir, launcherBinaryAssetName), log)
        val modelServerBinary = extractOptionalBundledBinary(
            modelServerAssetName,
            File(binDir, modelServerAssetName),
            log,
        )

        File(runtimeRoot, "VERSION.txt").writeText(BuildConfig.VERSION_NAME + "\n")
        log("Локальный runtime готов.")

        LocalRuntimeInstallation(
            runtimeVersion = BuildConfig.VERSION_NAME,
            runtimeRoot = runtimeRoot.absolutePath,
            launcherUrl = launcherUrl,
            launcherToken = launcherToken,
            coreBinaryPath = coreBinary.absolutePath,
            launcherBinaryPath = launcherBinary.absolutePath,
            modelServerBinaryPath = modelServerBinary?.absolutePath.orEmpty(),
        )
    }

    suspend fun startLocalRuntime(config: LocalRuntimeConfig): LocalRuntimeConnection = withContext(Dispatchers.IO) {
        val ggufFile = config.ggufPath.trim().takeIf { it.isNotBlank() }?.let(::resolveGgufFile)

        val coreBinary = File(binDir, coreBinaryAssetName)
        val launcherBinary = File(binDir, launcherBinaryAssetName)
        val modelServerBinary = File(binDir, modelServerAssetName)
        val required = buildList {
            add(coreBinary)
            add(launcherBinary)
        }
        val missing = required.firstOrNull { !it.exists() || !it.canExecute() }
        if (missing != null) {
            throw IOException("Локальный runtime не установлен полностью. Сначала нажми «Установить».")
        }
        if (ggufFile != null && (!modelServerBinary.exists() || !modelServerBinary.canExecute())) {
            throw IOException(
                "В этом APK нет локального model server для GGUF. " +
                    "Либо очисти путь до GGUF, либо поставь Android-релиз с bundled llama-server.",
            )
        }

        stopLocalRuntime()
        writeLauncherConfig()
        updateAppConfig(ggufFile)

        modelServerProcess = if (ggufFile != null) {
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
            )
        } else {
            null
        }
        if (ggufFile != null) {
            waitForModelServer()
        }

        launcherProcess = startProcess(
            name = "ruclaw-launcher",
            command = listOf(
                launcherBinary.absolutePath,
                "-no-browser",
                "-lang",
                "ru",
                "-port",
                "18800",
            ),
        )

        LocalRuntimeConnection(
            launcherUrl = launcherUrl,
            launcherToken = launcherToken,
            runtimeVersion = BuildConfig.VERSION_NAME,
            runtimeRoot = runtimeRoot.absolutePath,
        )
    }

    suspend fun stopLocalRuntime() = withContext(Dispatchers.IO) {
        stopProcess("ruclaw-launcher", launcherProcess)
        launcherProcess = null
        stopProcess("llama-server", modelServerProcess)
        modelServerProcess = null
    }

    private suspend fun extractBundledBinary(
        assetName: String,
        target: File,
        log: suspend (String) -> Unit,
    ): File {
        log("Распаковываю $assetName…")
        try {
            appContext.assets.open("$assetDir/$assetName").use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            }
        } catch (error: FileNotFoundException) {
            throw IOException(
                "В этом APK нет встроенного локального runtime ($assetName). " +
                    "Нужен релиз Android с bundled local binaries.",
            )
        }
        target.setReadable(true, true)
        target.setWritable(true, true)
        if (!target.setExecutable(true, true)) {
            throw IOException("Не удалось выставить флаг запуска для ${target.name}")
        }
        return target
    }

    private suspend fun extractOptionalBundledBinary(
        assetName: String,
        target: File,
        log: suspend (String) -> Unit,
    ): File? {
        return try {
            extractBundledBinary(assetName, target, log)
        } catch (_: IOException) {
            runCatching { target.delete() }
            log("Пропускаю $assetName: локальный GGUF останется опциональным.")
            null
        }
    }

    private fun writeLauncherConfig() {
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

    private fun updateAppConfig(ggufFile: File?) {
        val file = File(homeDir, "config.json")
        if (!file.exists() && ggufFile == null) {
            return
        }

        val root = if (file.exists()) {
            runCatching { json.decodeFromString<JsonObject>(file.readText()) }.getOrElse { JsonObject(emptyMap()) }
        } else {
            JsonObject(emptyMap())
        }
        val existingModels = root["model_list"]?.jsonArray?.toList().orEmpty()
        val filteredModels = existingModels.filterNot { element ->
            element.jsonObject["model_name"]?.jsonPrimitive?.content == localModelName
        }
        val currentAgents = root["agents"]?.jsonObject ?: JsonObject(emptyMap())
        val currentDefaults = currentAgents["defaults"]?.jsonObject ?: JsonObject(emptyMap())

        if (ggufFile == null) {
            val nextDefaults = buildJsonObject {
                currentDefaults.forEach { (key, value) ->
                    if (!(key == "model_name" && value.jsonPrimitive.contentOrNull == localModelName)) {
                        put(key, value)
                    }
                }
            }
            val nextRoot = buildJsonObject {
                root.forEach { (key, value) ->
                    if (key != "model_list" && key != "agents") {
                        put(key, value)
                    }
                }
                if (filteredModels.isNotEmpty() || root.containsKey("model_list")) {
                    put("model_list", JsonArray(filteredModels))
                }
                if (currentAgents.isNotEmpty() || root.containsKey("agents")) {
                    put(
                        "agents",
                        buildJsonObject {
                            currentAgents.forEach { (key, value) ->
                                if (key != "defaults") {
                                    put(key, value)
                                }
                            }
                            if (nextDefaults.isNotEmpty() || currentAgents.containsKey("defaults")) {
                                put("defaults", nextDefaults)
                            }
                        },
                    )
                }
            }
            file.parentFile?.mkdirs()
            file.writeText(json.encodeToString(nextRoot) + "\n")
            return
        }

        val modelId = sanitizeModelId(ggufFile.nameWithoutExtension.ifBlank { "local-gguf" })
        val nextModels = filteredModels + buildJsonObject {
            put("model_name", JsonPrimitive(localModelName))
            put("model", JsonPrimitive("lmstudio/openai/$modelId"))
            put("api_base", JsonPrimitive(localModelApiBase))
        }

        val nextRoot = buildJsonObject {
            root.forEach { (key, value) ->
                if (key != "model_list" && key != "agents") {
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
                            currentDefaults.forEach { (key, value) ->
                                put(key, value)
                            }
                            put("model_name", JsonPrimitive(localModelName))
                        },
                    )
                },
            )
        }

        file.parentFile?.mkdirs()
        file.writeText(json.encodeToString(nextRoot) + "\n")
    }

    private fun resolveGgufFile(pathOrUri: String): File {
        return if (pathOrUri.startsWith("content://", ignoreCase = true)) {
            materializeGgufUri(Uri.parse(pathOrUri))
        } else {
            File(pathOrUri).also { file ->
                if (!file.exists() || !file.isFile) {
                    throw IOException("GGUF файл не найден: $pathOrUri")
                }
            }
        }
    }

    private fun materializeGgufUri(uri: Uri): File {
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

    private suspend fun waitForModelServer() {
        var lastError: Throwable? = null
        repeat(120) {
            runCatching {
                if (probeHttpReady(localModelApiBase + "/models") || probeHttpReady("http://127.0.0.1:1234/health")) {
                    return
                }
            }.onFailure { error ->
                lastError = error
            }
            delay(1000)
        }
        throw IOException(lastError?.message ?: "Локальная GGUF модель не успела подняться.")
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

    private fun startProcess(
        name: String,
        command: List<String>,
    ): Process {
        val process = ProcessBuilder(command)
            .directory(runtimeRoot)
            .redirectErrorStream(true)
            .apply {
                environment()["HOME"] = homeDir.absolutePath
                environment()["TMPDIR"] = tmpDir.absolutePath
                environment()["PICOCLAW_HOME"] = homeDir.absolutePath
                environment()["PICOCLAW_CONFIG"] = File(homeDir, "config.json").absolutePath
                environment()["PICOCLAW_BINARY"] = File(binDir, coreBinaryAssetName).absolutePath
                environment()["PICOCLAW_LAUNCHER_TOKEN"] = launcherToken
            }
            .start()

        watchProcessOutput(name, process)
        if (process.waitFor(400, TimeUnit.MILLISECONDS)) {
            throw IOException("$name завершился раньше запуска.")
        }
        AppDiagnostics.log("Local runtime process started: $name")
        return process
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

    private fun watchProcessOutput(
        name: String,
        process: Process,
    ) {
        thread(name = "ruclaw-$name-log", isDaemon = true) {
            runCatching {
                process.inputStream.bufferedReader().useLines { lines ->
                    lines.forEach { line ->
                        if (line.isNotBlank()) {
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

    private fun sanitizeModelId(value: String): String {
        return value.replace(Regex("[^A-Za-z0-9._-]+"), "-").trim('-').ifBlank { "local-gguf" }
    }

    companion object {
        const val launcherUrl = "http://127.0.0.1:18800"
        const val launcherToken = "ruclaw-local"

        private const val assetDir = "runtime"
        private const val coreBinaryAssetName = "ruclaw"
        private const val launcherBinaryAssetName = "ruclaw-launcher"
        private const val modelServerAssetName = "llama-server"
        private const val localModelName = "local-gguf"
        private const val localModelApiBase = "http://127.0.0.1:1234/v1"
    }
}

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
)

data class LocalRuntimeConnection(
    val launcherUrl: String,
    val launcherToken: String,
    val runtimeVersion: String,
    val runtimeRoot: String,
)
