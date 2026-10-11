package com.glassous.dsha.ui.web.adapt.v020rc2

import com.glassous.dsha.ui.web.adapt.VersionAdapt

/**
 * 注入的样式。选择器一律使用稳定的类名/属性**后缀**锚点（`[class$=_sidebarCol]` 等），
 * 不碰构建哈希前缀（`pI_x6G_` / `VOzbGW_` 会随构建变化）。
 */
private val V020RC2_CSS = """
/* 抽屉遮罩：位于抽屉之下、内容之上。
   起点在顶栏下沿：顶栏按钮不会被盖住，也就不必给顶栏加定位层级——
   顶栏一旦成为定位元素，锚在它内部的绝对定位浮层会跟着改定位基准。 */
#__DSHA_SCRIM_ID__ {
  position: fixed;
  top: var(--dsha-scrim-top, calc(__DSHA_SAFE_TOP__px + 76px));
  right: 0;
  bottom: 0;
  left: 0;
  z-index: 35;
  display: none;
  background: rgba(0, 0, 0, .28);
}

/* 呼出侧栏按钮：注入在顶栏标题左侧（`conversation.header.leading`），
   非手机断点下不显示（display:none 兜底，JS 也在窄屏时才插入）。 */
#__DSHA_NAV_ID__ {
  display: none;
  flex: none;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  margin-right: 6px;
  padding: 0;
  border: none;
  border-radius: 8px;
  background: transparent;
  color: var(--dsw-alias-label-secondary, currentColor);
  cursor: pointer;
  -webkit-tap-highlight-color: transparent;
}
#__DSHA_NAV_ID__:active {
  background: var(--dsw-alias-interactive-bg-hover, rgba(128, 128, 128, .16));
}
#__DSHA_NAV_ID__ svg {
  display: block;
}

/* ===== 手机端（视口 < 600 CSS px，平板不命中） ===== */
@media (max-width: __DSHA_MOBILE_LIMIT__px) {
  /* 1. 外壳：三栏改单栏，左右栏降级为覆盖式抽屉 */
  [class$=_frame] {
    grid-template-columns: 0px minmax(0px, 1fr) 0px !important;
  }
  /* 侧栏脱离栅格流后，会话列必须显式占满整行，否则会被自动放进 0px 的第一列 */
  [class$=_centerCol] {
    grid-column: 1 / -1 !important;
    border-left: none !important;
    border-right: none !important;
  }
  [class$=_sidebarCol],
  [class$=_rightbarCol] {
    position: fixed !important;
    top: 0 !important;
    bottom: 0 !important;
    z-index: 40 !important;
    width: min(280px, 84vw) !important;
    max-width: 84vw !important;
    box-sizing: border-box !important;
    padding-top: calc(__DSHA_SAFE_TOP__px + 4px) !important;
    box-shadow: 0 8px 32px rgba(0, 0, 0, .4) !important;
    transition: transform .22s cubic-bezier(.2, 0, 0, 1) !important;
    will-change: transform;
    /* 收起态不吃触摸事件：容器滑出屏幕后，任何残留区域都可能拦住顶栏按钮 */
    pointer-events: none !important;
  }
  /* 内层面板按容器宽度铺满：右栏 panel 带内联 width（桌面值，可达 768px），
     会让内容按错误宽度排版并溢出容器，必须压回 100%。 */
  [class$=_sidebarCol] > [class$=_root],
  [class$=_rightbarCol] [class$=_panel] {
    width: 100% !important;
    min-width: 0 !important;
    max-width: none !important;
  }
  /* 右栏 panel 是 absolute 定位，容器的 padding-top 推不动它，状态栏安全距离单独补。
     溢出裁剪只加在 panel 上：侧栏容器若裁切，工作区行的按钮会被切掉一角。 */
  [class$=_rightbarCol] [class$=_panel] {
    padding-top: calc(__DSHA_SAFE_TOP__px + 4px) !important;
    box-sizing: border-box !important;
    overflow: hidden !important;
  }
  html.dsha-drawer-open #__DSHA_NAV_ID__,
  html.dsha-rightbar-open #__DSHA_NAV_ID__ {
    opacity: 0;
    pointer-events: none;
  }
  [class$=_sidebarCol] {
    left: 0 !important;
    right: auto !important;
    transform: translateX(-101%);
  }
  [class$=_rightbarCol] {
    right: 0 !important;
    left: auto !important;
    transform: translateX(101%);
  }
  html.dsha-drawer-open [class$=_sidebarCol],
  html.dsha-rightbar-open [class$=_rightbarCol] {
    transform: none;
    pointer-events: auto !important;
  }
  /* 浮层菜单不超出安全区，超出部分改为滚动：
     部分菜单按锚点向下展开，锚点贴底时会大半个落到屏幕外。
     注意这里不能强制 height：正常菜单的高度来自内容，强制 auto 会压塌
     那些用类样式定高的菜单；被算成几像素高的「一条」由脚本按需清理。 */
  [class$=_menu] {
    max-height: calc(100vh - __DSHA_SAFE_TOP__px - __DSHA_SAFE_BOTTOM__px - 24px) !important;
    max-height: calc(100dvh - __DSHA_SAFE_TOP__px - __DSHA_SAFE_BOTTOM__px - 24px) !important;
    overflow-y: auto !important;
    /* 提到内容层之上；flex: none 防止被父级 flex 容器压缩高度 */
    z-index: 900 !important;
    flex: none !important;
  }
  /* 输入框外壳整体提层：它是 sticky + z-index（低值），会形成层叠上下文，
     锚在这里的浮层会被连同封在低层，展开后被上方/下方容器盖住。 */
  [class$=_composerSeat],
  [class$=_composerStack] {
    z-index: 30 !important;
  }
  /* 拖拽调宽手柄在手机上只会误触（会话区的 widthHandle 还会多出约 18px 横向溢出） */
  [class$=_handle],
  [class$=_widthHandle] {
    display: none !important;
  }

  /* 2. 会话区宽度：去掉 clamp(680px, …) 下限，消除窄屏横向溢出 */
  [data-slot="main.conversation"] [class$=_body],
  [data-slot="main.conversation"] [class$=_embeddedBody] {
    --dsh-chat-content-width: min(calc(100% - 24px), 920px) !important;
    --dsh-composer-card-max-width: min(calc(100% - 16px), 952px) !important;
    --dsh-composer-side-clearance: 8px !important;
    --dsh-composer-dock-inset: 8px !important;
  }

  /* 3. 触控目标：只放大顶栏与侧栏底部这类主要按钮（28px → 36px）。
     列表行内的图标按钮（16px / 28px）不能碰：撑大后会把行内布局挤歪，
     例如工作区标题行最右侧的「添加工作区」按钮会被顶到边缘外。 */
  [data-dsha-frame="header"] [class*=_iconButton],
  [class$=_settingsArea] [class*=_iconButton],
  [class$=_footArea] [class*=_iconButton] {
    min-width: 36px !important;
    min-height: 36px !important;
  }

  /* 3.1 顶栏标题左侧的呼出侧栏按钮 */
  #__DSHA_NAV_ID__ {
    display: inline-flex;
  }

  /* 4. 设置弹窗：小屏改成「顶部横向切换 + 内容区双向滚动」的定高面板。
     各部件由脚本打 data-dsha-set 标记，不依赖 CSS Modules 的哈希类名。 */
  [data-dsha-set="overlay"] {
    box-sizing: border-box !important;
    align-items: center !important;
    /* 上下都留出系统栏安全区，弹窗才不会被状态栏或手势条压住 */
    padding-top: calc(__DSHA_SAFE_TOP__px + 8px) !important;
    padding-bottom: calc(__DSHA_SAFE_BOTTOM__px + 8px) !important;
  }
  [data-dsha-set="panel"] {
    box-sizing: border-box !important;
    position: relative !important;
    flex-direction: column !important;
    width: calc(100vw - 16px) !important;
    max-width: calc(100vw - 16px) !important;
    height: calc(100vh - __DSHA_SAFE_TOP__px - __DSHA_SAFE_BOTTOM__px - 16px) !important;
    max-height: calc(100vh - __DSHA_SAFE_TOP__px - __DSHA_SAFE_BOTTOM__px - 16px) !important;
    overflow: hidden !important;
    /* 顶部留一条给关闭按钮，切换区自然排在它下方 */
    padding-top: 42px !important;
  }
  @supports (height: 100dvh) {
    [data-dsha-set="panel"] {
      height: calc(100dvh - __DSHA_SAFE_TOP__px - __DSHA_SAFE_BOTTOM__px - 16px) !important;
      max-height: calc(100dvh - __DSHA_SAFE_TOP__px - __DSHA_SAFE_BOTTOM__px - 16px) !important;
    }
  }
  /* 关闭按钮提到弹窗最顶部（右上角），与标题/切换区解耦 */
  [data-dsha-set="close"] {
    position: absolute !important;
    top: 5px !important;
    right: 5px !important;
    z-index: 6 !important;
    width: 34px !important;
    height: 34px !important;
    border-radius: 8px !important;
    background: var(--dsw-alias-interactive-bg-hover, rgba(128, 128, 128, .14)) !important;
  }
  /* 顶部切换区：横向排列、图标在上、文字在下 */
  [data-dsha-set="nav"] {
    flex-direction: row !important;
    flex: none !important;
    width: 100% !important;
    gap: 2px !important;
    padding: 0 6px !important;
    overflow: hidden !important;
    border-bottom: .5px solid var(--dsw-alias-border-l3, rgba(128, 128, 128, .24)) !important;
  }
  [data-dsha-set="navTitle"] {
    display: none !important;
  }
  [data-dsha-set="navList"] {
    flex-direction: row !important;
    gap: 2px !important;
    width: 100% !important;
    padding-bottom: 2px !important;
    overflow-x: auto !important;
    overflow-y: hidden !important;
    -webkit-overflow-scrolling: touch;
    scrollbar-width: none;
  }
  [data-dsha-set="navList"]::-webkit-scrollbar {
    display: none;
  }
  /* 每个标签固定两行：图标一行、文字一行。高度写死，选中态不再改变文字所在行。 */
  [data-dsha-set="navCell"] {
    flex: 0 0 auto !important;
    flex-direction: column !important;
    align-items: center !important;
    justify-content: flex-start !important;
    width: auto !important;
    min-width: 64px !important;
    height: 70px !important;
    min-height: 70px !important;
    max-height: 70px !important;
    gap: 4px !important;
    padding: 8px 6px 9px !important;
    line-height: 16px !important;
    text-align: center !important;
  }
  [data-dsha-set="navIcon"] {
    flex: none !important;
    height: 16px !important;
    line-height: 16px !important;
  }
  [data-dsha-set="navLabel"] {
    display: block !important;
    flex: none !important;
    box-sizing: border-box !important;
    max-width: 84px !important;
    min-height: 32px !important;
    font-size: 12px !important;
    line-height: 16px !important;
    text-align: center !important;
    white-space: normal !important;
    overflow: hidden !important;
  }
  /* 中间列必须能被压扁（min-height: 0），否则内容把面板撑高、滚动容器失效 */
  [data-dsha-set="content"] {
    flex: 1 !important;
    min-height: 0 !important;
  }
  /* 标题行压缩；内容区是唯一滚动容器，上下左右都能滑 */
  [data-dsha-set="header"] {
    height: auto !important;
    min-height: 0 !important;
    align-items: center !important;
    padding: 6px 12px 2px !important;
  }
  [data-dsha-set="options"] {
    flex: 1 !important;
    min-height: 0 !important;
    overflow: auto !important;
    -webkit-overflow-scrolling: touch;
    padding: 0 12px 16px !important;
  }
  /* 侧栏里的设置入口在手机上要够大 */
  [class$=_trigger] {
    min-height: 44px !important;
  }
}
"""

/**
 * 注入脚本：写入样式、按应用自身的折叠属性驱动抽屉显隐、维护遮罩并标记设置弹窗。
 *
 * 抽屉显隐复用应用自身状态（`data-sidebar-collapsed` / `data-rightbar-collapsed`），
 * 所以侧栏里的「收起侧边栏」与顶部的「打开右侧边栏」等按钮天然成为抽屉开关，无需新增按钮。
 */
private val V020RC2_JS = """
(function () {
  try {
    var top = __DSHA_SAFE_TOP__, bottom = __DSHA_SAFE_BOTTOM__, limit = __DSHA_MOBILE_LIMIT__;
    var root = document.documentElement;
    if (!root) { return "nopage"; }
    var style = document.getElementById("__DSHA_STYLE_ID__");
    if (!style) {
      style = document.createElement("style");
      style.id = "__DSHA_STYLE_ID__";
      (document.head || root).appendChild(style);
    }
    style.textContent = __DSHA_CSS__;

    var frame = document.querySelector("[class$=_frame]");
    var scrimId = "__DSHA_SCRIM_ID__";
    var navId = "__DSHA_NAV_ID__";
    var flag = "__DSHA_SETTINGS_ATTR__";
    var toggles = { sidebar: null, rightbar: null };
    var state = window.__dshaAdapt || (window.__dshaAdapt = {});
    var timer = null;
    if (!state.fresh) { state.fresh = []; }

    function col(kind) {
      return document.querySelector(kind === "sidebar" ? "[class$=_sidebarCol]" : "[class$=_rightbarCol]");
    }
    function isMobile() {
      return (window.innerWidth || root.clientWidth || 0) < limit;
    }
    function attr(kind) {
      return kind === "sidebar" ? "data-sidebar-collapsed" : "data-rightbar-collapsed";
    }
    function cls(kind) {
      return kind === "sidebar" ? "dsha-drawer-open" : "dsha-rightbar-open";
    }
    function isOpen(kind) {
      return isMobile() && !!frame && !frame.hasAttribute(attr(kind));
    }
    /* 应用自己的抽屉开合按钮：侧栏内有 [class$=_toggle]；右栏开关在顶栏、不在侧栏容器内，
       必须全文档找。判定顺序：容器内 [class$=_toggle] → aria-label 含「侧边栏/侧栏」
       （用「右」字区分左右）→ 顶栏内含「右」的按钮 → 容器内尺寸启发式。找到后缓存。 */
    function findToggle(kind) {
      var cached = toggles[kind];
      if (cached && cached.isConnected) { return cached; }
      var wrap = col(kind);
      var wantRight = kind === "rightbar";
      var btn = null;
      var i;
      if (wrap) { btn = wrap.querySelector("[class$=_toggle]"); }
      if (!btn) {
        var all = document.querySelectorAll("button[aria-label]");
        for (i = 0; i < all.length; i++) {
          var lb = all[i].getAttribute("aria-label") || "";
          if (lb.indexOf("侧边栏") < 0 && lb.indexOf("侧栏") < 0) { continue; }
          if ((lb.indexOf("右") >= 0) === wantRight) { btn = all[i]; break; }
        }
      }
      if (!btn && wantRight) {
        var head = document.querySelector("[class$=_header]");
        var hb = head ? head.querySelectorAll("button[aria-label]") : [];
        for (i = 0; i < hb.length; i++) {
          var hlb = hb[i].getAttribute("aria-label") || "";
          if (hlb.indexOf("右") >= 0) { btn = hb[i]; break; }
        }
      }
      if (!btn && wrap) {
        var list = wrap.querySelectorAll("button");
        for (i = 0; i < list.length; i++) {
          var r = list[i].getBoundingClientRect();
          if (r.width > 0 && r.width <= 44 && r.height > 0 && list[i].getAttribute("aria-label")) {
            btn = list[i];
            break;
          }
        }
      }
      if (btn) { toggles[kind] = btn; }
      return btn;
    }
    function drawer(kind) {
      var on = isOpen(kind);
      root.classList.toggle(cls(kind), on);
      if (on) { findToggle(kind); }
    }
    /* 点击应用自己的开关，切换抽屉（展开的抽屉 → 收起；收起的 → 展开）。
       抽屉移出屏幕后按钮仍在 DOM 里且事件有效，程序化点击即可驱动应用状态。
       切换一旦发生即视为用户接管，停掉 autoCollapse 的重试，避免刚打开又被收起。 */
    function toggleDrawer(kind) {
      state.collapsed = true;
      var btn = findToggle(kind);
      if (btn) { btn.click(); return true; }
      return false;
    }
    function closeDrawer(kind) {
      return toggleDrawer(kind);
    }
    function scrim() {
      var need = isMobile() && (root.classList.contains(cls("sidebar")) || root.classList.contains(cls("rightbar")));
      var el = document.getElementById(scrimId);
      if (need && !el) {
        el = document.createElement("div");
        el.id = scrimId;
        el.addEventListener("click", function () {
          /* 先收起当前真正打开的那一侧，否则会点开另一侧 */
          var order = root.classList.contains(cls("sidebar")) ? ["sidebar", "rightbar"] : ["rightbar", "sidebar"];
          for (var i = 0; i < order.length; i++) {
            if (closeDrawer(order[i])) { return; }
          }
        });
        (document.body || root).appendChild(el);
      }
      if (el) { el.style.display = need ? "block" : "none"; }
    }
    /* 给设置弹窗各部件打标记：只在入口按钮上见过 settings.* 槽位，弹窗内部未必有，
       若只认槽位则手机端整套设置弹窗样式都不会生效。因此叠加弹窗独有的结构特征
       （标签导航 navList / navCell）判定，避免 `[class$=_panel]` 误伤其它面板。
       部件逐个打 data-dsha-set：CSS Modules 的哈希前缀会随版本变化，
       且选中项 class 是 `xx_navCell xx_active`，后缀选择器根本匹配不到。 */
    function mark(el, kind) {
      if (el) { el.setAttribute("data-dsha-set", kind); }
      return el;
    }
    function settingsTag() {
      var list = document.querySelectorAll("[class$=_overlay]");
      for (var i = 0; i < list.length; i++) {
        var panel = list[i].querySelector("[class$=_panel]");
        if (!panel) { continue; }
        if (!(list[i].querySelector('[data-slot^="settings"]') ||
              panel.querySelector("[class$=_navList]") ||
              panel.querySelector("[class$=_navCell]") ||
              panel.querySelector('[class*="_navCell"]'))) { continue; }
        panel.setAttribute(flag, "1");
        mark(list[i], "overlay");
        mark(panel, "panel");
        mark(panel.querySelector("[class$=_content]"), "content");
        mark(panel.querySelector("[class$=_nav]"), "nav");
        mark(panel.querySelector("[class$=_navTitle]"), "navTitle");
        mark(panel.querySelector("[class$=_navList]"), "navList");
        var cells = panel.querySelectorAll('[class*="_navCell"]');
        var j;
        for (j = 0; j < cells.length; j++) {
          mark(cells[j], "navCell");
          mark(cells[j].querySelector('[class*="_navIcon"]'), "navIcon");
          mark(cells[j].querySelector('[class*="_navLabel"]'), "navLabel");
        }
        mark(panel.querySelector("[class$=_header]"), "header");
        mark(panel.querySelector("[class$=_options]"), "options");
        var btns = panel.querySelectorAll("button");
        var closeBtn = null;
        for (j = 0; j < btns.length; j++) {
          var lb = btns[j].getAttribute("aria-label") || "";
          if (lb.indexOf("关闭") >= 0) { closeBtn = btns[j]; break; }
        }
        mark(closeBtn || panel.querySelector("[class$=_close]"), "close");
      }
    }
    /* 应用顶栏打标记，供 CSS 提层（压遮罩、低于抽屉）。必须排除浮层内的 header，
       否则设置弹窗自己的 header 会被提层，弹窗内的绝对定位元素会跟着跑偏。 */
    function frameTag() {
      var list = document.querySelectorAll("[class$=_header]");
      for (var i = 0; i < list.length; i++) {
        var h = list[i];
        if (h.closest("[class$=_overlay]") || h.closest("[" + flag + "]")) { continue; }
        h.setAttribute("data-dsha-frame", "header");
        return;
      }
    }
    /* 手机上门以抽屉呈现，默认收起，避免一进来就盖住会话内容。
       注入发生在 onPageFinished，应用此时可能还没完成水合，按钮的点击回调尚未挂上，
       所以按时间预算重试几次；一旦量到应用自己的折叠属性就收手，不再反复点击。 */
    function autoCollapse() {
      if (state.collapsed || !isMobile() || !frame) { return; }
      if (frame.hasAttribute("data-sidebar-collapsed")) { state.collapsed = true; return; }
      if ((state.tries || 0) >= 6) { state.collapsed = true; return; }
      var now = Date.now();
      if (state.nextTry && now < state.nextTry) { return; }
      var btn = findToggle("sidebar");
      if (!btn) { return; }
      state.tries = (state.tries || 0) + 1;
      state.nextTry = now + 450;
      btn.click();
    }
    /* 顶栏标题左侧的「呼出侧栏」按钮：抽屉收起后，应用自己的开关随侧栏一起移出屏幕，
       这里注入一个等价按钮补位；点击驱动应用自身的侧栏开关，开合状态仍由应用持有。 */
    function navButton() {
      var host = document.querySelector("[data-conversation-header-leading]") ||
        document.querySelector("[class$=_headerLeading]");
      var btn = document.getElementById(navId);
      if (!host || !isMobile()) {
        if (btn && btn.parentNode) { btn.parentNode.removeChild(btn); }
        return;
      }
      if (!btn) {
        btn = document.createElement("button");
        btn.id = navId;
        btn.type = "button";
        btn.className = "dsha-navbtn";
        btn.setAttribute("aria-label", "打开侧边栏");
        btn.innerHTML = '<svg width="20" height="20" viewBox="0 0 16 16" fill="none" stroke-width="1" aria-hidden="true">' +
          '<path d="M13.5 1.5H2.5C1.94772 1.5 1.5 1.94772 1.5 2.5V13.5C1.5 14.0523 1.94772 14.5 2.5 14.5H13.5C14.0523 14.5 14.5 14.0523 14.5 13.5V2.5C14.5 1.94772 14.0523 1.5 13.5 1.5Z" stroke="currentColor"></path>' +
          '<path d="M5.5 1.5V14.5" stroke="currentColor"></path></svg>';
        btn.addEventListener("click", function (e) {
          e.preventDefault();
          e.stopPropagation();
          toggleDrawer("sidebar");
        });
      }
      if (btn.parentNode !== host) { host.appendChild(btn); }
    }
    /* 手机屏幕放不下两个抽屉：检测到两侧同时展开时，收起侧栏，让右栏独占 */
    function exclusive() {
      if (!root.classList.contains(cls("sidebar")) || !root.classList.contains(cls("rightbar"))) { return; }
      if (state.exclusiveBusy) { return; }
      state.exclusiveBusy = true;
      setTimeout(function () { state.exclusiveBusy = false; }, 400);
      closeDrawer("sidebar");
    }
    /* 遮罩起点对齐顶栏下沿：顶栏按钮不被遮罩盖住，顶栏本身就不必变成定位元素 */
    function measureHeader() {
      var head = document.querySelector("[data-dsha-frame=header]");
      if (!head) { return; }
      var b = head.getBoundingClientRect().bottom;
      if (b > 0) { root.style.setProperty("--dsha-scrim-top", Math.round(b) + "px"); }
    }
    /* 浮层兜底：输入框区域带 container-type，锚在其中的 fixed 浮层会改以该容器为基准，
       坐标算错后卡片大半个落到屏幕外（只露一条边）。这里按渲染后的真实位置做增量平移，
       把浮层拉回视口内；用独立的 translate 属性，不覆盖应用自己的 transform 动画。 */
    /* 浮层若被祖先的 overflow 裁得只剩一条，就地搬到 body 下并按当前实际位置固定，
       从而脱离祖先的裁剪与低层级上下文。用数据属性防重复搬运。 */
    function rescueClip(m) {
      if (m.getAttribute("data-dsha-rescued")) { return; }
      var r = m.getBoundingClientRect();
      if (r.width < 48 || r.height < 48) { return; }
      var l = r.left, t = r.top, rr = r.right, b = r.bottom;
      var n = m.parentElement;
      while (n && n !== document.documentElement) {
        var cs2 = window.getComputedStyle(n);
        if (cs2.overflow !== "visible" || cs2.overflowX !== "visible" || cs2.overflowY !== "visible") {
          var cr = n.getBoundingClientRect();
          if (cr.width > 0 && cr.height > 0) {
            l = Math.max(l, cr.left);
            t = Math.max(t, cr.top);
            rr = Math.min(rr, cr.right);
            b = Math.min(b, cr.bottom);
          }
        }
        n = n.parentElement;
      }
      var vis = Math.max(0, rr - l) * Math.max(0, b - t);
      if (vis / (r.width * r.height) > 0.35) { return; }
      m.setAttribute("data-dsha-rescued", "1");
      (document.body || root).appendChild(m);
      m.style.position = "fixed";
      m.style.left = r.left + "px";
      m.style.top = r.top + "px";
      m.style.right = "auto";
      m.style.bottom = "auto";
    }
    /* 「只剩一条」的浮层：应用按锚点剩余空间算高度，锚点贴底时会算出接近 0 的高度。
       这里清掉小得异常的高度限制（内联与类样式都算），必要时兜底撑开。 */
    function tiny(v) {
      var n = parseFloat(v);
      return !!v && n >= 0 && n < 60;
    }
    function squashFix(m) {
      var r = m.getBoundingClientRect();
      /* 高度已恢复：撤掉之前加的兜底样式，避免长期压着应用自己的高度 */
      if (r.height >= 60 && m.getAttribute("data-dsha-squash")) {
        m.style.removeProperty("min-height");
        m.style.removeProperty("max-height");
        m.style.removeProperty("overflow-y");
        m.removeAttribute("data-dsha-squash");
        return;
      }
      if (r.width < 120 || r.height <= 0 || r.height > 44) { return; }
      var cs = window.getComputedStyle(m);
      if (cs.position === "static") { return; }
      /* 先清内联，再按计算值压掉类样式带来的限制 */
      if (tiny(m.style.height)) { m.style.removeProperty("height"); }
      if (tiny(m.style.maxHeight)) { m.style.removeProperty("max-height"); }
      var cmh = parseFloat(cs.maxHeight), chh = parseFloat(cs.height);
      if (cs.maxHeight !== "none" && !isNaN(cmh) && cmh < 60) {
        m.style.setProperty("max-height", "none", "important");
      }
      if (cs.height !== "auto" && !isNaN(chh) && chh < 60) {
        m.style.setProperty("height", "auto", "important");
      }
      /* 复查：限制都清了仍很矮、且元素里确实有内容，多半是祖先不给空间，兜底给一个可用高度。
         要求 scrollHeight > 60：空容器（分隔条之类）不该被撑开。 */
      if (m.getBoundingClientRect().height < 60 && m.scrollHeight > 60) {
        m.style.setProperty("min-height", "150px", "important");
        m.style.setProperty("max-height", "calc(100dvh - 160px)", "important");
        m.style.setProperty("overflow-y", "auto", "important");
        m.setAttribute("data-dsha-squash", "1");
      }
    }
    /* 浮层候选：已知后缀/角色 + 最近新增的定位元素（覆盖任意类名） */
    function menuCandidates() {
      var out = [];
      var list = document.querySelectorAll(
        "[class$=_menu], [role=menu], [role=listbox], [class*=_popover], [class*=_dropdown]");
      var i;
      for (i = 0; i < list.length; i++) {
        if (out.indexOf(list[i]) < 0) { out.push(list[i]); }
      }
      /* 浮层常常包在新增的 wrapper 里：新增节点自身和它的子树都要看 */
      function consider(el) {
        if (!el || !el.isConnected || out.indexOf(el) >= 0) { return; }
        var fcs = window.getComputedStyle(el);
        if (fcs.position === "static") { return; }
        var z = parseInt(fcs.zIndex, 10) || 0;
        var role = el.getAttribute("role") || "";
        if (z >= 2 || role === "menu" || role === "listbox" || role === "dialog" || el.hasAttribute("aria-modal")) {
          out.push(el);
        }
      }
      var fresh = state.fresh || [];
      for (i = 0; i < fresh.length; i++) {
        var el = fresh[i];
        if (!el.isConnected) { continue; }
        consider(el);
        var subs = el.querySelectorAll("div,section,aside,ul,ol,menu,form,span");
        for (var s = 0; s < subs.length && s < 60; s++) { consider(subs[s]); }
      }
      return out;
    }
    function fitMenus() {
      var vh = window.innerHeight || 0, vw = window.innerWidth || 0;
      if (!vh || !vw) { return; }
      var list = menuCandidates();
      for (var i = 0; i < list.length; i++) {
        var m = list[i];
        if (m.id === scrimId || m.closest("[data-dsha-set]")) { continue; }
        rescueClip(m);
        squashFix(m);
        var cs = window.getComputedStyle(m);
        if (cs.display === "none" || cs.visibility === "hidden" || cs.position === "static") { continue; }
        var r = m.getBoundingClientRect();
        if (r.height < 16 || r.width < 16) { continue; }
        var pp = (m.getAttribute("data-dsha-shift") || "0,0").split(",");
        var px = parseFloat(pp[0]) || 0, py = parseFloat(pp[1]) || 0;
        /* 先还原出未平移时的自然位置，再据此算需要的总偏移，避免自我累加后震荡 */
        var natL = r.left - px, natR = r.right - px;
        var natT = r.top - py, natB = r.bottom - py;
        var nx = 0, ny = 0;
        if (natR > vw - 6) { nx = (vw - 6) - natR; }
        if (natL + nx < 6) { nx = 6 - natL; }
        if (natB > vh - 6) { ny = (vh - 6) - natB; }
        if (natT + ny < 6) { ny = 6 - natT; }
        nx = Math.round(nx); ny = Math.round(ny);
        if (nx === Math.round(px) && ny === Math.round(py)) { continue; }
        m.setAttribute("data-dsha-shift", nx + "," + ny);
        m.style.translate = (nx || ny)
          ? ((nx ? nx + "px" : "0") + " " + (ny ? ny + "px" : "0"))
          : "";
      }
    }
    /* 浮层是随点击出现的，且带动画：折叠窗口期多补几次，另有低频轮询兜底 */
    function watchMenus() {
      if (state.menuWatch) { return; }
      state.menuWatch = setInterval(function () {
        if (!isMobile()) { return; }
        fitMenus();
      }, 450);
    }
    function sync() {
      autoCollapse();
      navButton();
      frameTag();
      settingsTag();
      drawer("sidebar");
      drawer("rightbar");
      exclusive();
      scrim();
      measureHeader();
      fitMenus();
      setTimeout(fitMenus, 160);
      setTimeout(fitMenus, 420);
    }
    function schedule() {
      if (timer) { clearTimeout(timer); }
      timer = setTimeout(function () { timer = null; sync(); }, 120);
    }
    if (!state.bound) {
      state.bound = true;
      if (frame && typeof MutationObserver === "function") {
        new MutationObserver(sync).observe(frame, {
          attributes: true,
          attributeFilter: ["data-sidebar-collapsed", "data-rightbar-collapsed"]
        });
        if (document.body) {
          new MutationObserver(function (records) {
            for (var i = 0; i < records.length; i++) {
              var nodes = records[i].addedNodes;
              for (var j = 0; j < nodes.length; j++) {
                if (nodes[j] && nodes[j].nodeType === 1) { state.fresh.push(nodes[j]); }
              }
            }
            if (state.fresh.length > 80) { state.fresh.splice(0, state.fresh.length - 80); }
            schedule();
          }).observe(document.body, { childList: true, subtree: true });
        }
      }
      window.addEventListener("resize", schedule);
      window.addEventListener("orientationchange", function () { setTimeout(sync, 250); });
      watchMenus();
    }
    sync();
    /* 水合前点击可能无效，且应用首屏未必再有 DOM 变动：前几秒补几次校准 */
    setTimeout(sync, 600);
    setTimeout(sync, 1400);
    setTimeout(sync, 2600);
    return "ok:mobile=" + (isMobile() ? 1 : 0) + ",top=" + top + ",bottom=" + bottom +
      ",drawer=" + (root.classList.contains(cls("sidebar")) ? 1 : 0);
  } catch (e) {
    return "error:" + e;
  }
})();
"""

/**
 * DeepSeek Harness 0.2.0-rc.2 的定制适配：这个版本专属的组件级改造注入。
 *
 * 目录约定：`ui/web/adapt/` 下每个已适配版本各占一个目录（如本目录 `v020rc2/`），
 * 目录里只放这个版本的 CSS 与 JS。不同版本的页面结构、类名和可用的驱动方式都可能不同，
 * 分目录存放后改一个版本不会波及其它版本，新增版本也只是多一个目录。
 *
 * 本版本的页面结构：`#root` 内一个 CSS Grid 框架元素带内联
 * `grid-template-columns: 280px minmax(0px,1fr) minmax(0px,0px)`，没有任何媒体查询，
 * 因此手机宽度下会话区被挤到 110px 左右。这里的做法是不改网页源码，只注入 CSS/JS：
 *
 * 1. 手机（视口 < 600 CSS px，等价 Android sw600dp 平板分界）下把三栏改成单栏 + 覆盖式抽屉；
 * 2. 手机下把设置弹窗左侧的标签导航搬到顶部，图标在上、文字在下。
 *
 * 外壳级的安全区与视口塌陷兜底不在这里，见 `adapt/BaseAdapt.kt`：那些与版本无关，
 * 且关掉响应式适配后也必须继续施加。
 *
 * 单位说明：网页 viewport 为 `width=device-width, initial-scale=1`，即 1 CSS px = 1 dp，
 * 因此这里注入的插值一律是 **dp 值**，不能传物理像素。
 */
internal val V020Rc2Adapt = VersionAdapt(css = V020RC2_CSS, js = V020RC2_JS)
