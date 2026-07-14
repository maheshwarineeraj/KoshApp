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

    /** Parse a user-entered decimal amount like "1,234.50" into minor units. */
    fun parseAmount(text: String): Long? {
        val clean = text.replace(",", "").replace("₹", "").trim()
        if (clean.isEmpty()) return null
        return runCatching {
            java.math.BigDecimal(clean).movePointRight(2).longValueExact()
        }.getOrNull()?.takeIf { it > 0 }
    }
}
