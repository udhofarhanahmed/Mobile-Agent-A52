package com.farhan.mobileagent.agent.tools

import java.math.BigDecimal
import java.math.RoundingMode

data class CalculationResult(
    val value: Double,
    val formatted: String
)

object LocalCalculator {
    fun calculate(expression: String): CalculationResult {
        val source = expression.trim()
        require(source.isNotEmpty()) { "Expression is empty." }
        require(source.length <= 200) { "Expression is too long." }

        val parser = Parser(source)
        val value = parser.parseExpression()
        parser.skipWhitespace()
        require(parser.isAtEnd()) { "Unexpected input at position " + parser.position() + "." }
        require(value.isFinite()) { "The result is not finite." }

        return CalculationResult(
            value = value,
            formatted = format(value)
        )
    }

    fun percentOf(percent: String, amount: String): CalculationResult {
        val p = calculate(percent).value
        val a = calculate(amount).value
        val value = p / 100.0 * a
        require(value.isFinite()) { "The result is not finite." }
        return CalculationResult(value, format(value))
    }

    private fun format(value: Double): String =
        BigDecimal.valueOf(value)
            .setScale(12, RoundingMode.HALF_UP)
            .stripTrailingZeros()
            .toPlainString()

    private class Parser(private val source: String) {
        private var index = 0

        fun position(): Int = index

        fun isAtEnd(): Boolean = index >= source.length

        fun skipWhitespace() {
            while (index < source.length && source[index].isWhitespace()) {
                index++
            }
        }

        fun parseExpression(): Double {
            var value = parseTerm()
            while (true) {
                skipWhitespace()
                when {
                    match('+') -> value += parseTerm()
                    match('-') -> value -= parseTerm()
                    else -> return value
                }
            }
        }

        private fun parseTerm(): Double {
            var value = parseFactor()
            while (true) {
                skipWhitespace()
                when {
                    match('*') -> value *= parseFactor()
                    match('/') -> {
                        val divisor = parseFactor()
                        require(divisor != 0.0) { "Division by zero is not allowed." }
                        value /= divisor
                    }
                    else -> return value
                }
            }
        }

        private fun parseFactor(): Double {
            skipWhitespace()

            if (match('+')) return parseFactor()
            if (match('-')) return -parseFactor()

            if (match('(')) {
                val value = parseExpression()
                skipWhitespace()
                require(match(')')) { "Missing closing parenthesis." }
                return value
            }

            return parseNumber()
        }

        private fun parseNumber(): Double {
            skipWhitespace()
            val start = index
            var seenDigit = false
            var seenDot = false

            while (index < source.length) {
                val c = source[index]
                when {
                    c.isDigit() -> {
                        seenDigit = true
                        index++
                    }
                    c == '.' && !seenDot -> {
                        seenDot = true
                        index++
                    }
                    else -> break
                }
            }

            require(seenDigit) { "Expected a number at position $start." }
            return source.substring(start, index).toDoubleOrNull()
                ?: throw IllegalArgumentException("Invalid number.")
        }

        private fun match(expected: Char): Boolean {
            skipWhitespace()
            if (index < source.length && source[index] == expected) {
                index++
                return true
            }
            return false
        }
    }
}
