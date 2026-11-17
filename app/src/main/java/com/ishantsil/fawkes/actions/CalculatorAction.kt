package com.ishantsil.fawkes.actions

/**
 * Spoken arithmetic. Vosk may return numbers as digits ("45") or words
 * ("forty five"), so both are parsed. Two operands, one operator.
 */
object CalculatorAction {

    private val ones = mapOf(
        "zero" to 0, "one" to 1, "two" to 2, "three" to 3, "four" to 4,
        "five" to 5, "six" to 6, "seven" to 7, "eight" to 8, "nine" to 9,
        "ten" to 10, "eleven" to 11, "twelve" to 12, "thirteen" to 13,
        "fourteen" to 14, "fifteen" to 15, "sixteen" to 16, "seventeen" to 17,
        "eighteen" to 18, "nineteen" to 19,
    )
    private val tens = mapOf(
        "twenty" to 20, "thirty" to 30, "forty" to 40, "fifty" to 50,
        "sixty" to 60, "seventy" to 70, "eighty" to 80, "ninety" to 90,
    )

    /** Returns the spoken answer ("45 times 12 is 540.") or null. */
    fun evaluate(phrase: String): String? {
        val cleaned = phrase.lowercase()
            .removePrefix("what is ").removePrefix("calculate ").removePrefix("compute ")
            .trim()
        val expr = cleaned
            .replace("multiplied by", "*").replace("divided by", "/")
            .replace("plus", "+").replace("minus", "-").replace("times", "*")
            .replace(" x ", " * ")
        val tokens = expr.split(" ").filter { it.isNotBlank() }
        val opIndex = tokens.indexOfFirst { it in setOf("+", "-", "*", "/") }
        if (opIndex <= 0 || opIndex >= tokens.size - 1) return null
        val left = parseNumber(tokens.subList(0, opIndex)) ?: return null
        val right = parseNumber(tokens.subList(opIndex + 1, tokens.size)) ?: return null
        val op = tokens[opIndex]
        val result: Double = when (op) {
            "+" -> (left + right).toDouble()
            "-" -> (left - right).toDouble()
            "*" -> (left * right).toDouble()
            "/" -> {
                if (right == 0) return null
                left.toDouble() / right
            }
            else -> return null
        }
        return "$cleaned is ${formatNumber(result)}."
    }

    private fun parseNumber(tokens: List<String>): Int? {
        if (tokens.isEmpty()) return null
        if (tokens.size == 1) {
            tokens[0].toIntOrNull()?.let { return it }
        }
        var result = 0
        var current = 0
        var consumed = false
        for (token in tokens) {
            when {
                ones.containsKey(token) -> {
                    current += ones.getValue(token)
                    consumed = true
                }
                tens.containsKey(token) -> {
                    current += tens.getValue(token)
                    consumed = true
                }
                token == "hundred" -> {
                    current = (if (current == 0) 1 else current) * 100
                    consumed = true
                }
                token == "thousand" -> {
                    result += (if (current == 0) 1 else current) * 1000
                    current = 0
                    consumed = true
                }
                token.toIntOrNull() != null -> {
                    current += token.toInt()
                    consumed = true
                }
                else -> return null
            }
        }
        return if (consumed) result + current else null
    }

    private fun formatNumber(value: Double): String {
        return if (value == value.toLong().toDouble()) {
            value.toLong().toString()
        } else {
            "%.2f".format(value).trimEnd('0').trimEnd('.')
        }
    }
}
