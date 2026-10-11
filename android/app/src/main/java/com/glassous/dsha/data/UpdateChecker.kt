package com.glassous.dsha.data

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * 一次可用发布的摘要。
 *
 * @param version 归一化后的版本号（如 `1.0.1`），用于弹窗标题与版本比较
 * @param changelog 发布说明原文（Markdown），交给界面渲染
 * @param downloadUrl APK 直链，用浏览器打开即可下载
 */
data class ReleaseInfo(
    val version: String,
    val changelog: String,
    val downloadUrl: String,
)

/**
 * 更新检测：向 GitHub 查询本仓库的最新正式发布。
 *
 * 只用平台自带的 [HttpURLConnection] 与 org.json，不引入网络库与序列化库。
 * 一切异常——无网络、匿名请求被限流、响应结构变化、仓库还没发布过 Release——
 * 都归一为 null：更新提示是锦上添花，取不到就当没有，绝不能影响启动。
 */
object UpdateChecker {

    /** 发布页地址：发布里没有 APK 附件时用它兜底，用户仍能在网页上看到说明与附件。 */
    const val RELEASES_PAGE = "https://github.com/Glassous/DSHA/releases"

    private const val LATEST_RELEASE_API =
        "https://api.github.com/repos/Glassous/DSHA/releases/latest"

    /** GitHub 要求带 User-Agent，并推荐用 Accept 指定版本化的媒体类型。 */
    private const val USER_AGENT = "DSHA-Android"
    private const val ACCEPT = "application/vnd.github+json"

    private const val TIMEOUT_MS = 10_000
    private const val TAG = "UpdateChecker"

    /**
     * APK 附件的文件名格式：`DSHA-v1.0.1.apk`，与 [app/build.gradle.kts] 里配置的输出名一致，
     * 因此 tag 与附件名天然对应，这里优先挑同名的那一个。
     */
    private fun apkNameOf(version: String): String = "DSHA-v$version.apk"

    /** 最新正式发布（跳过草稿与预发布）；查询失败、版本号无法解析时返回 null。 */
    suspend fun latestRelease(): ReleaseInfo? = withContext(Dispatchers.IO) {
        runCatching { request() }
            .onFailure { Log.w(TAG, "查询最新发布失败", it) }
            .getOrNull()
    }

    private fun request(): ReleaseInfo? {
        val connection = (URL(LATEST_RELEASE_API).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            setRequestProperty("User-Agent", USER_AGENT)
            setRequestProperty("Accept", ACCEPT)
        }
        return try {
            val code = connection.responseCode
            when (code) {
                HttpURLConnection.HTTP_OK ->
                    connection.inputStream.bufferedReader().use { parse(it.readText()) }

                // 404：仓库还没有正式发布；403：匿名请求被限流。都按"没有更新"处理
                else -> {
                    Log.i(TAG, "最新发布不可用：HTTP $code")
                    null
                }
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun parse(body: String): ReleaseInfo? {
        val json = JSONObject(body)
        // tag 是唯一的版本事实来源：解析不出来的发布直接忽略，避免弹出一个版本号看不懂的更新
        val version = SemanticVersion.normalize(json.optString("tag_name")) ?: return null
        val downloadUrl = findApkUrl(json.optJSONArray("assets"), version)
            ?: json.optString("html_url").takeIf { it.isNotBlank() }
            ?: RELEASES_PAGE
        return ReleaseInfo(
            version = version,
            changelog = json.optString("body"),
            downloadUrl = downloadUrl,
        )
    }

    /** 取 APK 附件直链：优先与版本号同名的那一个，其次任意以 .apk 结尾的附件。 */
    private fun findApkUrl(assets: JSONArray?, version: String): String? {
        if (assets == null) return null
        val expected = apkNameOf(version)
        var fallback: String? = null
        for (index in 0 until assets.length()) {
            val asset = assets.optJSONObject(index) ?: continue
            val name = asset.optString("name")
            val url = asset.optString("browser_download_url")
            if (name.isBlank() || url.isBlank()) continue
            if (!name.endsWith(".apk", ignoreCase = true)) continue
            if (name.equals(expected, ignoreCase = true)) return url
            if (fallback == null) fallback = url
        }
        return fallback
    }
}
