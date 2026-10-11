package com.glassous.dsha.ui.web.adapt

import android.util.Log
import android.webkit.WebView
import com.glassous.dsha.data.HarnessVersion
import java.util.Locale

/**
 * 注入执行器：把某一层（基础层 / 某版本的定制层）的 CSS 与 JS 组装成一段脚本交给 WebView。
 *
 * 两层各写各的样式节点（id 不同），互不覆盖，也不必争谁先谁后：
 * - 基础层 [applyBaseAdapt]：只保证页面能正常显示，任何版本、开关关闭时都施加；
 * - 版本层 [applyVersionAdapt]：该版本专属的组件级改造，只在连接组开启响应式适配时施加。
 *
 * 脚本内容本身不在这里，见 [BASE_ADAPT_CSS]/[BASE_ADAPT_JS] 与 `adapt/<版本>/` 各目录。
 */
private const val TAG = "DshaAdapt"

/** 基础层与版本层各自的样式节点 id：两层各自幂等，重复调用只刷新数值，不叠加 DOM。 */
private const val BASE_STYLE_ID = "dsha-base-adapt"
private const val ADAPT_STYLE_ID = "dsha-version-adapt"

/** 版本层脚本用到的 DOM 锚点与标记属性（基础层不涉及这些）。 */
private const val SCRIM_ID = "dsha-drawer-scrim"
private const val NAV_ID = "dsha-sidebar-button"
private const val SETTINGS_FLAG_ATTR = "data-dsha-settings"

/** 手机断点上限（CSS px = dp），与 Android sw600dp 平板分界对齐；600dp 恰为平板，故媒体查询取 599.98px。 */
private const val MOBILE_MEDIA_LIMIT = "599.98"

private const val TOKEN_TOP = "__DSHA_SAFE_TOP__"
private const val TOKEN_BOTTOM = "__DSHA_SAFE_BOTTOM__"
private const val TOKEN_LIMIT = "__DSHA_MOBILE_LIMIT__"
private const val TOKEN_STYLE_ID = "__DSHA_STYLE_ID__"
private const val TOKEN_SCRIM_ID = "__DSHA_SCRIM_ID__"
private const val TOKEN_NAV_ID = "__DSHA_NAV_ID__"
private const val TOKEN_SETTINGS_ATTR = "__DSHA_SETTINGS_ATTR__"
private const val TOKEN_CSS = "__DSHA_CSS__"

/**
 * 注入基础层：安全区与视口兜底所需的样式与变量。
 *
 * 无条件施加——塌陷是 WebView 视口单位解析的问题，与 Harness 版本无关，
 * 不解决它连"基本页面显示"都做不到。
 *
 * @param safeTopDp 状态栏高度（dp，即网页 CSS px）
 * @param safeBottomDp 导航栏高度（dp，即网页 CSS px）；软键盘弹出时传 0
 */
internal fun applyBaseAdapt(webView: WebView, safeTopDp: Float, safeBottomDp: Float) {
    inject(
        webView = webView,
        tag = "base",
        styleId = BASE_STYLE_ID,
        css = BASE_ADAPT_CSS,
        js = BASE_ADAPT_JS,
        safeTopDp = safeTopDp,
        safeBottomDp = safeBottomDp,
    )
}

/**
 * 注入连接组所选版本的定制适配：手机断点下的结构性改造。
 *
 * @param version 连接组选定的 Harness 版本，分派到 `adapt/` 下对应目录的实现
 * @param safeTopDp 状态栏高度（dp，即网页 CSS px）
 * @param safeBottomDp 导航栏高度（dp，即网页 CSS px）；软键盘弹出时传 0
 */
internal fun applyVersionAdapt(
    webView: WebView,
    version: HarnessVersion,
    safeTopDp: Float,
    safeBottomDp: Float,
) {
    val adapt = versionAdaptOf(version)
    inject(
        webView = webView,
        tag = "version=${version.label}",
        styleId = ADAPT_STYLE_ID,
        css = adapt.css,
        js = adapt.js,
        safeTopDp = safeTopDp,
        safeBottomDp = safeBottomDp,
    )
}

/** 把一层脚本的占位符替换成实际取值后交给 WebView 执行。 */
private fun inject(
    webView: WebView,
    tag: String,
    styleId: String,
    css: String,
    js: String,
    safeTopDp: Float,
    safeBottomDp: Float,
) {
    val tokens = mapOf(
        TOKEN_TOP to fmt(safeTopDp),
        TOKEN_BOTTOM to fmt(safeBottomDp),
        TOKEN_LIMIT to MOBILE_MEDIA_LIMIT,
        TOKEN_STYLE_ID to styleId,
        TOKEN_SCRIM_ID to SCRIM_ID,
        TOKEN_NAV_ID to NAV_ID,
        TOKEN_SETTINGS_ATTR to SETTINGS_FLAG_ATTR,
    )
    val resolvedCss = tokens.entries.fold(css) { acc, (key, value) -> acc.replace(key, value) }
    val script = tokens.entries.fold(js) { acc, (key, value) -> acc.replace(key, value) }
        .replace(TOKEN_CSS, resolvedCss.toJsStringLiteral())
    webView.evaluateJavascript(script) { result ->
        Log.d(TAG, "注入 $tag top=${fmt(safeTopDp)} bottom=${fmt(safeBottomDp)} -> $result")
    }
}

private fun fmt(value: Float): String = String.format(Locale.US, "%.2f", value)

/**
 * 折成 JS 字符串字面量：CSS 里的换行会破坏字面量，双引号与反斜杠需要转义。
 */
private fun String.toJsStringLiteral(): String {
    val body = replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\r", " ")
        .replace("\n", " ")
    return "\"" + body + "\""
}
