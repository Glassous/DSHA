package com.glassous.dsha.data

import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * 一个"连接组"：服务地址 + 账号 + 密码。
 *
 * 密码按需求明文保存在本机（自用内网场景），不做加密。
 */
@Serializable
data class Profile(
    val id: String = newId(),
    val name: String = "",
    val baseUrl: String = "",
    val username: String = "",
    val password: String = "",
    val lastUsedAt: Long = 0L,
) {
    /** 列表与工具栏上展示的名称：用户没填组名时退化为主机名。 */
    val displayName: String
        get() = name.ifBlank { UrlUtils.hostOf(baseUrl) ?: baseUrl }

    companion object {
        fun newId(): String = UUID.randomUUID().toString()
    }
}
