package com.glassous.dsha.data

/**
 * 当前应用已适配的 DeepSeek Harness 版本。
 *
 * 手机端响应式适配深度绑定 Harness 的页面结构，不同版本各有一套注入实现，
 * 因此这里集中登记"已适配版本"，作为单一事实来源：连接组的选择界面、新建时的默认值、
 * 以及已存版本失效后的回退目标，全部取自这里。
 *
 * 按「从旧到新」声明，[latest] 即最后一项——后续新增适配版本时只需追加枚举项，
 * 默认值与回退随之自动前移。
 */
enum class HarnessVersion(val label: String) {
    V0_2_0_RC_2("0.2.0-rc.2"),
    ;

    companion object {
        /** 当前适配的最高版本：新建连接组的默认值，也是已存版本不再被适配时的回退目标。 */
        val latest: HarnessVersion get() = entries.last()

        /** 已适配版本列表（旧 → 新），供连接组的版本选择界面展示。 */
        val supported: List<HarnessVersion> get() = entries

        /** 持久化取值 → 版本；未适配（为空、或为已下线版本）时回退到 [latest]。 */
        fun fromStored(stored: String?): HarnessVersion =
            entries.firstOrNull { it.label == stored } ?: latest
    }
}
