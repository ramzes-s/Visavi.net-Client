package com.ramzes.visavinet.network

import com.google.gson.annotations.SerializedName

data class GitHubAsset(
    @SerializedName("name") val name: String? = null,
    @SerializedName("browser_download_url") val downloadUrl: String? = null,
    @SerializedName("size") val size: Long = 0,
    @SerializedName("content_type") val contentType: String? = null
)

data class GitHubRelease(
    @SerializedName("tag_name") val tagName: String? = null,
    @SerializedName("name") val name: String? = null,
    @SerializedName("html_url") val htmlUrl: String? = null,
    @SerializedName("body") val body: String? = null,
    @SerializedName("published_at") val publishedAt: String? = null,
    @SerializedName("assets") val assets: List<GitHubAsset>? = null
) {
    val apkDownloadUrl: String?
        get() = assets?.firstOrNull { it.name?.endsWith(".apk", ignoreCase = true) == true }?.downloadUrl
            ?: assets?.firstOrNull()?.downloadUrl
}

/**
 * Сравнение двух версий по правилам Semantic Versioning.
 * Возвращает true, если latestVersion новее currentVersion.
 */
fun isNewerVersion(currentVersion: String, latestVersion: String): Boolean {
    val cleanCurrent = currentVersion.removePrefix("v").trim()
    val cleanLatest = latestVersion.removePrefix("v").trim()
    if (cleanCurrent == cleanLatest) return false

    val currentParts = cleanCurrent.split(".").mapNotNull { it.toIntOrNull() }
    val latestParts = cleanLatest.split(".").mapNotNull { it.toIntOrNull() }

    val maxLen = maxOf(currentParts.size, latestParts.size)
    for (i in 0 until maxLen) {
        val c = currentParts.getOrElse(i) { 0 }
        val l = latestParts.getOrElse(i) { 0 }
        if (l > c) return true
        if (l < c) return false
    }
    return false
}
