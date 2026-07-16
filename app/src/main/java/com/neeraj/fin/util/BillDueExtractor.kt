package com.neeraj.fin.util

import com.google.mlkit.nl.entityextraction.DateTimeEntity
import com.google.mlkit.nl.entityextraction.Entity
import com.google.mlkit.nl.entityextraction.EntityExtraction
import com.google.mlkit.nl.entityextraction.EntityExtractionParams
import com.google.mlkit.nl.entityextraction.EntityExtractorOptions
import java.time.LocalDate
import java.time.ZoneId
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * Resolves the due date inside a bill-due message. Prefers ML Kit's on-device
 * entity extraction (handles "25th July", "05-08-26", "tomorrow"); falls back
 * to regex date patterns when the model isn't available yet.
 */
object BillDueExtractor {

    private val numericDate = Regex("""\b(\d{1,2})[-/](\d{1,2})[-/](\d{2,4})\b""")
    private val monthNames = listOf(
        "jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec"
    )
    private val wordDate = Regex(
        """\b(\d{1,2})(?:st|nd|rd|th)?[ -]([A-Za-z]{3,9})\.?(?:[ -](\d{2,4}))?\b"""
    )

    suspend fun dueDateMillis(text: String): Long? =
        mlkitDate(text) ?: regexDate(text)

    private suspend fun mlkitDate(text: String): Long? = suspendCoroutine { cont ->
        runCatching {
            val extractor = EntityExtraction.getClient(
                EntityExtractorOptions.Builder(EntityExtractorOptions.ENGLISH).build()
            )
            extractor.downloadModelIfNeeded()
                .onSuccessTask {
                    extractor.annotate(
                        EntityExtractionParams.Builder(text).build()
                    )
                }
                .addOnSuccessListener { annotations ->
                    val millis = annotations
                        .flatMap { it.entities }
                        .filterIsInstance<DateTimeEntity>()
                        .map { it.timestampMillis }
                        .filter { it > System.currentTimeMillis() - 24L * 60 * 60 * 1000 }
                        .minOrNull()
                    cont.resume(millis)
                }
                .addOnFailureListener { cont.resume(null) }
        }.onFailure { cont.resume(null) }
    }

    internal fun regexDate(text: String, today: LocalDate = LocalDate.now()): Long? {
        val zone = ZoneId.systemDefault()
        numericDate.find(text)?.let { m ->
            val (d, mo, yRaw) = m.destructured
            val y = yRaw.toInt().let { if (it < 100) 2000 + it else it }
            return runCatching {
                LocalDate.of(y, mo.toInt(), d.toInt()).atStartOfDay(zone).toInstant().toEpochMilli()
            }.getOrNull()
        }
        wordDate.find(text)?.let { m ->
            val day = m.groupValues[1].toIntOrNull() ?: return null
            val monIdx = monthNames.indexOfFirst { m.groupValues[2].lowercase().startsWith(it) }
            if (monIdx < 0) return null
            var y = m.groupValues[3].toIntOrNull()?.let { if (it < 100) 2000 + it else it } ?: today.year
            var date = runCatching { LocalDate.of(y, monIdx + 1, day) }.getOrNull() ?: return null
            if (m.groupValues[3].isBlank() && date.isBefore(today)) date = date.plusYears(1)
            return date.atStartOfDay(zone).toInstant().toEpochMilli()
        }
        return null
    }
}
