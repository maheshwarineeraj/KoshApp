package com.neeraj.fin.util

/**
 * Merchant-name normalization so "SWIGGY*ORDER8747", "Swiggy Instamart" and
 * "swiggy@ybl" cluster together for insights, learning, and suggestions.
 */
object Merchants {

    /** Canonical grouping key for a raw merchant string. */
    fun key(raw: String): String {
        var m = raw.trim().lowercase()
        if (m.isEmpty()) return m
        // VPAs: keep the handle before @
        if (m.contains('@')) m = m.substringBefore('@')
        // Strip punctuation and trailing reference digits
        m = m.replace(Regex("""[*_#/\-]+"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()
        m = m.replace(Regex("""\s*\d{3,}$"""), "")
        val first = m.substringBefore(' ')
        // A distinctive first token (5+ chars) identifies the brand: swiggy,
        // zomato, amazon… Short tokens ("tata") keep the full name so
        // "Tata Neu" and "Tata Motors" stay separate.
        return if (first.length >= 5) first else m
    }

    fun same(a: String, b: String): Boolean = key(a).isNotEmpty() && key(a) == key(b)
}
