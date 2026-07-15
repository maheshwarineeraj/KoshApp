package com.neeraj.fin.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.neeraj.fin.data.db.PendingSms
import com.neeraj.fin.data.db.TxnType
import com.neeraj.fin.ui.AppViewModel
import com.neeraj.fin.ui.components.EmptyState
import com.neeraj.fin.util.Format

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewScreen(vm: AppViewModel, nav: NavController) {
    val pending by vm.pendingSms.collectAsState()
    val currency by vm.currencyCode.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Review SMS transactions") },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        if (pending.isEmpty()) {
            Column(Modifier.fillMaxSize().padding(padding)) {
                EmptyState(
                    emoji = "✅",
                    title = "All caught up",
                    subtitle = "New bank SMS will appear here for your approval. You can also scan your inbox from Settings."
                )
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize().padding(padding),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(pending.size, key = { pending[it].id }) { i ->
                    PendingCard(pending[i], vm, currency)
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PendingCard(item: PendingSms, vm: AppViewModel, currency: String) {
    val categories by vm.categories.collectAsState()

    var amountText by remember(item.id) {
        mutableStateOf("%.2f".format(item.amountMinor / 100.0).removeSuffix(".00"))
    }
    var type by remember(item.id) { mutableStateOf(item.type) }
    var categoryId by remember(item.id) { mutableStateOf(item.suggestedCategoryId) }
    var merchant by remember(item.id) { mutableStateOf(item.merchant) }
    var expanded by remember(item.id) { mutableStateOf(false) }

    val amountMinor = Format.parseAmount(amountText)
    val matchingCats = categories.filter { it.kind == type }
    val selectedCat = categories.firstOrNull { it.id == categoryId }

    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(item.sender, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(Format.dateTime(item.timestamp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(
                    Format.money(item.amountMinor, currency),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }

            Text(
                item.body,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = if (expanded) Int.MAX_VALUE else 2,
                modifier = Modifier.fillMaxWidth()
            )

            if (item.foreignCurrency != null) {
                Text(
                    "⚠️ Spent in ${item.foreignCurrency} — amount shows the ${item.foreignCurrency} value. " +
                        "Edit it to the amount charged in your currency before approving.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            if (item.type == TxnType.TRANSFER) {
                Text(
                    "⚠️ Credit card bill payment. Your card spends are already tracked one by one, " +
                        "so counting the bill too would double your expenses. Approve as Transfer to " +
                        "record it without affecting any numbers, Reject to skip it, or Edit and switch " +
                        "to Expense only if you don't track individual card transactions.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            if (!expanded) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Text(
                        when (type) {
                            TxnType.EXPENSE -> "Expense"
                            TxnType.INCOME -> "Income"
                            else -> "Transfer"
                        } +
                            " · ${merchant.ifBlank { "Unknown" }}" +
                            (if (type == TxnType.TRANSFER) ""
                            else selectedCat?.let { " · ${it.emoji} ${it.name}" } ?: " · Uncategorized"),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            vm.approvePending(item, item.amountMinor, type, categoryId, merchant.trim(), "")
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text("Approve") }
                    OutlinedButton(onClick = { expanded = true }, modifier = Modifier.weight(1f)) { Text("Edit") }
                    OutlinedButton(onClick = { vm.rejectPending(item) }, modifier = Modifier.weight(1f)) { Text("Reject") }
                }
            } else {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = type == TxnType.EXPENSE,
                        onClick = { type = TxnType.EXPENSE; categoryId = null },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 3)
                    ) { Text("Expense") }
                    SegmentedButton(
                        selected = type == TxnType.INCOME,
                        onClick = { type = TxnType.INCOME; categoryId = null },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 3)
                    ) { Text("Income") }
                    SegmentedButton(
                        selected = type == TxnType.TRANSFER,
                        onClick = { type = TxnType.TRANSFER; categoryId = null },
                        shape = SegmentedButtonDefaults.itemShape(index = 2, count = 3)
                    ) { Text("Transfer") }
                }
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text("Amount") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    isError = amountMinor == null,
                    supportingText = if (Format.isExpression(amountText) && amountMinor != null) {
                        { Text("= ${Format.money(amountMinor, currency)}") }
                    } else null,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = merchant,
                    onValueChange = { merchant = it },
                    label = { Text("Merchant") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    matchingCats.forEach { c ->
                        FilterChip(
                            selected = categoryId == c.id,
                            onClick = { categoryId = if (categoryId == c.id) null else c.id },
                            label = { Text("${c.emoji} ${c.name}") }
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            vm.approvePending(item, amountMinor!!, type, categoryId, merchant.trim(), "")
                        },
                        enabled = amountMinor != null,
                        modifier = Modifier.weight(1f)
                    ) { Text("Approve") }
                    OutlinedButton(onClick = { expanded = false }, modifier = Modifier.weight(1f)) { Text("Collapse") }
                }
            }
        }
    }
}
