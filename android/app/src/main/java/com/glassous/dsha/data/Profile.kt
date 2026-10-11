package com.glassous.dsha.data

import java.util.UUID

/**
 * 一个"连接组"：服务地址 + 账号 + 密码 + 该组的网页适配方式。
 *
 * 密码按需求明文保存在本机（自用内网场景），不做加密。
 * 持久化由 [ProfileRepository] 手写 JSON 完成，不再依赖序列化库。
 *
 * @param adaptiveCompatEnabled 是否为此组注入该版本专属的组件级适配；默认开启，新建组即得到
 *   与之前一致的可读体验，需要时再单独关掉。关闭后只是不改组件，页面仍由基础注入层保证正常显示。
 * @param harnessVersion 适配所依据的 Harness 版本；默认取当前适配的最高版本，
 *   不同连接组可能连向不同版本的 Harness，因此版本随组存放而不是全局共用。
 */
data class Profile(
    val id: String = newId(),
    val name: String = "",
    val baseUrl: String = "",
    val username: String = "",
    val password: String = "",
    val lastUsedAt: Long = 0L,
    val adaptiveCompatEnabled: Boolean = true,
    val harnessVersion: HarnessVersion = HarnessVersion.latest,
) {
    /** 列表与工具栏上展示的名称：用户没填组名时退化为主机名。 */
    val displayName: String
        get() = name.ifBlank { UrlUtils.hostOf(baseUrl) ?: baseUrl }

    companion object {
        fun newId(): String = UUID.randomUUID().toString()
    }
}
