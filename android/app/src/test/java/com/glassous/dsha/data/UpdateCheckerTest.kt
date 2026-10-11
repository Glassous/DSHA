package com.glassous.dsha.data

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 发布说明的语言摘取：以本地模拟的发布正文覆盖双语分段、缺语言回退与旧格式兼容，
 * 保证更新弹窗在不同界面语言下拿到的是对应语言的说明。
 *
 * 只测纯字符串处理（[UpdateChecker.pickChangelog]），不触网、不依赖 Android 环境。
 */
class UpdateCheckerTest {

    /** 与发布模板一致的模拟正文：英文段在前、中文段在后，每段以国旗行开头。 */
    private val englishFirst = """
        <!-- en -->
        ## 🇬🇧 English

        First stable release.

        - One tap to connect
        <!-- zh -->
        ## 🇨🇳 简体中文

        第一个正式版。

        - 一键连接
    """.trimIndent()

    @Test
    fun englishRequestGetsEnglishSection() {
        assertEquals(
            "First stable release.\n\n- One tap to connect",
            UpdateChecker.pickChangelog(englishFirst, "en"),
        )
    }

    @Test
    fun chineseRequestGetsChineseSection() {
        assertEquals(
            "第一个正式版。\n\n- 一键连接",
            UpdateChecker.pickChangelog(englishFirst, "zh"),
        )
    }

    @Test
    fun sectionOrderDoesNotMatter() {
        val chineseFirst = "<!-- zh -->\n中文正文\n<!-- en -->\nEnglish body"
        assertEquals("中文正文", UpdateChecker.pickChangelog(chineseFirst, "zh"))
        assertEquals("English body", UpdateChecker.pickChangelog(chineseFirst, "en"))
    }

    @Test
    fun missingRequestedLanguageFallsBackToEnglish() {
        val englishOnly = "<!-- en -->\nEnglish only."
        assertEquals("English only.", UpdateChecker.pickChangelog(englishOnly, "zh"))
    }

    @Test
    fun unsupportedLanguageFallsBackToEnglish() {
        assertEquals(
            "First stable release.\n\n- One tap to connect",
            UpdateChecker.pickChangelog(englishFirst, "ja"),
        )
    }

    @Test
    fun missingEnglishFallsBackToFirstSection() {
        val chineseOnly = "<!-- zh -->\n仅中文说明。"
        assertEquals("仅中文说明。", UpdateChecker.pickChangelog(chineseOnly, "en"))
    }

    @Test
    fun bodyWithoutMarkersIsReturnedTrimmed() {
        assertEquals("Plain notes.", UpdateChecker.pickChangelog("  Plain notes.  ", "zh"))
    }

    @Test
    fun emptyBodyStaysEmpty() {
        assertEquals("", UpdateChecker.pickChangelog("", "zh"))
        assertEquals("", UpdateChecker.pickChangelog("   \n  ", "en"))
    }

    @Test
    fun crlfBodyIsParsed() {
        val crlf = "<!-- en -->\r\nEnglish line\r\n<!-- zh -->\r\n中文行"
        assertEquals("English line", UpdateChecker.pickChangelog(crlf, "en"))
        assertEquals("中文行", UpdateChecker.pickChangelog(crlf, "zh"))
    }

    @Test
    fun markerCaseSpacingAndRegionAreTolerated() {
        val body = "<!--  EN  -->\nEnglish\n<!-- zh-CN -->\n中文"
        assertEquals("English", UpdateChecker.pickChangelog(body, "en"))
        assertEquals("中文", UpdateChecker.pickChangelog(body, "zh"))
    }

    @Test
    fun languageFlagLinesAreStripped() {
        // 网页上的国旗分隔行在应用内不显示（englishFirst 的两个断言已覆盖常规形态，这里再测只带国旗的形态）
        assertEquals("English body", UpdateChecker.pickChangelog("<!-- en -->\n# 🇬🇧\nEnglish body", "en"))
        assertEquals("中文正文", UpdateChecker.pickChangelog("<!-- zh -->\n# 🇨🇳\n中文正文", "zh"))
    }

    @Test
    fun strayFlagLineIsRemovedWhole() {
        // 约定国旗只用于语言分隔行；出现在普通行里同样整行移除
        val body = "<!-- en -->\nPlain line\n🇬🇧 stray line\nTail"
        assertEquals("Plain line\nTail", UpdateChecker.pickChangelog(body, "en"))
    }

    @Test
    fun emptySectionIsSkipped() {
        // 中文标记后没有内容：请求中文时按缺中文处理，回退英文段
        val body = "<!-- en -->\nEnglish\n<!-- zh -->\n"
        assertEquals("English", UpdateChecker.pickChangelog(body, "zh"))
    }
}
