package com.memorychat.app.ui.markdown

data class MarkdownDocument(val blocks: List<MarkdownBlock>)

sealed interface MarkdownBlock {
    data class Paragraph(val inlines: List<MarkdownInline>) : MarkdownBlock
    data class Heading(val level: Int, val inlines: List<MarkdownInline>) : MarkdownBlock
    data class BulletList(val items: List<List<MarkdownInline>>) : MarkdownBlock
    data class Quote(val inlines: List<MarkdownInline>) : MarkdownBlock
    data class CodeBlock(val language: String?, val code: String) : MarkdownBlock
}

sealed interface MarkdownInline {
    data class Text(val text: String) : MarkdownInline
    data class Strong(val text: String) : MarkdownInline
    data class Emphasis(val text: String) : MarkdownInline
    data class Code(val text: String) : MarkdownInline
    data class Link(val text: String, val url: String) : MarkdownInline
}

object MarkdownParser {
    fun parse(markdown: String): MarkdownDocument {
        val lines = markdown.replace("\r\n", "\n").replace('\r', '\n').split('\n')
        val blocks = mutableListOf<MarkdownBlock>()
        var index = 0

        while (index < lines.size) {
            val line = lines[index]
            if (line.isBlank()) {
                index++
                continue
            }

            if (line.trimStart().startsWith("```")) {
                val fence = line.trimStart()
                val language = fence.removePrefix("```").trim().takeIf { it.isNotBlank() }
                val codeLines = mutableListOf<String>()
                index++
                while (index < lines.size && !lines[index].trimStart().startsWith("```")) {
                    codeLines += lines[index]
                    index++
                }
                if (index < lines.size) index++
                blocks += MarkdownBlock.CodeBlock(language, codeLines.joinToString("\n"))
                continue
            }

            val heading = parseHeading(line)
            if (heading != null) {
                blocks += heading
                index++
                continue
            }

            if (isBullet(line)) {
                val items = mutableListOf<List<MarkdownInline>>()
                while (index < lines.size && isBullet(lines[index])) {
                    items += parseInlines(lines[index].trimStart().drop(2).trim())
                    index++
                }
                blocks += MarkdownBlock.BulletList(items)
                continue
            }

            if (line.trimStart().startsWith(">")) {
                val quoteLines = mutableListOf<String>()
                while (index < lines.size && lines[index].trimStart().startsWith(">")) {
                    quoteLines += lines[index].trimStart().removePrefix(">").trim()
                    index++
                }
                blocks += MarkdownBlock.Quote(parseInlines(quoteLines.joinToString("\n")))
                continue
            }

            val paragraph = mutableListOf<String>()
            while (index < lines.size && lines[index].isNotBlank() && !startsBlock(lines[index])) {
                paragraph += lines[index].trim()
                index++
            }
            blocks += MarkdownBlock.Paragraph(parseInlines(paragraph.joinToString("\n")))
        }

        return MarkdownDocument(blocks)
    }

    private fun parseHeading(line: String): MarkdownBlock.Heading? {
        val trimmed = line.trimStart()
        val hashes = trimmed.takeWhile { it == '#' }.length
        if (hashes !in 1..3 || trimmed.getOrNull(hashes) != ' ') return null
        return MarkdownBlock.Heading(hashes, parseInlines(trimmed.drop(hashes + 1).trim()))
    }

    private fun startsBlock(line: String): Boolean {
        val trimmed = line.trimStart()
        return trimmed.startsWith("```") || parseHeading(line) != null || isBullet(line) || trimmed.startsWith(">")
    }

    private fun isBullet(line: String): Boolean {
        val trimmed = line.trimStart()
        return trimmed.startsWith("- ") || trimmed.startsWith("* ")
    }

    private fun parseInlines(text: String): List<MarkdownInline> {
        if (hasUnclosedMarkdownMarker(text)) return listOf(MarkdownInline.Text(text))

        val result = mutableListOf<MarkdownInline>()
        var index = 0
        val buffer = StringBuilder()

        fun flushText() {
            if (buffer.isNotEmpty()) {
                result += MarkdownInline.Text(buffer.toString())
                buffer.clear()
            }
        }

        while (index < text.length) {
            when {
                text.startsWith("**", index) -> {
                    val end = text.indexOf("**", startIndex = index + 2)
                    flushText()
                    result += MarkdownInline.Strong(text.substring(index + 2, end))
                    index = end + 2
                }
                text[index] == '*' -> {
                    val end = text.indexOf('*', startIndex = index + 1)
                    flushText()
                    result += MarkdownInline.Emphasis(text.substring(index + 1, end))
                    index = end + 1
                }
                text[index] == '`' -> {
                    val end = text.indexOf('`', startIndex = index + 1)
                    flushText()
                    result += MarkdownInline.Code(text.substring(index + 1, end))
                    index = end + 1
                }
                text[index] == '[' -> {
                    val closeText = text.indexOf("](", startIndex = index + 1)
                    val closeUrl = if (closeText >= 0) text.indexOf(')', startIndex = closeText + 2) else -1
                    if (closeText >= 0 && closeUrl >= 0) {
                        flushText()
                        result += MarkdownInline.Link(
                            text = text.substring(index + 1, closeText),
                            url = text.substring(closeText + 2, closeUrl)
                        )
                        index = closeUrl + 1
                    } else {
                        buffer.append(text[index])
                        index++
                    }
                }
                else -> {
                    buffer.append(text[index])
                    index++
                }
            }
        }
        flushText()
        return result
    }

    private fun hasUnclosedMarkdownMarker(text: String): Boolean {
        var index = 0
        while (index < text.length) {
            when {
                text.startsWith("**", index) -> {
                    val end = text.indexOf("**", startIndex = index + 2)
                    if (end < 0) return true
                    index = end + 2
                }
                text[index] == '*' -> {
                    val end = text.indexOf('*', startIndex = index + 1)
                    if (end < 0) return true
                    index = end + 1
                }
                text[index] == '`' -> {
                    val end = text.indexOf('`', startIndex = index + 1)
                    if (end < 0) return true
                    index = end + 1
                }
                else -> index++
            }
        }
        return false
    }
}
