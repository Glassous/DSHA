package com.glassous.dsha.ui.web.adapt

import android.util.Log
import android.webkit.WebView

/** 基础层日志标签：与注入执行器分开，便于按层过滤日志。 */
private const val TAG = "DshaBaseAdapt"

/**
 * 基础注入层：与 Harness 版本无关，也与"响应式适配"开关无关，始终施加。
 *
 * 它只做"让页面能显示"的外壳级事情——注入安全区、兜底视口高度链——不改造任何组件的内部结构：
 * 样式只落在 `html`/`body` 与全屏浮层这些外壳元素上（浮层那条是按安全区补边距，版本层若有同类
 * 规则会因样式节点后写入而覆盖它，此时以版本层为准）。
 *
 * 关掉响应式适配后页面会整片塌陷，缺的正是这一层：部分设备的 WebView 把百分比与 `vh`
 * 解析成 0，而 DSH 依赖 `html → body → #root` 逐级 100% 的高度链，整条链会一起塌成 0。
 */

/** 基础层样式：安全区（状态栏与导航栏）与画布底色，全部作用在 html/body 这类外壳元素上。 */
internal val BASE_ADAPT_CSS = """
/* ===== 基础层：只保证页面在手机 WebView 里能正常显示，与 Harness 版本无关 =====
   html 走 border-box 且高度锁定 100%，因此 padding 计入自身高度：
   页面总高不变（不产生多余上下滚动），可视内容整体内移，净内容高度与改动前等价。 */
html {
  padding-top: __DSHA_SAFE_TOP__px !important;
  padding-bottom: __DSHA_SAFE_BOTTOM__px !important;
  box-sizing: border-box !important;
  height: 100% !important;
  --dsha-safe-top: __DSHA_SAFE_TOP__px;
  --dsha-safe-bottom: __DSHA_SAFE_BOTTOM__px;
  --dsh-frame-overlay-top: calc(__DSHA_SAFE_TOP__px + 12px);
}
/* 画布底色（状态栏那一条露出的就是它）跟随网页明暗，避免与应用背景出现色差 */
body {
  background-color: var(--dsw-alias-bg-base, #fff) !important;
}
/* 触屏点击不再出现系统的蓝色高亮块：该属性不继承，应用自身也只在部分组件上关掉了 */
html,
body,
* {
  -webkit-tap-highlight-color: transparent !important;
}
/* 应用内全屏浮层（设置弹窗等，position:fixed）不受 html padding 影响，需各自避让上下安全区；
   遮罩是 absolute inset:0，相对 padding box 定位，仍然铺满整屏。 */
[class$=_overlay] {
  padding-top: __DSHA_SAFE_TOP__px !important;
  padding-bottom: __DSHA_SAFE_BOTTOM__px !important;
  box-sizing: border-box !important;
}
"""

/** 基础层脚本：写入样式节点，并让页面按新的安全区尺寸重新测量。 */
internal val BASE_ADAPT_JS = """
(function () {
  try {
    var root = document.documentElement;
    if (!root) { return "nopage"; }
    var style = document.getElementById("__DSHA_STYLE_ID__");
    if (!style) {
      style = document.createElement("style");
      style.id = "__DSHA_STYLE_ID__";
      (document.head || root).appendChild(style);
    }
    style.textContent = __DSHA_CSS__;
    /* 安全区变化会影响高度链，通知页面按新尺寸重新测量 */
    window.dispatchEvent(new Event("resize"));
    return "ok";
  } catch (e) {
    return "error:" + e;
  }
})();
"""

/** 视口塌陷兜底的执行时机：太早量不到真实尺寸，等页面渲染稳定后再来一次。 */
private const val SHELL_COMPAT_DELAY_MS = 1_500L

private const val DIALOG_MIN_SIDE_PX = 2
private const val DIALOG_MAX_WIDTH_PX = 380
private const val DIALOG_VIEWPORT_MARGIN_PX = 48

/**
 * 视口单位兜底：部分设备的 WebView 里，以视口为基准的百分比与 `vh` 都会解析成 **0**
 * （JS 侧的 `innerHeight` 却是正常值），于是所有依赖它们的高度/宽度约束都会塌陷。
 * 这里全部改用 JS 读到的像素值，绕过单位解析；只在量到塌陷时才动手，正常环境不受影响。
 *
 * 两处受影响的地方：
 * 1. DSH 的 base.css 用 `html、body、#root { height: 100% }` 逐级传高度（见其底座样式），
 *    整条链塌成 0，界面只剩溢出可见；
 * 2. 它的弹窗卡片是 `width: min(380px, 100%)` + `max-height: 100%`，同样会被算成 0 尺寸，
 *    表现为"只看到背景模糊、看不到弹窗面板"（遮罩用的是 inset 拉伸，所以它没事）。
 *
 * 高度链失效时只能用 JS 量到的像素值兜底，此时 `100%` 不再生效，注入的安全高度
 * （[BASE_ADAPT_CSS] 写在 html 上的 padding，由 --dsha-safe-* 暴露）必须显式扣掉，
 * 否则 body/#root 会撑出多余的可滚动空白。
 */
private val SHELL_COMPAT_JS = """
    (function () {
      try {
        var html = document.documentElement;
        var body = document.body;
        var root = document.getElementById('root');
        var height = Math.round(window.innerHeight || 0);
        if (height <= 0) { height = Math.round(html.clientHeight || 0); }

        var readInset = function (name) {
          try {
            var n = parseFloat(getComputedStyle(html).getPropertyValue(name));
            return isNaN(n) ? 0 : n;
          } catch (e) { return 0; }
        };
        var insetGap = function () {
          return Math.max(Math.round(readInset('--dsha-safe-top') + readInset('--dsha-safe-bottom')), 0);
        };

        var applyRootHeight = function () {
          var h = Math.round(window.innerHeight || height);
          if (h <= 0) { return; }
          var inner = Math.max(h - insetGap(), 0) + 'px';
          html.style.setProperty('height', h + 'px', 'important');
          if (body !== null) { body.style.setProperty('height', inner, 'important'); }
          if (root !== null) { root.style.setProperty('height', inner, 'important'); }
        };

        var collapsed = height > 0 && (
          html.getBoundingClientRect().height <= 0 ||
          (body !== null && body.getBoundingClientRect().height <= 0) ||
          (root !== null && root.getBoundingClientRect().height === 0));
        if (collapsed) {
          window.__dshaRootFixed = true;
          applyRootHeight();
          // 前端可能已按塌陷尺寸量过一次，补一次 resize 让它重新测量
          window.dispatchEvent(new Event('resize'));
        }

        // 弹窗卡片：量到 0 尺寸才补，等价于它自己想要的 min(380px,100%) / max-height:100%
        var fixDialogs = function () {
          var list = document.querySelectorAll('[role=dialog],[role=alertdialog]');
          var w = Math.max(Math.round(window.innerWidth || 0) - $DIALOG_VIEWPORT_MARGIN_PX, 0);
          var h = Math.max(Math.round(window.innerHeight || 0) - $DIALOG_VIEWPORT_MARGIN_PX, 0);
          if (w <= 0 || h <= 0) { return; }
          var width = Math.min($DIALOG_MAX_WIDTH_PX, w);
          for (var i = 0; i < list.length; i++) {
            var el = list[i];
            var rescued = el.hasAttribute('data-dsha-compat');
            var rect = el.getBoundingClientRect();
            // 没量到 0 尺寸的正常弹窗一律不碰；已兜底过的继续跟随视口尺寸
            if (!rescued && rect.width > $DIALOG_MIN_SIDE_PX && rect.height > $DIALOG_MIN_SIDE_PX) {
              continue;
            }
            if (!rescued) { el.setAttribute('data-dsha-compat', '1'); }
            el.style.setProperty('width', width + 'px', 'important');
            el.style.setProperty('height', 'auto', 'important');
            el.style.setProperty('max-height', h + 'px', 'important');
          }
        };
        fixDialogs();

        if (!window.__dshaCompatBound) {
          window.__dshaCompatBound = true;
          var remeasure = function () {
            if (window.__dshaRootFixed) { applyRootHeight(); }
            fixDialogs();
          };
          window.addEventListener('resize', remeasure);
          window.addEventListener('orientationchange', function () { setTimeout(remeasure, 300); });
          // 弹窗是动态挂载到 body 上的，监听 body 的直接子节点变化就够了（不必 subtree，避免流式渲染时频繁触发）
          if (body !== null && typeof MutationObserver === 'function') {
            new MutationObserver(fixDialogs).observe(body, { childList: true });
          }
        }
        return 'ok';
      } catch (e) {
        return 'error:' + e;
      }
    })();
"""

/**
 * 页面稳定后做一次视口单位兜底，并按需持续看护动态挂载的弹窗（详见 [SHELL_COMPAT_JS]）。
 *
 * 与版本无关：塌陷与弹窗 0 尺寸都是 WebView 视口单位解析的问题，任何 Harness 版本都会遇到，
 * 所以它属于基础层，关掉响应式适配时同样要执行。
 */
internal fun applyShellCompat(webView: WebView) {
    webView.postDelayed({
        webView.evaluateJavascript(SHELL_COMPAT_JS) { result ->
            Log.d(TAG, "视口兜底: ${result?.trim('"')}")
        }
    }, SHELL_COMPAT_DELAY_MS)
}
