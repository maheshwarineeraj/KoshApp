package com.neeraj.fin.ui.screens

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.neeraj.fin.ui.AppViewModel
import com.neeraj.fin.data.db.Reminder
import com.neeraj.fin.data.db.ReminderRecurrence
import com.neeraj.fin.data.db.ReminderSource
import com.neeraj.fin.data.db.Txn
import com.neeraj.fin.data.db.TxnType
import com.neeraj.fin.notify.Notifications
import com.neeraj.fin.util.Format
import com.neeraj.fin.util.Merchants
import java.time.LocalDate

/**
 * Payment reminders: things the user must do (rent transfer, manual SIP,
 * insurance premium). Suggestions arrive from repeated-payment patterns and
 * bill-due SMS; both stay disabled until accepted.
 */
@Composable
fun RemindersTab(vm: AppViewModel) {
    val reminders by vm.reminders.collectAsState()
    val txns by vm.transactions.collectAsState()
    val rules by vm.recurringRules.collectAsState()
    val currency by vm.currencyCode.collectAsState()
    val today = remember { LocalDate.now() }

    var editing by remember { mutableStateOf<Reminder?>(null) }
    var creating by remember { mutableStateOf(false) }

    val active = reminders.filter { it.enabled }
    val suggested = reminders.filter { !it.enabled }
    val dismissed by vm.dismissedPatterns.collectAsState()
    val patterns = remember(txns, rules, reminders, dismissed) {
        detectPaymentPatterns(
            txns, rules.map { it.merchant },
            reminders.map { it.title } + reminders.map { it.merchant }, today, dismissed
        )
    }

    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Button(
                onClick = { creating = true },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 12.dp)
            ) { Text("+ Add Reminder") }
        }

        if (suggested.isNotEmpty() || patterns.isNotEmpty()) {
            item {
                Text(
                    "SUGGESTED",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
            items(suggested.size, key = { "s${suggested[it].id}" }) { i ->
                val r = suggested[i]
                Card(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            "${r.title}" + (if (r.amountMinor > 0) " · ${Format.money(r.amountMinor, currency)}" else ""),
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            "From a bill-due message" + (r.dueMillis?.let { " · due ${Format.toLocalDate(it)}" } ?: ""),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (r.sourceBody.isNotBlank()) {
                            var showFull by remember(r.id) { mutableStateOf(false) }
                            Text(
                                "“${r.sourceBody}”",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = if (showFull) Int.MAX_VALUE else 2,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                modifier = Modifier.fillMaxWidth()
                                    .clickable { showFull = !showFull }
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { vm.saveReminder(r.copy(enabled = true)) }, modifier = Modifier.weight(1f)) { Text("Add") }
                            OutlinedButton(onClick = { vm.deleteReminder(r.id) }, modifier = Modifier.weight(1f)) { Text("Dismiss") }
                        }
                    }
                }
            }
            items(patterns.size, key = { "p${patterns[it].title}" }) { i ->
                val p = patterns[i]
                Card(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("${p.title} · ~${Format.money(p.amountMinor, currency)}", fontWeight = FontWeight.SemiBold)
                        Text(
                            "You pay this around day ${p.dayOfMonth} each month" +
                                (if (p.missedThisMonth) " — not seen yet this month" else ""),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (p.missedThisMonth) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = {
                                    vm.saveReminder(
                                        Reminder(
                                            title = p.title, amountMinor = p.amountMinor,
                                            recurrence = ReminderRecurrence.MONTHLY,
                                            dayOfMonth = p.dayOfMonth, merchant = p.title,
                                            source = ReminderSource.PATTERN
                                        )
                                    )
                                },
                                modifier = Modifier.weight(1f)
                            ) { Text("Add Reminder") }
                            OutlinedButton(
                                onClick = { vm.dismissPattern(p.title) },
                                modifier = Modifier.weight(1f)
                            ) { Text("Dismiss") }
                        }
                    }
                }
            }
        }

        if (active.isEmpty() && suggested.isEmpty() && patterns.isEmpty()) {
            item {
                com.neeraj.fin.ui.components.EmptyState(
                    emoji = "⏰",
                    title = "No reminders yet",
                    subtitle = "Add reminders for rent, manual SIPs or insurance premiums. Kosh will also suggest them from your payment patterns and bill-due messages."
                )
            }
        }

        if (active.isNotEmpty()) {
            item {
                Text(
                    "REMINDERS",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
            items(active.size, key = { active[it].id }) { i ->
                val r = active[i]
                val due = Notifications.isDue(r, today)
                Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row {
                            Text(
                                r.title + (if (r.amountMinor > 0) " · ${Format.money(r.amountMinor, currency)}" else ""),
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f)
                            )
                            if (due) Text("DUE", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                        }
                        Text(
                            when (r.recurrence) {
                                ReminderRecurrence.MONTHLY -> "Every month on day ${r.dayOfMonth}"
                                ReminderRecurrence.YEARLY -> "Every year, ${r.dayOfMonth}/${r.monthOfYear}"
                                else -> r.dueMillis?.let { "Once, on ${Format.toLocalDate(it)}" } ?: "Once"
                            } + (r.lastDoneKey?.let { "  ·  last done $it" } ?: ""),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (r.sourceBody.isNotBlank()) {
                            var showFull by remember(r.id) { mutableStateOf(false) }
                            Text(
                                "“${r.sourceBody}”",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = if (showFull) Int.MAX_VALUE else 2,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                modifier = Modifier.fillMaxWidth()
                                    .clickable { showFull = !showFull }
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (due) {
                                Button(onClick = { vm.markReminderDone(r) }, modifier = Modifier.weight(1f)) { Text("Mark done") }
                            }
                            OutlinedButton(onClick = { editing = r }, modifier = Modifier.weight(1f)) { Text("Edit") }
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }

    if (creating) {
        ReminderDialog(null, onSave = { vm.saveReminder(it); creating = false }, onDelete = null, onDismiss = { creating = false })
    }
    editing?.let { r ->
        ReminderDialog(
            r,
            onSave = { vm.saveReminder(it); editing = null },
            onDelete = { vm.deleteReminder(r.id); editing = null },
            onDismiss = { editing = null }
        )
    }
}

@androidx.compose.runtime.Composable
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
private fun ReminderDialog(
    initial: Reminder?,
    onSave: (Reminder) -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit
) {
    var title by remember { mutableStateOf(initial?.title ?: "") }
    var amountText by remember {
        mutableStateOf(initial?.amountMinor?.takeIf { it > 0 }?.let { "%.0f".format(it / 100.0) } ?: "")
    }
    var recurrence by remember { mutableStateOf(initial?.recurrence ?: ReminderRecurrence.MONTHLY) }
    var dayText by remember { mutableStateOf((initial?.dayOfMonth ?: 1).toString()) }
    var monthText by remember { mutableStateOf((initial?.monthOfYear ?: LocalDate.now().monthValue).toString()) }
    var dueMillis by remember { mutableStateOf(initial?.dueMillis) }
    var pickDate by remember { mutableStateOf(false) }

    val amount = if (amountText.isBlank()) 0L else Format.parseAmount(amountText) ?: -1L
    val day = dayText.toIntOrNull()?.coerceIn(1, 28)
    val month = monthText.toIntOrNull()?.coerceIn(1, 12)
    val valid = title.isNotBlank() && amount >= 0 &&
        (recurrence != ReminderRecurrence.ONCE || dueMillis != null) &&
        (recurrence == ReminderRecurrence.ONCE || day != null)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "New reminder" else "Edit reminder") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("What (e.g. Rent transfer)") }, singleLine = true)
                OutlinedTextField(
                    value = amountText, onValueChange = { amountText = it },
                    label = { Text("Amount (optional)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = recurrence == ReminderRecurrence.MONTHLY, onClick = { recurrence = ReminderRecurrence.MONTHLY }, label = { Text("Monthly") })
                    FilterChip(selected = recurrence == ReminderRecurrence.YEARLY, onClick = { recurrence = ReminderRecurrence.YEARLY }, label = { Text("Yearly") })
                    FilterChip(selected = recurrence == ReminderRecurrence.ONCE, onClick = { recurrence = ReminderRecurrence.ONCE }, label = { Text("Once") })
                }
                when (recurrence) {
                    ReminderRecurrence.MONTHLY -> OutlinedTextField(
                        value = dayText, onValueChange = { dayText = it },
                        label = { Text("Day of month (1–28)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                    ReminderRecurrence.YEARLY -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = dayText, onValueChange = { dayText = it }, label = { Text("Day") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true, modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = monthText, onValueChange = { monthText = it }, label = { Text("Month") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true, modifier = Modifier.weight(1f)
                        )
                    }
                    else -> OutlinedButton(onClick = { pickDate = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(dueMillis?.let { Format.toLocalDate(it).toString() } ?: "Pick due date")
                    }
                }
            }
        },
        confirmButton = {
            Row {
                if (onDelete != null) {
                    TextButton(onClick = onDelete) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                }
                TextButton(
                    onClick = {
                        onSave(
                            (initial ?: Reminder(title = "")).copy(
                                title = title.trim(),
                                amountMinor = amount.coerceAtLeast(0),
                                recurrence = recurrence,
                                dayOfMonth = day ?: 1,
                                monthOfYear = month ?: 1,
                                dueMillis = if (recurrence == ReminderRecurrence.ONCE) dueMillis else null,
                                enabled = true
                            )
                        )
                    },
                    enabled = valid
                ) { Text("Save") }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )

    if (pickDate) {
        val state = androidx.compose.material3.rememberDatePickerState(
            initialSelectedDateMillis = dueMillis ?: System.currentTimeMillis()
        )
        androidx.compose.material3.DatePickerDialog(
            onDismissRequest = { pickDate = false },
            confirmButton = {
                TextButton(onClick = { state.selectedDateMillis?.let { dueMillis = it }; pickDate = false }) { Text("OK") }
            }
        ) { androidx.compose.material3.DatePicker(state = state) }
    }
}

data class PaymentPattern(
    val title: String,
    val amountMinor: Long,
    val dayOfMonth: Int,
    val missedThisMonth: Boolean
)

/**
 * Repeated manual payments (incl. transfers like rent) with ~monthly cadence
 * that are not yet covered by a recurring rule or a reminder.
 */
fun detectPaymentPatterns(
    txns: List<Txn>,
    ruleMerchants: List<String>,
    reminderNames: List<String>,
    today: LocalDate,
    dismissed: Set<String> = emptySet()
): List<PaymentPattern> {
    val covered = (ruleMerchants + reminderNames).map { Merchants.key(it) }.toSet()
    return txns.asSequence()
        .filter { it.type == TxnType.EXPENSE || it.type == TxnType.TRANSFER }
        .filter { it.merchant.isNotBlank() && !it.merchant.equals("unknown", true) }
        .groupBy { Merchants.key(it.merchant) }
        .filterKeys { it.isNotBlank() && it !in covered && it !in dismissed.map { d -> Merchants.key(d) } }
        .mapNotNull { (_, group) ->
            if (group.size < 3) return@mapNotNull null
            val sorted = group.sortedBy { it.timestamp }
            val gaps = sorted.zipWithNext { a, b -> (b.timestamp - a.timestamp) / 86_400_000L }
            if (gaps.isEmpty() || gaps.any { it < 21 || it > 40 }) return@mapNotNull null
            val amounts = sorted.map { it.amountMinor }.sorted()
            val days = sorted.map { Format.toLocalDate(it.timestamp).dayOfMonth }.sorted()
            val day = days[days.size / 2].coerceIn(1, 28)
            val thisMonthSeen = sorted.any {
                val d = Format.toLocalDate(it.timestamp)
                d.year == today.year && d.monthValue == today.monthValue
            }
            PaymentPattern(
                title = sorted.last().merchant,
                amountMinor = amounts[amounts.size / 2],
                dayOfMonth = day,
                missedThisMonth = !thisMonthSeen && today.dayOfMonth > day + 2
            )
        }
        .sortedByDescending { it.amountMinor }
        .take(3)
}
