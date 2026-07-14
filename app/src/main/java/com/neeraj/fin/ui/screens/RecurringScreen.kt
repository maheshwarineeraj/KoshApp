package com.neeraj.fin.ui.screens

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.navigation.NavController
import com.neeraj.fin.data.db.RecurringRule
import com.neeraj.fin.data.db.TxnType
import com.neeraj.fin.ui.AppViewModel
import com.neeraj.fin.ui.components.CategoryDot
import com.neeraj.fin.ui.components.ConfirmDialog
import com.neeraj.fin.ui.components.EmptyState
import com.neeraj.fin.ui.theme.expenseColor
import com.neeraj.fin.ui.theme.incomeColor
import com.neeraj.fin.util.Format

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecurringScreen(vm: AppViewModel, nav: NavController) {
    val rules by vm.recurringRules.collectAsState()
    val categories by vm.categories.collectAsState()
    val currency by vm.currencyCode.collectAsState()
    val catById = categories.associateBy { it.id }

    var creating by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<RecurringRule?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Recurring") },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { creating = true }) {
                Icon(Icons.Filled.Add, contentDescription = "Add recurring")
            }
        }
    ) { padding ->
        if (rules.isEmpty()) {
            Column(Modifier.fillMaxSize().padding(padding)) {
                EmptyState(
                    emoji = "🔁",
                    title = "Repeat the boring stuff",
                    subtitle = "Rent, SIPs, salary, subscriptions — set them once and Kosh posts them automatically on the day you choose each month."
                )
            }
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(padding)) {
                items(rules.size, key = { rules[it].id }) { i ->
                    val rule = rules[i]
                    val cat = catById[rule.categoryId]
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { editing = rule }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CategoryDot(cat)
                        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                            Text(rule.merchant.ifBlank { cat?.name ?: "Recurring" }, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "Every month on day ${rule.dayOfMonth}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            Format.signedMoney(rule.amountMinor, rule.type == TxnType.EXPENSE, currency),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = if (rule.type == TxnType.EXPENSE) expenseColor() else incomeColor()
                        )
                    }
                }
                item { Spacer(Modifier.height(88.dp)) }
            }
        }
    }

    if (creating) {
        RecurringDialog(
            initial = null, vm = vm,
            onSave = { vm.saveRecurringRule(it); creating = false },
            onDelete = null,
            onDismiss = { creating = false }
        )
    }
    editing?.let { rule ->
        RecurringDialog(
            initial = rule, vm = vm,
            onSave = { vm.saveRecurringRule(it); editing = null },
            onDelete = { vm.deleteRecurringRule(rule.id); editing = null },
            onDismiss = { editing = null }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RecurringDialog(
    initial: RecurringRule?,
    vm: AppViewModel,
    onSave: (RecurringRule) -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit
) {
    val categories by vm.categories.collectAsState()
    var type by remember { mutableStateOf(initial?.type ?: TxnType.EXPENSE) }
    var amountText by remember {
        mutableStateOf(initial?.amountMinor?.let { "%.0f".format(it / 100.0) } ?: "")
    }
    var merchant by remember { mutableStateOf(initial?.merchant ?: "") }
    var categoryId by remember { mutableStateOf(initial?.categoryId) }
    var dayText by remember { mutableStateOf(initial?.dayOfMonth?.toString() ?: "1") }
    var confirmDelete by remember { mutableStateOf(false) }

    val amount = Format.parseAmount(amountText)
    val day = dayText.toIntOrNull()?.takeIf { it in 1..28 }
    val matchingCats = categories.filter { it.kind == type }

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.extraLarge, tonalElevation = 6.dp) {
            Column(
                Modifier.padding(20.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    if (initial == null) "New recurring transaction" else "Edit recurring",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = type == TxnType.EXPENSE,
                        onClick = { type = TxnType.EXPENSE; categoryId = null },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                    ) { Text("Expense") }
                    SegmentedButton(
                        selected = type == TxnType.INCOME,
                        onClick = { type = TxnType.INCOME; categoryId = null },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                    ) { Text("Income") }
                }
                OutlinedTextField(
                    value = amountText, onValueChange = { amountText = it },
                    label = { Text("Amount") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    isError = amountText.isNotBlank() && amount == null,
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = merchant, onValueChange = { merchant = it },
                    label = { Text(if (type == TxnType.EXPENSE) "Payee (e.g. Landlord, Netflix)" else "Source (e.g. Salary)") },
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = dayText, onValueChange = { dayText = it },
                    label = { Text("Day of month (1–28)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    isError = day == null,
                    singleLine = true, modifier = Modifier.fillMaxWidth()
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
                Row(Modifier.fillMaxWidth()) {
                    if (onDelete != null) {
                        TextButton(onClick = { confirmDelete = true }) {
                            Text("Delete", color = MaterialTheme.colorScheme.error)
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    TextButton(
                        onClick = {
                            onSave(
                                (initial ?: RecurringRule(amountMinor = 0, type = type, categoryId = null, merchant = "", dayOfMonth = 1))
                                    .copy(
                                        amountMinor = amount!!, type = type, categoryId = categoryId,
                                        merchant = merchant.trim(), dayOfMonth = day!!
                                    )
                            )
                        },
                        enabled = amount != null && day != null
                    ) { Text("Save") }
                }
            }
        }
    }

    if (confirmDelete) {
        ConfirmDialog(
            title = "Delete recurring transaction?",
            text = "Already-posted transactions stay in your history.",
            confirmLabel = "Delete",
            onConfirm = { onDelete?.invoke() },
            onDismiss = { confirmDelete = false }
        )
    }
}
