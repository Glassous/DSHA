package com.glassous.dsha.ui.web

import android.util.Log
import android.webkit.WebView
import org.json.JSONObject
import org.json.JSONTokener

/**
 * 访问认证闸门：识别 `@fonlan/dsh-web-auth` 插件的登录页/首次设置密码页，把认证过程交给本地弹窗接管。
 *
 * 该插件未登录时会把整页 302 重定向到它自己服务端渲染的 `/login` 页面（登录态与首次设置态共用同一张表单，
 * 设置态多一个确认密码框）。本文件只负责「认页面」与「驱动页面提交」两件事：
 *
 * - 认页面：[buildWebAuthDetectScript] 以 DOM 签名为准（`/login` 表单里的密码框），比只看 URL 更能区分
 *   登录/设置两种形态，也不会误伤普通含密码框的页面；
 * - 驱动提交：[buildWebAuthSubmitScript] 填好输入框并触发表单提交，让 WebView 自己完成 POST、302、回跳
 *   与 HttpOnly 签名 Cookie 的下发——与既有 Basic 鉴权自动填充同一套手法（不猜服务端接口，只操作 DOM），
 *   因此天然兼容表单里的 `next` 隐藏字段与后续可能的页面改动。
 *
 * 界面侧的闸门状态（弹窗 / 自动登录中）见 [WebAuthGate]。
 */
private const val TAG = "DshaWebAuth"

/** 插件要求的访问密码最短长度：本地校验与服务端 `minlength` 保持一致。 */
internal const val WEB_AUTH_MIN_PASSWORD_LENGTH = 8

/** 插件访问认证页的两种形态。 */
enum class WebAuthMode {
    /** 已设置过密码：要求输入访问密码。 */
    LOGIN,

    /** 还没设置过密码：首次使用，要求设置并确认新密码。 */
    SETUP,
}

/**
 * 一次访问认证页检测的结果。
 *
 * @param mode 页面形态
 * @param errorText 服务端回显的错误文案（页面上的 `p.error`），为空表示这次加载没有失败提示
 * @param loggedOut 是否是「已退出登录」页：这种页面不静默自动重登，交给用户自己决定
 */
data class WebAuthPage(
    val mode: WebAuthMode,
    val errorText: String = "",
    val loggedOut: Boolean = false,
)

/**
 * 访问认证闸门状态：非空即表示「插件登录页正被本地接管」，此时启动动画覆盖层强制常驻，
 * 网页里的登录页不会露出来。
 */
sealed interface WebAuthGate {

    /** 等待用户输入；[setup] 为首次设置密码，[errorText] 非空时弹窗里同时给出失败提示。 */
    data class Prompt(val setup: Boolean, val errorText: String) : WebAuthGate

    /** 正在用已记住的密码静默自动登录：加载动画下方的文案切换为「自动登录中」。 */
    data object AutoLoggingIn : WebAuthGate

    /** 已提交、等待页面跳转（弹窗收起后的等待期，覆盖层保持）。 */
    data object Submitting : WebAuthGate
}

/**
 * 定位插件登录表单的 JS 片段：检测、提交、Basic 跳过三处共用同一份判定，
 * 避免「认得出页面」与「提交到哪个表单」出现分歧。
 *
 * 判定要件：`action` 路径以 `login` 结尾，且表单里有名为 `password` 的密码框。
 * 先按 action 找；找不到再按字段名找密码框并回溯它所在的表单——这样既兼容插件将来把 action
 * 写成相对路径/带查询串的形态，又不会认下普通站点里名字恰好也叫 password 的输入框。
 */
private const val WEB_AUTH_FORM_FINDER_JS = """
        function __dshaWebAuthForm() {
          var form = document.querySelector('form[action${'$'}="/login"]');
          var pwd = form ? form.querySelector('input[name=password][type=password]') : null;
          if (!form || !pwd) {
            var el = document.querySelector('input[name=password][type=password]');
            form = el ? el.form : null;
            pwd = el || null;
          }
          if (!form || !pwd) { return null; }
          var action = form.getAttribute('action') || '';
          var path = action.split('?')[0].replace(/\/+${'$'}/, '');
          if (!/(^|\/)login${'$'}/.test(path)) { return null; }
          return { form: form, pwd: pwd };
        }
"""

/**
 * 「本页是访问认证插件登录页」的判定表达式，供 Basic 鉴权自动填充复用。
 *
 * 两种机制都以密码框为锚点，同时启用时会互相抢填：命中插件登录页时让访问认证接管，
 * Basic 自动填充直接跳过。
 */
internal val WEB_AUTH_FILL_GUARD = """
    (function () {
      try {
$WEB_AUTH_FORM_FINDER_JS
        return __dshaWebAuthForm() !== null;
      } catch (e) {
        return false;
      }
    })()
"""

/**
 * 检测脚本：返回 `none`，或一段 JSON（`mode` / `error` / `loggedOut`），或空串表示脚本异常。
 *
 * 命中后先 blur 当前焦点元素：该页的 `#password` 带 `autofocus`，不 blur 会让网页把输入法抢走，
 * 与本地弹窗的输入框打架。返回前把结果 `JSON.stringify`，由 [parseWebAuthPage] 两步解码。
 */
internal val WEB_AUTH_DETECT_SCRIPT = """
    (function () {
      try {
$WEB_AUTH_FORM_FINDER_JS
        var found = __dshaWebAuthForm();
        if (!found) { return 'none'; }
        var confirm = found.form.querySelector('input[name=confirm][type=password]');
        var errorNode = document.querySelector('p.error');
        var infoNode = document.querySelector('p.info');
        try {
          if (document.activeElement && document.activeElement.blur) { document.activeElement.blur(); }
        } catch (blurError) {}
        return JSON.stringify({
          mode: confirm ? 'setup' : 'login',
          error: errorNode ? (errorNode.textContent || '').trim() : '',
          loggedOut: infoNode !== null
        });
      } catch (e) {
        return '';
      }
    })();
"""

/**
 * 提交脚本：把密码填进页面表单并触发提交。
 *
 * 与既有 Basic 鉴权自动填充一致，走原型上的原生 setter 再派发 `input`/`change`，
 * 这样页面自身的校验与监听都能正常感知。确认框只在首次设置态存在，没有就跳过。
 * 提交前先 `checkValidity()`：万一填进去的值过不了页面自身校验，直接回报 `invalid`，
 * 由界面层退回弹窗，避免覆盖层下静默卡住。
 */
internal fun buildWebAuthSubmitScript(password: String, confirm: String): String {
    val quotedPassword = JSONObject.quote(password)
    val quotedConfirm = JSONObject.quote(confirm)
    return """
        (function () {
          try {
$WEB_AUTH_FORM_FINDER_JS
            var found = __dshaWebAuthForm();
            if (!found) { return 'nopage'; }
            function setValue(field, value) {
              var desc = Object.getOwnPropertyDescriptor(Object.getPrototypeOf(field), 'value');
              if (desc && desc.set) { desc.set.call(field, value); } else { field.value = value; }
              field.dispatchEvent(new Event('input', { bubbles: true }));
              field.dispatchEvent(new Event('change', { bubbles: true }));
            }
            setValue(found.pwd, $quotedPassword);
            var confirmField = found.form.querySelector('input[name=confirm][type=password]');
            if (confirmField) { setValue(confirmField, $quotedConfirm); }
            if (typeof found.form.checkValidity === 'function' && !found.form.checkValidity()) {
              return 'invalid';
            }
            if (typeof found.form.requestSubmit === 'function') {
              found.form.requestSubmit();
            } else {
              found.form.submit();
            }
            return 'ok';
          } catch (e) {
            return 'error:' + e;
          }
        })();
    """.trimIndent()
}

/**
 * 解析检测脚本的返回值。
 *
 * `evaluateJavascript` 回传的是「返回值的 JSON 编码」：字符串会多带一层引号，对象则是 JSON
 * 字面量本身。这里两种形态都接住——回传形态一旦不合预期，整条链路会静默失效（永远认不出页面），
 * 代价太大，不值得依赖某一种约定。
 *
 * @return null 表示这一页不是插件登录页（含脚本异常、结果无法解析）
 */
internal fun parseWebAuthPage(raw: String?): WebAuthPage? {
    val literal = raw?.trim().orEmpty()
    if (literal.isEmpty() || literal == "null") return null
    val json = runCatching { JSONObject(literal) }.getOrNull()
        ?: runCatching { JSONObject(decodedString(literal).orEmpty()) }.getOrNull()
        ?: return null
    val mode = when (json.optString("mode")) {
        "login" -> WebAuthMode.LOGIN
        "setup" -> WebAuthMode.SETUP
        else -> return null
    }
    return WebAuthPage(
        mode = mode,
        errorText = json.optString("error"),
        loggedOut = json.optBoolean("loggedOut"),
    )
}

/** 把 `evaluateJavascript` 的返回值（JSON 字面量）解成它内部的字符串。 */
private fun decodedString(literal: String): String? =
    runCatching { JSONTokener(literal).nextValue() as? String }.getOrNull()

/**
 * 用给定密码在网页表单里完成一次提交。
 *
 * @param onResult 是否真的提交出去了；false 表示页面上已经找不到可提交的表单（页面已被换掉）
 *   或填进去的值过不了页面自身校验，调用方应退回「露出网页原生登录页」，不要卡在覆盖层里
 */
internal fun submitWebAuth(
    webView: WebView,
    password: String,
    confirm: String,
    onResult: (Boolean) -> Unit,
) {
    webView.evaluateJavascript(buildWebAuthSubmitScript(password, confirm)) { raw ->
        val result = raw?.trim('"').orEmpty()
        val submitted = result == "ok"
        // 只记录结果本身，绝不打印密码
        if (!submitted) Log.w(TAG, "访问认证提交未生效：$result")
        onResult(submitted)
    }
}
