package com.glassous.dsha.ui.update

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp

/** 块与块之间的间距；行内的换行交给 Text 自己处理。 */
private val BLOCK_SPACING = 8.dp

/** 列表每一层缩进的宽度，以及标记与正文之间的间距。 */
private val LIST_INDENT = 14.dp
private val LIST_MARKER_SPACING = 8.dp

/**
 * 极简 Markdown 渲染：只覆盖发布说明里真正会用到的语法，从而省掉一个第三方 Markdown 库
 * （本项目的依赖一直保持最小）。
 *
 * 支持标题、段落、无序/有序列表（行首两空格缩进算一层）、引用、围栏代码块、分隔线，
 * 以及行内的加粗、斜体、行内代码与链接；表格、图片、内嵌 HTML 等一律按普通文字原样显示。
 */
@Composable
fun MarkdownText(
    text: String,
    modifier: Modifier = Modifier,
) {
    val blocks = remember(text) { parseBlocks(text) }
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(BLOCK_SPACING),
    ) {
        blocks.forEachIndexed { index, block ->
            // 标题前多留一点空档，让段落与标题的层次靠间距也能看出来
            val spacing = if (index > 0 && block is MarkdownBlock.Heading) {
                Modifier.padding(top = BLOCK_SPACING)
            } else {
                Modifier
            }
            when (block) {
                is MarkdownBlock.Heading -> HeadingBlock(block, spacing)
                is MarkdownBlock.Paragraph -> InlineText(text = block.content, modifier = spacing)
                is MarkdownBlock.ListItem -> ListItemBlock(block, spacing)
                is MarkdownBlock.Quote -> QuoteBlock(block, spacing)
                is MarkdownBlock.Code -> CodeBlock(block, spacing)
                MarkdownBlock.Divider -> HorizontalDivider(
                    modifier = spacing,
                    color = MaterialTheme.colorScheme.outlineVariant,
                )
            }
        }
    }
}

@Composable
private fun HeadingBlock(block: MarkdownBlock.Heading, modifier: Modifier = Modifier) {
    Text(
        text = inlineMarkdown(block.content),
        modifier = modifier,
        style = when (block.level) {
            1 -> MaterialTheme.typography.titleMedium
            2 -> MaterialTheme.typography.titleSmall
            else -> MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Medium)
        },
        color = MaterialTheme.colorScheme.onSurface,
    )
}

/** 列表项：标记与正文分列，正文较长换行时悬挂缩进，标记始终对齐首行。 */
@Composable
private fun ListItemBlock(block: MarkdownBlock.ListItem, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = LIST_INDENT * block.depth),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = block.marker,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(LIST_MARKER_SPACING))
        InlineText(
            text = block.content,
            modifier = Modifier.weight(1f),
        )
    }
}

/** 引用：左侧一根竖线（跟随正文高度）+ 稍暗的正文。 */
@Composable
private fun QuoteBlock(block: MarkdownBlock.Quote, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .fillMaxHeight()
                .clip(RoundedCornerShape(2.dp))
                .background(MaterialTheme.colorScheme.outlineVariant),
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = inlineMarkdown(block.content),
            style = MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 代码块：等宽字体 + 底色，过长时不换行而是横向滚动。 */
@Composable
private fun CodeBlock(block: MarkdownBlock.Code, modifier: Modifier = Modifier) {
    Text(
        text = block.content,
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 10.dp),
        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** 一段普通正文，行内标记已解析。 */
@Composable
private fun InlineText(text: String, modifier: Modifier = Modifier) {
    Text(
        text = inlineMarkdown(text),
        modifier = modifier,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** 行内样式：加粗、行内代码、链接、斜体。 */
@OptIn(ExperimentalTextApi::class)
@Composable
private fun inlineMarkdown(content: String): AnnotatedString {
    val linkColor = MaterialTheme.colorScheme.primary
    val codeColor = MaterialTheme.colorScheme.onSurface
    val codeBackground = MaterialTheme.colorScheme.surfaceContainerHigh
    return remember(content, linkColor, codeColor, codeBackground) {
        buildAnnotatedString { appendInline(content, linkColor, codeColor, codeBackground) }
    }
}

private val INLINE_PATTERN = Regex(
    "\\*\\*(.+?)\\*\\*" +           // 1：加粗
        "|`([^`]+)`" +              // 2：行内代码
        "|\\[([^]]+)]\\(([^)\\s]+)\\)" + // 3：链接文字，4：链接地址
        "|\\*([^*]+)\\*" +          // 5：斜体 *
        "|(?<![0-9A-Za-z])_([^_]+)_(?![0-9A-Za-z])", // 6：斜体 _（不与 a_b_c 这类标识符冲突）
)

/**
 * 把一段文字里的行内标记转成带样式的文本。
 * 正则按"从当前光标往后找第一个匹配"推进，找不到就原样输出剩余部分，因此未闭合的标记不会吞掉后面的内容。
 */
@OptIn(ExperimentalTextApi::class)
private fun AnnotatedString.Builder.appendInline(
    content: String,
    linkColor: Color,
    codeColor: Color,
    codeBackground: Color,
) {
    var cursor = 0
    while (cursor < content.length) {
        val match = INLINE_PATTERN.find(content, cursor) ?: break
        if (match.range.first > cursor) {
            append(content.substring(cursor, match.range.first))
        }
        val groups = match.groupValues
        when {
            groups[1].isNotEmpty() -> withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) {
                appendInline(groups[1], linkColor, codeColor, codeBackground)
            }

            groups[2].isNotEmpty() -> withStyle(
                SpanStyle(
                    fontFamily = FontFamily.Monospace,
                    background = codeBackground,
                    color = codeColor,
                ),
            ) {
                append(groups[2])
            }

            groups[3].isNotEmpty() -> withLink(LinkAnnotation.Url(groups[4])) {
                withStyle(
                    SpanStyle(
                        color = linkColor,
                        textDecoration = TextDecoration.Underline,
                    ),
                ) {
                    append(groups[3])
                }
            }

            else -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                append(groups[5].ifEmpty { groups[6] })
            }
        }
        cursor = match.range.last + 1
    }
    if (cursor < content.length) {
        append(content.substring(cursor))
    }
}

/** 渲染前的中间结构：一个块对应一段排版。 */
private sealed interface MarkdownBlock {

    data class Heading(val level: Int, val content: String) : MarkdownBlock

    data class Paragraph(val content: String) : MarkdownBlock

    /** @param marker 列表标记，无序为 `•`，有序为 `1.` 这类序号 */
    data class ListItem(val marker: String, val content: String, val depth: Int) : MarkdownBlock

    data class Quote(val content: String) : MarkdownBlock

    data class Code(val content: String) : MarkdownBlock

    data object Divider : MarkdownBlock
}

private val HEADING_PATTERN = Regex("^(#{1,6})\\s+(.*)$")
private val BULLET_PATTERN = Regex("^[-*+]\\s+(.*)$")
private val ORDERED_PATTERN = Regex("^(\\d+)[.)]\\s+(.*)$")
private val DIVIDER_PATTERN = Regex("^(-{3,}|\\*{3,}|_{3,})$")

/** 行首空白宽度：两格算一层缩进（制表符按四格算）。 */
private fun indentWidth(line: String): Int = line.takeWhile { it == ' ' || it == '\t' }
    .sumOf { if (it == '\t') 4 else 1 }

/**
 * 逐行切块。段落里的换行按 Markdown 的规则合成一行空格；
 * 围栏代码块原样保留内容与换行，不给其中的标记做任何解析。
 */
private fun parseBlocks(markdown: String): List<MarkdownBlock> {
    val blocks = mutableListOf<MarkdownBlock>()
    val lines = markdown.replace("\r\n", "\n").replace('\r', '\n').split('\n')
    val paragraph = mutableListOf<String>()
    var index = 0

    fun flushParagraph() {
        if (paragraph.isNotEmpty()) {
            blocks += MarkdownBlock.Paragraph(paragraph.joinToString(" "))
            paragraph.clear()
        }
    }

    while (index < lines.size) {
        val line = lines[index]
        val content = line.trim()
        when {
            content.startsWith("```") -> {
                flushParagraph()
                index++
                val code = mutableListOf<String>()
                while (index < lines.size && !lines[index].trim().startsWith("```")) {
                    code += lines[index]
                    index++
                }
                // 跳过收尾的 ```（缺失时 index 已到末尾，下一轮循环自然结束）
                index++
                blocks += MarkdownBlock.Code(code.joinToString("\n"))
            }

            content.isEmpty() -> {
                flushParagraph()
                index++
            }

            else -> {
                val heading = HEADING_PATTERN.find(content)
                val bullet = BULLET_PATTERN.find(content)
                val ordered = ORDERED_PATTERN.find(content)
                val depth = indentWidth(line) / 2
                when {
                    heading != null -> {
                        flushParagraph()
                        blocks += MarkdownBlock.Heading(
                            level = heading.groupValues[1].length,
                            content = heading.groupValues[2].trim(),
                        )
                    }

                    DIVIDER_PATTERN.matches(content) -> {
                        flushParagraph()
                        blocks += MarkdownBlock.Divider
                    }

                    content.startsWith(">") -> {
                        flushParagraph()
                        blocks += MarkdownBlock.Quote(content.removePrefix(">").trim())
                    }

                    bullet != null -> {
                        flushParagraph()
                        blocks += MarkdownBlock.ListItem("•", bullet.groupValues[1].trim(), depth)
                    }

                    ordered != null -> {
                        flushParagraph()
                        blocks += MarkdownBlock.ListItem(
                            marker = "${ordered.groupValues[1]}.",
                            content = ordered.groupValues[2].trim(),
                            depth = depth,
                        )
                    }

                    else -> paragraph += content
                }
                index++
            }
        }
    }
    flushParagraph()
    return blocks
}
