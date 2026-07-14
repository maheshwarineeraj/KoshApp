package com.neeraj.fin.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.navigation.NavController
import com.neeraj.fin.data.db.Category
import com.neeraj.fin.data.db.Goal
import com.neeraj.fin.data.db.TxnType
import com.neeraj.fin.ui.AppViewModel
import com.neeraj.fin.ui.components.CategoryDot
import com.neeraj.fin.ui.components.ConfirmDialog
import com.neeraj.fin.ui.components.EmptyState
import com.neeraj.fin.ui.components.ProgressBar
import com.neeraj.fin.ui.theme.expenseColor
import com.neeraj.fin.ui.theme.incomeColor
import com.neeraj.fin.util.Format
import com.neeraj.fin.util.PeriodKind
import com.neeraj.fin.util.Periods
import java.time.LocalDate
import java.time.temporal.ChronoUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BudgetsScreen(vm: AppViewModel, nav: NavController) {
    var tab by remember { mutableStateOf(0) }
    var creatingGoal by remember { mutableStateOf(false) }
    var creatingEvent by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Budgets & Goals") },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            when (tab) {
                1 -> FloatingActionButton(onClick = { creatingEvent = true }) {
                    Icon(Icons.Filled.Add, contentDescription = "Add event budget")
                }
                2 -> FloatingActionButton(onClick = { creatingGoal = true }) {
                    Icon(Icons.Filled.Add, contentDescription = "Add goal")
                }
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            TabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Monthly") })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Events") })
                Tab(selected = tab == 2, onClick = { tab = 2 }, text = { Text("Goals") })
            }
            when (tab) {
                0 -> CategoryBudgetsTab(vm)
                1 -> EventBudgetsTab(vm, creatingEvent, onDialogHandled = { creatingEvent = false })
                else -> GoalsTab(vm, creatingGoal, onDialogHandled = { creatingGoal = false })
            }
        }
    }
}

@Composable
private fun EventBudgetsTab(vm: AppViewModel, creating: Boolean, onDialogHandled: () -> Unit) {
    val eventBudgets by vm.eventBudgets.collectAsState()
    val txns by vm.transactions.collectAsState()
    val currency by vm.currencyCode.collectAsState()

    var editing by remember { mutableStateOf<com.neeraj.fin.data.db.EventBudget?>(null) }

    if (eventBudgets.isEmpty()) {
        EmptyState(
            emoji = "🧳",
            title = "Budget for an event",
            subtitle = "Planning a trip or a celebration? Set aside an amount, then tag expenses to it when you add or edit them — Kosh shows planned vs actually spent. Tap + to create one."
        )
    } else {
        LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { Spacer(Modifier.height(4.dp)) }
            items(eventBudgets.size, key = { eventBudgets[it].id }) { i ->
                val budget = eventBudgets[i]
                val tagged = txns.filter { it.eventBudgetId == budget.id && it.type == TxnType.EXPENSE }
                val spent = tagged.sumOf { it.amountMinor }
                val over = spent > budget.plannedMinor
                Card(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp).clickable { editing = budget }
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(budget.emoji, style = MaterialTheme.typography.headlineSmall)
                            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                                Text(budget.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                Text(
                                    "${tagged.size} expense${if (tagged.size == 1) "" else "s"} tagged",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    Format.money(spent, currency),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (over) expenseColor() else MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    "of ${Format.money(budget.plannedMinor, currency)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        ProgressBar(
                            fraction = if (budget.plannedMinor > 0) spent.toFloat() / budget.plannedMinor else 0f,
                            color = if (over) expenseColor() else MaterialTheme.colorScheme.primary
                        )
                        Text(
                            if (over) "Over budget by ${Format.money(spent - budget.plannedMinor, currency)}"
                            else "${Format.money(budget.plannedMinor - spent, currency)} left",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (over) expenseColor() else incomeColor()
                        )
                    }
                }
            }
            item {
                Text(
                    "Tag expenses to a budget from the add/edit transaction screen.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
            item { Spacer(Modifier.height(88.dp)) }
        }
    }

    if (creating) {
        EventBudgetDialog(
            initial = null,
            onSave = { vm.saveEventBudget(it); onDialogHandled() },
            onDelete = null,
            onDismiss = onDialogHandled
        )
    }
    editing?.let { budget ->
        EventBudgetDialog(
            initial = budget,
            onSave = { vm.saveEventBudget(it); editing = null },
            onDelete = { vm.deleteEventBudget(budget.id); editing = null },
            onDismiss = { editing = null }
        )
    }
}

private val eventEmojis = listOf("🧳", "✈️", "🏖️", "💍", "🎉", "🎂", "🏠", "🛍️", "🎄", "🪔", "👶", "🎓")

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EventBudgetDialog(
    initial: com.neeraj.fin.data.db.EventBudget?,
    onSave: (com.neeraj.fin.data.db.EventBudget) -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var emoji by remember { mutableStateOf(initial?.emoji ?: "🧳") }
    var plannedText by remember {
        mutableStateOf(initial?.plannedMinor?.let { "%.0f".format(it / 100.0) } ?: "")
    }
    var confirmDelete by remember { mutableStateOf(false) }
    val planned = Format.parseAmount(plannedText)

    Dialog(onDismissRequest = onDismiss) {
        androidx.compose.material3.Surface(shape = MaterialTheme.shapes.extraLarge, tonalElevation = 6.dp) {
            Column(
                Modifier.padding(20.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    if (initial == null) "New event budget" else "Edit event budget",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text("Event (e.g. Goa trip, Diwali)") },
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    eventEmojis.forEach { e ->
                        Text(
                            e,
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(if (emoji == e) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                                .clickable { emoji = e }
                                .padding(6.dp),
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }
                OutlinedTextField(
                    value = plannedText, onValueChange = { plannedText = it },
                    label = { Text("Planned amount") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    isError = plannedText.isNotBlank() && planned == null,
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
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
                                (initial ?: com.neeraj.fin.data.db.EventBudget(name = "", emoji = "", plannedMinor = 0))
                                    .copy(name = name.trim(), emoji = emoji, plannedMinor = planned!!)
                            )
                        },
                        enabled = name.isNotBlank() && planned != null
                    ) { Text("Save") }
                }
            }
        }
    }

    if (confirmDelete) {
        ConfirmDialog(
            title = "Delete \"${initial?.name}\"?",
            text = "Tagged expenses stay in your history — they just lose the tag.",
            confirmLabel = "Delete",
            onConfirm = { onDelete?.invoke() },
            onDismiss = { confirmDelete = false }
        )
    }
}

@Composable
private fun CategoryBudgetsTab(vm: AppViewModel) {
    val categories by vm.categories.collectAsState()
    val budgets by vm.budgets.collectAsState()
    val currency by vm.currencyCode.collectAsState()

    val month = remember { Periods.rangeFor(PeriodKind.MONTH, LocalDate.now()) }
    val monthTxns by remember(month) { vm.txnsBetween(month.startMillis, month.endMillis) }
        .collectAsState(initial = emptyList())

    val limits = budgets.associate { it.categoryId to it.monthlyLimitMinor }
    var editing by remember { mutableStateOf<Category?>(null) }

    val expenseCats = categories.filter { it.kind == TxnType.EXPENSE }
    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        item {
            Text(
                "Tap a category to set its monthly limit. Progress tracks ${month.label}.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp)
            )
        }
        items(expenseCats.size, key = { expenseCats[it].id }) { i ->
            val cat = expenseCats[i]
            val limit = limits[cat.id]
            val spent = monthTxns.filter { it.type == TxnType.EXPENSE && it.categoryId == cat.id }
                .sumOf { it.amountMinor }
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { editing = cat }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CategoryDot(cat)
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Row {
                        Text(cat.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        Text(
                            if (limit != null) "${Format.compact(spent, currency)} / ${Format.compact(limit, currency)}"
                            else "No budget",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (limit != null && spent > limit) expenseColor()
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (limit != null) {
                        Spacer(Modifier.height(6.dp))
                        ProgressBar(
                            fraction = spent.toFloat() / limit,
                            color = if (spent > limit) expenseColor() else Color(cat.color)
                        )
                    }
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }

    editing?.let { cat ->
        var text by remember(cat.id) {
            mutableStateOf(limits[cat.id]?.let { "%.0f".format(it / 100.0) } ?: "")
        }
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text("${cat.emoji} ${cat.name}") },
            text = {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Monthly limit (leave empty to remove)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.setBudget(cat.id, Format.parseAmount(text))
                    editing = null
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { editing = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun GoalsTab(vm: AppViewModel, creating: Boolean, onDialogHandled: () -> Unit) {
    val goals by vm.goals.collectAsState()
    val contributions by vm.goalContributions.collectAsState()
    val txns by vm.transactions.collectAsState()
    val currency by vm.currencyCode.collectAsState()

    // Saved = manual contributions + income transactions tagged to the goal
    val contributed = contributions.groupBy { it.goalId }.mapValues { (_, cs) -> cs.sumOf { it.amountMinor } }
    val taggedIncome = txns.filter { it.type == TxnType.INCOME && it.goalId != null }
        .groupBy { it.goalId!! }.mapValues { (_, ts) -> ts.sumOf { it.amountMinor } }
    val savedByGoal = (contributed.keys + taggedIncome.keys).associateWith { id ->
        (contributed[id] ?: 0L) + (taggedIncome[id] ?: 0L)
    }
    var editing by remember { mutableStateOf<Goal?>(null) }
    var contributingTo by remember { mutableStateOf<Goal?>(null) }

    if (goals.isEmpty()) {
        EmptyState(
            emoji = "🎯",
            title = "Save for something",
            subtitle = "A trip, a wedding, an emergency fund — set a target and log money as you put it aside. Tap + to create your first goal."
        )
    } else {
        LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { Spacer(Modifier.height(4.dp)) }
            items(goals.size, key = { goals[it].id }) { i ->
                val goal = goals[i]
                val saved = savedByGoal[goal.id] ?: 0L
                GoalCard(
                    goal, saved, currency,
                    taggedIncomeMinor = taggedIncome[goal.id] ?: 0L,
                    onAddMoney = { contributingTo = goal },
                    onEdit = { editing = goal }
                )
            }
            item {
                Text(
                    "Tip: you can also tag income to a goal from the add/edit transaction screen — it counts towards the goal automatically.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
            item { Spacer(Modifier.height(88.dp)) }
        }
    }

    if (creating) {
        GoalDialog(
            initial = null,
            onSave = { vm.saveGoal(it); onDialogHandled() },
            onDelete = null,
            onDismiss = onDialogHandled
        )
    }
    editing?.let { goal ->
        GoalDialog(
            initial = goal,
            onSave = { vm.saveGoal(it); editing = null },
            onDelete = { vm.deleteGoal(goal.id); editing = null },
            onDismiss = { editing = null }
        )
    }
    contributingTo?.let { goal ->
        var amountText by remember(goal.id) { mutableStateOf("") }
        var note by remember(goal.id) { mutableStateOf("") }
        val amount = Format.parseAmount(amountText)
        AlertDialog(
            onDismissRequest = { contributingTo = null },
            title = { Text("${goal.emoji} ${goal.name}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = amountText, onValueChange = { amountText = it },
                        label = { Text("Amount saved") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = note, onValueChange = { note = it },
                        label = { Text("Note (optional)") },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.addContribution(goal.id, amount!!, note.trim())
                        contributingTo = null
                    },
                    enabled = amount != null
                ) { Text("Add") }
            },
            dismissButton = { TextButton(onClick = { contributingTo = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun GoalCard(goal: Goal, saved: Long, currency: String, taggedIncomeMinor: Long = 0L, onAddMoney: () -> Unit, onEdit: () -> Unit) {
    val fraction = if (goal.targetMinor > 0) saved.toFloat() / goal.targetMinor else 0f
    val done = saved >= goal.targetMinor
    Card(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp).clickable(onClick = onEdit)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(goal.emoji, style = MaterialTheme.typography.headlineSmall)
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(goal.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    val deadlineText = goal.deadlineMillis?.let {
                        val days = ChronoUnit.DAYS.between(LocalDate.now(), Format.toLocalDate(it))
                        when {
                            done -> "Goal reached 🎉"
                            days < 0 -> "Deadline passed"
                            days == 0L -> "Due today"
                            else -> "$days days left"
                        }
                    } ?: if (done) "Goal reached 🎉" else null
                    if (deadlineText != null) {
                        Text(
                            deadlineText,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (done) incomeColor() else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        Format.money(saved, currency),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (done) incomeColor() else MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        "of ${Format.money(goal.targetMinor, currency)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (taggedIncomeMinor > 0) {
                        Text(
                            "incl. ${Format.compact(taggedIncomeMinor, currency)} tagged income",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            ProgressBar(
                fraction = fraction,
                color = if (done) incomeColor() else MaterialTheme.colorScheme.primary
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onAddMoney, modifier = Modifier.weight(1f)) { Text("Add money") }
                OutlinedButton(onClick = onEdit, modifier = Modifier.weight(1f)) { Text("Edit") }
            }
        }
    }
}

private val goalEmojis = listOf("🎯", "✈️", "🏖️", "💍", "🎉", "🏠", "🚗", "🎓", "🛡️", "💻", "🎸", "👶")

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun GoalDialog(
    initial: Goal?,
    onSave: (Goal) -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var emoji by remember { mutableStateOf(initial?.emoji ?: "🎯") }
    var targetText by remember {
        mutableStateOf(initial?.targetMinor?.let { "%.0f".format(it / 100.0) } ?: "")
    }
    var deadline by remember { mutableStateOf(initial?.deadlineMillis) }
    var showDatePicker by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    val target = Format.parseAmount(targetText)

    Dialog(onDismissRequest = onDismiss) {
        androidx.compose.material3.Surface(shape = MaterialTheme.shapes.extraLarge, tonalElevation = 6.dp) {
            Column(
                Modifier.padding(20.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    if (initial == null) "New goal" else "Edit goal",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text("What are you saving for?") },
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    goalEmojis.forEach { e ->
                        Text(
                            e,
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(if (emoji == e) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                                .clickable { emoji = e }
                                .padding(6.dp),
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }
                OutlinedTextField(
                    value = targetText, onValueChange = { targetText = it },
                    label = { Text("Target amount") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    isError = targetText.isNotBlank() && target == null,
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                OutlinedButton(onClick = { showDatePicker = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(deadline?.let { "Target date: ${Format.dayLabel(Format.toLocalDate(it))}" } ?: "Set target date (optional)")
                }
                if (deadline != null) {
                    TextButton(onClick = { deadline = null }) { Text("Clear target date") }
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
                                (initial ?: Goal(name = "", emoji = "", targetMinor = 0))
                                    .copy(name = name.trim(), emoji = emoji, targetMinor = target!!, deadlineMillis = deadline)
                            )
                        },
                        enabled = name.isNotBlank() && target != null
                    ) { Text("Save") }
                }
            }
        }
    }

    if (showDatePicker) {
        val state = androidx.compose.material3.rememberDatePickerState(initialSelectedDateMillis = deadline)
        androidx.compose.material3.DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    deadline = state.selectedDateMillis
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Cancel") } }
        ) { androidx.compose.material3.DatePicker(state = state) }
    }

    if (confirmDelete) {
        ConfirmDialog(
            title = "Delete \"${initial?.name}\"?",
            text = "Its contribution history will also be deleted.",
            confirmLabel = "Delete",
            onConfirm = { onDelete?.invoke() },
            onDismiss = { confirmDelete = false }
        )
    }
}
