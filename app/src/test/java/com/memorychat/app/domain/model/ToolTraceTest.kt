package com.memorychat.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolTraceTest {
    @Test
    fun completedSearchSummaryUsesRealMetadataWhenAvailable() {
        val trace = ToolTrace.search(
            status = ToolTraceStatus.COMPLETED,
            usage = WebSearchUsage(keywordCount = 3, pageCount = 18),
            citations = listOf(
                SearchCitation(title = "深圳天气预报", url = "https://example.com/weather", siteName = "天气网"),
                SearchCitation(title = "深圳周末降雨", url = "https://example.com/rain", siteName = "新闻")
            )
        )

        assertEquals("搜索 3 个关键词，参考 18 篇资料", trace.summary)
        assertEquals("深圳天气预报", trace.citations.first().title)
    }

    @Test
    fun completedSearchSummaryFallsBackWithoutMetadata() {
        val trace = ToolTrace.search(status = ToolTraceStatus.COMPLETED)

        assertEquals("已联网搜索", trace.summary)
        assertTrue(trace.detailLines().contains("搜索已执行，但供应商未返回可展示的来源统计。"))
    }

    @Test
    fun runningSearchSummaryUsesActionText() {
        val trace = ToolTrace.search(status = ToolTraceStatus.RUNNING)

        assertEquals("正在搜索最新信息", trace.summary)
    }

    @Test
    fun memoryRecallSummaryIncludesCount() {
        val trace = ToolTrace.memoryRecall(count = 4)

        assertEquals("已读取 4 条相关记忆", trace.summary)
    }
}
