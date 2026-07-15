package com.neeraj.fin.util

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * On-device OCR (ML Kit) over a receipt photo, then heuristics to pull out
 * the total and the merchant. Everything runs locally; the image never
 * leaves the device.
 */
object ReceiptScanner {

    data class Result(val amountMinor: Long?, val merchant: String?, val rawText: String)

    private val amountLine = Regex(
        """(?:rs\.?|inr|₹)?\s*([0-9][0-9,]*(?:\.[0-9]{1,2})?)\s*$""",
        RegexOption.IGNORE_CASE
    )
    private val totalWords = listOf(
        "grand total", "net payable", "amount payable", "total amount",
        "amount due", "net amount", "total", "payable", "amount paid", "paid"
    )
    private val notMerchant = Regex(
        """(?i)invoice|receipt|bill|gst|tax|date|time|tel|phone|www\.|@|order|table|cashier|no\.|#"""
    )

    suspend fun scan(context: Context, uri: Uri): Result = suspendCoroutine { cont ->
        val image = InputImage.fromFilePath(context, uri)
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            .process(image)
            .addOnSuccessListener { visionText ->
                val lines = visionText.textBlocks.flatMap { it.lines }.map { it.text.trim() }
                cont.resume(extract(lines, visionText.text))
            }
            .addOnFailureListener { cont.resume(Result(null, null, "")) }
    }

    internal fun extract(lines: List<String>, raw: String): Result {
        // Amount: prefer a number on a line mentioning total/payable; otherwise
        // the largest money-looking number on the receipt.
        var amount: Long? = null
        for (word in totalWords) {
            val line = lines.lastOrNull { it.lowercase().contains(word) } ?: continue
            val m = amountLine.find(line)
                ?: lines.getOrNull(lines.indexOf(line) + 1)?.let { amountLine.find(it) }
            val v = m?.groupValues?.get(1)?.replace(",", "")?.toDoubleOrNull()
            if (v != null && v > 0) { amount = (v * 100).toLong(); break }
        }
        if (amount == null) {
            amount = lines.mapNotNull { l ->
                amountLine.find(l)?.groupValues?.get(1)?.replace(",", "")?.toDoubleOrNull()
            }.filter { it > 0 && it < 10_00_000 }.maxOrNull()?.let { (it * 100).toLong() }
        }

        // Merchant: first short-ish line near the top that isn't boilerplate.
        val merchant = lines.take(5).firstOrNull { l ->
            l.length in 3..40 && !notMerchant.containsMatchIn(l) && l.any { it.isLetter() } &&
                amountLine.find(l)?.groupValues?.get(1) == null
        }?.split(" ")?.joinToString(" ") { w -> w.lowercase().replaceFirstChar { it.uppercase() } }

        return Result(amount, merchant, raw)
    }
}
