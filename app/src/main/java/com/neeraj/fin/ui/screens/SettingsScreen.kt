package com.neeraj.fin.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.CurrencyExchange
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MarkEmailUnread
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import com.neeraj.fin.ui.AppViewModel
import java.time.LocalDate

private val currencies = listOf("INR", "USD", "EUR", "GBP", "AED", "SGD", "AUD", "CAD", "JPY")

@Composable
fun SettingsScreen(vm: AppViewModel, nav: NavController, page: String = "more") {
    val context = LocalContext.current
    val currency by vm.currencyCode.collectAsState()
    val autoCapture by vm.smsAutoCapture.collectAsState()
    val pendingCount by vm.pendingCount.collectAsState()
    val appLock by vm.appLock.collectAsState()
    val notificationsEnabled by vm.notificationsEnabled.collectAsState()
    val blockScreenshots by vm.blockScreenshots.collectAsState()
    val notificationCapture by vm.notificationCapture.collectAsState()
    val autoBackupFolder by vm.autoBackupFolder.collectAsState()
    val autoBackupFrequency by vm.autoBackupFrequency.collectAsState()
    val autoBackupLast by vm.autoBackupLast.collectAsState()
    val hasNotifAccess = androidx.core.app.NotificationManagerCompat
        .getEnabledListenerPackages(context).contains(context.packageName)

    var hasSmsPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        hasSmsPermission = grants[Manifest.permission.READ_SMS] == true
        if (hasSmsPermission) vm.scanInbox(90)
    }

    // Backup / restore / CSV
    var pendingPassphrase by remember { mutableStateOf("") }
    var showExportDialog by remember { mutableStateOf(false) }
    var showImportDialog by remember { mutableStateOf<android.net.Uri?>(null) }
    var showCurrencyDialog by remember { mutableStateOf(false) }
    var showScanDialog by remember { mutableStateOf(false) }

    // Play-policy prominent disclosure: shown BEFORE the system SMS permission
    // prompt, every time permission is requested. Holds the action to run on consent.
    var smsDisclosureAction by remember { mutableStateOf<(() -> Unit)?>(null) }

    // Auto backup setup: passphrase + frequency first, then the folder picker.
    var showAutoBackupSetup by remember { mutableStateOf(false) }
    var showAutoBackupManage by remember { mutableStateOf(false) }
    var pendingAutoPass by remember { mutableStateOf("") }
    var pendingAutoFreq by remember { mutableStateOf("WEEKLY") }
    val autoBackupFolderLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null && pendingAutoPass.isNotEmpty()) {
            context.contentResolver.takePersistableUriPermission(
                uri,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            vm.enableAutoBackup(uri.toString(), pendingAutoPass, pendingAutoFreq)
        }
        pendingAutoPass = ""
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri -> uri?.let { vm.exportBackup(it, pendingPassphrase) }; pendingPassphrase = "" }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { showImportDialog = it } }

    val csvLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri -> uri?.let { vm.exportCsv(it) } }

    val importTxnCsvLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { vm.importTransactionsCsv(it) } }

    val importHoldingsCsvLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { vm.importHoldingsCsv(it) } }

    val notifPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            when (page) { "prefs" -> "Settings"; "backup" -> "Backup & export"; else -> "More" },
            style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold
        )

        if (page == "more") SectionCard("Organize") {
            SettingRow(Icons.Filled.Savings, "Budgets & Goals", "Monthly limits, event budgets, savings goals") { nav.navigate("budgets") }
            HorizontalDivider()
            SettingRow(Icons.Filled.Repeat, "Recurring", "Rent, SIPs, salary — posted automatically each month") { nav.navigate("recurring") }
            HorizontalDivider()
            SettingRow(Icons.Filled.Category, "Categories", "Create and edit custom categories") { nav.navigate("categories") }
            HorizontalDivider()
            SettingRow(Icons.Filled.AccountBalanceWallet, "Pockets", "Isolate money streams — business, family, rental") { nav.navigate("pockets") }
            HorizontalDivider()
            SettingRow(Icons.Filled.CurrencyExchange, "Currency", currency) { showCurrencyDialog = true }
        }

        if (page == "prefs") SectionCard("Security & alerts") {
            Row(
                Modifier.fillMaxWidth().padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("App lock", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Require fingerprint / PIN to open Kosh",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(checked = appLock, onCheckedChange = { vm.setAppLock(it) })
            }
            HorizontalDivider()
            Row(
                Modifier.fillMaxWidth().padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Block screenshots", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Hide app content in screenshots, screen recordings and Recents",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(checked = blockScreenshots, onCheckedChange = { vm.setBlockScreenshots(it) })
            }
            HorizontalDivider()
            Row(
                Modifier.fillMaxWidth().padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Notifications", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Budget alerts, review reminders, monthly summary",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = notificationsEnabled,
                    onCheckedChange = { enabled ->
                        vm.setNotificationsEnabled(enabled)
                        if (enabled && android.os.Build.VERSION.SDK_INT >= 33) {
                            notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    }
                )
            }
        }

        if (page == "prefs") SectionCard("SMS capture") {
            SettingRow(
                Icons.Filled.Sms,
                "SMS permission",
                if (hasSmsPermission) "Granted — bank SMS are captured automatically" else "Grant to detect transactions from bank SMS"
            ) {
                if (!hasSmsPermission) {
                    smsDisclosureAction = {
                        permissionLauncher.launch(arrayOf(Manifest.permission.READ_SMS, Manifest.permission.RECEIVE_SMS))
                    }
                }
            }
            HorizontalDivider()
            Row(
                Modifier.fillMaxWidth().padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Auto-capture new SMS", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Detected transactions always wait for your approval",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(checked = autoCapture, onCheckedChange = { vm.setSmsAutoCapture(it) })
            }
            HorizontalDivider()
            SettingRow(Icons.Filled.Restore, "Scan inbox for past transactions", "Import bank SMS from recent months") {
                if (hasSmsPermission) showScanDialog = true
                else smsDisclosureAction = {
                    permissionLauncher.launch(arrayOf(Manifest.permission.READ_SMS, Manifest.permission.RECEIVE_SMS))
                }
            }
            if (pendingCount > 0) {
                HorizontalDivider()
                SettingRow(Icons.Filled.MarkEmailUnread, "Review queue", "$pendingCount waiting for approval") {
                    nav.navigate("review")
                }
            }
        }

        if (page == "prefs") SectionCard("Notification capture") {
            SettingRow(
                Icons.Filled.Notifications,
                "Notification access",
                if (hasNotifAccess) "Granted — Kosh can read transaction notifications"
                else "Grant to detect transactions without SMS permission"
            ) {
                context.startActivity(
                    android.content.Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                )
            }
            HorizontalDivider()
            Row(
                Modifier.fillMaxWidth().padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Capture from notifications", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Detect transactions from bank-app and SMS notifications. " +
                            "Only new notifications — past messages can't be read this way.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = notificationCapture && hasNotifAccess,
                    onCheckedChange = { enabled ->
                        vm.setNotificationCapture(enabled)
                        if (enabled && !hasNotifAccess) {
                            context.startActivity(
                                android.content.Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                            )
                        }
                    }
                )
            }
        }

        if (page == "more") SectionCard("Shortcuts") {
            SettingRow(Icons.Filled.Lock, "Settings", "App lock, screenshots, alerts, SMS & notification capture") { nav.navigate("appsettings") }
            HorizontalDivider()
            SettingRow(Icons.Filled.CloudUpload, "Backup & export", "Encrypted backups, auto backup, CSV import/export") { nav.navigate("backup") }
        }

        if (page == "backup") SectionCard("Backup & export") {
            SettingRow(
                Icons.Filled.CloudUpload,
                "Encrypted backup",
                "Save an encrypted file to Google Drive, OneDrive or anywhere you choose"
            ) { showExportDialog = true }
            HorizontalDivider()
            SettingRow(Icons.Filled.Restore, "Restore from backup", "Replaces current data") {
                importLauncher.launch(arrayOf("*/*"))
            }
            HorizontalDivider()
            if (autoBackupFolder == null) {
                SettingRow(
                    Icons.Filled.CloudUpload, "Auto backup",
                    "Save an encrypted backup to a Drive/OneDrive folder every week or month"
                ) { showAutoBackupSetup = true }
            } else {
                SettingRow(
                    Icons.Filled.CloudUpload,
                    "Auto backup — " + (if (autoBackupFrequency == "MONTHLY") "monthly" else "weekly"),
                    "To \"${folderLabel(autoBackupFolder!!)}\"" +
                        (autoBackupLast?.let { " · last: $it" } ?: " · no backup yet")
                ) { showAutoBackupManage = true }
            }
            HorizontalDivider()
            SettingRow(Icons.Filled.Description, "Export CSV", "Plain spreadsheet of all transactions") {
                csvLauncher.launch("kosh-transactions-${LocalDate.now()}.csv")
            }
            HorizontalDivider()
            SettingRow(
                Icons.Filled.FileUpload, "Import transactions (CSV)",
                "Columns: date, type, amount, category, merchant, note"
            ) { importTxnCsvLauncher.launch(arrayOf("*/*")) }
            HorizontalDivider()
            SettingRow(
                Icons.Filled.FileUpload, "Import holdings (CSV)",
                "Columns: name, platform, type, invested, current — updates Wealth values"
            ) { importHoldingsCsvLauncher.launch(arrayOf("*/*")) }
        }

        if (page == "more") Card {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(
                    "All your data lives on this device only. Nothing is sent to any server. " +
                        "Backups are AES-256 encrypted before they leave the app.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 12.dp)
                )
            }
        }

        Spacer(Modifier.height(24.dp))
    }

    if (showExportDialog) {
        PassphraseDialog(
            title = "Encrypt backup",
            subtitle = "Choose a passphrase. You will need it to restore — it is not stored anywhere. " +
                "A longer passphrase keeps the backup safe wherever you store it.",
            confirmLabel = "Choose location",
            minLength = 8,
            onConfirm = { pass ->
                pendingPassphrase = pass
                showExportDialog = false
                exportLauncher.launch("fin-backup-${LocalDate.now()}.finbak")
            },
            onDismiss = { showExportDialog = false }
        )
    }

    showImportDialog?.let { uri ->
        PassphraseDialog(
            title = "Restore backup",
            subtitle = "Enter the passphrase used when this backup was created. Current data will be replaced.",
            confirmLabel = "Restore",
            onConfirm = { pass ->
                vm.importBackup(uri, pass)
                showImportDialog = null
            },
            onDismiss = { showImportDialog = null }
        )
    }

    smsDisclosureAction?.let { onConsent ->
        AlertDialog(
            onDismissRequest = { smsDisclosureAction = null },
            icon = { Icon(Icons.Filled.Sms, contentDescription = null) },
            title = { Text("SMS access & your privacy") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Kosh reads your SMS to detect bank and UPI transaction messages " +
                            "and turn them into expense entries that wait for your approval.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        "• 100% on-device. Kosh has no internet permission — message " +
                            "content cannot leave this phone.\n" +
                            "• Only transaction alerts are used. Personal messages, OTPs and " +
                            "offers are ignored and never stored.\n" +
                            "• Nothing is added to your records without your explicit approval.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        "You can revoke SMS access anytime in Android Settings.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { smsDisclosureAction = null; onConsent() }) { Text("Agree & continue") }
            },
            dismissButton = {
                TextButton(onClick = { smsDisclosureAction = null }) { Text("Not now") }
            }
        )
    }

    if (showAutoBackupSetup) {
        var pass by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAutoBackupSetup = false },
            title = { Text("Auto backup") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "Kosh will save an encrypted backup file into a folder you choose — " +
                            "pick a Google Drive or OneDrive folder to get it off this device automatically.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = pendingAutoFreq == "WEEKLY",
                            onClick = { pendingAutoFreq = "WEEKLY" },
                            label = { Text("Weekly") }
                        )
                        FilterChip(
                            selected = pendingAutoFreq == "MONTHLY",
                            onClick = { pendingAutoFreq = "MONTHLY" },
                            label = { Text("Monthly") }
                        )
                    }
                    OutlinedTextField(
                        value = pass,
                        onValueChange = { pass = it },
                        label = { Text("Passphrase") },
                        supportingText = { Text(if (pass.length < 8) "At least 8 characters" else "✓") },
                        isError = pass.isNotEmpty() && pass.length < 8,
                        singleLine = true
                    )
                    Text(
                        "The passphrase is kept on this device so backups can run unattended. " +
                            "You will need it to restore — write it down somewhere safe.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingAutoPass = pass
                        showAutoBackupSetup = false
                        autoBackupFolderLauncher.launch(null)
                    },
                    enabled = pass.length >= 8
                ) { Text("Choose folder") }
            },
            dismissButton = { TextButton(onClick = { showAutoBackupSetup = false }) { Text("Cancel") } }
        )
    }

    if (showAutoBackupManage) {
        AlertDialog(
            onDismissRequest = { showAutoBackupManage = false },
            title = { Text("Auto backup") },
            text = {
                Text(
                    "Backups are written to \"${autoBackupFolder?.let { folderLabel(it) }}\" " +
                        (if (autoBackupFrequency == "MONTHLY") "every month." else "every week.") +
                        (autoBackupLast?.let { "\nLast backup period: $it" } ?: "\nNo backup written yet.")
                )
            },
            confirmButton = {
                Row {
                    TextButton(onClick = { vm.runAutoBackupNow(); showAutoBackupManage = false }) { Text("Back up now") }
                    TextButton(onClick = { vm.disableAutoBackup(); showAutoBackupManage = false }) {
                        Text("Turn off", color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            dismissButton = { TextButton(onClick = { showAutoBackupManage = false }) { Text("Close") } }
        )
    }

    if (showCurrencyDialog) {
        AlertDialog(
            onDismissRequest = { showCurrencyDialog = false },
            title = { Text("Currency") },
            text = {
                Column {
                    currencies.forEach { code ->
                        Text(
                            code,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { vm.setCurrency(code); showCurrencyDialog = false }
                                .padding(vertical = 10.dp),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = if (code == currency) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showCurrencyDialog = false }) { Text("Close") } }
        )
    }

    if (showScanDialog) {
        AlertDialog(
            onDismissRequest = { showScanDialog = false },
            title = { Text("Scan SMS inbox") },
            text = { Text("How far back should Fin look for transaction messages?") },
            confirmButton = {
                Row {
                    TextButton(onClick = { vm.scanInbox(30); showScanDialog = false }) { Text("1 month") }
                    TextButton(onClick = { vm.scanInbox(90); showScanDialog = false }) { Text("3 months") }
                    TextButton(onClick = { vm.scanInbox(365); showScanDialog = false }) { Text("1 year") }
                }
            },
            dismissButton = { TextButton(onClick = { showScanDialog = false }) { Text("Cancel") } }
        )
    }
}

/** Human-readable folder name from a SAF tree URI ("primary:Backups" → "Backups"). */
private fun folderLabel(treeUri: String): String =
    android.net.Uri.parse(treeUri).lastPathSegment
        ?.substringAfterLast(':')?.substringAfterLast('/')
        ?.ifBlank { null } ?: "chosen folder"

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Card {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(vertical = 8.dp)
            )
            content()
        }
    }
}

@Composable
private fun SettingRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Column(Modifier.padding(start = 16.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun PassphraseDialog(
    title: String,
    subtitle: String,
    confirmLabel: String,
    minLength: Int = 1,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var pass by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(subtitle, style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(
                    value = pass,
                    onValueChange = { pass = it },
                    label = { Text("Passphrase") },
                    supportingText = if (minLength > 1) {
                        { Text(if (pass.length < minLength) "At least $minLength characters" else "✓") }
                    } else null,
                    isError = minLength > 1 && pass.isNotEmpty() && pass.length < minLength,
                    singleLine = true
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(pass) }, enabled = pass.length >= minLength) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
