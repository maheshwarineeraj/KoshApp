package com.neeraj.fin.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.neeraj.fin.data.db.Category
import com.neeraj.fin.data.db.Txn
import com.neeraj.fin.data.db.TxnType
import com.neeraj.fin.ui.AppViewModel
import com.neeraj.fin.ui.components.BarChart
import com.neeraj.fin.ui.components.BarEntry
import com.neeraj.fin.ui.components.DonutChart
import com.neeraj.fin.ui.components.DonutSlice
import com.neeraj.fin.ui.components.EmptyState
import com.neeraj.fin.ui.components.ProgressBar
import com.neeraj.fin.ui.theme.expenseColor
import com.neeraj.fin.ui.theme.incomeColor
import com.neeraj.fin.util.CompareKind
import com.neeraj.fin.util.Format
import com.neeraj.fin.util.PeriodKind
import com.neeraj.fin.util.Periods
import java.time.LocalDate
import kotlin.math.abs

@Composable
fun InsightsScreen(vm: AppViewModel) {
    var tab by remember { mutableStateOf(0) }
    Column(Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = tab) {
            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Overview") })
            Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Compare") })
        }
        when (tab) {
            0 -> OverviewTab(vm)
            else -> CompareTab(vm)
        }
    }
}

@Composable
private fun OverviewTab(vm: AppViewModel) {
    val categories by vm.categories.collectAsState()
    val currency by vm.currencyCode.collectAsState()

    var kind by remember { mutableStateOf(PeriodKind.MONTH) }
    var anchor by remember { mutableStateOf(LocalDate.now()) }
    var breakdownType by remember { mutableStateOf(TxnType.EXPENSE) }

    val range = remember(kind, anchor) { Periods.rangeFor(kind, anchor) }
    val txns by remember(range) { vm.txnsBetween(range.startMillis, range.endMillis) }
        .collectAsState(initial = emptyList())

    val income = txns.filter { it.type == TxnType.INCOME }.sumOf { it.amountMinor }
    val expense = txns.filter { it.type == TxnType.EXPENSE }.sumOf { it.amountMinor }
    val catById = categories.associateBy { it.id }

    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PeriodKind.entries.forEach { k ->
                    FilterChip(
                        selected = kind == k,
                        onClick = { kind = k; anchor = LocalDate.now() },
                        label = { Text(k.label) }
                    )
                }
            }
        }

        item {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { anchor = Periods.shift(kind, anchor, -1) }) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous")
                }
                Text(
                    range.label,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                IconButton(onClick = { anchor = Periods.shift(kind, anchor, 1) }) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next")
                }
            }
        }

        item {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SummaryCard("Income", income, incomeColor(), currency, Modifier.weight(1f))
                SummaryCard("Expenses", expense, expenseColor(), currency, Modifier.weight(1f))
            }
        }

        if (txns.isEmpty()) {
            item {
                EmptyState(
                    emoji = "📊",
                    title = "No data for this period",
                    subtitle = "Insights appear once you have transactions in ${range.label}."
                )
            }
        } else {
            item {
                // Plain-language digest, generated on-device from the period's numbers.
                val prevRange = remember(kind, anchor) { Periods.rangeFor(kind, Periods.shift(kind, anchor, -1)) }
                val prevTxns by remember(prevRange) { vm.txnsBetween(prevRange.startMillis, prevRange.endMillis) }
                    .collectAsState(initial = emptyList())
                val digest = buildDigest(txns, prevTxns, catById, currency)
                if (digest.isNotEmpty()) {
                    Card(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                    ) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("In short", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            digest.forEach { line ->
                                Text(line, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }
            item {
                Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Breakdown", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                            FilterChip(
                                selected = breakdownType == TxnType.EXPENSE,
                                onClick = { breakdownType = TxnType.EXPENSE },
                                label = { Text("Spend") }
                            )
                            Spacer(Modifier.padding(2.dp))
                            FilterChip(
                                selected = breakdownType == TxnType.INCOME,
                                onClick = { breakdownType = TxnType.INCOME },
                                label = { Text("Income") }
                            )
                        }
                        CategoryBreakdown(
                            txns.filter { it.type == breakdownType },
                            catById, currency,
                            if (breakdownType == TxnType.EXPENSE) "spent" else "earned"
                        )
                    }
                }
            }

            item {
                Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Spending trend", style = MaterialTheme.typography.titleMedium)
                        val buckets = Periods.trendBuckets(kind, anchor)
                        BarChart(
                            entries = buckets.map { b ->
                                BarEntry(
                                    b.label,
                                    txns.filter { it.type == TxnType.EXPENSE && it.timestamp >= b.startMillis && it.timestamp < b.endMillis }
                                        .sumOf { it.amountMinor }
                                )
                            },
                            primaryColor = MaterialTheme.colorScheme.primary,
                            valueFormatter = { if (it == 0L) "" else Format.compact(it, currency) }
                        )
                    }
                }
            }

            item {
                val topMerchants = txns.filter { it.type == TxnType.EXPENSE && it.merchant.isNotBlank() }
                    .groupBy { com.neeraj.fin.util.Merchants.key(it.merchant) }
                    .map { (_, group) -> Triple(group.first().merchant, group.sumOf { it.amountMinor }, group.size) }
                    .sortedByDescending { it.second }
                    .take(5)
                if (topMerchants.isNotEmpty()) {
                    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("Top merchants", style = MaterialTheme.typography.titleMedium)
                            topMerchants.forEach { (name, total, count) ->
                                Row {
                                    Column(Modifier.weight(1f)) {
                                        Text(name, style = MaterialTheme.typography.bodyMedium)
                                        Text(
                                            "$count transaction${if (count == 1) "" else "s"}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Text(
                                        Format.money(total, currency),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item {
                // Likely subscriptions: same merchant, same amount, ~monthly cadence (all-time scan)
                val allTxns by vm.transactions.collectAsState()
                val recurringRules by vm.recurringRules.collectAsState()
                val subscriptions = remember(allTxns) { detectSubscriptions(allTxns) }
                if (subscriptions.isNotEmpty()) {
                    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("Likely subscriptions", style = MaterialTheme.typography.titleMedium)
                            subscriptions.forEach { sub ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(sub.merchant, style = MaterialTheme.typography.bodyMedium)
                                        Text(
                                            "Next expected ~${Format.dayLabel(Format.toLocalDate(sub.nextExpected))}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(
                                            "${Format.money(sub.amountMinor, currency)}/mo",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        if (recurringRules.none {
                                                it.merchant.equals(sub.merchant, ignoreCase = true) &&
                                                    it.amountMinor == sub.amountMinor
                                            }
                                        ) {
                                            androidx.compose.material3.TextButton(
                                                onClick = { vm.makeRecurringFromSubscription(sub.merchant, sub.amountMinor, sub.nextExpected) },
                                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                                            ) { Text("Make recurring") }
                                        }
                                    }
                                }
                            }
                            Text(
                                "Detected from repeated same-amount payments about a month apart.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            item {
                // Quick stats
                val days = ((range.endMillis - range.startMillis) / 86_400_000L).coerceAtLeast(1)
                val biggest = txns.filter { it.type == TxnType.EXPENSE }.maxByOrNull { it.amountMinor }
                Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Quick stats", style = MaterialTheme.typography.titleMedium)
                        StatRow("Average daily spend", Format.money(expense / days, currency))
                        StatRow("Savings rate", if (income > 0) "${((income - expense) * 100 / income)}%" else "—")
                        if (biggest != null) {
                            StatRow(
                                "Biggest expense",
                                "${biggest.merchant.ifBlank { "Unknown" }} · ${Format.money(biggest.amountMinor, currency)}"
                            )
                        }
                        StatRow("Transactions", txns.size.toString())
                    }
                }
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }
}

private data class DetectedSubscription(val merchant: String, val amountMinor: Long, val nextExpected: Long)

/** Same merchant + same amount, at least 3 occurrences ~25–35 days apart. */
private fun detectSubscriptions(txns: List<Txn>): List<DetectedSubscription> =
    txns.asSequence()
        .filter { it.type == TxnType.EXPENSE && it.merchant.isNotBlank() }
        .groupBy { it.merchant.lowercase() to it.amountMinor }
        .mapNotNull { (_, group) ->
            if (group.size < 3) return@mapNotNull null
            val sorted = group.sortedBy { it.timestamp }
            val gapsDays = sorted.zipWithNext { a, b -> (b.timestamp - a.timestamp) / 86_400_000.0 }
            if (gapsDays.all { it in 25.0..35.0 }) {
                DetectedSubscription(
                    merchant = sorted.last().merchant,
                    amountMinor = sorted.last().amountMinor,
                    nextExpected = sorted.last().timestamp + 30L * 86_400_000
                )
            } else null
        }
        .sortedByDescending { it.amountMinor }
        .take(6)

@Composable
private fun StatRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun SummaryCard(label: String, amount: Long, color: Color, currency: String, modifier: Modifier = Modifier) {
    Card(modifier) {
        Column(Modifier.padding(16.dp)) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                Format.money(amount, currency),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = color
            )
        }
    }
}

@Composable
private fun CategoryBreakdown(
    txns: List<Txn>,
    catById: Map<Long, Category>,
    currency: String,
    verb: String
) {
    val total = txns.sumOf { it.amountMinor }
    val byCat = txns.groupBy { it.categoryId }
        .map { (catId, group) -> (catId?.let { catById[it] }) to group.sumOf { it.amountMinor } }
        .sortedByDescending { it.second }

    val slices = byCat.map { (cat, sum) ->
        DonutSlice(cat?.name ?: "Uncategorized", sum, cat?.let { Color(it.color) } ?: Color.Gray)
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        DonutChart(
            slices = slices,
            centerTop = Format.compact(total, currency),
            centerBottom = verb,
            modifier = Modifier.fillMaxWidth()
        )
        byCat.take(6).forEach { (cat, sum) ->
            Column {
                Row {
                    Text(
                        "${cat?.emoji ?: "❓"} ${cat?.name ?: "Uncategorized"}",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        "${Format.money(sum, currency)} · ${if (total > 0) sum * 100 / total else 0}%",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(4.dp))
                ProgressBar(
                    fraction = if (total > 0) sum.toFloat() / total else 0f,
                    color = cat?.let { Color(it.color) } ?: Color.Gray
                )
            }
        }
    }
}

@Composable
private fun CompareTab(vm: AppViewModel) {
    val categories by vm.categories.collectAsState()
    val currency by vm.currencyCode.collectAsState()
    var kind by remember { mutableStateOf(CompareKind.MOM) }

    val (cur, prev) = remember(kind) { Periods.compareRanges(kind) }
    val curTxns by remember(cur) { vm.txnsBetween(cur.startMillis, cur.endMillis) }
        .collectAsState(initial = emptyList())
    val prevTxns by remember(prev) { vm.txnsBetween(prev.startMillis, prev.endMillis) }
        .collectAsState(initial = emptyList())

    val catById = categories.associateBy { it.id }

    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CompareKind.entries.forEach { k ->
                    FilterChip(
                        selected = kind == k,
                        onClick = { kind = k },
                        label = { Text(k.name) }
                    )
                }
            }
        }

        item {
            Text(
                "${cur.label} vs ${prev.label}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                textAlign = TextAlign.Center
            )
        }

        item {
            val curExp = curTxns.filter { it.type == TxnType.EXPENSE }.sumOf { it.amountMinor }
            val prevExp = prevTxns.filter { it.type == TxnType.EXPENSE }.sumOf { it.amountMinor }
            val curInc = curTxns.filter { it.type == TxnType.INCOME }.sumOf { it.amountMinor }
            val prevInc = prevTxns.filter { it.type == TxnType.INCOME }.sumOf { it.amountMinor }

            Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    DeltaRow("Expenses", curExp, prevExp, currency, higherIsBad = true)
                    DeltaRow("Income", curInc, prevInc, currency, higherIsBad = false)
                    DeltaRow("Net savings", curInc - curExp, prevInc - prevExp, currency, higherIsBad = false)
                    BarChart(
                        entries = listOf(
                            BarEntry("Expenses", curExp, prevExp),
                            BarEntry("Income", curInc, prevInc)
                        ),
                        primaryColor = MaterialTheme.colorScheme.primary,
                        secondaryColor = MaterialTheme.colorScheme.outlineVariant,
                        valueFormatter = { if (it == 0L) "" else Format.compact(it, currency) }
                    )
                    Text(
                        "Solid = ${cur.label} · Muted = ${prev.label}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        item {
            // Per-category expense change
            val curByCat = curTxns.filter { it.type == TxnType.EXPENSE }.groupBy { it.categoryId }
                .mapValues { (_, g) -> g.sumOf { it.amountMinor } }
            val prevByCat = prevTxns.filter { it.type == TxnType.EXPENSE }.groupBy { it.categoryId }
                .mapValues { (_, g) -> g.sumOf { it.amountMinor } }
            val allCatIds = (curByCat.keys + prevByCat.keys)
            val rows = allCatIds.map { id ->
                Triple(id?.let { catById[it] }, curByCat[id] ?: 0L, prevByCat[id] ?: 0L)
            }.sortedByDescending { abs(it.second - it.third) }.take(8)

            if (rows.isNotEmpty()) {
                Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Biggest movers (expenses)", style = MaterialTheme.typography.titleMedium)
                        rows.forEach { (cat, curV, prevV) ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "${cat?.emoji ?: "❓"} ${cat?.name ?: "Uncategorized"}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.weight(1f)
                                )
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        "${Format.compact(prevV, currency)} → ${Format.compact(curV, currency)}",
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    val delta = curV - prevV
                                    val pct = if (prevV > 0) " (${if (delta >= 0) "+" else ""}${delta * 100 / prevV}%)" else ""
                                    Text(
                                        (if (delta >= 0) "+" else "−") + Format.money(abs(delta), currency) + pct,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (delta > 0) expenseColor() else incomeColor()
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                EmptyState(
                    emoji = "🔁",
                    title = "Nothing to compare yet",
                    subtitle = "Once you have transactions in both periods, changes show up here."
                )
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun DeltaRow(label: String, cur: Long, prev: Long, currency: String, higherIsBad: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                Format.money(cur, currency),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }
        val delta = cur - prev
        val good = if (higherIsBad) delta <= 0 else delta >= 0
        Column(horizontalAlignment = Alignment.End) {
            Text(
                (if (delta >= 0) "▲ " else "▼ ") + Format.compact(abs(delta), currency),
                color = if (good) incomeColor() else expenseColor(),
                fontWeight = FontWeight.SemiBold
            )
            if (prev != 0L) {
                Text(
                    "${if (delta >= 0) "+" else ""}${delta * 100 / abs(prev)}% vs prev",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Rule-based plain-language summary of a period vs the previous one.
 * Deterministic and fully on-device — no models, no guesswork in the numbers.
 */
private fun buildDigest(
    cur: List<Txn>,
    prev: List<Txn>,
    catById: Map<Long, Category>,
    currency: String
): List<String> {
    val lines = mutableListOf<String>()
    val exp = cur.filter { it.type == TxnType.EXPENSE }
    val inc = cur.filter { it.type == TxnType.INCOME }.sumOf { it.amountMinor }
    val expTotal = exp.sumOf { it.amountMinor }
    val prevExp = prev.filter { it.type == TxnType.EXPENSE }.sumOf { it.amountMinor }

    if (expTotal > 0) {
        var line = "You spent ${Format.money(expTotal, currency, withDecimals = false)}"
        if (prevExp > 0) {
            val pct = (expTotal - prevExp) * 100 / prevExp
            line += when {
                pct > 0 -> " — $pct% more than the previous period."
                pct < 0 -> " — ${-pct}% less than the previous period."
                else -> " — same as the previous period."
            }
        } else line += "."
        lines += line
    }

    exp.filter { it.categoryId != null }
        .groupBy { it.categoryId }
        .maxByOrNull { (_, v) -> v.sumOf { it.amountMinor } }
        ?.let { (catId, v) ->
            val cat = catById[catId] ?: return@let
            val total = v.sumOf { it.amountMinor }
            val share = if (expTotal > 0) total * 100 / expTotal else 0
            lines += "Biggest category: ${cat.emoji} ${cat.name} " +
                "(${Format.money(total, currency, withDecimals = false)}, $share% of spending)."
        }

    exp.maxByOrNull { it.amountMinor }?.takeIf { it.amountMinor > 0 }?.let {
        lines += "Largest expense: ${Format.money(it.amountMinor, currency, withDecimals = false)}" +
            (it.merchant.takeIf { m -> m.isNotBlank() }?.let { m -> " at $m." } ?: ".")
    }

    if (inc > 0) {
        val saved = (inc - expTotal) * 100 / inc
        lines += if (saved >= 0) "You saved $saved% of your income."
        else "You spent more than you earned this period."
    }
    return lines
}
