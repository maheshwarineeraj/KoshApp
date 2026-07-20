package com.neeraj.fin.data.sms

import com.neeraj.fin.data.db.TxnType
import java.math.BigDecimal

data class ParsedSms(
    val amountMinor: Long,
    val type: String,        // TxnType.EXPENSE, TxnType.INCOME or TxnType.TRANSFER
    val merchant: String,
    val accountTail: String?,
    // Set when the SMS reports a spend in a foreign currency (e.g. "USD 23.60 spent");
    // amountMinor then holds the foreign value for the user to correct to INR on review.
    val foreignCurrency: String? = null,
    // Purpose text pulled from the message ("For: Gas Cylinder Booking") when present.
    val note: String = ""
)

/**
 * Heuristic parser for Indian bank / card / UPI transaction SMS.
 * Content-based: works on scanned inbox messages and live broadcasts.
 */
object SmsParser {

    private val amountRegex = Regex(
        """(?:rs\.?|inr|₹)\s*([0-9][0-9,]*(?:\.[0-9]{1,2})?)|([0-9][0-9,]*(?:\.[0-9]{1,2})?)\s*(?:rs\.?|inr)""",
        RegexOption.IGNORE_CASE
    )

    private val accountRegex = Regex(
        """(?:a/c|acct|account|card|a/c no)\s*(?:no\.?\s*)?(?:ending\s*(?:in\s*)?)?[x\*]*([0-9]{3,6})""",
        RegexOption.IGNORE_CASE
    )

    private val debitKeywords = listOf(
        "debited", "spent", "paid", "sent", "withdrawn", "deducted", "purchase", "charged", "payment of",
        "used at", "used for", "used on"
    )
    private val creditKeywords = listOf(
        "credited", "received", "deposited", "refunded", "refund of", "cashback of", "salary of"
    )

    // Messages containing these are not completed transactions — skip.
    private val rejectPhrases = listOf(
        "otp", "one time password", "one-time password", "verification code", "will be debited",
        "will be deducted", "will be charged", "is due", "due on", "has requested", "requested money",
        "payment request", "autopay", "e-mandate", "mandate", "insufficient", "failed", "declined", "reversed",
        "offer", "% off", "flat off", "discount", "win ", "congratulations", "pre-approved",
        "loan", "apply now", "download", "upgrade", "expire", "last day", "hurry", "sale ends",
        "recharge now", "available balance is", "statement", "min due",
        // Money merely earmarked, not moved: IPO/ASBA applications, lien marks.
        "asba", "ipo", "is blocked in", "blocked in your", "amount blocked", "application money",
        "lien marked",
        // The card-side confirmation of a bill payment; the bank-side debit SMS is
        // already captured as a transfer, so this mirror message would double count.
        "received towards your credit card", "payment received towards", "received on your credit card",
        "credited to your card", "credited to your credit card"
    )

    // A credit-card bill payment moves money between the user's own accounts —
    // the underlying spends were (or will be) captured individually — so suggest
    // it as a transfer rather than a fresh expense.
    private val ccBillPhrases = listOf(
        "credit card payment", "towards your credit card", "towards credit card",
        "credit card bill", "card bill payment", "payment for your credit card"
    )

    // An amount immediately preceded by one of these is a limit/balance/due figure,
    // never the transaction amount (e.g. "Avl Limit: INR 3,62,771.71").
    private val amountContextExclusions = listOf(
        "limit", "lmt", "bal", "balance", "avbl", "avl", "due", "outstanding", "o/s", "total"
    )

    private val foreignAmountRegex = Regex(
        """\b(usd|eur|gbp|aed|sgd|aud|cad|chf|jpy|sar|qar|nzd|hkd|thb|myr|lkr|bdt)\s*([0-9][0-9,]*(?:\.[0-9]{1,2})?)""",
        RegexOption.IGNORE_CASE
    )

    // Senders that are personal phone numbers are ignored; banks use alphanumeric headers.
    private val phoneSenderRegex = Regex("""^\+?[0-9]{7,15}$""")

    fun looksLikePersonalSender(sender: String): Boolean =
        phoneSenderRegex.matches(sender.replace(" ", "").replace("-", ""))

    fun parse(sender: String, body: String): ParsedSms? {
        if (looksLikePersonalSender(sender)) return null
        val text = body.trim()
        if (text.length < 20) return null
        val lower = text.lowercase()

        if (rejectPhrases.any { lower.contains(it) }) return null

        // Pick the transaction amount, skipping limit/balance/due figures.
        var amountMinor: Long? = null
        for (m in amountRegex.findAll(text)) {
            if (isExcludedAmountContext(lower, m.range.first)) continue
            val str = (m.groupValues[1].ifEmpty { m.groupValues[2] }).replace(",", "")
            val minor = try {
                BigDecimal(str).movePointRight(2).longValueExact()
            } catch (e: Exception) {
                continue
            }
            if (minor > 0) {
                amountMinor = minor
                break
            }
        }

        // No usable INR amount — a foreign-currency spend ("USD 23.60 spent using
        // ICICI Bank Card ... Avl Limit: INR 3,62,771.71") still describes a real
        // transaction; surface the foreign value so the user can correct it to INR.
        var foreignCurrency: String? = null
        if (amountMinor == null) {
            val fm = foreignAmountRegex.findAll(text)
                .firstOrNull { !isExcludedAmountContext(lower, it.range.first) } ?: return null
            val minor = try {
                BigDecimal(fm.groupValues[2].replace(",", "")).movePointRight(2).longValueExact()
            } catch (e: Exception) {
                return null
            }
            if (minor <= 0) return null
            amountMinor = minor
            foreignCurrency = fm.groupValues[1].uppercase()
        }

        val debitIdx = debitKeywords.mapNotNull { k -> lower.indexOf(k).takeIf { it >= 0 } }.minOrNull()
        val creditIdx = creditKeywords.mapNotNull { k -> lower.indexOf(k).takeIf { it >= 0 } }.minOrNull()

        var type = when {
            debitIdx == null && creditIdx == null -> return null
            debitIdx == null -> TxnType.INCOME
            creditIdx == null -> TxnType.EXPENSE
            // Both present (e.g. "debited from your a/c and credited to merchant"):
            // whichever keyword appears first wins.
            debitIdx <= creditIdx -> TxnType.EXPENSE
            else -> TxnType.INCOME
        }

        // Paying a credit card bill from a bank account is money moved between the
        // user's own accounts, not new spending.
        if (type == TxnType.EXPENSE && ccBillPhrases.any { lower.contains(it) }) {
            type = TxnType.TRANSFER
        }

        val accountTail = accountRegex.find(text)?.groupValues?.get(1)
        val merchant = if (type == TxnType.TRANSFER) "Credit card bill"
        else extractMerchant(text, type) ?: "Unknown"

        return ParsedSms(amountMinor, type, merchant, accountTail, foreignCurrency, extractNote(text))
    }

    // "For: Gas Cylinder Booking From HDFC..." → note "Gas Cylinder Booking";
    // stops at from/via/on/at/ref boundaries and punctuation.
    private val noteRegex = Regex(
        """\bfor:?\s+(?!upi\b|rs\.?(?:\s|\d)|inr\b|a/c\b|card\b)([A-Za-z][A-Za-z0-9 &._'\-]{2,40}?)(?=\s+(?:from|via|on|at|ref|txn|upi|a/c|to)\b|[.,;:\n]|$)""",
        RegexOption.IGNORE_CASE
    )

    private fun extractNote(text: String): String {
        val raw = noteRegex.find(text)?.groupValues?.get(1)?.trim() ?: return ""
        return raw.split(" ").filter { it.isNotBlank() }
            .joinToString(" ") { w -> w.lowercase().replaceFirstChar { it.uppercase() } }
    }

    /** Public keyword lookup so manual entry can suggest categories from typed text. */
    fun suggestCategoryFromText(text: String, type: String): String? {
        val hay = text.lowercase()
        for ((keys, cat) in categoryKeywords) {
            if (keys.any { hay.contains(it) }) return cat
        }
        return if (type == TxnType.INCOME) "Other Income" else null
    }

    /** True if the amount starting at [start] is preceded by a limit/balance/due label. */
    private fun isExcludedAmountContext(lower: String, start: Int): Boolean {
        val window = lower.substring((start - 20).coerceAtLeast(0), start)
        return amountContextExclusions.any { window.contains(it) }
    }

    private val merchantPatterns = listOf(
        Regex("""(?:\bat|\bto)\s+([A-Za-z][A-Za-z0-9 &._'\-*]{1,40}?)(?:\s+on\b|\s+via\b|\s+ref\b|\s+using\b|\s+upi\b|\s+for\b|[.,;\n]|$)""", RegexOption.IGNORE_CASE),
        Regex("""\bfrom\s+([A-Za-z][A-Za-z0-9 &._'\-*]{1,40}?)(?:\s+on\b|\s+via\b|\s+ref\b|\s+a/c\b|[.,;\n]|$)""", RegexOption.IGNORE_CASE),
        Regex("""\bvpa\s+([\w.\-]+@[\w]+)""", RegexOption.IGNORE_CASE),
        // A bare UPI id after to/at ("for UPI to bookmyshow@axis")
        Regex("""(?:\bto|\bat)\s+([\w.\-]+@[\w]+)""", RegexOption.IGNORE_CASE),
        Regex("""\binfo:?\s*([A-Za-z0-9 &._'\-*/]{2,40})""", RegexOption.IGNORE_CASE),
        // Card-spend format: "spent using ... Card XX1234 on 29-Jun-26 on MERCHANT."
        // A leading letter is required, so dates after "on" never match.
        Regex("""\bon\s+([A-Za-z][A-Za-z0-9 &._'\-*]{1,40}?)(?:\s+on\b|\s+via\b|\s+ref\b|\s+avl\b|[.,;\n]|$)""", RegexOption.IGNORE_CASE)
    )

    private val merchantNoise = setOf(
        "your", "you", "a/c", "account", "bank", "the", "ac", "card", "upi", "neft", "imps"
    )

    private fun extractMerchant(text: String, type: String): String? {
        val ordered = if (type == TxnType.INCOME) {
            // Prefer "from X" for credits, then the remaining patterns in order.
            listOf(merchantPatterns[1], merchantPatterns[0]) + merchantPatterns.drop(2)
        } else merchantPatterns
        for (pattern in ordered) {
            val m = pattern.find(text) ?: continue
            val raw = m.groupValues[1].trim().trimEnd('.', ',', '-')
            if (raw.length < 2) continue
            if (raw.lowercase() in merchantNoise) continue
            // Title-case plain words, keep VPAs as-is
            return if (raw.contains("@")) raw.lowercase()
            else raw.split(" ").filter { it.isNotBlank() }
                .joinToString(" ") { w -> w.lowercase().replaceFirstChar { it.uppercase() } }
        }
        return null
    }

    private val categoryKeywords: List<Pair<List<String>, String>> = listOf(
        listOf("swiggy", "zomato", "dominos", "pizza", "mcdonald", "kfc", "restaurant", "cafe", "eatclub", "faasos", "dinner", "lunch", "breakfast", "chai", "coffee", "tiffin", "biryani") to "Food & Dining",
        listOf("bigbasket", "blinkit", "zepto", "grofers", "dmart", "grocery", "instamart", "jiomart", "kirana", "vegetable", "sabzi", "milk") to "Groceries",
        listOf("uber", "ola", "rapido", "redbus", "metro", "irctc rail", "fuel", "petrol", "diesel", "hpcl", "iocl", "bpcl", "fastag", "cab", "taxi", "auto fare", "bus", "parking") to "Transport",
        listOf("amazon", "flipkart", "myntra", "ajio", "meesho", "nykaa", "croma", "reliance digital", "clothes", "shoes") to "Shopping",
        listOf("electricity", "bescom", "msedcl", "tneb", "airtel", "jio", "vi ", "vodafone", "bsnl", "broadband", "wifi", "dth", "tata sky", "gas bill", "gas cylinder", "cylinder", "lpg", "water bill", "postpaid", "bill payment", "recharge") to "Bills & Utilities",
        listOf("netflix", "spotify", "hotstar", "prime video", "sonyliv", "bookmyshow", "pvr", "inox", "youtube premium", "movie") to "Entertainment",
        listOf("pharmacy", "apollo", "1mg", "pharmeasy", "netmeds", "hospital", "clinic", "diagnostic", "practo", "medicine", "doctor", "dentist", "lab test") to "Health",
        listOf("makemytrip", "goibibo", "cleartrip", "indigo", "air india", "vistara", "oyo", "airbnb", "yatra", "ixigo", "hotel", "flight", "trip") to "Travel",
        listOf("udemy", "coursera", "byjus", "unacademy", "school fee", "tuition", "college", "exam fee", "books") to "Education",
        listOf("rent", "nobroker", "housing.com", "maintenance", "plumber", "electrician", "repair", "furniture") to "Rent & Home",
        listOf("emi", "loan repay", "bajaj fin", "home credit") to "EMI & Loans",
        listOf("zerodha", "groww", "upstox", "mutual fund", "sip", "nps", "ppf", "etmoney", "indmoney", "kuvera") to "Investments",
        listOf("salon", "spa", "urban company", "barber", "hair", "parlour", "grooming", "facial") to "Personal Care",
        listOf("salary", "sal credit", "payroll") to "Salary",
        listOf("refund", "cashback", "reversal") to "Refunds & Cashback",
        listOf("interest", "dividend", "int.pd", "int credited") to "Interest & Dividends"
    )

    /** Suggest a default-category name from merchant + body keywords. */
    fun suggestCategoryName(parsed: ParsedSms): String? {
        val hay = parsed.merchant.lowercase()
        for ((keys, cat) in categoryKeywords) {
            if (keys.any { hay.contains(it) }) return cat
        }
        return if (parsed.type == TxnType.INCOME) "Other Income" else null
    }

    fun smsHash(sender: String, body: String, timestamp: Long): Long {
        var h = 1125899906842597L
        for (c in "$sender|$body|${timestamp / 60000}") h = 31 * h + c.code
        return h
    }
}


data class BillDue(val title: String, val amountMinor: Long, val dueDayHint: String)

object BillDueParser {
    private val duePhrase = Regex(
        """(?:is due|due on|due by|due date|pay by|payable by|last date|will be debited|autopay|e-mandate|mandate|renew(?:al)? (?:by|on|due))""",
        RegexOption.IGNORE_CASE
    )
    private val amountCur = Regex("""(?:rs\.?|inr|₹)\s*([0-9][0-9,]*(?:\.[0-9]{1,2})?)""", RegexOption.IGNORE_CASE)
    // "Total due: 3567.86" - banks often omit the currency marker in bill SMS.
    private val amountDue = Regex(
        """(?:total\s+due|amount\s+due|bill\s+amount|outstanding|due)\s*[:\-]?\s*(?:rs\.?|inr|₹)?\s*([0-9][0-9,]*(?:\.[0-9]{1,2})?)""",
        RegexOption.IGNORE_CASE
    )
    private val biller = Regex(
        """(electricity|power|water|gas|broadband|internet|mobile|postpaid|prepaid|recharge|dth|credit card|card|insurance|premium|emi|loan|rent|maintenance|subscription|membership|fee|fastag|society|school|tax|bill)""",
        RegexOption.IGNORE_CASE
    )

    /**
     * Detects "your bill of Rs X is due on <date>" style messages that the
     * transaction parser rejects. Returns the raw text span holding the due
     * date; actual date resolution happens in BillDueExtractor (ML Kit entity
     * extraction with a regex fallback).
     */
    fun parse(sender: String, body: String): BillDue? {
        if (SmsParser.looksLikePersonalSender(sender)) return null
        if (!duePhrase.containsMatchIn(body)) return null
        val lower = body.lowercase()
        val dueAmt = amountDue.findAll(body).firstOrNull { m ->
            !lower.substring((m.range.first - 10).coerceAtLeast(0), m.range.first).contains("min")
        }?.groupValues?.get(1)
        val amt = (dueAmt ?: amountCur.find(body)?.groupValues?.get(1))
            ?.replace(",", "")?.toDoubleOrNull() ?: return null
        if (amt <= 0) return null
        // Any due-phrase + amount is a payment reminder even without a known
        // biller keyword; fall back to a generic title with the sender.
        val what = biller.find(body)?.groupValues?.get(1)
            ?.lowercase()?.replaceFirstChar { it.uppercase() }
        val title = when {
            what == null -> "Payment due (${sender.takeLast(8)})"
            what.equals("bill", true) -> "Bill payment"
            else -> "$what payment"
        }
        return BillDue(title, (amt * 100).toLong(), body)
    }
}
