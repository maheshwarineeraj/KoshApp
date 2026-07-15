package com.neeraj.fin.util

import com.neeraj.fin.data.db.TxnType
import java.time.LocalDate
import java.time.ZoneId

/**
 * Deterministic natural-language transaction search — fully on-device, no ML.
 * Understands queries like:
 *   "did I pay advance tax in Q2 last year"
 *   "advance tax of 20K"
 *   "swiggy last month"
 *   "received above 50000 in 2025"
 */
object QueryParser {

    data class Parsed(
        val terms: List<String>,      // free-text words to match merchant/note/category
        val amountMinor: Long? = null,
        val amountIsMin: Boolean = false,   // "above/over 20K"
        val amountIsMax: Boolean = false,   // "below/under 20K"
        val fromMillis: Long? = null,
        val toMillis: Long? = null,   // exclusive
        val type: String? = null,
        /** Human-readable echo of what was understood, e.g. "Apr–Jun 2025 · ₹20,000 · expense". */
        val understood: List<String> = emptyList()
    ) {
        val isStructured: Boolean
            get() = amountMinor != null || fromMillis != null || type != null
    }

    /** Light stemmer so "payments" matches "payment", "bills" matches "bill". */
    fun stem(word: String): String = when {
        word.length > 4 && word.endsWith("es") -> word.dropLast(2)
        word.length > 3 && word.endsWith("s") -> word.dropLast(1)
        else -> word
    }

    private val expenseWords = setOf("paid", "pay", "payed", "spent", "spend", "debited", "expense", "expenses", "bought", "purchase", "purchased")
    private val incomeWords = setOf("received", "credited", "income", "earned", "got", "salary", "refund", "refunded")
    private val transferWords = setOf("transfer", "transferred", "moved")
    private val minWords = setOf("above", "over", "more", "greater", ">", ">=", "atleast")
    private val maxWords = setOf("below", "under", "less", "lesser", "<", "<=", "upto")
    private val stopWords = setOf(
        "did", "i", "a", "an", "the", "in", "on", "of", "for", "to", "at", "was", "is",
        "have", "has", "had", "any", "my", "me", "we", "do", "does", "with", "from",
        "rs", "rs.", "inr", "amount", "and", "or", "that", "than", "worth", "around", "about"
    )
    private val months = listOf(
        "january", "february", "march", "april", "may", "june",
        "july", "august", "september", "october", "november", "december"
    )

    // "20k", "1.5l", "2lakh", "1cr", "20,000", "2000.50"
    private val amountRegex = Regex("""^₹?([0-9][0-9,]*(?:\.[0-9]+)?)(k|l|lac|lakh|lakhs|cr|crore|crores)?$""")

    fun parse(raw: String): Parsed {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now()
        val tokens = raw.lowercase()
            .replace(Regex("""[?!,;]"""), " ")
            .split(Regex("""\s+"""))
            .filter { it.isNotBlank() }

        var type: String? = null
        var amountMinor: Long? = null
        var amountIsMin = false
        var amountIsMax = false
        var from: LocalDate? = null
        var toExclusive: LocalDate? = null
        var periodLabel: String? = null
        val terms = mutableListOf<String>()

        var yearRef: Int? = null      // explicit year mentioned anywhere ("2025")
        var quarter: Int? = null
        var monthIdx: Int? = null
        var lastYear = false
        var thisYear = false
        var pendingMin = false
        var pendingMax = false

        var i = 0
        while (i < tokens.size) {
            val t = tokens[i]
            val next = tokens.getOrNull(i + 1)
            when {
                t in expenseWords -> type = TxnType.EXPENSE
                t in incomeWords -> type = TxnType.INCOME
                t in transferWords -> type = TxnType.TRANSFER
                t in minWords -> pendingMin = true
                t in maxWords -> pendingMax = true

                t == "today" -> { from = today; toExclusive = today.plusDays(1); periodLabel = "today" }
                t == "yesterday" -> { from = today.minusDays(1); toExclusive = today; periodLabel = "yesterday" }
                t == "this" && next == "week" -> { from = today.minusDays(today.dayOfWeek.value - 1L); toExclusive = today.plusDays(1); periodLabel = "this week"; i++ }
                t == "this" && next == "month" -> { from = today.withDayOfMonth(1); toExclusive = today.plusDays(1); periodLabel = "this month"; i++ }
                t == "this" && next == "year" -> { thisYear = true; i++ }
                t == "last" && next == "week" -> {
                    val start = today.minusDays(today.dayOfWeek.value - 1L).minusWeeks(1)
                    from = start; toExclusive = start.plusWeeks(1); periodLabel = "last week"; i++
                }
                t == "last" && next == "month" -> {
                    val start = today.withDayOfMonth(1).minusMonths(1)
                    from = start; toExclusive = start.plusMonths(1); periodLabel = "last month"; i++
                }
                t == "last" && next == "year" -> { lastYear = true; i++ }

                t.matches(Regex("""q[1-4]""")) -> quarter = t[1].digitToInt()
                t.matches(Regex("""(19|20)\d\d""")) -> yearRef = t.toInt()

                months.any { it.startsWith(t) && t.length >= 3 } ->
                    monthIdx = months.indexOfFirst { it.startsWith(t) } + 1

                else -> {
                    val m = amountRegex.find(t)
                    val looksLikeAmount = m != null && (
                        t.startsWith("₹") || m.groupValues[2].isNotEmpty() ||           // ₹500 / 20k / 2lakh
                            m.groupValues[1].replace(",", "").length >= 3 ||             // 500, 2000, 20,000
                            tokens.getOrNull(i - 1) in setOf("of", "rs", "rs.", "inr", "worth", "around", "about") ||
                            pendingMin || pendingMax
                        )
                    if (looksLikeAmount && amountMinor == null) {
                        val base = m!!.groupValues[1].replace(",", "").toDouble()
                        val mult = when (m.groupValues[2]) {
                            "k" -> 1_000.0
                            "l", "lac", "lakh", "lakhs" -> 1_00_000.0
                            "cr", "crore", "crores" -> 1_00_00_000.0
                            else -> 1.0
                        }
                        amountMinor = (base * mult * 100).toLong()
                        amountIsMin = pendingMin
                        amountIsMax = pendingMax
                        pendingMin = false; pendingMax = false
                    } else if (t !in stopWords) {
                        terms += t
                    }
                }
            }
            i++
        }

        // Resolve quarter / month / year combinations.
        val year = yearRef ?: when {
            lastYear -> today.year - 1
            thisYear -> today.year
            else -> null
        }
        if (quarter != null) {
            val y = year ?: today.year
            val startMonth = (quarter - 1) * 3 + 1
            from = LocalDate.of(y, startMonth, 1)
            toExclusive = from!!.plusMonths(3)
            periodLabel = "Q$quarter $y"
        } else if (monthIdx != null) {
            var y = year ?: today.year
            var start = LocalDate.of(y, monthIdx, 1)
            // A bare month name that hasn't happened yet this year means last year's.
            if (year == null && start.isAfter(today)) { y -= 1; start = LocalDate.of(y, monthIdx, 1) }
            from = start; toExclusive = start.plusMonths(1)
            periodLabel = "${months[monthIdx - 1].replaceFirstChar { it.uppercase() }} $y"
        } else if (from == null && year != null) {
            from = LocalDate.of(year, 1, 1); toExclusive = from!!.plusYears(1)
            periodLabel = "$year"
        }

        val understood = mutableListOf<String>()
        periodLabel?.let { understood += it }
        amountMinor?.let {
            val prefix = if (amountIsMin) "≥ " else if (amountIsMax) "≤ " else ""
            understood += prefix + "₹" + "%,.0f".format(it / 100.0)
        }
        type?.let { understood += it.lowercase() }
        if (terms.isNotEmpty()) understood += "\"${terms.joinToString(" ")}\""

        return Parsed(
            terms = terms,
            amountMinor = amountMinor,
            amountIsMin = amountIsMin,
            amountIsMax = amountIsMax,
            fromMillis = from?.atStartOfDay(zone)?.toInstant()?.toEpochMilli(),
            toMillis = toExclusive?.atStartOfDay(zone)?.toInstant()?.toEpochMilli(),
            type = type,
            understood = understood
        )
    }
}
