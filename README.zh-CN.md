# DSHA

[English](README.md) · **简体中文**

[![Release](https://img.shields.io/github/v/release/Glassous/DSHA?style=flat-square&label=release)](https://github.com/Glassous/DSHA/releases)
![Platform](https://img.shields.io/badge/platform-Android%2013%2B-3DDC84?style=flat-square&logo=android&logoColor=white)
![License](https://img.shields.io/badge/license-MIT-blue?style=flat-square)

**DSHA** 是你自建 DeepSeek Harness（DSH）服务的轻量 Android 外壳。填入服务地址（若服务位于 Basic 鉴权之后，再填用户名与密码），DSHA 就会在全屏 WebView 中打开该页面，并注入 CSS/JS，让按桌面端设计的 DSH 网页在手机上正常可用。

<p align="center">
  <img src="docs/screenshots/sidebar.jpg" alt="DSHA 在 Android 手机上运行的 DSH 界面" width="320">
</p>

## 功能

- **连接组** — 可保存任意多个 DSH 地址，每个连接组有独立的名字（留空时回退为主机名）、独立的账号密码、独立的适配设置，以及「上次打开」时间。
- **按连接组启用 Basic 鉴权** — 默认关闭，因为 DSH 本身没有登录。当服务位于 1Panel 之类的反向代理之后时打开它：DSHA 会自动填充浏览器登录表单，并在首个请求上附带 `Authorization: Basic` 头重试。
- **手机端网页适配** — 把 DSH 页面重排为适合手机屏幕的布局。每个连接组各自声明适配的 DSH 版本，适配也可整体关闭（此时页面按原样加载），并可随时切换版本。
- **像一个应用那样工作的 WebView** — 启动动画、避让安全区的边到边布局、加载失败与鉴权失败的独立错误态、页面内返回处理、关闭前的二次确认。同站链接留在壳内，站外链接与下载交给系统浏览器和下载管理器。
- **应用内更新** — 启动时自动检测（可开关）或手动检测 GitHub Release，发布说明以 Markdown 渲染，支持忽略某个版本。
- **多语言** — 内置英文与简体中文，界面跟随系统语言，并支持 Android 13+ 的按应用语言设置。首次启动依次引导选择语言与阅读用户协议。
- **没有多余负担** — Jetpack Compose + Material 3，只用平台 API（`SharedPreferences`、`org.json`、`HttpURLConnection`），无统计、无广告、无 DI 框架。清单文件只声明 `INTERNET` 与 `POST_NOTIFICATIONS` 两个权限。

## 环境要求

| 项目 | 取值 |
| --- | --- |
| Android | 13（API 33）及以上 — `minSdk 33`、`targetSdk 36` |
| 构建 SDK | Android SDK Platform 37（`compileSdk 37`） |
| JDK | Gradle toolchain 21，由 foojay resolver 自动下载；源码/目标兼容级别为 Java 17 |
| Gradle / AGP / Kotlin | Gradle 9.5.0（wrapper）· AGP 9.3.3 · Kotlin 2.2.10 |
| 网络 | 仅需访问你自建的 DSH 服务，以及更新检测用的 `api.github.com` |

## 构建与安装

```bash
cd android

./gradlew :app:assembleDebug      # -> app/build/outputs/apk/debug/DSHA-v1.0.0-debug.apk
./gradlew :app:assembleRelease    # -> app/build/outputs/apk/release/DSHA-v1.0.0.apk
./gradlew :app:testDebugUnitTest  # 单元测试
```

Windows 下把 `./gradlew` 换成 `gradlew.bat`。

- **Release APK 未签名。** 项目没有配置 `signingConfigs`，装到设备前请自行签名（或补上自己的签名配置）。Debug 构建使用默认调试签名。
- Debug 构建会开启 WebView 远程调试，可用 `chrome://inspect` 查看已加载的页面。
- 构建前无需任何配置：没有 API Key、没有服务地址、没有密钥。应用在运行时由连接组列表指向你的部署。

### Debug 与 Release 是两个独立的应用

调试版有自己的应用标识（`app/build.gradle.kts` 中的 `applicationIdSuffix = ".debug"`）和自己的桌面名称（在 `src/debug/res/` 中覆盖）：

| | Release | Debug |
| --- | --- | --- |
| 应用包名 | `com.glassous.dsha` | `com.glassous.dsha.debug` |
| 桌面名称 | DSHA | DSHA Debug |
| 签名 | 未签名 —— 自行签名 | 默认调试签名 |
| WebView 远程调试 | 关闭 | 开启 —— `chrome://inspect` |
| 产物 | `DSHA-v<版本>.apk` | `DSHA-v<版本>-debug.apk` |

两者的包名不同，因此可以同时装在同一台设备上：谁也覆盖不了谁，连接组、设置、用户协议接受状态各自独立保存。其余部分完全共用 —— 相同的 `versionName`、相同的更新检测、相同的代码。

## 网页适配是怎么工作的

适配分三层注入，因此版本专属规则不必与通用规则互相打架：

| 层 | 文件 | 职责 |
| --- | --- | --- |
| 基础层 | `ui/web/adapt/BaseAdapt.kt` | 始终施加 —— 对所有已适配的 DSH 版本、乃至适配开关关闭时都生效：安全区 padding、视口单位兜底、滚动行为。 |
| 版本层 | `ui/web/adapt/AdaptInjection.kt` | 把当前生效各层的 CSS/JS 组装为一段脚本，将 `__DSHA_SAFE_TOP__` 之类的 token 替换为真实值，再交给 `evaluateJavascript` 执行。 |
| 具体版本 | `ui/web/adapt/v020rc2/V020Rc2Adapt.kt` | 针对某一个 DSH 版本的组件级 CSS/JS（当前为 `0.2.0-rc.2`）。规则以类名后缀为锚点，因此不受构建哈希前缀影响。 |

`ui/web/adapt/VersionAdapt.kt` 用没有 `else` 分支的 `when` 按版本枚举分派：只加 `HarnessVersion` 条目而不写对应的适配，项目会直接编译失败。

### 新增一个 DSH 版本的适配

1. 在 `data/HarnessVersion.kt` 中加入该版本。
2. 在 `ui/web/adapt/` 下、与 `v020rc2/` 平级新建包，放入该版本的 CSS/JS。
3. 在 `VersionAdapt.kt` 中接上分派 —— 编译器会直接指出缺失的分支。

## 发版与更新

- **版本号只有一处事实来源**：`android/app/build.gradle.kts` 中的 `appVersionName`。改完之后把提交打上 `v<同一版本号>` 的 tag 并发布 GitHub Release —— 更新检测正是拿这个 tag 与应用自身的 `versionName` 比较。
- 版本比较是宽松的语义化版本：`1.0` 与 `1.0.0` 等价。
- 构建产物命名为 `DSHA-v<版本>.apk`（Debug 为 `DSHA-v<版本>-debug.apk`）。更新时优先寻找同名附件，其次任意 `.apk` 附件，最后回退到发布页。
- **两个构建类型共用同一个版本号**：Debug 刻意不加 `versionName` 后缀，因为更新检测正是拿它与 Release tag 比较；区分两版靠的是包名，而不是版本字符串。
- **双语发布说明**：发布正文中用仅含 `<!-- en -->` 或 `<!-- zh -->` 的一行分段。应用按界面语言摘取对应段落，缺少目标语言时回落英文。
- 更新检测刻意宽松：网络异常、403、404 一律当作「没有更新」，因此检测永远不会挡住正常使用。

## 项目结构

```
android/
├── app/
│   ├── build.gradle.kts                 # 版本号、构建类型、SDK 级别、依赖
│   └── src/
│       ├── main/java/com/glassous/dsha/
│       │   ├── MainActivity.kt          # 唯一的 Activity：边到边 Compose 宿主
│       │   ├── DshaApplication.kt       # 应用级仓库
│       │   ├── data/                    # 连接组、设置、版本比较、更新检测、URL 工具
│       │   ├── ui/nav/                  # 路由表与转场
│       │   ├── ui/profile/              # 连接组列表、编辑页、版本选择
│       │   ├── ui/settings/             # 设置、语言、首次引导、用户协议
│       │   ├── ui/update/               # 更新状态机、弹窗、Markdown 渲染
│       │   ├── ui/web/                  # WebView 宿主、加载与错误状态
│       │   │   └── adapt/               # 基础层、版本层、各版本适配
│       │   ├── ui/theme/                # 配色与字体
│       │   └── ui/transition/           # 共享元素转场
│       ├── main/res/                    # 文案（英文为默认，简体中文在 values-zh）、主题、图标
│       ├── debug/res/                   # 仅调试版覆盖：app_name → "DSHA Debug"
│       ├── test/                        # JVM 单元测试（更新检测）
│       └── androidTest/                 # 仪器测试源集
├── gradle/libs.versions.toml            # 版本目录
├── gradle/wrapper/                      # Gradle 9.5.0 wrapper
└── settings.gradle.kts
docs/                                    # 品牌资源与截图
```

## 数据与隐私

- 连接组、设置、用户协议的接受状态以 JSON 存放在应用私有的 `SharedPreferences` 中。**密码在本机以明文保存** —— 这是刻意的取舍，因为它只用于解锁你自建服务前的 Basic 鉴权。请勿在不受信任的设备上保存账号密码。
- 应用自身不上传任何数据，也不含统计、追踪或广告代码。你在页面内做的一切只发生在你与服务之间。
- 协议全文见应用内 **设置 → 用户协议**。

## 许可协议

本项目基于 [MIT 协议](LICENSE) 发布。
