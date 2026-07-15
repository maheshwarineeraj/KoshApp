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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MarkEmailUnread
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.neeraj.fin.data.db.TxnType
import com.neeraj.fin.ui.AppViewModel
import com.neeraj.fin.ui.components.EmptyState
import com.neeraj.fin.ui.components.TxnRow
import com.neeraj.fin.data.db.Txn
import com.neeraj.fin.util.Format
import com.neeraj.fin.util.QueryParser

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionsScreen(vm: AppViewModel, nav: NavController) {
    val txns by vm.transactions.collectAsState()
    val categories by vm.categories.collectAsState()
    val currency by vm.currencyCode.collectAsState()
    val pendingCount by vm.pendingCount.collectAsState()
    val catById = categories.associateBy { it.id }

    var query by remember { mutableStateOf("") }
    var typeFilter by remember { mutableStateOf<String?>(null) }
    var categoryFilter by remember { mutableStateOf<Long?>(null) }

    // Natural-language search: "advance tax 20K in Q2 last year", "swiggy last month".
    val parsed = remember(query) { if (query.isBlank()) null else QueryParser.parse(query) }
    val filtered = txns.filter { t ->
        (typeFilter == null || t.type == typeFilter) &&
            (categoryFilter == null || t.categoryId == categoryFilter) &&
            (parsed == null || matches(t, parsed, catById[t.categoryId]?.name))
    }
    val grouped = filtered.groupBy { Format.toLocalDate(it.timestamp) }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = { nav.navigate("edit/0") }) {
                Icon(Icons.Filled.Add, contentDescription = "Add transaction")
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (pendingCount > 0) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .clickable { nav.navigate("review") },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
                ) {
                    Row(
                        Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.MarkEmailUnread, contentDescription = null)
                        Text(
                            "$pendingCount detected from SMS — tap to review",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f).padding(horizontal = 12.dp)
                        )
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
                    }
                }
            }
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("Try: advance tax 20K in Q2 last year") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true
            )
            if (parsed != null && parsed.isStructured) {
                Text(
                    "Searching: " + parsed.understood.joinToString(" · "),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp)
                )
            }

            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = typeFilter == TxnType.EXPENSE,
                    onClick = { typeFilter = if (typeFilter == TxnType.EXPENSE) null else TxnType.EXPENSE },
                    label = { Text("Expenses") }
                )
                FilterChip(
                    selected = typeFilter == TxnType.INCOME,
                    onClick = { typeFilter = if (typeFilter == TxnType.INCOME) null else TxnType.INCOME },
                    label = { Text("Income") }
                )
                FilterChip(
                    selected = typeFilter == TxnType.TRANSFER,
                    onClick = { typeFilter = if (typeFilter == TxnType.TRANSFER) null else TxnType.TRANSFER },
                    label = { Text("Transfers") }
                )
                categories.forEach { c ->
                    FilterChip(
                        selected = categoryFilter == c.id,
                        onClick = { categoryFilter = if (categoryFilter == c.id) null else c.id },
                        label = { Text("${c.emoji} ${c.name}") }
                    )
                }
            }

            if (filtered.isEmpty()) {
                EmptyState(
                    emoji = "🔍",
                    title = "Nothing here",
                    subtitle = if (txns.isEmpty()) "Add your first transaction with the + button." else "No transactions match your filters."
                )
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    grouped.forEach { (date, dayTxns) ->
                        item(key = "h-$date") {
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
                            ) {
                                Text(
                                    Format.dayLabel(date),
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.weight(1f)
                                )
                                val net = dayTxns.sumOf {
                                    when (it.type) {
                                        TxnType.EXPENSE -> -it.amountMinor
                                        TxnType.INCOME -> it.amountMinor
                                        else -> 0L // transfers don't affect the day's net
                                    }
                                }
                                Text(
                                    Format.money(net, currency),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        items(dayTxns.size, key = { i -> dayTxns[i].id }) { i ->
                            val t = dayTxns[i]
                            TxnRow(t, catById[t.categoryId], currency) { nav.navigate("edit/${t.id}") }
                        }
                    }
                    item { Spacer(Modifier.height(88.dp)) }
                }
            }
        }
    }
}

/** Apply a parsed natural-language query to one transaction. */
private fun matches(t: Txn, q: QueryParser.Parsed, categoryName: String?): Boolean {
    if (q.type != null && t.type != q.type) return false
    if (q.fromMillis != null && t.timestamp < q.fromMillis) return false
    if (q.toMillis != null && t.timestamp >= q.toMillis) return false
    if (q.amountMinor != null) {
        val ok = when {
            q.amountIsMin -> t.amountMinor >= q.amountMinor
            q.amountIsMax -> t.amountMinor <= q.amountMinor
            else -> t.amountMinor == q.amountMinor
        }
        if (!ok) return false
    }
    if (q.terms.isNotEmpty()) {
        val hay = "${t.merchant} ${t.note} ${categoryName.orEmpty()}".lowercase()
        if (!q.terms.all { hay.contains(it) }) return false
    }
    return true
}
