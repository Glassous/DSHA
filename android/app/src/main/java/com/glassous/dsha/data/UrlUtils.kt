package com.glassous.dsha.data

import android.net.Uri
import android.util.Base64

/** 地址解析、同站判定与鉴权头的公共工具，供仓库、登录客户端与 WebView 共用。 */
object UrlUtils {

    private const val HTTP = "http://"
    private const val HTTPS = "https://"

    /**
     * 归一化用户输入的服务地址：缺省协议补 `https://`，去掉首尾空白与末尾斜杠。
     * 无法解析出主机名时返回 null。
     */
    fun normalizeBaseUrl(raw: String): String? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null
        val withScheme = when {
            trimmed.startsWith(HTTPS, ignoreCase = true) || trimmed.startsWith(HTTP, ignoreCase = true) -> trimmed
            else -> HTTPS + trimmed
        }
        val uri = Uri.parse(withScheme)
        if (uri.host.isNullOrBlank()) return null
        return withScheme.trimEnd('/')
    }

    /** 取主机名（含端口之外的纯 host），解析失败返回 null。 */
    fun hostOf(url: String?): String? {
        if (url.isNullOrBlank()) return null
        return Uri.parse(url).host?.takeIf { it.isNotBlank() }
    }

    /** 判断目标地址是否属于同一站点（含子域），用于决定链接留在壳内还是交给系统浏览器。 */
    fun isSameSite(targetUrl: String?, baseUrl: String?): Boolean {
        val targetHost = hostOf(targetUrl) ?: return false
        val baseHost = hostOf(baseUrl) ?: return false
        return targetHost.equals(baseHost, ignoreCase = true) ||
            targetHost.endsWith(".$baseHost", ignoreCase = true)
    }

    /**
     * 生成 HTTP Basic 鉴权头。RFC 7617 要求 `user:pass` 先按 UTF-8 编码再 Base64。
     * 账号密码都为空时返回 null，表示不需要附带鉴权头。
     */
    fun basicAuthHeader(username: String, password: String): String? {
        if (username.isEmpty() && password.isEmpty()) return null
        val token = Base64.encodeToString(
            "$username:$password".toByteArray(Charsets.UTF_8),
            Base64.NO_WRAP,
        )
        return "Basic $token"
    }
}
