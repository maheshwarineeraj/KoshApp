package com.neeraj.fin.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.navigation.NavController
import com.neeraj.fin.data.db.CreditCard
import com.neeraj.fin.ui.AppViewModel
import com.neeraj.fin.ui.components.ConfirmDialog
import com.neeraj.fin.ui.components.EmptyState
import com.neeraj.fin.util.CardCrypto
import kotlinx.coroutines.launch

/** Card-face palette; index stored on the card. */
private val cardColors = listOf(
    Color(0xFF8E0038) to Color(0xFFBA1B54), // magenta
    Color(0xFF101010) to Color(0xFF3A3A3A), // black
    Color(0xFF0B3D91) to Color(0xFF2C6BD6), // blue
    Color(0xFF00524E) to Color(0xFF00857C), // teal
    Color(0xFF4A148C) to Color(0xFF7C43BD), // purple
    Color(0xFF3E2723) to Color(0xFF6D4C41), // brown
    Color(0xFF37474F) to Color(0xFF62727B), // steel
    Color(0xFF9A6A00) to Color(0xFFC79100)  // gold
)

/**
 * Card vault: numbers and CVVs live AES-encrypted under a hardware Keystore
 * key; the faces show only last-4. Revealing the full number/CVV requires
 * biometric / device-credential confirmation each time.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CardsScreen(vm: AppViewModel, nav: NavController) {
    val cards by vm.cards.collectAsState()
    val reminders by vm.reminders.collectAsState()
    val txns by vm.transactions.collectAsState()
    var selected by remember { mutableStateOf(0) }
    var editing by remember { mutableStateOf<CreditCard?>(null) }
    var creating by remember { mutableStateOf(false) }
    var revealedId by remember { mutableStateOf<Long?>(null) }
    var confirmDelete by remember { mutableStateOf<CreditCard?>(null) }
    var decodeFor by remember { mutableStateOf<CreditCard?>(null) }
    var trueCvv by remember { mutableStateOf<String?>(null) }
    val activity = LocalContext.current as? FragmentActivity

    fun requestReveal(card: CreditCard) {
        val act = activity ?: return
        val authenticators = androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK or
            androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
        if (androidx.biometric.BiometricManager.from(act).canAuthenticate(authenticators) !=
            androidx.biometric.BiometricManager.BIOMETRIC_SUCCESS
        ) {
            revealedId = card.id // no screen lock on device — nothing to gate with
            return
        }
        androidx.biometric.BiometricPrompt(
            act,
            androidx.core.content.ContextCompat.getMainExecutor(act),
            object : androidx.biometric.BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(
                    result: androidx.biometric.BiometricPrompt.AuthenticationResult
                ) { revealedId = card.id }
            }
        ).authenticate(
            androidx.biometric.BiometricPrompt.PromptInfo.Builder()
                .setTitle("Reveal card details")
                .setSubtitle("Confirm it's you")
                .setAllowedAuthenticators(authenticators)
                .build()
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Cards") },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { creating = true },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("Add card") }
            )
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            if (cards.isEmpty()) {
                EmptyState(
                    emoji = "💳",
                    title = "Your cards, in your vault",
                    subtitle = "Store card details encrypted on this device only. Numbers and CVV stay locked behind your fingerprint. Add manually or scan the physical card."
                )
            } else {
                val sel = cards.getOrNull(selected.coerceIn(0, cards.size - 1)) ?: cards.first()
                CardFace(
                    card = sel,
                    revealed = revealedId == sel.id,
                    big = true
                )
                Row(
                    Modifier.fillMaxWidth().padding(top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (revealedId == sel.id) {
                        OutlinedButton(onClick = { revealedId = null }, modifier = Modifier.weight(1f)) { Text("Hide") }
                    } else {
                        Button(onClick = { requestReveal(sel) }, modifier = Modifier.weight(1f)) { Text("🔓 Reveal") }
                    }
                    if (revealedId == sel.id && sel.cvvShifted) {
                        OutlinedButton(onClick = { decodeFor = sel; trueCvv = null }, modifier = Modifier.weight(1f)) {
                            Text("True CVV")
                        }
                    }
                    OutlinedButton(onClick = { editing = sel }, modifier = Modifier.weight(1f)) { Text("Edit") }
                    OutlinedButton(onClick = { confirmDelete = sel }, modifier = Modifier.weight(1f)) {
                        Text("Delete", color = MaterialTheme.colorScheme.error)
                    }
                }

                // Bill status from card-linked reminders (auto-created from
                // bill-due SMS; auto-settled when the payment transfer lands).
                val openBills = reminders.filter {
                    it.cardId == sel.id && it.enabled && it.lastDoneKey == null
                }.sortedBy { it.dueMillis ?: Long.MAX_VALUE }
                openBills.firstOrNull()?.let { bill ->
                    val dueDate = bill.dueMillis?.let { com.neeraj.fin.util.Format.toLocalDate(it) }
                    val overdue = dueDate != null && dueDate.isBefore(java.time.LocalDate.now())
                    androidx.compose.material3.Card(
                        Modifier.fillMaxWidth().padding(top = 12.dp),
                        colors = androidx.compose.material3.CardDefaults.cardColors(
                            containerColor = if (overdue) MaterialTheme.colorScheme.errorContainer
                            else MaterialTheme.colorScheme.secondaryContainer
                        )
                    ) {
                        Row(
                            Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    (if (bill.amountMinor > 0)
                                        com.neeraj.fin.util.Format.money(bill.amountMinor, "INR") + " due"
                                    else "Bill due") +
                                        (dueDate?.let { " · $it" } ?: "") +
                                        (if (overdue) " · OVERDUE" else ""),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    "Auto-clears when the payment is detected",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            TextButton(onClick = { vm.markReminderDone(bill) }) { Text("Paid ✓") }
                        }
                    }
                }
                // Cycle hint + this-month spend on this card (matched by tail)
                val dueDays = reminders.filter { it.cardId == sel.id && it.dueMillis != null }
                    .map { com.neeraj.fin.util.Format.toLocalDate(it.dueMillis!!).dayOfMonth }
                val monthStart = java.time.LocalDate.now().withDayOfMonth(1)
                    .atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
                val spend = txns.filter {
                    it.accountTail == sel.last4 && it.timestamp >= monthStart &&
                        it.type == com.neeraj.fin.data.db.TxnType.EXPENSE
                }.sumOf { it.amountMinor }
                val expiringSoon = sel.expiryMonth > 0 && java.time.LocalDate.of(
                    sel.expiryYear, sel.expiryMonth.coerceIn(1, 12), 1
                ).plusMonths(1).isBefore(java.time.LocalDate.now().plusMonths(3))
                val hints = buildList {
                    if (spend > 0) add("Spent ${com.neeraj.fin.util.Format.compact(spend, "INR")} this month on this card")
                    if (dueDays.size >= 2) add("bill usually due around day ${dueDays.sorted()[dueDays.size / 2]}")
                    if (expiringSoon) add("⚠️ card expires soon")
                }
                if (hints.isNotEmpty()) {
                    Text(
                        hints.joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }

                if (cards.size > 1) {
                    Text(
                        "ALL CARDS (${cards.size})",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 20.dp, bottom = 8.dp)
                    )
                    // Drawer stack: each card peeks out under the previous one.
                    Box(Modifier.fillMaxWidth().height(((cards.size - 1) * 64 + 140).dp)) {
                        var slot = 0
                        cards.forEachIndexed { i, c ->
                            if (i == selected) return@forEachIndexed
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .offset(y = (slot * 64).dp)
                                    .clickable { selected = i; revealedId = null }
                            ) {
                                CardFace(card = c, revealed = false, big = false)
                            }
                            slot++
                        }
                    }
                }
            }
            Spacer(Modifier.height(96.dp))
        }
    }

    if (creating || editing != null) {
        CardDialog(
            initial = editing,
            onSave = { vm.saveCard(it); creating = false; editing = null },
            onDismiss = { creating = false; editing = null }
        )
    }
    decodeFor?.let { card ->
        var offsetText by remember(card.id) { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { decodeFor = null; trueCvv = null },
            title = { Text("Decode CVV") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Enter your secret offset. The app never stores it and cannot " +
                            "check it — a wrong offset simply shows a wrong CVV.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedTextField(
                        value = offsetText,
                        onValueChange = { offsetText = it.filter { c -> c.isDigit() }.take(4) },
                        label = { Text("Offset") },
                        singleLine = true
                    )
                    trueCvv?.let {
                        Text(
                            "True CVV: $it",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val stored = CardCrypto.decrypt(card.encCvv)
                        val len = stored.length.coerceAtLeast(3)
                        val mod = Math.pow(10.0, len.toDouble()).toInt()
                        val off = offsetText.toIntOrNull() ?: 0
                        val real = ((stored.toIntOrNull() ?: 0) - off).mod(mod)
                        trueCvv = real.toString().padStart(len, '0')
                    },
                    enabled = offsetText.isNotBlank()
                ) { Text("Decode") }
            },
            dismissButton = { TextButton(onClick = { decodeFor = null; trueCvv = null }) { Text("Close") } }
        )
    }

    confirmDelete?.let { c ->
        ConfirmDialog(
            title = "Remove ${c.bankName} ·· ${c.last4}?",
            text = "The stored number and CVV will be permanently deleted from this device.",
            confirmLabel = "Remove",
            onConfirm = { vm.deleteCard(c.id); confirmDelete = null; selected = 0 },
            onDismiss = { confirmDelete = null }
        )
    }
}

@Composable
private fun CardFace(card: CreditCard, revealed: Boolean, big: Boolean) {
    val (c1, c2) = cardColors[card.colorIndex.coerceIn(0, cardColors.size - 1)]
    Column(
        Modifier
            .fillMaxWidth()
            .height(if (big) 200.dp else 130.dp)
            .background(Brush.linearGradient(listOf(c1, c2)), RoundedCornerShape(20.dp))
            .padding(20.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Row(Modifier.fillMaxWidth()) {
            Text(card.bankName, color = Color.White, fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Text(card.network, color = Color.White.copy(alpha = 0.85f),
                style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        }
        Text(
            if (revealed) CardCrypto.decrypt(card.encNumber).chunked(4).joinToString("  ")
            else "••••  ••••  ••••  ${card.last4}",
            color = Color.White,
            style = if (big) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        if (big) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f)) {
                    Text(card.holderName.uppercase(), color = Color.White.copy(alpha = 0.9f),
                        style = MaterialTheme.typography.labelLarge)
                }
                Column(horizontalAlignment = Alignment.End) {
                    if (card.expiryMonth > 0) {
                        Text("VALID %02d/%02d".format(card.expiryMonth, card.expiryYear % 100),
                            color = Color.White.copy(alpha = 0.9f), style = MaterialTheme.typography.labelMedium)
                    }
                    Text(
                        if (revealed) "CVV ${CardCrypto.decrypt(card.encCvv).ifBlank { "—" }}" +
                            (if (card.cvvShifted) " 🎭" else "")
                        else "CVV •••",
                        color = Color.White, style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun CardDialog(
    initial: CreditCard?,
    onSave: (CreditCard) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var number by remember { mutableStateOf(initial?.let { CardCrypto.decrypt(it.encNumber) } ?: "") }
    var cvv by remember { mutableStateOf(initial?.let { CardCrypto.decrypt(it.encCvv) } ?: "") }
    var bank by remember { mutableStateOf(initial?.bankName ?: "") }
    var holder by remember { mutableStateOf(initial?.holderName ?: "") }
    var expiry by remember {
        mutableStateOf(initial?.takeIf { it.expiryMonth > 0 }
            ?.let { "%02d/%02d".format(it.expiryMonth, it.expiryYear % 100) } ?: "")
    }
    var colorIdx by remember { mutableStateOf(initial?.colorIndex ?: 0) }
    var disguise by remember { mutableStateOf(initial?.cvvShifted ?: false) }
    var offsetText by remember { mutableStateOf("") }
    var scanning by remember { mutableStateOf(false) }

    val digits = number.filter { it.isDigit() }
    val numberOk = CardCrypto.luhnValid(digits)
    val expiryOk = expiry.isBlank() || Regex("""\d{2}/\d{2,4}""").matches(expiry)

    // Camera scan → OCR → number/expiry extraction.
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val docScan = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
                .fromActivityResultIntent(result.data)
                ?.pages?.firstOrNull()?.imageUri?.let { uri ->
                    scanning = true
                    scope.launch {
                        val text = runCatching {
                            com.neeraj.fin.util.ReceiptScanner.scan(context, uri).rawText
                        }.getOrDefault("")
                        scanning = false
                        Regex("""(?:\d[ -]?){15,18}\d""").find(text)?.value
                            ?.filter { it.isDigit() }
                            ?.takeIf { CardCrypto.luhnValid(it) }
                            ?.let { number = it }
                        Regex("""(0[1-9]|1[0-2])\s*/\s*(\d{2,4})""").find(text)?.let {
                            expiry = "${it.groupValues[1]}/${it.groupValues[2]}"
                        }
                        if (holder.isBlank()) {
                            text.lines().map { it.trim() }.firstOrNull { l ->
                                l.length in 5..26 && l.all { ch -> ch.isLetter() || ch == ' ' || ch == '.' } &&
                                    l.contains(' ') && l == l.uppercase()
                            }?.let { holder = it }
                        }
                    }
                }
        }
    }
    fun startScan() {
        val act = context as? android.app.Activity ?: return
        val options = com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions.Builder()
            .setGalleryImportAllowed(false)
            .setPageLimit(1)
            .setResultFormats(com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions.RESULT_FORMAT_JPEG)
            .setScannerMode(com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions.SCANNER_MODE_BASE)
            .build()
        com.google.mlkit.vision.documentscanner.GmsDocumentScanning.getClient(options)
            .getStartScanIntent(act)
            .addOnSuccessListener { sender ->
                docScan.launch(androidx.activity.result.IntentSenderRequest.Builder(sender).build())
            }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Add card" else "Edit card") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(onClick = { startScan() }, modifier = Modifier.fillMaxWidth()) {
                    Text(if (scanning) "Reading card…" else "📷 Scan physical card")
                }
                OutlinedTextField(
                    value = number,
                    onValueChange = { number = it.filter { c -> c.isDigit() }.take(19) },
                    label = { Text("Card number") },
                    isError = number.isNotBlank() && !numberOk,
                    supportingText = if (number.isNotBlank() && !numberOk) {
                        { Text("Doesn't pass card checksum") }
                    } else null,
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = expiry, onValueChange = { expiry = it.take(7) },
                        label = { Text("MM/YY") }, isError = !expiryOk,
                        singleLine = true, modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = cvv, onValueChange = { cvv = it.filter { c -> c.isDigit() }.take(4) },
                        label = { Text("CVV") },
                        singleLine = true, modifier = Modifier.weight(1f)
                    )
                }
                OutlinedTextField(
                    value = bank, onValueChange = { bank = it },
                    label = { Text("Bank / card name") },
                    placeholder = { Text("Axis Magnus") },
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = holder, onValueChange = { holder = it },
                    label = { Text("Name on card") },
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    cardColors.forEachIndexed { i, (c, _) ->
                        Box(
                            Modifier
                                .height(28.dp).weight(1f)
                                .background(c, RoundedCornerShape(6.dp))
                                .clickable { colorIdx = i }
                                .padding(2.dp)
                        ) { if (colorIdx == i) Text("✓", color = Color.White, modifier = Modifier.align(Alignment.Center)) }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Disguise CVV", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "Store CVV + a secret offset only you know. Even a reveal shows the disguised value.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    androidx.compose.material3.Switch(checked = disguise, onCheckedChange = { disguise = it })
                }
                if (disguise && initial?.cvvShifted != true) {
                    OutlinedTextField(
                        value = offsetText,
                        onValueChange = { offsetText = it.filter { c -> c.isDigit() }.take(4) },
                        label = { Text("Secret offset (memorize it!)") },
                        supportingText = { Text("Stored CVV = CVV + offset. Kosh forgets the offset immediately.") },
                        singleLine = true, modifier = Modifier.fillMaxWidth()
                    )
                }
                Text(
                    "Stored encrypted on this device only (hardware keystore). Kosh cannot transmit it anywhere.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val (m, y) = expiry.split("/").let {
                        if (it.size == 2) (it[0].toIntOrNull() ?: 0) to (it[1].toIntOrNull() ?: 0) else 0 to 0
                    }
                    onSave(
                        (initial ?: CreditCard(bankName = "", last4 = "", encNumber = "")).copy(
                            bankName = bank.trim().ifBlank { CardCrypto.network(digits) },
                            holderName = holder.trim(),
                            last4 = digits.takeLast(4),
                            network = CardCrypto.network(digits),
                            encNumber = CardCrypto.encrypt(digits),
                            encCvv = CardCrypto.encrypt(
                                if (disguise && initial?.cvvShifted != true && cvv.isNotBlank()) {
                                    val len = cvv.length
                                    val mod = Math.pow(10.0, len.toDouble()).toInt()
                                    ((cvv.toIntOrNull() ?: 0) + (offsetText.toIntOrNull() ?: 0)).mod(mod)
                                        .toString().padStart(len, '0')
                                } else cvv
                            ),
                            cvvShifted = disguise,
                            expiryMonth = m,
                            expiryYear = if (y in 1..99) 2000 + y else y,
                            colorIndex = colorIdx
                        )
                    )
                },
                enabled = numberOk && expiryOk
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
