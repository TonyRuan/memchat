package com.memorychat.app.ui.markdown

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownParserTest {
    @Test
    fun parsesInlineEmphasisCodeAndLinks() {
        val document = MarkdownParser.parse("Hello **bold** and *italic* with `code` plus [site](https://example.com)")
        val paragraph = document.blocks.single() as MarkdownBlock.Paragraph

        assertEquals(
            listOf(
                MarkdownInline.Text("Hello "),
                MarkdownInline.Strong("bold"),
                MarkdownInline.Text(" and "),
                MarkdownInline.Emphasis("italic"),
                MarkdownInline.Text(" with "),
                MarkdownInline.Code("code"),
                MarkdownInline.Text(" plus "),
                MarkdownInline.Link("site", "https://example.com")
            ),
            paragraph.inlines
        )
    }

    @Test
    fun parsesFencedCodeBlockWithoutInlineMarkdown() {
        val document = MarkdownParser.parse("Before\n```kotlin\nfun main() = println(\"**raw**\")\n```\nAfter")

        assertEquals(3, document.blocks.size)
        val code = document.blocks[1] as MarkdownBlock.CodeBlock
        assertEquals("kotlin", code.language)
        assertEquals("fun main() = println(\"**raw**\")", code.code)
    }

    @Test
    fun parsesListQuoteAndHeadingBlocks() {
        val document = MarkdownParser.parse("# Title\n- one\n- **two**\n> note")

        assertEquals(MarkdownBlock.Heading(1, listOf(MarkdownInline.Text("Title"))), document.blocks[0])
        val list = document.blocks[1] as MarkdownBlock.BulletList
        assertEquals(2, list.items.size)
        assertEquals(listOf(MarkdownInline.Text("one")), list.items[0])
        assertEquals(listOf(MarkdownInline.Strong("two")), list.items[1])
        assertEquals(MarkdownBlock.Quote(listOf(MarkdownInline.Text("note"))), document.blocks[2])
    }

    @Test
    fun leavesUnclosedMarkersAsPlainText() {
        val document = MarkdownParser.parse("This is **not closed and `raw")
        val paragraph = document.blocks.single() as MarkdownBlock.Paragraph

        assertTrue(paragraph.inlines.single() is MarkdownInline.Text)
        assertEquals("This is **not closed and `raw", (paragraph.inlines.single() as MarkdownInline.Text).text)
    }
}
