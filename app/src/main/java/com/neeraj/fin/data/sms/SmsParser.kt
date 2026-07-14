package com.neeraj.fin.data.sms

import com.neeraj.fin.data.db.TxnType
import java.math.BigDecimal

data class ParsedSms(
    val amountMinor: Long,
    val type: String,        // TxnType.EXPENSE or TxnType.INCOME
    val merchant: String,
    val accountTail: String?
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
        "debited", "spent", "paid", "sent", "withdrawn", "deducted", "purchase", "charged", "payment of"
    )
    private val creditKeywords = listOf(
        "credited", "received", "deposited", "refunded", "refund of", "cashback of", "salary of"
    )

    // Messages containing these are not completed transactions — skip.
    private val rejectPhrases = listOf(
        "otp", "one time password", "one-time password", "verification code", "will be debited",
        "will be deducted", "will be charged", "is due", "due on", "has requested", "requested money",
        "payment request", "autopay", "e-mandate", "insufficient", "failed", "declined", "reversed",
        "offer", "% off", "flat off", "discount", "win ", "congratulations", "pre-approved",
        "loan", "apply now", "download", "upgrade", "expire", "last day", "hurry", "sale ends",
        "recharge now", "avl bal", "available balance is", "bal:", "statement", "min due"
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

        val amountMatch = amountRegex.find(text) ?: return null
        val amountStr = (amountMatch.groupValues[1].ifEmpty { amountMatch.groupValues[2] })
            .replace(",", "")
        val amountMinor = try {
            BigDecimal(amountStr).movePointRight(2).longValueExact()
        } catch (e: Exception) {
            return null
        }
        if (amountMinor <= 0) return null

        val debitIdx = debitKeywords.mapNotNull { k -> lower.indexOf(k).takeIf { it >= 0 } }.minOrNull()
        val creditIdx = creditKeywords.mapNotNull { k -> lower.indexOf(k).takeIf { it >= 0 } }.minOrNull()

        val type = when {
            debitIdx == null && creditIdx == null -> return null
            debitIdx == null -> TxnType.INCOME
            creditIdx == null -> TxnType.EXPENSE
            // Both present (e.g. "debited from your a/c and credited to merchant"):
            // whichever keyword appears first wins.
            debitIdx <= creditIdx -> TxnType.EXPENSE
            else -> TxnType.INCOME
        }

        val accountTail = accountRegex.find(text)?.groupValues?.get(1)
        val merchant = extractMerchant(text, type) ?: "Unknown"

        return ParsedSms(amountMinor, type, merchant, accountTail)
    }

    private val merchantPatterns = listOf(
        Regex("""(?:\bat|\bto)\s+([A-Za-z][A-Za-z0-9 &._'\-*]{1,40}?)(?:\s+on\b|\s+via\b|\s+ref\b|\s+using\b|\s+upi\b|[.,;\n]|$)""", RegexOption.IGNORE_CASE),
        Regex("""\bfrom\s+([A-Za-z][A-Za-z0-9 &._'\-*]{1,40}?)(?:\s+on\b|\s+via\b|\s+ref\b|\s+a/c\b|[.,;\n]|$)""", RegexOption.IGNORE_CASE),
        Regex("""\bvpa\s+([\w.\-]+@[\w]+)""", RegexOption.IGNORE_CASE),
        Regex("""\binfo:?\s*([A-Za-z0-9 &._'\-*/]{2,40})""", RegexOption.IGNORE_CASE)
    )

    private val merchantNoise = setOf(
        "your", "you", "a/c", "account", "bank", "the", "ac", "card", "upi", "neft", "imps"
    )

    private fun extractMerchant(text: String, type: String): String? {
        val ordered = if (type == TxnType.INCOME) {
            listOf(merchantPatterns[1], merchantPatterns[0], merchantPatterns[2], merchantPatterns[3])
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
        listOf("swiggy", "zomato", "dominos", "pizza", "mcdonald", "kfc", "restaurant", "cafe", "eatclub", "faasos") to "Food & Dining",
        listOf("bigbasket", "blinkit", "zepto", "grofers", "dmart", "grocery", "instamart", "jiomart") to "Groceries",
        listOf("uber", "ola", "rapido", "redbus", "metro", "irctc rail", "fuel", "petrol", "hpcl", "iocl", "bpcl", "fastag") to "Transport",
        listOf("amazon", "flipkart", "myntra", "ajio", "meesho", "nykaa", "croma", "reliance digital") to "Shopping",
        listOf("electricity", "bescom", "msedcl", "tneb", "airtel", "jio", "vi ", "vodafone", "bsnl", "broadband", "dth", "tata sky", "gas bill", "water bill", "postpaid", "bill payment") to "Bills & Utilities",
        listOf("netflix", "spotify", "hotstar", "prime video", "sonyliv", "bookmyshow", "pvr", "inox", "youtube premium") to "Entertainment",
        listOf("pharmacy", "apollo", "1mg", "pharmeasy", "netmeds", "hospital", "clinic", "diagnostic", "practo") to "Health",
        listOf("makemytrip", "goibibo", "cleartrip", "indigo", "air india", "vistara", "oyo", "airbnb", "yatra", "ixigo", "hotel") to "Travel",
        listOf("udemy", "coursera", "byjus", "unacademy", "school fee", "tuition", "college") to "Education",
        listOf("rent", "nobroker", "housing.com", "maintenance") to "Rent & Home",
        listOf("emi", "loan repay", "bajaj fin", "home credit") to "EMI & Loans",
        listOf("zerodha", "groww", "upstox", "mutual fund", "sip", "nps", "ppf", "etmoney", "indmoney", "kuvera") to "Investments",
        listOf("salon", "spa", "urban company", "barber") to "Personal Care",
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
