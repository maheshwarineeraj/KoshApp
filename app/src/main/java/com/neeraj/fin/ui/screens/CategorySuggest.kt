package com.neeraj.fin.ui.screens

import com.neeraj.fin.data.db.Txn

/**
 * Top category suggestions for manual entry, best first:
 * 1) what you used before for this merchant, 2) keyword match on
 * merchant+note text, 3) your overall most-used categories of this kind.
 */
internal fun suggestCategories(
    merchant: String,
    note: String,
    matchingCats: List<com.neeraj.fin.data.db.Category>,
    txns: List<Txn>
): List<com.neeraj.fin.data.db.Category> {
    val result = mutableListOf<com.neeraj.fin.data.db.Category>()
    val kind = matchingCats.firstOrNull()?.kind

    if (merchant.length >= 3) {
        txns.filter { it.categoryId != null && it.merchant.equals(merchant.trim(), ignoreCase = true) }
            .groupBy { it.categoryId }
            .entries.sortedByDescending { it.value.size }
            .mapNotNull { (id, _) -> matchingCats.firstOrNull { it.id == id } }
            .forEach { result += it }
    }

    val text = "$merchant $note".trim()
    if (text.length >= 3 && kind != null) {
        com.neeraj.fin.data.sms.SmsParser.suggestCategoryFromText(text, kind)?.let { name ->
            matchingCats.firstOrNull { it.name.equals(name, ignoreCase = true) }?.let { result += it }
        }
    }

    txns.filter { it.categoryId != null }
        .groupBy { it.categoryId }
        .entries.sortedByDescending { it.value.size }
        .mapNotNull { (id, _) -> matchingCats.firstOrNull { it.id == id } }
        .forEach { result += it }

    // Pad with defaults so there are always three chips.
    matchingCats.forEach { result += it }
    return result.distinctBy { it.id }.take(3)
}

/** History + keyword suggestions only — the confident ones worth auto-selecting. */
internal fun strongCategorySuggestions(
    merchant: String,
    note: String,
    matchingCats: List<com.neeraj.fin.data.db.Category>,
    txns: List<Txn>
): List<com.neeraj.fin.data.db.Category> {
    val result = mutableListOf<com.neeraj.fin.data.db.Category>()
    if (merchant.trim().length >= 3) {
        txns.filter {
            it.categoryId != null &&
                com.neeraj.fin.util.Merchants.same(it.merchant, merchant.trim())
        }
            .groupBy { it.categoryId }
            .entries.sortedByDescending { it.value.size }
            .mapNotNull { (id, _) -> matchingCats.firstOrNull { it.id == id } }
            .forEach { result += it }
    }
    val text = "$merchant $note".trim()
    val kind = matchingCats.firstOrNull()?.kind
    if (text.length >= 3 && kind != null) {
        com.neeraj.fin.data.sms.SmsParser.suggestCategoryFromText(text, kind)?.let { name ->
            matchingCats.firstOrNull { it.name.equals(name, ignoreCase = true) }?.let { result += it }
        }
    }
    return result.distinctBy { it.id }
}
