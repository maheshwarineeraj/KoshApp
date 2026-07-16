package com.neeraj.fin.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
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
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.neeraj.fin.data.db.Txn
import com.neeraj.fin.data.db.TxnType
import com.neeraj.fin.ui.AppViewModel
import com.neeraj.fin.ui.components.ConfirmDialog
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.neeraj.fin.util.Format
import com.neeraj.fin.util.ReceiptScanner
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EditTxnScreen(vm: AppViewModel, nav: NavController, txnId: Long) {
    val txns by vm.transactions.collectAsState()
    val categories by vm.categories.collectAsState()
    val currency by vm.currencyCode.collectAsState()
    val eventBudgets by vm.eventBudgets.collectAsState()
    val goals by vm.goals.collectAsState()
    val existing = txns.firstOrNull { it.id == txnId }

    var amountText by remember(existing?.id) {
        mutableStateOf(existing?.let { "%.2f".format(it.amountMinor / 100.0).removeSuffix(".00") } ?: "")
    }
    var type by remember(existing?.id) { mutableStateOf(existing?.type ?: TxnType.EXPENSE) }
    var categoryId by remember(existing?.id) { mutableStateOf(existing?.categoryId) }
    var catTouched by remember(existing?.id) { mutableStateOf(existing != null) }
    var merchant by remember(existing?.id) { mutableStateOf(existing?.merchant ?: "") }
    var note by remember(existing?.id) { mutableStateOf(existing?.note ?: "") }
    var timestamp by remember(existing?.id) { mutableStateOf(existing?.timestamp ?: System.currentTimeMillis()) }
    var eventBudgetId by remember(existing?.id) { mutableStateOf(existing?.eventBudgetId) }
    var eventTouched by remember(existing?.id) { mutableStateOf(existing != null) }
    var goalId by remember(existing?.id) { mutableStateOf(existing?.goalId) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }
    var showSplit by remember { mutableStateOf(false) }

    // Receipt OCR: pick a photo, extract amount + merchant on-device.
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var scanning by remember { mutableStateOf(false) }
    val receiptPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            scanning = true
            scope.launch {
                val result = runCatching { ReceiptScanner.scan(context, uri) }.getOrNull()
                scanning = false
                if (result?.amountMinor != null) {
                    amountText = "%.2f".format(result.amountMinor / 100.0).removeSuffix(".00")
                }
                result?.merchant?.let { if (merchant.isBlank()) merchant = it }
            }
        }
    }

    // ML Kit document scanner: opens the camera with edge detection and
    // auto-crop; falls back to the photo picker where Play services lacks it.
    val docScanLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
                .fromActivityResultIntent(result.data)
                ?.pages?.firstOrNull()?.imageUri?.let { uri ->
                    scanning = true
                    scope.launch {
                        val r = runCatching { ReceiptScanner.scan(context, uri) }.getOrNull()
                        scanning = false
                        if (r?.amountMinor != null) {
                            amountText = "%.2f".format(r.amountMinor / 100.0).removeSuffix(".00")
                        }
                        r?.merchant?.let { if (merchant.isBlank()) merchant = it }
                    }
                }
        }
    }
    fun startReceiptScan() {
        val options = com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions.Builder()
            .setGalleryImportAllowed(true)
            .setPageLimit(1)
            .setResultFormats(com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions.RESULT_FORMAT_JPEG)
            .setScannerMode(com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions.SCANNER_MODE_BASE)
            .build()
        val activity = context as? android.app.Activity
        if (activity != null) {
            com.google.mlkit.vision.documentscanner.GmsDocumentScanning.getClient(options)
                .getStartScanIntent(activity)
                .addOnSuccessListener { sender ->
                    docScanLauncher.launch(androidx.activity.result.IntentSenderRequest.Builder(sender).build())
                }
                .addOnFailureListener {
                    receiptPicker.launch(
                        androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                }
        } else {
            receiptPicker.launch(
                androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
        }
    }

    val amountMinor = Format.parseAmount(amountText)
    val matchingCats = categories.filter { it.kind == type }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (existing == null) "Add transaction" else "Edit transaction") },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (existing == null) {
                        TextButton(onClick = { startReceiptScan() }) {
                            Text(if (scanning) "Scanning…" else "📷 Scan receipt")
                        }
                    }
                    if (existing != null && existing.type != TxnType.TRANSFER) {
                        TextButton(onClick = { showSplit = true }) { Text("Split") }
                    }
                    if (existing != null) {
                        IconButton(onClick = { showDelete = true }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete")
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                // Keep the focused field visible above the keyboard.
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (existing == null) {
                // Habit-aware prefill: your own repeated (merchant, amount) at this
                // time of day, offered as a one-tap chip. Purely local statistics.
                val habit = remember(txns) { habitSuggestion(txns) }
                if (habit != null && amountText.isBlank() && merchant.isBlank()) {
                    androidx.compose.material3.AssistChip(
                        onClick = {
                            amountText = "%.2f".format(habit.amountMinor / 100.0).removeSuffix(".00")
                            merchant = habit.merchant
                            type = habit.type
                            categoryId = habit.categoryId
                        },
                        label = {
                            Text(
                                "Usual around now: ${Format.money(habit.amountMinor, currency)}" +
                                    (habit.merchant.takeIf { it.isNotBlank() }?.let { " · $it" } ?: "") +
                                    " — tap to fill"
                            )
                        }
                    )
                }
            }

            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = type == TxnType.EXPENSE,
                    onClick = { type = TxnType.EXPENSE; if (categoryId != null && categories.firstOrNull { it.id == categoryId }?.kind != TxnType.EXPENSE) categoryId = null },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 3)
                ) { Text("Expense") }
                SegmentedButton(
                    selected = type == TxnType.INCOME,
                    onClick = { type = TxnType.INCOME; if (categoryId != null && categories.firstOrNull { it.id == categoryId }?.kind != TxnType.INCOME) categoryId = null },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 3)
                ) { Text("Income") }
                SegmentedButton(
                    selected = type == TxnType.TRANSFER,
                    onClick = { type = TxnType.TRANSFER; categoryId = null },
                    shape = SegmentedButtonDefaults.itemShape(index = 2, count = 3)
                ) { Text("Transfer") }
            }

            if (type == TxnType.TRANSFER) {
                Text(
                    "Transfers move money between your own accounts and are excluded from income and expense stats.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            OutlinedTextField(
                value = amountText,
                onValueChange = { amountText = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Amount") },
                prefix = { Text(Format.money(0, currency).first().toString()) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                isError = amountText.isNotBlank() && amountMinor == null,
                supportingText = if (Format.isExpression(amountText) && amountMinor != null) {
                    { Text("= ${Format.money(amountMinor, currency)}") }
                } else null,
                singleLine = true
            )

            OutlinedTextField(
                value = merchant,
                onValueChange = { merchant = it },
                modifier = Modifier.fillMaxWidth(),
                label = {
                    Text(
                        when (type) {
                            TxnType.EXPENSE -> "Merchant / Payee"
                            TxnType.INCOME -> "Source"
                            else -> "Description (e.g. Savings → FD)"
                        }
                    )
                },
                singleLine = true
            )

            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Note (optional)") }
            )

            if (type != TxnType.TRANSFER) {
                // Top-3 category suggestions from your history and keywords;
                // "All" opens the full picker. Custom categories scale cleanly.
                Column {
                    Text("Category", style = MaterialTheme.typography.labelLarge)
                    val strong = remember(merchant, note, type, txns, categories) {
                        strongCategorySuggestions(merchant, note, matchingCats, txns)
                    }
                    val suggested = remember(merchant, note, type, txns, categories) {
                        (strong + suggestCategories(merchant, note, matchingCats, txns)).distinctBy { it.id }
                    }
                    // Auto-pick the best history/keyword match until the user
                    // chooses a category themselves.
                    androidx.compose.runtime.LaunchedEffect(strong, type) {
                        if (!catTouched && strong.isNotEmpty()) categoryId = strong.first().id
                    }
                    var showCatPicker by remember { mutableStateOf(false) }
                    val selectedCat = categories.firstOrNull { it.id == categoryId }
                    val chips = (listOfNotNull(selectedCat) + suggested)
                        .distinctBy { it.id }
                        .filter { it.kind == type }
                        .take(3)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        chips.forEach { c ->
                            FilterChip(
                                selected = categoryId == c.id,
                                onClick = {
                                    catTouched = true
                                    categoryId = if (categoryId == c.id) null else c.id
                                },
                                label = { Text("${c.emoji} ${c.name}") }
                            )
                        }
                        FilterChip(
                            selected = false,
                            onClick = { showCatPicker = true },
                            label = { Text("All ▾") }
                        )
                    }
                    if (showCatPicker) {
                        androidx.compose.material3.AlertDialog(
                            onDismissRequest = { showCatPicker = false },
                            title = { Text("Category") },
                            text = {
                                Column(Modifier.verticalScroll(rememberScrollState())) {
                                    matchingCats.forEach { c ->
                                        Text(
                                            "${c.emoji} ${c.name}",
                                            style = MaterialTheme.typography.bodyLarge,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    catTouched = true
                                                    categoryId = c.id; showCatPicker = false
                                                }
                                                .padding(vertical = 10.dp)
                                        )
                                    }
                                }
                            },
                            confirmButton = {
                                TextButton(onClick = { showCatPicker = false; nav.navigate("categories") }) {
                                    Text("Manage categories")
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = { showCatPicker = false }) { Text("Close") }
                            }
                        )
                    }
                }
            }


            if (type == TxnType.EXPENSE && eventBudgets.isNotEmpty()) {
                // Trip mode: while the date falls inside an event's window,
                // pre-tag the expense to it until the user chooses otherwise.
                androidx.compose.runtime.LaunchedEffect(timestamp, type, eventBudgets) {
                    if (!eventTouched && eventBudgetId == null) {
                        eventBudgets.firstOrNull { b ->
                            b.startMillis != null && b.endMillis != null &&
                                timestamp >= b.startMillis && timestamp < b.endMillis + 86_400_000L
                        }?.let { eventBudgetId = it.id }
                    }
                }
                Column {
                    Text("Part of a budget? (optional)", style = MaterialTheme.typography.labelLarge)
                    var showEventPicker by remember { mutableStateOf(false) }
                    val active = eventBudgets.filter { b ->
                        b.startMillis != null && b.endMillis != null &&
                            timestamp >= b.startMillis && timestamp < b.endMillis + 86_400_000L
                    }
                    val eventChips = (listOfNotNull(eventBudgets.firstOrNull { it.id == eventBudgetId }) +
                        active + eventBudgets.sortedByDescending { it.createdAt })
                        .distinctBy { it.id }.take(3)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        eventChips.forEach { b ->
                            FilterChip(
                                selected = eventBudgetId == b.id,
                                onClick = {
                                    eventTouched = true
                                    eventBudgetId = if (eventBudgetId == b.id) null else b.id
                                },
                                label = { Text("${b.emoji} ${b.name}") }
                            )
                        }
                        if (eventBudgets.size > eventChips.size) {
                            FilterChip(selected = false, onClick = { showEventPicker = true }, label = { Text("All ▾") })
                        }
                    }
                    if (showEventPicker) {
                        androidx.compose.material3.AlertDialog(
                            onDismissRequest = { showEventPicker = false },
                            title = { Text("Event budget") },
                            text = {
                                Column(Modifier.verticalScroll(rememberScrollState())) {
                                    eventBudgets.forEach { b ->
                                        Text(
                                            "${b.emoji} ${b.name}",
                                            style = MaterialTheme.typography.bodyLarge,
                                            modifier = Modifier.fillMaxWidth()
                                                .clickable {
                                                    eventTouched = true
                                                    eventBudgetId = b.id; showEventPicker = false
                                                }
                                                .padding(vertical = 10.dp)
                                        )
                                    }
                                }
                            },
                            confirmButton = {
                                TextButton(onClick = { showEventPicker = false }) { Text("Close") }
                            }
                        )
                    }
                }
            }

            if (type == TxnType.INCOME && goals.isNotEmpty()) {
                Column {
                    Text("Put towards a goal? (optional)", style = MaterialTheme.typography.labelLarge)
                    var showGoalPicker by remember { mutableStateOf(false) }
                    val goalChips = (listOfNotNull(goals.firstOrNull { it.id == goalId }) +
                        goals.sortedBy { it.deadlineMillis ?: Long.MAX_VALUE })
                        .distinctBy { it.id }.take(3)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        goalChips.forEach { g ->
                            FilterChip(
                                selected = goalId == g.id,
                                onClick = { goalId = if (goalId == g.id) null else g.id },
                                label = { Text("${g.emoji} ${g.name}") }
                            )
                        }
                        if (goals.size > goalChips.size) {
                            FilterChip(selected = false, onClick = { showGoalPicker = true }, label = { Text("All ▾") })
                        }
                    }
                    if (showGoalPicker) {
                        androidx.compose.material3.AlertDialog(
                            onDismissRequest = { showGoalPicker = false },
                            title = { Text("Goal") },
                            text = {
                                Column(Modifier.verticalScroll(rememberScrollState())) {
                                    goals.forEach { g ->
                                        Text(
                                            "${g.emoji} ${g.name}",
                                            style = MaterialTheme.typography.bodyLarge,
                                            modifier = Modifier.fillMaxWidth()
                                                .clickable { goalId = g.id; showGoalPicker = false }
                                                .padding(vertical = 10.dp)
                                        )
                                    }
                                }
                            },
                            confirmButton = {
                                TextButton(onClick = { showGoalPicker = false }) { Text("Close") }
                            }
                        )
                    }
                }
            }

            OutlinedButton(onClick = { showDatePicker = true }, modifier = Modifier.fillMaxWidth()) {
                Text(Format.dateTime(timestamp))
            }

            Button(
                onClick = {
                    vm.saveTxn(
                        (existing ?: Txn(amountMinor = 0, type = type, categoryId = null, merchant = "", timestamp = timestamp)).copy(
                            amountMinor = amountMinor!!,
                            type = type,
                            categoryId = if (type == TxnType.TRANSFER) null else categoryId,
                            merchant = merchant.trim(),
                            note = note.trim(),
                            timestamp = timestamp,
                            eventBudgetId = if (type == TxnType.EXPENSE) eventBudgetId else null,
                            goalId = if (type == TxnType.INCOME) goalId else null
                        )
                    )
                    nav.popBackStack()
                },
                enabled = amountMinor != null,
                modifier = Modifier.fillMaxWidth()
            ) { Text(if (existing == null) "Add" else "Save") }
        }
    }

    if (showDatePicker) {
        val state = rememberDatePickerState(initialSelectedDateMillis = timestamp)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { selected ->
                        // Keep the original time of day, change the date
                        val date = Instant.ofEpochMilli(selected).atZone(ZoneId.of("UTC")).toLocalDate()
                        val time = Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()).toLocalTime()
                        timestamp = date.atTime(time).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                    }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Cancel") } }
        ) { DatePicker(state = state) }
    }

    if (showSplit && existing != null) {
        SplitDialog(
            original = existing,
            categories = categories.filter { it.kind == existing.type },
            currency = currency,
            onSplit = { parts ->
                vm.splitTxn(existing, parts)
                showSplit = false
                nav.popBackStack()
            },
            onDismiss = { showSplit = false }
        )
    }

    if (showDelete && existing != null) {
        ConfirmDialog(
            title = "Delete transaction?",
            text = "This cannot be undone.",
            confirmLabel = "Delete",
            onConfirm = {
                vm.deleteTxn(existing)
                nav.popBackStack()
            },
            onDismiss = { showDelete = false }
        )
    }
}

/**
 * Split one transaction into 2+ parts, each with its own amount and category.
 * The part amounts must add up to the original amount.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SplitDialog(
    original: Txn,
    categories: List<com.neeraj.fin.data.db.Category>,
    currency: String,
    onSplit: (List<Pair<Long, Long?>>) -> Unit,
    onDismiss: () -> Unit
) {
    data class Part(var amountText: String, var categoryId: Long?)

    val parts = remember {
        androidx.compose.runtime.mutableStateListOf(
            Part("%.2f".format(original.amountMinor / 100.0).removeSuffix(".00"), original.categoryId),
            Part("", null)
        )
    }
    val amounts = parts.map { Format.parseAmount(it.amountText) }
    val total = amounts.filterNotNull().sum()
    val valid = amounts.all { it != null } && total == original.amountMinor && parts.size >= 2
    val remaining = original.amountMinor - total

    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        androidx.compose.material3.Surface(shape = MaterialTheme.shapes.extraLarge, tonalElevation = 6.dp) {
            Column(
                Modifier.padding(20.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                        "Split ${Format.money(original.amountMinor, currency)}",
                        style = MaterialTheme.typography.titleLarge
                    )
                    parts.forEachIndexed { index, part ->
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedTextField(
                                value = part.amountText,
                                onValueChange = { parts[index] = part.copy(amountText = it) },
                                label = { Text("Part ${index + 1} amount") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                categories.forEach { c ->
                                    FilterChip(
                                        selected = part.categoryId == c.id,
                                        onClick = {
                                            parts[index] = part.copy(
                                                categoryId = if (part.categoryId == c.id) null else c.id
                                            )
                                        },
                                        label = { Text("${c.emoji} ${c.name}") }
                                    )
                                }
                            }
                        }
                    }
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        TextButton(onClick = { parts.add(Part("", null)) }) { Text("+ Add part") }
                        Spacer(Modifier.weight(1f))
                        Text(
                            if (remaining == 0L) "Adds up ✓"
                            else "${if (remaining > 0) "Remaining" else "Over by"} ${Format.money(kotlin.math.abs(remaining), currency)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (remaining == 0L) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                        )
                    }
                    Row(Modifier.fillMaxWidth()) {
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = onDismiss) { Text("Cancel") }
                        TextButton(
                            onClick = { onSplit(parts.map { Format.parseAmount(it.amountText)!! to it.categoryId }) },
                            enabled = valid
                        ) { Text("Split") }
                    }
            }
        }
    }
}

private data class HabitSuggestion(val merchant: String, val amountMinor: Long, val categoryId: Long?, val type: String)

/**
 * Most frequent (merchant, amount) among your last 60 days of transactions
 * that happened within ±90 minutes of the current time of day; needs at
 * least three occurrences to qualify.
 */
private fun habitSuggestion(txns: List<com.neeraj.fin.data.db.Txn>): HabitSuggestion? {
    val now = java.util.Calendar.getInstance()
    val nowMin = now.get(java.util.Calendar.HOUR_OF_DAY) * 60 + now.get(java.util.Calendar.MINUTE)
    val cutoff = System.currentTimeMillis() - 60L * 24 * 60 * 60 * 1000
    val cal = java.util.Calendar.getInstance()
    return txns.asSequence()
        .filter { it.timestamp >= cutoff && it.type != com.neeraj.fin.data.db.TxnType.TRANSFER }
        .filter {
            cal.timeInMillis = it.timestamp
            val m = cal.get(java.util.Calendar.HOUR_OF_DAY) * 60 + cal.get(java.util.Calendar.MINUTE)
            val diff = kotlin.math.abs(m - nowMin)
            kotlin.math.min(diff, 1440 - diff) <= 90
        }
        .groupBy { Triple(it.merchant.lowercase(), it.amountMinor, it.type) }
        .filterValues { it.size >= 3 }
        .maxByOrNull { it.value.size }
        ?.value?.first()
        ?.let { HabitSuggestion(it.merchant, it.amountMinor, it.categoryId, it.type) }
}



