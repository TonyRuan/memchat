package com.memorychat.app.ui.markdown

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun MarkdownText(
    markdown: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    val document = remember(markdown) { MarkdownParser.parse(markdown) }
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        document.blocks.forEach { block ->
            when (block) {
                is MarkdownBlock.Paragraph -> InlineMarkdownText(block.inlines, color)
                is MarkdownBlock.Heading -> InlineMarkdownText(
                    inlines = block.inlines,
                    color = color,
                    style = when (block.level) {
                        1 -> MaterialTheme.typography.titleMedium
                        2 -> MaterialTheme.typography.titleSmall
                        else -> MaterialTheme.typography.bodyLarge
                    }.copy(fontWeight = FontWeight.SemiBold)
                )
                is MarkdownBlock.BulletList -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    block.items.forEach { item ->
                        Row(modifier = Modifier.fillMaxWidth()) {
                            Text("•", color = color, modifier = Modifier.width(18.dp))
                            InlineMarkdownText(item, color, modifier = Modifier.weight(1f))
                        }
                    }
                }
                is MarkdownBlock.Quote -> Row(modifier = Modifier.fillMaxWidth()) {
                    Box(
                        modifier = Modifier
                            .width(3.dp)
                            .background(color.copy(alpha = 0.35f))
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    InlineMarkdownText(
                        inlines = block.inlines,
                        color = color.copy(alpha = 0.82f),
                        modifier = Modifier.weight(1f)
                    )
                }
                is MarkdownBlock.CodeBlock -> Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.62f),
                            RoundedCornerShape(6.dp)
                        )
                        .padding(8.dp)
                ) {
                    Text(
                        text = block.code,
                        color = color,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun InlineMarkdownText(
    inlines: List<MarkdownInline>,
    color: Color,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyMedium
) {
    val annotated = buildAnnotatedString {
        inlines.forEach { inline ->
            when (inline) {
                is MarkdownInline.Text -> append(inline.text)
                is MarkdownInline.Strong -> pushStyle(SpanStyle(fontWeight = FontWeight.Bold)).also {
                    append(inline.text)
                    pop()
                }
                is MarkdownInline.Emphasis -> pushStyle(SpanStyle(fontStyle = FontStyle.Italic)).also {
                    append(inline.text)
                    pop()
                }
                is MarkdownInline.Code -> pushStyle(
                    SpanStyle(
                        fontFamily = FontFamily.Monospace,
                        background = color.copy(alpha = 0.12f)
                    )
                ).also {
                    append(inline.text)
                    pop()
                }
                is MarkdownInline.Link -> pushStyle(
                    SpanStyle(
                        color = MaterialTheme.colorScheme.primary,
                        textDecoration = TextDecoration.Underline
                    )
                ).also {
                    append(inline.text)
                    pop()
                }
            }
        }
    }
    Text(
        text = annotated,
        color = color,
        style = style,
        modifier = modifier
    )
}
