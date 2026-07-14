package com.neeraj.fin.util

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

enum class PeriodKind(val label: String) { DAY("Day"), WEEK("Week"), MONTH("Month"), YEAR("Year") }

enum class CompareKind(val label: String) { MOM("Month on Month"), QOQ("Quarter on Quarter"), YOY("Year on Year") }

data class PeriodRange(
    val startMillis: Long,
    val endMillis: Long, // exclusive
    val label: String
)

data class TrendBucket(val label: String, val startMillis: Long, val endMillis: Long)

object Periods {

    private val zone: ZoneId get() = ZoneId.systemDefault()
    private fun LocalDate.millis(): Long = atStartOfDay(zone).toInstant().toEpochMilli()

    private val monthYearFmt = DateTimeFormatter.ofPattern("MMM yyyy")
    private val dayFmt = DateTimeFormatter.ofPattern("d MMM yyyy")
    private val shortDayFmt = DateTimeFormatter.ofPattern("d MMM")

    fun rangeFor(kind: PeriodKind, anchor: LocalDate): PeriodRange = when (kind) {
        PeriodKind.DAY -> PeriodRange(anchor.millis(), anchor.plusDays(1).millis(), anchor.format(dayFmt))
        PeriodKind.WEEK -> {
            val start = anchor.with(DayOfWeek.MONDAY)
            val end = start.plusDays(7)
            PeriodRange(start.millis(), end.millis(), "${start.format(shortDayFmt)} – ${end.minusDays(1).format(shortDayFmt)}")
        }
        PeriodKind.MONTH -> {
            val start = anchor.withDayOfMonth(1)
            PeriodRange(start.millis(), start.plusMonths(1).millis(), start.format(monthYearFmt))
        }
        PeriodKind.YEAR -> {
            val start = anchor.withDayOfYear(1)
            PeriodRange(start.millis(), start.plusYears(1).millis(), start.year.toString())
        }
    }

    fun shift(kind: PeriodKind, anchor: LocalDate, delta: Long): LocalDate = when (kind) {
        PeriodKind.DAY -> anchor.plusDays(delta)
        PeriodKind.WEEK -> anchor.plusWeeks(delta)
        PeriodKind.MONTH -> anchor.plusMonths(delta)
        PeriodKind.YEAR -> anchor.plusYears(delta)
    }

    /** Buckets used for the trend chart within a period. */
    fun trendBuckets(kind: PeriodKind, anchor: LocalDate): List<TrendBucket> = when (kind) {
        PeriodKind.DAY -> {
            (0 until 24 step 4).map { h ->
                val start = anchor.atStartOfDay(zone).plusHours(h.toLong())
                TrendBucket("${h}h", start.toInstant().toEpochMilli(), start.plusHours(4).toInstant().toEpochMilli())
            }
        }
        PeriodKind.WEEK -> {
            val monday = anchor.with(DayOfWeek.MONDAY)
            (0 until 7).map { d ->
                val day = monday.plusDays(d.toLong())
                TrendBucket(day.dayOfWeek.name.take(3).lowercase().replaceFirstChar { it.uppercase() }, day.millis(), day.plusDays(1).millis())
            }
        }
        PeriodKind.MONTH -> {
            val first = anchor.withDayOfMonth(1)
            val days = first.lengthOfMonth()
            // Group into ~6 buckets of 5-6 days for readability
            val step = if (days > 28) 6 else 5
            (1..days step step).map { d ->
                val start = first.withDayOfMonth(d)
                val endDay = minOf(d + step - 1, days)
                val end = first.withDayOfMonth(endDay).plusDays(1)
                TrendBucket(if (d == endDay) "$d" else "$d–$endDay", start.millis(), end.millis())
            }
        }
        PeriodKind.YEAR -> {
            val jan = anchor.withDayOfYear(1)
            (0 until 12).map { m ->
                val start = jan.plusMonths(m.toLong())
                TrendBucket(start.month.name.take(3).lowercase().replaceFirstChar { it.uppercase() }, start.millis(), start.plusMonths(1).millis())
            }
        }
    }

    /** Current vs previous ranges for MoM / QoQ / YoY comparison, anchored at today. */
    fun compareRanges(kind: CompareKind, anchor: LocalDate = LocalDate.now()): Pair<PeriodRange, PeriodRange> = when (kind) {
        CompareKind.MOM -> {
            val cur = rangeFor(PeriodKind.MONTH, anchor)
            val prev = rangeFor(PeriodKind.MONTH, anchor.minusMonths(1))
            cur to prev
        }
        CompareKind.QOQ -> {
            val qStartMonth = ((anchor.monthValue - 1) / 3) * 3 + 1
            val qStart = LocalDate.of(anchor.year, qStartMonth, 1)
            val cur = PeriodRange(qStart.millis(), qStart.plusMonths(3).millis(), "Q${(qStartMonth - 1) / 3 + 1} ${anchor.year}")
            val pStart = qStart.minusMonths(3)
            val prev = PeriodRange(pStart.millis(), qStart.millis(), "Q${(pStart.monthValue - 1) / 3 + 1} ${pStart.year}")
            cur to prev
        }
        CompareKind.YOY -> {
            val cur = rangeFor(PeriodKind.YEAR, anchor)
            val prev = rangeFor(PeriodKind.YEAR, anchor.minusYears(1))
            cur to prev
        }
    }
}
