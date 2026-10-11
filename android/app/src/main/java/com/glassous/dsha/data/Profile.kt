package com.glassous.dsha.data

import java.util.UUID

/**
 * 一个"连接组"：服务地址 + 账号 + 密码 + 该组的网页适配方式。
 *
 * 密码按需求明文保存在本机（自用内网场景），不做加密。
 * 持久化由 [ProfileRepository] 手写 JSON 完成，不再依赖序列化库。
 *
 * @param credentialsEnabled 是否在本组使用账号密码应答鉴权与自动填充；默认关闭，连接直接进入，
 *   已填写的凭据会被忽略。DeepSeek Harness 本身没有登录模块，账号密码只是 1Panel 等反向代理
 *   在入口加 Basic 鉴权的产物，因此改由每个组单独决定，而不是应用级统一开关。
 * @param adaptiveCompatEnabled 是否为此组注入该版本专属的组件级适配；默认开启，新建组即得到
 *   与之前一致的可读体验，需要时再单独关掉。关闭后只是不改组件，页面仍由基础注入层保证正常显示。
 * @param harnessVersion 适配所依据的 Harness 版本；默认取当前适配的最高版本，
 *   不同连接组可能连向不同版本的 Harness，因此版本随组存放而不是全局共用。
 * @param webAuthPassword 该部署「访问认证」插件（@fonlan/dsh-web-auth）的访问密码，由应用在本地弹窗里
 *   登录成功后记住；空串表示没记住过——此时编辑页不出现任何访问认证相关字段。与 [password] 一样明文存本机。
 * @param webAuthAutoLogin 是否在遇到该插件登录页时静默自动登录；默认关闭，只有记住了密码的连接组才谈得上开启。
 */
data class Profile(
    val id: String = newId(),
    val name: String = "",
    val baseUrl: String = "",
    val username: String = "",
    val password: String = "",
    val credentialsEnabled: Boolean = false,
    val lastUsedAt: Long = 0L,
    val adaptiveCompatEnabled: Boolean = true,
    val harnessVersion: HarnessVersion = HarnessVersion.latest,
    val webAuthPassword: String = "",
    val webAuthAutoLogin: Boolean = false,
) {
    /** 列表与工具栏上展示的名称：用户没填组名时退化为主机名。 */
    val displayName: String
        get() = name.ifBlank { UrlUtils.hostOf(baseUrl) ?: baseUrl }

    companion object {
        fun newId(): String = UUID.randomUUID().toString()
    }
}
