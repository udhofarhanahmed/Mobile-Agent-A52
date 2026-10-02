package com.farhan.mobileagent.agent.tools

import java.util.Locale

data class NumericColumnSummary(
    val name: String,
    val count: Int,
    val missing: Int,
    val sum: Double,
    val average: Double,
    val minimum: Double,
    val maximum: Double
)

data class FileAnalysis(
    val title: String,
    val details: List<String>,
    val numericColumns: List<NumericColumnSummary>
)

object LocalFileAnalyzer {
    fun analyze(name: String, content: String): FileAnalysis {
        require(content.length <= 5_000_000) { "File is larger than the local 5 MB safety limit." }
        val lower = name.lowercase(Locale.ROOT)

        return when {
            lower.endsWith(".csv") -> analyzeCsv(name, content)
            lower.endsWith(".tsv") -> analyzeCsv(name, content, delimiter = '\t')
            lower.endsWith(".txt") || lower.endsWith(".log") ->
                FileAnalysis(
                    title = name,
                    details = listOf(
                        "Type: text",
                        "Characters: " + content.length,
                        "Lines: " + if (content.isEmpty()) 0 else content.count { it == '\n' } + 1,
                        "Words: " + content.trim().split(Regex("\\s+")).count { it.isNotBlank() }
                    ),
                    numericColumns = emptyList()
                )
            else ->
                FileAnalysis(
                    title = name,
                    details = listOf(
                        "Type: local text",
                        "Characters: " + content.length,
                        "This first File Lab build analyzes CSV/TSV tables and text files."
                    ),
                    numericColumns = emptyList()
                )
        }
    }

    fun parseCsv(content: String, delimiter: Char = ','): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        val currentRow = mutableListOf<String>()
        val field = StringBuilder()
        var inQuotes = false
        var index = 0

        while (index < content.length) {
            val c = content[index]
            when {
                c == '"' -> {
                    if (inQuotes && index + 1 < content.length && content[index + 1] == '"') {
                        field.append('"')
                        index++
                    } else {
                        inQuotes = !inQuotes
                    }
                }
                c == delimiter && !inQuotes -> {
                    currentRow.add(field.toString())
                    field.setLength(0)
                }
                (c == '\n' || c == '\r') && !inQuotes -> {
                    currentRow.add(field.toString())
                    field.setLength(0)
                    if (c == '\r' && index + 1 < content.length && content[index + 1] == '\n') {
                        index++
                    }
                    if (currentRow.any { it.isNotBlank() }) {
                        rows.add(currentRow.toList())
                    }
                    currentRow.clear()
                }
                else -> field.append(c)
            }
            index++
        }

        if (field.isNotEmpty() || currentRow.isNotEmpty()) {
            currentRow.add(field.toString())
            if (currentRow.any { it.isNotBlank() }) {
                rows.add(currentRow.toList())
            }
        }
        return rows
    }

    private fun analyzeCsv(name: String, content: String, delimiter: Char = ','): FileAnalysis {
        val rows = parseCsv(content, delimiter)
        if (rows.isEmpty()) {
            return FileAnalysis(name, listOf("The table is empty."), emptyList())
        }

        val headers = rows.first().mapIndexed { index, value ->
            value.trim().ifBlank { "Column " + (index + 1) }
        }
        val dataRows = rows.drop(1)
        val width = headers.size
        val numeric = mutableListOf<NumericColumnSummary>()

        for (column in 0 until width) {
            val nameForColumn = headers[column]
            val values = dataRows.map { row -> row.getOrNull(column)?.trim().orEmpty() }
            val present = values.filter { it.isNotBlank() }
            val numbers = present.mapNotNull { value ->
                value.toDoubleOrNull()?.takeIf { it.isFinite() }
            }
            if (numbers.isNotEmpty() && numbers.size * 2 >= present.size) {
                val sum = numbers.sum()
                numeric += NumericColumnSummary(
                    name = nameForColumn,
                    count = numbers.size,
                    missing = values.count { it.isBlank() },
                    sum = sum,
                    average = sum / numbers.size,
                    minimum = numbers.minOrNull() ?: 0.0,
                    maximum = numbers.maxOrNull() ?: 0.0
                )
            }
        }

        val details = mutableListOf(
            "Rows: " + dataRows.size,
            "Columns: " + headers.size,
            "Headers: " + headers.joinToString(", "),
            "Numeric columns detected: " + numeric.size
        )

        return FileAnalysis(name, details, numeric)
    }
}