package com.memorychat.app.domain.model

enum class ToolTraceKind {
    THINKING,
    WEB_SEARCH,
    MEMORY_RECALL,
    MEMORY_WRITE,
    PERSONA_UPDATE,
    DOC_SEARCH
}

enum class ToolTraceStatus {
    RUNNING,
    COMPLETED,
    FAILED
}

data class SearchCitation(
    val title: String,
    val url: String? = null,
    val siteName: String? = null,
    val summary: String? = null,
    val publishTime: String? = null
)

data class WebSearchUsage(
    val keywordCount: Int? = null,
    val pageCount: Int? = null
)

data class ToolTrace(
    val kind: ToolTraceKind,
    val status: ToolTraceStatus,
    val summary: String,
    val query: String? = null,
    val usage: WebSearchUsage? = null,
    val citations: List<SearchCitation> = emptyList(),
    val count: Int? = null
) {
    fun complete(
        usage: WebSearchUsage? = this.usage,
        citations: List<SearchCitation> = this.citations,
        count: Int? = this.count
    ): ToolTrace {
        return when (kind) {
            ToolTraceKind.WEB_SEARCH -> search(ToolTraceStatus.COMPLETED, query, usage, citations)
            ToolTraceKind.MEMORY_RECALL -> memoryRecall(count ?: this.count ?: 0)
            ToolTraceKind.MEMORY_WRITE -> memoryWrite(count ?: this.count ?: 0)
            ToolTraceKind.PERSONA_UPDATE -> personaUpdate(ToolTraceStatus.COMPLETED)
            ToolTraceKind.DOC_SEARCH -> docSearch(ToolTraceStatus.COMPLETED, query, count ?: this.count)
            ToolTraceKind.THINKING -> thinking(ToolTraceStatus.COMPLETED)
        }
    }

    fun detailLines(maxSources: Int = 5): List<String> {
        return when (kind) {
            ToolTraceKind.WEB_SEARCH -> {
                if (citations.isNotEmpty()) {
                    citations.take(maxSources).map { citation ->
                        listOfNotNull(citation.title, citation.siteName?.let { "来源：$it" }).joinToString(" · ")
                    }
                } else {
                    listOf("搜索已执行，但供应商未返回可展示的来源统计。")
                }
            }
            else -> emptyList()
        }
    }

    companion object {
        fun thinking(status: ToolTraceStatus = ToolTraceStatus.RUNNING): ToolTrace {
            val summary = if (status == ToolTraceStatus.RUNNING) "正在判断是否需要工具" else "已完成工具判断"
            return ToolTrace(ToolTraceKind.THINKING, status, summary)
        }

        fun search(
            status: ToolTraceStatus,
            query: String? = null,
            usage: WebSearchUsage? = null,
            citations: List<SearchCitation> = emptyList()
        ): ToolTrace {
            val summary = when (status) {
                ToolTraceStatus.RUNNING -> "正在搜索最新信息"
                ToolTraceStatus.FAILED -> "搜索未返回可用资料"
                ToolTraceStatus.COMPLETED -> {
                    val keywords = usage?.keywordCount
                    val pages = usage?.pageCount
                    when {
                        keywords != null && pages != null -> "搜索 $keywords 个关键词，参考 $pages 篇资料"
                        pages != null -> "参考 $pages 篇资料"
                        citations.isNotEmpty() -> "参考 ${citations.size} 篇资料"
                        else -> "已联网搜索"
                    }
                }
            }
            return ToolTrace(
                kind = ToolTraceKind.WEB_SEARCH,
                status = status,
                summary = summary,
                query = query,
                usage = usage,
                citations = citations
            )
        }

        fun memoryRecall(count: Int): ToolTrace {
            return ToolTrace(
                kind = ToolTraceKind.MEMORY_RECALL,
                status = ToolTraceStatus.COMPLETED,
                summary = "已读取 $count 条相关记忆",
                count = count
            )
        }

        fun memoryWrite(count: Int): ToolTrace {
            return ToolTrace(
                kind = ToolTraceKind.MEMORY_WRITE,
                status = ToolTraceStatus.COMPLETED,
                summary = "已更新 $count 条记忆",
                count = count
            )
        }

        fun personaUpdate(status: ToolTraceStatus = ToolTraceStatus.RUNNING): ToolTrace {
            val summary = if (status == ToolTraceStatus.RUNNING) "正在更新角色设定" else "已更新角色设定"
            return ToolTrace(ToolTraceKind.PERSONA_UPDATE, status, summary)
        }

        fun docSearch(status: ToolTraceStatus, query: String? = null, count: Int? = null): ToolTrace {
            val summary = when {
                status == ToolTraceStatus.RUNNING -> "正在查阅资料"
                count != null -> "查阅 $count 份文档"
                else -> "已查阅资料"
            }
            return ToolTrace(ToolTraceKind.DOC_SEARCH, status, summary, query = query, count = count)
        }
    }
}
