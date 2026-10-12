package com.glassous.dsha.ui.web.adapt

import android.util.Log
import android.webkit.WebView

/** 基础层日志标签：与注入执行器分开，便于按层过滤日志。 */
private const val TAG = "DshaBaseAdapt"

/**
 * 基础注入层：与 Harness 版本无关，也与"响应式适配"开关无关，始终施加。
 *
 * 它只做"让页面能显示"的外壳级事情——注入安全区、兜底视口高度链、把塌陷的弹窗摆正——
 * 不改造任何组件的内部结构：样式只落在 `html`/`body`、全屏浮层与弹窗本体这些外壳元素上
 * （浮层那条是按安全区补边距，版本层若有同类规则会因样式节点后写入而覆盖它，此时以版本层为准；
 * 弹窗那几条只在量到塌陷时以行内样式兜底，尺寸正常的弹窗——包括版本层改造过的设置面板——一律不碰）。
 *
 * 关掉响应式适配后页面会整片塌陷，缺的正是这一层：部分设备的 WebView 把百分比与 `vh`
 * 解析成 0，而 DSH 依赖 `html → body → #root` 逐级 100% 的高度链，整条链会一起塌成 0。
 *
 * 同一台设备上，普通网页（访问认证登录页、反向代理的错误页等）也会被这套视口单位问题波及，
 * 所以高度链与弹窗兜底都不假设页面是 DSH：先按通用骨架兜底，DSH 专属的部分（`#root`、
 * 设计令牌）只在页面上确实存在时才生效。
 */

/** 基础层样式：安全区（状态栏与导航栏）与浮层避让，全部作用在 html/body 这类外壳元素上。 */
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
/* 画布底色（状态栏那一条露出的就是它）不在这里写死：普通网页有自己的背景，
   用 #fff 强刷只会把它们的不透明白底/深底一起刷掉；改由脚本确认页面上找得到 DSH 设计令牌
   之后再去接管 body 底色（见 SHELL_COMPAT_JS 第 2 段）。 */
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

/** 弹窗偏离安全区中心的容差（px）：小于它的偏移不纠正，免得与应用自己的定位较劲。 */
private const val DIALOG_CENTER_TOLERANCE_PX = 8

/** 「整屏遮罩」的高度下限：宽度铺满视口、高度却低于 max(该值, 视口 40%) 才算塌陷。 */
private const val DIALOG_STAGE_MIN_HEIGHT_PX = 260

/** 弹窗兜底的轮询节奏：弹窗多挂在深层的 portal 容器里，DOM 监听覆盖不全，靠低频轮询补齐。 */
private const val DIALOG_POLL_INTERVAL_MS = 800L

/**
 * 视口单位兜底：部分设备的 WebView 里，以视口为基准的百分比与 `vh` 都会解析成 **0**
 * （JS 侧的 `innerHeight` 却是正常值），于是所有依赖它们的高度/宽度约束都会塌陷。
 * 这里全部改用 JS 读到的像素值，绕过单位解析；只在量到塌陷时才动手，正常环境不受影响。
 *
 * 三件事，都不假设页面是 DSH——普通网页（访问认证登录页、反向代理的错误页）同样适用：
 *
 * 1. **外壳高度链**：`html → body → 应用挂载容器`逐级传高度是最通用的页面骨架
 *    （DSH 的 base.css 就是 `html、body、#root { height: 100% }`），整条链会一起塌成 0，
 *    界面只剩溢出可见或者干脆一片空白。逐级量、逐级用像素高度顶上，某一级本来就是正常高度
 *    就完全不碰它；
 * 2. **画布底色**：页面上读得到 DSH 的设计令牌（`--dsw-alias-bg-base`）才说明这是 DSH 自己的
 *    页面，此时把 body 底色对齐画布色，状态栏那一条才不会与应用背景脱节；普通网页有自己的
 *    背景，保持不动；
 * 3. **弹窗**：`role=dialog` 这类组件是「整屏遮罩 + 居中面板」的结构，视口单位一塌，面板会被
 *    算成 0 尺寸（表现为"只看到背景模糊、看不到弹窗面板"），或者被塌陷的遮罩挤到屏幕边上。
 *    这里按需补尺寸、把塌陷的遮罩撑回整屏，并把弹窗摆回安全区正中。
 *
 * 高度链失效时只能用 JS 量到的像素值兜底，此时 `100%` 不再生效，注入的安全高度
 * （[BASE_ADAPT_CSS] 写在 html 上的 padding，由 --dsha-safe-* 暴露）必须显式扣掉，
 * 否则外壳会撑出多余的可滚动空白。
 */
private val SHELL_COMPAT_JS = """
    (function () {
      try {
        var html = document.documentElement;
        if (!html) { return 'nopage'; }
        var body = document.body;

        /* 尺寸一律用 JS 读到的像素值：这套兜底存在的理由，就是这台设备的视口单位靠不住。
           安全区是活的（软键盘弹出时底部会变成 0），所以每次用到都现读，不在闭包里缓存。 */
        var readInset = function (name) {
          try {
            var n = parseFloat(getComputedStyle(html).getPropertyValue(name));
            return isNaN(n) ? 0 : n;
          } catch (e) { return 0; }
        };
        var insetTop = function () { return Math.max(Math.round(readInset('--dsha-safe-top')), 0); };
        var insetBottom = function () { return Math.max(Math.round(readInset('--dsha-safe-bottom')), 0); };
        var viewportW = function () { return Math.round(window.innerWidth || html.clientWidth || 0); };
        var viewportH = function () { return Math.round(window.innerHeight || html.clientHeight || 0); };
        var safeHeight = function () { return Math.max(viewportH() - insetTop() - insetBottom(), 0); };
        var heightOf = function (el) { return el ? el.getBoundingClientRect().height : 0; };
        var px = function (n) { return Math.round(n) + 'px'; };

        /* ===== 1. 外壳高度链：html → body → 应用挂载容器 =====
           塌陷时用像素高度顶上：某一级断了，它下面所有 100% 都会跟着算成 0。
           安全高度（写在 html 上的 padding）随视口一起扣掉，外壳才不会撑出多余的可滚动空白。 */
        var shell = window.__dshaShell || (window.__dshaShell = { pinned: [] });
        var isPinned = function (el) { return shell.pinned.indexOf(el) >= 0; };
        var pin = function (el, h) {
          if (!isPinned(el)) { shell.pinned.push(el); }
          el.style.setProperty('height', px(h), 'important');
        };
        /* 常见前端框架的挂载容器：认不出骨架也不影响兜底，只是少一级保护 */
        var MOUNTS = ['#root', '#app', '#__next', '#__nuxt', '#app-root'];
        var fixShell = function () {
          var h = viewportH();
          if (h <= 0) { return false; }
          var inner = Math.max(h - insetTop() - insetBottom(), 0);
          var did = false;
          /* 只兜量到 0 的那一级；兜过的元素按新视口尺寸持续跟随（旋转、分屏、键盘都会改窗口高度） */
          if (isPinned(html) || heightOf(html) <= 0) {
            pin(html, h);
            did = true;
          }
          if (body && (isPinned(body) || heightOf(body) <= 0)) {
            pin(body, inner);
            did = true;
          }
          for (var i = 0; i < MOUNTS.length; i++) {
            var el = document.querySelector(MOUNTS[i]);
            /* 挂载容器只在本级真的塌了、且里面确实有内容时接管：空容器（分隔条、占位节点）不该被撑开 */
            if (el && (isPinned(el) || (heightOf(el) <= 0 && (el.children.length > 0 || el.scrollHeight > 0)))) {
              pin(el, inner);
              did = true;
            }
          }
          return did;
        };
        if (fixShell()) {
          /* 前端可能已按塌陷尺寸量过一次，补一次 resize 让它重新测量 */
          window.dispatchEvent(new Event('resize'));
        }

        /* ===== 2. 画布底色：只有 DSH 自己的页面才接管 =====
           读得到设计令牌，说明这一页就是 DSH（普通网页、代理错误页都没有它）。
           普通网页有自己的背景，用 #fff 强刷只会把它们刷白，这里什么都不做。 */
        var canvasToken = function () {
          var read = function (el) {
            if (!el) { return ''; }
            try { return (getComputedStyle(el).getPropertyValue('--dsw-alias-bg-base') || '').trim(); }
            catch (e) { return ''; }
          };
          return read(html) || read(body);
        };
        if (body && canvasToken() !== '') {
          body.style.setProperty('background-color', 'var(--dsw-alias-bg-base)', 'important');
        }

        /* ===== 3. 弹窗：尺寸兜底与居中 =====
           只补尺寸与位置这类外壳属性，且只在量到异常（0 尺寸、遮罩塌了）时才动手：
           版本层改造过的弹窗（设置面板等）只要自身是正常的就完全不碰，
           真塌了则先由这里兜住，至少保证它显示得出来、并且在屏幕正中。 */
        var DIALOG_FLAG = 'data-dsha-compat';
        var SHIFT_FLAG = 'data-dsha-compat-shift';
        var STAGE_FLAG = 'data-dsha-compat-stage';
        var CANDIDATES = '[role=dialog],[role=alertdialog],[aria-modal=true],dialog[open]';

        /* 弹窗的定位上下文：最近的定位祖先（近似它的包含块），没有就用 body */
        var stageOf = function (el) {
          var n = el.parentElement;
          while (n && n !== html) {
            if (getComputedStyle(n).position !== 'static') { return n; }
            n = n.parentElement;
          }
          return body;
        };
        /* 遮罩层是不是塌了：整屏遮罩是「宽度铺满视口 + 高度撑满安全区」的形状，
           高度塌成 0、装不下弹窗、或者铺满宽却明显不够高，都是塌陷。
           锚定式弹层（宽度只有内容那么大）不合任何一条，不会被误判。 */
        var stageBroken = function (stage, dialogRect) {
          if (!stage || stage === body) { return false; }
          var rect = stage.getBoundingClientRect();
          if (rect.height <= 0) { return true; }
          if (rect.height < dialogRect.height) { return true; }
          return rect.width >= viewportW() * 0.9 &&
            rect.height < Math.max($DIALOG_STAGE_MIN_HEIGHT_PX, safeHeight() * 0.4);
        };
        /* 遮罩塌了就把它撑回整屏、内容居中：否则弹窗会被 0 高的遮罩裁掉或挤在顶边。
           只动量到塌陷的遮罩，正常的全屏遮罩完全不碰。 */
        var repairStage = function (stage) {
          if (!stage || stage === body || stage.getAttribute(STAGE_FLAG)) { return; }
          stage.setAttribute(STAGE_FLAG, '1');
          var s = stage.style;
          s.setProperty('position', 'fixed', 'important');
          s.setProperty('top', '0', 'important');
          s.setProperty('right', '0', 'important');
          s.setProperty('bottom', '0', 'important');
          s.setProperty('left', '0', 'important');
          s.setProperty('height', 'auto', 'important');
          s.setProperty('max-height', 'none', 'important');
          s.setProperty('box-sizing', 'border-box', 'important');
          s.setProperty('padding-top', px(insetTop()), 'important');
          s.setProperty('padding-bottom', px(insetBottom()), 'important');
          s.setProperty('display', 'flex', 'important');
          s.setProperty('align-items', 'center', 'important');
          s.setProperty('justify-content', 'center', 'important');
          s.setProperty('overflow', 'visible', 'important');
        };
        /* 把弹窗挪到安全区正中：用独立的 translate 属性做增量平移，不覆盖应用自己的
           transform 动画；先减去上次的位移再算总偏移，避免自我累加后震荡。 */
        var centerDialog = function (el) {
          var rect = el.getBoundingClientRect();
          if (rect.width <= 0 || rect.height <= 0) { return; }
          var last = (el.getAttribute(SHIFT_FLAG) || '0,0').split(',');
          var dx0 = parseFloat(last[0]) || 0;
          var dy0 = parseFloat(last[1]) || 0;
          var dx = Math.round(viewportW() / 2 - (rect.left + rect.width / 2 - dx0));
          var dy = Math.round(insetTop() + safeHeight() / 2 - (rect.top + rect.height / 2 - dy0));
          if (Math.abs(dx) <= $DIALOG_CENTER_TOLERANCE_PX) { dx = 0; }
          if (Math.abs(dy) <= $DIALOG_CENTER_TOLERANCE_PX) { dy = 0; }
          if (dx === dx0 && dy === dy0) { return; }
          el.setAttribute(SHIFT_FLAG, dx + ',' + dy);
          el.style.translate = (dx || dy) ? ((dx ? dx + 'px' : '0') + ' ' + (dy ? dy + 'px' : '0')) : '';
        };
        var fixDialogs = function () {
          var list = document.querySelectorAll(CANDIDATES);
          if (list.length === 0) { return; }
          var room = Math.max(viewportW() - $DIALOG_VIEWPORT_MARGIN_PX, 0);
          var maxHeight = Math.max(safeHeight() - $DIALOG_VIEWPORT_MARGIN_PX, 0);
          if (room <= 0 || maxHeight <= 0) { return; }
          var width = Math.min($DIALOG_MAX_WIDTH_PX, room);
          for (var i = 0; i < list.length; i++) {
            var el = list[i];
            /* 只管弹窗本体：包着另一个弹窗候选的通常是它的遮罩/包装层，交给里面那一个处理 */
            if (el.querySelector(CANDIDATES)) { continue; }
            var cs = getComputedStyle(el);
            if (cs.display === 'none' || cs.visibility === 'hidden') { continue; }
            var rescued = el.hasAttribute(DIALOG_FLAG);
            var rect = el.getBoundingClientRect();
            var collapsedSize = rect.width <= $DIALOG_MIN_SIDE_PX || rect.height <= $DIALOG_MIN_SIDE_PX;
            var stage = cs.position === 'fixed' ? null : stageOf(el);
            var broken = stageBroken(stage, rect);
            /* 尺寸与遮罩都正常：这是应用自己排好的弹窗，一律不碰 */
            if (!rescued && !collapsedSize && !broken) { continue; }
            if (broken) { repairStage(stage); }
            el.setAttribute(DIALOG_FLAG, '1');
            if (collapsedSize || rescued) {
              /* 等价于它自己想要的 min(380px, 100%) / max-height: 100%：% 靠不住，改给像素值 */
              el.style.setProperty('width', width + 'px', 'important');
              el.style.setProperty('box-sizing', 'border-box', 'important');
              el.style.setProperty('height', 'auto', 'important');
            }
            el.style.setProperty('max-height', maxHeight + 'px', 'important');
            centerDialog(el);
          }
        };
        fixDialogs();

        if (!window.__dshaCompatBound) {
          window.__dshaCompatBound = true;
          var remeasure = function () {
            fixShell();
            fixDialogs();
          };
          window.addEventListener('resize', remeasure);
          window.addEventListener('orientationchange', function () { setTimeout(remeasure, 300); });
          /* 弹窗先随 body 直接子节点的变化跟一手（快），挂在深层 portal 里的交给下面的轮询 */
          if (body && typeof MutationObserver === 'function') {
            new MutationObserver(fixDialogs).observe(body, { childList: true });
          }
          setInterval(fixDialogs, $DIALOG_POLL_INTERVAL_MS);
        }
        return 'ok';
      } catch (e) {
        return 'error:' + e;
      }
    })();
"""

/**
 * 页面稳定后做一次视口单位兜底，并持续看护后续挂载的弹窗（详见 [SHELL_COMPAT_JS]）。
 *
 * 与版本无关：外壳高度链塌陷、普通网页被刷白、弹窗 0 尺寸或没居中，都是 WebView 视口单位
 * 解析的问题，任何 Harness 版本、乃至非 DSH 的页面都会遇到，所以它属于基础层，
 * 关掉响应式适配时同样要执行。
 */
internal fun applyShellCompat(webView: WebView) {
    webView.postDelayed({
        webView.evaluateJavascript(SHELL_COMPAT_JS) { result ->
            Log.d(TAG, "视口兜底: ${result?.trim('"')}")
        }
    }, SHELL_COMPAT_DELAY_MS)
}
