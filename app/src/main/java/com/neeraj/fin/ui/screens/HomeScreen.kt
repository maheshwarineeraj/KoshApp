package com.neeraj.fin.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MarkEmailUnread
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.neeraj.fin.data.db.TxnType
import com.neeraj.fin.ui.AppViewModel
import com.neeraj.fin.ui.components.EmptyState
import com.neeraj.fin.ui.components.ProgressBar
import com.neeraj.fin.ui.components.TxnRow
import com.neeraj.fin.ui.theme.expenseColor
import com.neeraj.fin.ui.theme.incomeColor
import com.neeraj.fin.util.Format
import com.neeraj.fin.util.PeriodKind
import com.neeraj.fin.util.Periods
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

@Composable
fun HomeScreen(vm: AppViewModel, nav: NavController) {
    val txns by vm.transactions.collectAsState()
    val categories by vm.categories.collectAsState()
    val budgets by vm.budgets.collectAsState()
    val pendingCount by vm.pendingCount.collectAsState()
    val currency by vm.currencyCode.collectAsState()

    // Current month only — used for the budgets snapshot below
    val month = remember { Periods.rangeFor(PeriodKind.MONTH, LocalDate.now()) }
    val monthTxns = txns.filter { it.timestamp >= month.startMillis && it.timestamp < month.endMillis }
    val catById = categories.associateBy { it.id }
    val recent = txns.take(8)

    Scaffold(
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { nav.navigate("edit/0") },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("Add") }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Column(Modifier.padding(horizontal = 16.dp).padding(top = 16.dp)) {
                    Text("Kosh", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "Your money, on your device",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            item {
                val today = remember { LocalDate.now() }
                val earliestMonth = remember(txns) {
                    txns.minByOrNull { it.timestamp }
                        ?.let { YearMonth.from(Format.toLocalDate(it.timestamp)) }
                        ?: YearMonth.from(today)
                }
                val monthCount =
                    (ChronoUnit.MONTHS.between(earliestMonth, YearMonth.from(today)).toInt() + 1)
                        .coerceIn(1, 120)
                var allTime by remember { mutableStateOf(false) }

                if (allTime) {
                    HeroSummaryCard(
                        label = "All time",
                        subLabel = "since ${earliestMonth.format(monthYearFmt)}",
                        scopedTxns = txns,
                        currency = currency,
                        onPrev = null,
                        onNext = null,
                        toggleLabel = "Monthly",
                        onToggle = { allTime = false }
                    )
                } else {
                    val pagerState = rememberPagerState(initialPage = monthCount - 1) { monthCount }
                    val scope = rememberCoroutineScope()
                    // Keep the pager anchored to the current month when the month span
                    // changes (e.g. the transactions flow emits after first composition).
                    LaunchedEffect(monthCount) { pagerState.scrollToPage(monthCount - 1) }
                    HorizontalPager(state = pagerState) { page ->
                        val monthStart = today.withDayOfMonth(1).minusMonths((monthCount - 1 - page).toLong())
                        val range = Periods.rangeFor(PeriodKind.MONTH, monthStart)
                        val scoped = txns.filter { it.timestamp >= range.startMillis && it.timestamp < range.endMillis }
                        HeroSummaryCard(
                            label = range.label,
                            subLabel = null,
                            scopedTxns = scoped,
                            currency = currency,
                            onPrev = if (page > 0) {
                                { scope.launch { pagerState.animateScrollToPage(page - 1) } }
                            } else null,
                            onNext = if (page < monthCount - 1) {
                                { scope.launch { pagerState.animateScrollToPage(page + 1) } }
                            } else null,
                            toggleLabel = "All time",
                            onToggle = { allTime = true }
                        )
                    }
                }
            }

            item {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    QuickAction("🎯", "Goals") { nav.navigate("budgets") }
                    QuickAction("💼", "Wealth") { nav.navigate("wealth") }
                    QuickAction("🏷️", "Categories") { nav.navigate("categories") }
                    QuickAction("📊", "Insights") { nav.navigate("insights") }
                }
            }

            if (pendingCount > 0) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .clickable { nav.navigate("review") },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
                    ) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.MarkEmailUnread, contentDescription = null)
                            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                                Text(
                                    "$pendingCount transaction${if (pendingCount == 1) "" else "s"} detected from SMS",
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text("Tap to review and approve", style = MaterialTheme.typography.bodySmall)
                            }
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
                        }
                    }
                }
            }

            // Budget snapshot: top 3 budgets by usage
            val budgetRows = budgets.mapNotNull { b ->
                val cat = catById[b.categoryId] ?: return@mapNotNull null
                val spent = monthTxns.filter { it.type == TxnType.EXPENSE && it.categoryId == b.categoryId }
                    .sumOf { it.amountMinor }
                Triple(cat, spent, b.monthlyLimitMinor)
            }.sortedByDescending { it.second.toDouble() / it.third }.take(3)

            if (budgetRows.isNotEmpty()) {
                item {
                    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Budgets", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                                TextButton(onClick = { nav.navigate("budgets") }) { Text("Manage") }
                            }
                            budgetRows.forEach { (cat, spent, limit) ->
                                Column {
                                    Row {
                                        Text("${cat.emoji} ${cat.name}", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                                        Text(
                                            "${Format.compact(spent, currency)} / ${Format.compact(limit, currency)}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = if (spent > limit) expenseColor() else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Spacer(Modifier.height(4.dp))
                                    ProgressBar(
                                        fraction = spent.toFloat() / limit,
                                        color = if (spent > limit) expenseColor() else Color(cat.color)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Recent", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    TextButton(onClick = { nav.navigate("transactions") }) { Text("See all") }
                }
            }

            if (recent.isEmpty()) {
                item {
                    EmptyState(
                        emoji = "🪙",
                        title = "No transactions yet",
                        subtitle = "Add one with the + button, or scan your SMS inbox from Settings to import bank messages."
                    )
                }
            } else {
                items(recent.size) { i ->
                    val t = recent[i]
                    TxnRow(t, catById[t.categoryId], currency) { nav.navigate("edit/${t.id}") }
                    if (i < recent.size - 1) HorizontalDivider(Modifier.padding(start = 68.dp))
                }
            }

            item { Spacer(Modifier.height(80.dp)) }
        }
    }
}

private val monthYearFmt = DateTimeFormatter.ofPattern("MMM yyyy")

@Composable
private fun HeroSummaryCard(
    label: String,
    subLabel: String?,
    scopedTxns: List<com.neeraj.fin.data.db.Txn>,
    currency: String,
    onPrev: (() -> Unit)?,
    onNext: (() -> Unit)?,
    toggleLabel: String,
    onToggle: () -> Unit
) {
    val income = scopedTxns.filter { it.type == TxnType.INCOME }.sumOf { it.amountMinor }
    val expense = scopedTxns.filter { it.type == TxnType.EXPENSE }.sumOf { it.amountMinor }
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp, top = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (onPrev != null) {
                    IconButton(onClick = onPrev) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous month")
                    }
                }
                Column(Modifier.weight(1f)) {
                    Text(label, style = MaterialTheme.typography.labelLarge)
                    if (subLabel != null) {
                        Text(
                            subLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                        )
                    }
                }
                if (onNext != null) {
                    IconButton(onClick = onNext) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next month")
                    }
                }
                TextButton(onClick = onToggle) { Text(toggleLabel) }
            }
            Text(
                Format.money(income - expense, currency),
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = if (income - expense < 0) expenseColor() else MaterialTheme.colorScheme.onPrimaryContainer
            )
            Text(
                if (subLabel != null) "Net, all time" else "Net this month",
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("Income", style = MaterialTheme.typography.labelMedium)
                    Text(
                        Format.money(income, currency),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = incomeColor()
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Expenses", style = MaterialTheme.typography.labelMedium)
                    Text(
                        Format.money(expense, currency),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = expenseColor()
                    )
                }
            }
        }
    }
}

@Composable
private fun QuickAction(emoji: String, label: String, onClick: () -> Unit) {
    Card(onClick = onClick) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(emoji)
            Text(label, style = MaterialTheme.typography.labelLarge)
        }
    }
}
