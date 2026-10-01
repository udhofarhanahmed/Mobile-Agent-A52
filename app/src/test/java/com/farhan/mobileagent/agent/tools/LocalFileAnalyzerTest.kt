package com.farhan.mobileagent.agent.tools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalFileAnalyzerTest {
    @Test fun parsesQuotedCsvAndNumericColumns() {
        val csv = "Name,Revenue,Units\nAlpha,100.5,2\nBeta,199.5,4\n"
        val rows = LocalFileAnalyzer.parseCsv(csv)
        assertEquals("Alpha", rows[1][0])
        assertEquals("199.5", rows[2][1])

        val report = LocalFileAnalyzer.analyze("sales.csv", csv)
        assertEquals(2, report.numericColumns.size)
        assertEquals(300.0, report.numericColumns.first().sum, 0.000001)
    }

    @Test fun supportsTextFiles() {
        val report = LocalFileAnalyzer.analyze("notes.txt", "hello world\nsecond line")
        assertTrue(report.details.any { it.startsWith("Lines: 2") })
        assertTrue(report.details.any { it.startsWith("Words: 4") })
    }
}
