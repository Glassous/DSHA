package com.glassous.dsha.ui.web

/** 主框架加载失败的分类：决定错误页的文案与「复制错误信息」的内容。 */
enum class WebErrorKind {
    /** 服务器返回了 4xx / 5xx 状态码。 */
    Http,

    /** 网络层失败：连不上、超时、域名解析不了等。 */
    Network,

    /** Basic 鉴权未通过（账号密码不对，或连接组里没填）。 */
    Auth,
}

/**
 * 一次主框架加载失败的信息。
 *
 * 界面只展示 [code]（如 "404" / "-2" / "401"）：用户需要的是一个能对上服务端日志的编号，
 * 而不是一屏解释。更完整的上下文放在 [detail] 与 [url] 里，由「复制错误信息」交给用户。
 */
data class WebLoadError(
    val kind: WebErrorKind,
    val code: String,
    val detail: String = "",
    val url: String? = null,
)
