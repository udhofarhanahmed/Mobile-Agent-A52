package com.farhan.mobileagent.agent.tools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class LocalCalculatorTest {
    @Test fun respectsOperatorPrecedence() {
        assertEquals("32", LocalCalculator.calculate("8 + 4 * 6").formatted)
    }

    @Test fun supportsParenthesesAndDecimals() {
        assertEquals("2.75", LocalCalculator.calculate("(5.5 - 1.1) / 1.6").formatted)
    }

    @Test fun supportsPercentOf() {
        assertEquals("45", LocalCalculator.percentOf("10", "450").formatted)
    }

    @Test fun rejectsDivisionByZero() {
        assertThrows(IllegalArgumentException::class.java) {
            LocalCalculator.calculate("10 / (5 - 5)")
        }
    }
}
