package com.glassous.dsha.ui.web.adapt

import com.glassous.dsha.data.HarnessVersion
import com.glassous.dsha.ui.web.adapt.v020rc2.V020Rc2Adapt

/**
 * 某个 Harness 版本的定制适配内容：该版本专属的 CSS 与 JS。
 *
 * 两段脚本共用同一套选择器锚点，必须整体替换，因此成对持有。
 */
class VersionAdapt(val css: String, val js: String)

/**
 * 版本 → 定制适配，也是 `adapt/` 这个目录的入口。
 *
 * 新增适配版本时三步走：在 [HarnessVersion] 登记版本号 → 在 `adapt/` 下新建该版本的目录
 * 并把它的 CSS/JS 放进去 → 在此补一条分支。when 是表达式且没有 else，
 * 漏掉分支会直接编译失败，不会出现"选出没适配的版本"这种静默错误。
 */
internal fun versionAdaptOf(version: HarnessVersion): VersionAdapt = when (version) {
    HarnessVersion.V0_2_0_RC_2 -> V020Rc2Adapt
}
