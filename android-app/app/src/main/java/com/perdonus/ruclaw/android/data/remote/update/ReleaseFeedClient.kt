package com.perdonus.ruclaw.android.data.remote.update

import com.perdonus.ruclaw.android.BuildConfig
import java.io.IOException
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request

class ReleaseFeedClient(
    private val httpClient: OkHttpClient,
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun fetchLatestRelease(): ReleaseFeedEntry = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("https://api.github.com/repos/${BuildConfig.UPDATE_REPO_OWNER}/${BuildConfig.UPDATE_REPO_NAME}/releases/latest")
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .header("User-Agent", "ruclaw-android")
            .get()
            .build()

        httpClient.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IOException(body.ifBlank { "GitHub release feed вернул HTTP ${response.code}" })
            }

            val release = json.decodeFromString<GitHubReleaseDto>(body)
            val apkAsset = release.assets.firstOrNull { it.name == BuildConfig.UPDATE_APK_ASSET_NAME }
                ?: throw IOException("В latest release нет ${BuildConfig.UPDATE_APK_ASSET_NAME}")
            val shaAsset = release.assets.firstOrNull { it.name == BuildConfig.UPDATE_SHA256_ASSET_NAME }

            ReleaseFeedEntry(
                tagName = release.tagName,
                versionName = release.tagName.removePrefix("v"),
                htmlUrl = release.htmlUrl,
                releaseNotes = release.body.trim().take(4000),
                apkUrl = apkAsset.browserDownloadUrl,
                apkSha256Url = shaAsset?.browserDownloadUrl.orEmpty(),
                publishedAtEpochMillis = parseTimestamp(release.publishedAt),
                apkSizeBytes = apkAsset.size,
            )
        }
    }

    private fun parseTimestamp(value: String?): Long {
        if (value.isNullOrBlank()) return System.currentTimeMillis()
        return runCatching { Instant.parse(value).toEpochMilli() }
            .getOrDefault(System.currentTimeMillis())
    }
}

data class ReleaseFeedEntry(
    val tagName: String,
    val versionName: String,
    val htmlUrl: String,
    val releaseNotes: String,
    val apkUrl: String,
    val apkSha256Url: String,
    val publishedAtEpochMillis: Long,
    val apkSizeBytes: Long,
)

@Serializable
private data class GitHubReleaseDto(
    @SerialName("tag_name")
    val tagName: String = "",
    @SerialName("html_url")
    val htmlUrl: String = "",
    @SerialName("published_at")
    val publishedAt: String? = null,
    val body: String = "",
    val assets: List<GitHubAssetDto> = emptyList(),
)

@Serializable
private data class GitHubAssetDto(
    val name: String = "",
    val size: Long = 0L,
    @SerialName("browser_download_url")
    val browserDownloadUrl: String = "",
)
