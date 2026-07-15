package com.neeraj.fin.util

import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Currency
import java.util.Locale

object Format {

    private fun localeFor(currencyCode: String): Locale = when (currencyCode) {
        "INR" -> Locale("en", "IN")
        "USD" -> Locale.US
        "EUR" -> Locale.GERMANY
        "GBP" -> Locale.UK
        else -> Locale.getDefault()
    }

    fun money(amountMinor: Long, currencyCode: String, withDecimals: Boolean = true): String {
        val nf = NumberFormat.getCurrencyInstance(localeFor(currencyCode))
        runCatching { nf.currency = Currency.getInstance(currencyCode) }
        nf.maximumFractionDigits = if (withDecimals) 2 else 0
        nf.minimumFractionDigits = 0
        return nf.format(amountMinor / 100.0)
    }

    fun signedMoney(amountMinor: Long, isExpense: Boolean, currencyCode: String): String =
        (if (isExpense) "−" else "+") + money(amountMinor, currencyCode)

    fun compact(amountMinor: Long, currencyCode: String): String {
        val v = amountMinor / 100.0
        val symbol = runCatching { Currency.getInstance(currencyCode).getSymbol(localeFor(currencyCode)) }
            .getOrDefault(currencyCode)
        return when {
            currencyCode == "INR" && v >= 1_00_00_000 -> "$symbol%.1fCr".format(v / 1_00_00_000)
            currencyCode == "INR" && v >= 1_00_000 -> "$symbol%.1fL".format(v / 1_00_000)
            v >= 1_000_000 -> "$symbol%.1fM".format(v / 1_000_000)
            v >= 1_000 -> "$symbol%.1fK".format(v / 1_000)
            else -> "$symbol%.0f".format(v)
        }
    }

    fun toLocalDate(timestamp: Long): LocalDate =
        Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()).toLocalDate()

    private val dayFmt = DateTimeFormatter.ofPattern("EEE, d MMM yyyy")
    private val timeFmt = DateTimeFormatter.ofPattern("d MMM yyyy, h:mm a")

    fun dayLabel(date: LocalDate): String {
        val today = LocalDate.now()
        return when (date) {
            today -> "Today"
            today.minusDays(1) -> "Yesterday"
            else -> date.format(dayFmt)
        }
    }

    fun dateTime(timestamp: Long): String =
        Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()).format(timeFmt)

    /**
     * Parse a user-entered amount like "1,234.50" into minor units.
     * Also accepts arithmetic: "250+50", "3*450", "(1200-200)/2", "1.5x1000".
     */
    fun parseAmount(text: String): Long? {
        val clean = text.replace(",", "").replace("₹", "").replace(" ", "")
        if (clean.isEmpty()) return null
        return runCatching {
            ExprParser(clean).parse()
                ?.setScale(2, java.math.RoundingMode.HALF_UP)
                ?.movePointRight(2)?.longValueExact()
        }.getOrNull()?.takeIf { it > 0 }
    }

    /** True when [text] is a formula (has an operator), not a plain number. */
    fun isExpression(text: String): Boolean =
        text.drop(1).any { it in "+-*/×÷x" } || text.contains('(')

    // Minimal recursive-descent evaluator: + - * / × ÷ x, parentheses, unary minus.
    private class ExprParser(private val s: String) {
        private var i = 0

        fun parse(): java.math.BigDecimal? {
            val v = expr() ?: return null
            return if (i == s.length) v else null
        }

        private fun expr(): java.math.BigDecimal? {
            var v = term() ?: return null
            while (i < s.length) {
                when (s[i]) {
                    '+' -> { i++; v = v.add(term() ?: return null) }
                    '-' -> { i++; v = v.subtract(term() ?: return null) }
                    else -> return v
                }
            }
            return v
        }

        private fun term(): java.math.BigDecimal? {
            var v = factor() ?: return null
            while (i < s.length) {
                when (s[i]) {
                    '*', '×', 'x', 'X' -> { i++; v = v.multiply(factor() ?: return null) }
                    '/', '÷' -> {
                        i++
                        val d = factor() ?: return null
                        if (d.signum() == 0) return null
                        v = v.divide(d, 6, java.math.RoundingMode.HALF_UP)
                    }
                    else -> return v
                }
            }
            return v
        }

        private fun factor(): java.math.BigDecimal? {
            if (i >= s.length) return null
            if (s[i] == '(') {
                i++
                val v = expr() ?: return null
                if (i >= s.length || s[i] != ')') return null
                i++
                return v
            }
            if (s[i] == '-') { i++; return factor()?.negate() }
            val start = i
            while (i < s.length && (s[i].isDigit() || s[i] == '.')) i++
            if (i == start) return null
            return s.substring(start, i).toBigDecimalOrNull()
        }
    }
}
