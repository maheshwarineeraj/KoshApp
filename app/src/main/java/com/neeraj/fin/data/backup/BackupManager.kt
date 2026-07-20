package com.neeraj.fin.data.backup

import android.content.Context
import android.net.Uri
import com.neeraj.fin.data.FinRepository
import com.neeraj.fin.data.db.Asset
import com.neeraj.fin.data.db.AssetValue
import com.neeraj.fin.data.db.Budget
import com.neeraj.fin.data.db.Category
import com.neeraj.fin.data.db.EventBudget
import com.neeraj.fin.data.db.Goal
import com.neeraj.fin.data.db.GoalContribution
import com.neeraj.fin.data.db.RecurringRule
import com.neeraj.fin.data.db.Txn
import com.neeraj.fin.data.db.TxnSource
import com.neeraj.fin.data.db.TxnType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

@Serializable
data class BackupCategory(val id: Long, val name: String, val emoji: String, val color: Long, val kind: String, val isDefault: Boolean)

@Serializable
data class BackupTxn(
    val id: Long, val amountMinor: Long, val type: String, val categoryId: Long?,
    val merchant: String, val note: String, val timestamp: Long, val source: String,
    val accountTail: String?, val smsHash: Long?,
    val eventBudgetId: Long? = null, val goalId: Long? = null,
    val pocketId: Long? = null
)

@Serializable
data class BackupBudget(val categoryId: Long, val monthlyLimitMinor: Long)

@Serializable
data class BackupAsset(
    val id: Long, val name: String, val type: String, val platform: String,
    val isLiability: Boolean, val investedMinor: Long, val notes: String
)

@Serializable
data class BackupAssetValue(val id: Long, val assetId: Long, val valueMinor: Long, val timestamp: Long)

@Serializable
data class BackupGoal(
    val id: Long, val name: String, val emoji: String, val targetMinor: Long,
    val deadlineMillis: Long?, val createdAt: Long
)

@Serializable
data class BackupGoalContribution(val id: Long, val goalId: Long, val amountMinor: Long, val timestamp: Long, val note: String)

@Serializable
data class BackupEventBudget(
    val id: Long, val name: String, val emoji: String, val plannedMinor: Long, val createdAt: Long,
    val startMillis: Long? = null, val endMillis: Long? = null
)

@Serializable
data class BackupReminder(
    val id: Long, val title: String, val amountMinor: Long, val recurrence: String,
    val dayOfMonth: Int, val monthOfYear: Int, val dueMillis: Long?, val merchant: String,
    val categoryId: Long?, val lastDoneKey: String?, val enabled: Boolean, val source: String,
    val sourceBody: String = "",
    val cardId: Long? = null
)

@Serializable
data class BackupPocket(val id: Long, val name: String, val emoji: String, val accountTails: String, val createdAt: Long)

@Serializable
data class BackupCard(
    val id: Long, val bankName: String, val holderName: String, val number: String,
    val cvv: String, val expiryMonth: Int, val expiryYear: Int, val network: String,
    val colorIndex: Int, val createdAt: Long, val cvvShifted: Boolean = false
)

@Serializable
data class BackupRecurringRule(
    val id: Long, val amountMinor: Long, val type: String, val categoryId: Long?,
    val merchant: String, val note: String, val dayOfMonth: Int, val startMillis: Long,
    val lastAppliedKey: String?
)

@Serializable
data class BackupData(
    val version: Int = 6,
    val exportedAtMillis: Long,
    val categories: List<BackupCategory>,
    val transactions: List<BackupTxn>,
    val budgets: List<BackupBudget>,
    // v2 fields — default to empty so v1 backups still restore
    val assets: List<BackupAsset> = emptyList(),
    val assetValues: List<BackupAssetValue> = emptyList(),
    val goals: List<BackupGoal> = emptyList(),
    val goalContributions: List<BackupGoalContribution> = emptyList(),
    val eventBudgets: List<BackupEventBudget> = emptyList(),
    val recurringRules: List<BackupRecurringRule> = emptyList(),
    // v5: preferences, so a restore on a new device keeps the same experience
    val currencyCode: String? = null,
    val smsAutoCapture: Boolean? = null,
    // v6
    val reminders: List<BackupReminder> = emptyList(),
    val pockets: List<BackupPocket> = emptyList(),
    // Card numbers/CVVs travel decrypted INSIDE this passphrase-encrypted file
    // (device Keystore blobs would not survive a device change).
    val cards: List<BackupCard> = emptyList()
)

/**
 * Encrypted backup to a user-chosen location (Storage Access Framework —
 * the user can point it at Google Drive / OneDrive without the app needing
 * any cloud SDK). Format: "FINBAK1" magic + 16B salt + 12B IV + AES-256-GCM ciphertext.
 * Key derived from the user's passphrase with PBKDF2-HMAC-SHA256 (200k iterations).
 */
class BackupManager(
    private val context: Context,
    private val repository: FinRepository,
    private val settings: com.neeraj.fin.data.SettingsStore
) {

    private val json = Json { ignoreUnknownKeys = true }
    private val magic = "FINBAK1".toByteArray(Charsets.US_ASCII)

    suspend fun export(uri: Uri, passphrase: CharArray) = withContext(Dispatchers.IO) {
        val snap = repository.snapshot()
        val data = BackupData(
            exportedAtMillis = System.currentTimeMillis(),
            categories = snap.categories.map { BackupCategory(it.id, it.name, it.emoji, it.color, it.kind, it.isDefault) },
            transactions = snap.txns.map {
                BackupTxn(it.id, it.amountMinor, it.type, it.categoryId, it.merchant, it.note, it.timestamp, it.source, it.accountTail, it.smsHash, it.eventBudgetId, it.goalId, it.pocketId)
            },
            budgets = snap.budgets.map { BackupBudget(it.categoryId, it.monthlyLimitMinor) },
            assets = snap.assets.map { BackupAsset(it.id, it.name, it.type, it.platform, it.isLiability, it.investedMinor, it.notes) },
            assetValues = snap.assetValues.map { BackupAssetValue(it.id, it.assetId, it.valueMinor, it.timestamp) },
            goals = snap.goals.map { BackupGoal(it.id, it.name, it.emoji, it.targetMinor, it.deadlineMillis, it.createdAt) },
            goalContributions = snap.goalContributions.map { BackupGoalContribution(it.id, it.goalId, it.amountMinor, it.timestamp, it.note) },
            eventBudgets = snap.eventBudgets.map { BackupEventBudget(it.id, it.name, it.emoji, it.plannedMinor, it.createdAt, it.startMillis, it.endMillis) },
            recurringRules = snap.recurringRules.map {
                BackupRecurringRule(it.id, it.amountMinor, it.type, it.categoryId, it.merchant, it.note, it.dayOfMonth, it.startMillis, it.lastAppliedKey)
            },
            currencyCode = settings.currencyCode.first(),
            smsAutoCapture = settings.smsAutoCapture.first(),
            reminders = snap.reminders.map {
                BackupReminder(it.id, it.title, it.amountMinor, it.recurrence, it.dayOfMonth,
                    it.monthOfYear, it.dueMillis, it.merchant, it.categoryId, it.lastDoneKey,
                    it.enabled, it.source, it.sourceBody, it.cardId)
            },
            pockets = snap.pockets.map { BackupPocket(it.id, it.name, it.emoji, it.accountTails, it.createdAt) },
            cards = snap.cards.map {
                BackupCard(
                    it.id, it.bankName, it.holderName,
                    com.neeraj.fin.util.CardCrypto.decrypt(it.encNumber),
                    com.neeraj.fin.util.CardCrypto.decrypt(it.encCvv),
                    it.expiryMonth, it.expiryYear, it.network, it.colorIndex, it.createdAt, it.cvvShifted
                )
            },
        )
        val plaintext = json.encodeToString(BackupData.serializer(), data).toByteArray(Charsets.UTF_8)

        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, deriveKey(passphrase, salt), GCMParameterSpec(128, iv))
        val ciphertext = cipher.doFinal(plaintext)

        context.contentResolver.openOutputStream(uri, "wt")?.use { out ->
            out.write(magic); out.write(salt); out.write(iv); out.write(ciphertext)
        } ?: throw IllegalStateException("Cannot open backup destination")
    }

    /**
     * Scheduled auto-export: writes a dated encrypted backup into the user's
     * chosen SAF folder once per period (ISO week or calendar month). Returns
     * true when a new backup file was written. Failures (folder permission
     * revoked, provider offline) are swallowed — the next daily run retries.
     */
    suspend fun autoBackupIfDue(force: Boolean = false): Boolean = withContext(Dispatchers.IO) {
        val folder = settings.autoBackupFolder.first() ?: return@withContext false
        val pass = settings.autoBackupPassphrase.first() ?: return@withContext false
        val today = java.time.LocalDate.now()
        val key = if (settings.autoBackupFrequency.first() == "MONTHLY") {
            "%04d-%02d".format(today.year, today.monthValue)
        } else {
            "%04d-W%02d".format(
                today.get(java.time.temporal.IsoFields.WEEK_BASED_YEAR),
                today.get(java.time.temporal.IsoFields.WEEK_OF_WEEK_BASED_YEAR)
            )
        }
        if (!force && settings.autoBackupLast.first() == key) return@withContext false

        val treeUri = Uri.parse(folder)
        val fileUri = runCatching {
            val parent = android.provider.DocumentsContract.buildDocumentUriUsingTree(
                treeUri, android.provider.DocumentsContract.getTreeDocumentId(treeUri)
            )
            android.provider.DocumentsContract.createDocument(
                context.contentResolver, parent, "application/octet-stream",
                "kosh-auto-backup-$today.finbak"
            )
        }.getOrNull() ?: return@withContext false

        val ok = runCatching { export(fileUri, pass.toCharArray()) }.isSuccess
        if (ok) settings.setAutoBackupLast(key)
        ok
    }

    suspend fun import(uri: Uri, passphrase: CharArray) = withContext(Dispatchers.IO) {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw IllegalStateException("Cannot open backup file")
        require(bytes.size > magic.size + 16 + 12 + 16) { "Not a valid backup file" }
        require(bytes.copyOfRange(0, magic.size).contentEquals(magic)) { "Not a valid backup file" }

        val salt = bytes.copyOfRange(magic.size, magic.size + 16)
        val iv = bytes.copyOfRange(magic.size + 16, magic.size + 28)
        val ciphertext = bytes.copyOfRange(magic.size + 28, bytes.size)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, deriveKey(passphrase, salt), GCMParameterSpec(128, iv))
        val plaintext = try {
            cipher.doFinal(ciphertext)
        } catch (e: Exception) {
            throw IllegalArgumentException("Wrong passphrase or corrupted backup")
        }
        val data = json.decodeFromString(BackupData.serializer(), plaintext.toString(Charsets.UTF_8))

        repository.replaceAll(
            FinRepository.Snapshot(
                categories = data.categories.map { Category(it.id, it.name, it.emoji, it.color, it.kind, it.isDefault) },
                txns = data.transactions.map {
                    Txn(it.id, it.amountMinor, it.type, it.categoryId, it.merchant, it.note, it.timestamp, it.source, it.accountTail, it.smsHash, it.eventBudgetId, it.goalId, it.pocketId)
                },
                budgets = data.budgets.map { Budget(it.categoryId, it.monthlyLimitMinor) },
                assets = data.assets.map { Asset(it.id, it.name, it.type, it.platform, it.isLiability, it.investedMinor, it.notes) },
                assetValues = data.assetValues.map { AssetValue(it.id, it.assetId, it.valueMinor, it.timestamp) },
                goals = data.goals.map { Goal(it.id, it.name, it.emoji, it.targetMinor, it.deadlineMillis, it.createdAt) },
                goalContributions = data.goalContributions.map { GoalContribution(it.id, it.goalId, it.amountMinor, it.timestamp, it.note) },
                eventBudgets = data.eventBudgets.map { EventBudget(it.id, it.name, it.emoji, it.plannedMinor, it.createdAt, it.startMillis, it.endMillis) },
                recurringRules = data.recurringRules.map {
                    RecurringRule(it.id, it.amountMinor, it.type, it.categoryId, it.merchant, it.note, it.dayOfMonth, it.startMillis, it.lastAppliedKey)
                },
                reminders = data.reminders.map {
                    com.neeraj.fin.data.db.Reminder(it.id, it.title, it.amountMinor, it.recurrence,
                        it.dayOfMonth, it.monthOfYear, it.dueMillis, it.merchant, it.categoryId,
                        it.lastDoneKey, it.enabled, it.source, it.sourceBody, it.cardId)
                },
                pockets = data.pockets.map {
                    com.neeraj.fin.data.db.Pocket(it.id, it.name, it.emoji, it.accountTails, it.createdAt)
                },
                cards = data.cards.map {
                    com.neeraj.fin.data.db.CreditCard(
                        id = it.id, bankName = it.bankName, holderName = it.holderName,
                        last4 = it.number.filter { c -> c.isDigit() }.takeLast(4),
                        network = it.network,
                        encNumber = com.neeraj.fin.util.CardCrypto.encrypt(it.number),
                        encCvv = com.neeraj.fin.util.CardCrypto.encrypt(it.cvv),
                        expiryMonth = it.expiryMonth, expiryYear = it.expiryYear,
                        colorIndex = it.colorIndex, cvvShifted = it.cvvShifted, createdAt = it.createdAt
                    )
                },
            )
        )
        data.currencyCode?.let { settings.setCurrencyCode(it) }
        data.smsAutoCapture?.let { settings.setSmsAutoCapture(it) }
        data.transactions.size
    }

    suspend fun exportCsv(uri: Uri) = withContext(Dispatchers.IO) {
        val snap = repository.snapshot()
        val catNames = snap.categories.associate { it.id to it.name }
        val sb = StringBuilder("date,type,amount,category,merchant,note,source,account\n")
        val fmt = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.US)
        for (t in snap.txns.sortedByDescending { it.timestamp }) {
            fun esc(s: String) = "\"" + s.replace("\"", "\"\"") + "\""
            sb.append(fmt.format(java.util.Date(t.timestamp))).append(',')
                .append(t.type).append(',')
                .append("%.2f".format(t.amountMinor / 100.0)).append(',')
                .append(esc(catNames[t.categoryId] ?: "Uncategorized")).append(',')
                .append(esc(t.merchant)).append(',')
                .append(esc(t.note)).append(',')
                .append(t.source).append(',')
                .append(t.accountTail ?: "").append('\n')
        }
        context.contentResolver.openOutputStream(uri, "wt")?.use {
            it.write(sb.toString().toByteArray(Charsets.UTF_8))
        } ?: throw IllegalStateException("Cannot open export destination")
    }

    /**
     * Import transactions from CSV: `date,type,amount,category,merchant,note`
     * (same shape as Kosh's own export; extra columns are ignored).
     * Dates: `yyyy-MM-dd` or `yyyy-MM-dd HH:mm`. Duplicate rows (same date,
     * amount, merchant) are skipped. Unknown categories are created.
     * Returns (imported, skipped).
     */
    suspend fun importTransactionsCsv(uri: Uri): Pair<Int, Int> = withContext(Dispatchers.IO) {
        val rows = readCsv(uri)
        require(rows.isNotEmpty()) { "Empty CSV" }
        val header = rows.first().map { it.trim().lowercase() }
        fun col(name: String) = header.indexOf(name)
        val dateIdx = col("date"); val typeIdx = col("type"); val amountIdx = col("amount")
        val catIdx = col("category"); val merchantIdx = col("merchant"); val noteIdx = col("note")
        require(dateIdx >= 0 && amountIdx >= 0) { "CSV must have at least 'date' and 'amount' columns" }

        val categories = repository.categoriesOnce().toMutableList()
        var imported = 0
        var skipped = 0
        val zone = java.time.ZoneId.systemDefault()

        for (row in rows.drop(1)) {
            if (row.all { it.isBlank() }) continue
            fun cell(i: Int) = if (i in row.indices) row[i].trim() else ""
            val parsedDate = parseCsvDate(cell(dateIdx))
            if (parsedDate == null) { skipped++; continue }
            val timestamp = parsedDate.atZone(zone).toInstant().toEpochMilli()
            val rawAmount = cell(amountIdx).replace(",", "").replace("₹", "")
            val amountMinor = runCatching {
                java.math.BigDecimal(rawAmount).abs().movePointRight(2).longValueExact()
            }.getOrNull()
            if (amountMinor == null || amountMinor == 0L) { skipped++; continue }

            val type = when {
                typeIdx >= 0 && cell(typeIdx).uppercase().startsWith("INC") -> TxnType.INCOME
                typeIdx >= 0 && cell(typeIdx).uppercase().startsWith("TRA") -> TxnType.TRANSFER
                rawAmount.startsWith("-") -> TxnType.EXPENSE
                typeIdx >= 0 && cell(typeIdx).uppercase().startsWith("EXP") -> TxnType.EXPENSE
                else -> TxnType.EXPENSE
            }
            val merchant = cell(merchantIdx)
            if (repository.txnExists(timestamp, amountMinor, merchant)) { skipped++; continue }

            val catName = cell(catIdx)
            val categoryId = if (catName.isBlank() || catName.equals("Uncategorized", true)) null else {
                categories.firstOrNull { it.name.equals(catName, true) }?.id ?: run {
                    val id = repository.addCategory(
                        Category(name = catName, emoji = "🧾", color = 0xFF78909C, kind = if (type == TxnType.INCOME) TxnType.INCOME else TxnType.EXPENSE)
                    )
                    categories += Category(id = id, name = catName, emoji = "🧾", color = 0xFF78909C, kind = type)
                    id
                }
            }
            repository.addTxn(
                Txn(
                    amountMinor = amountMinor, type = type, categoryId = categoryId,
                    merchant = merchant, note = cell(noteIdx), timestamp = timestamp,
                    source = TxnSource.IMPORT
                )
            )
            imported++
        }
        imported to skipped
    }

    /**
     * Import/update holdings from CSV: `name,platform,type,invested,current`.
     * Matches existing assets by name (case-insensitive): updates invested amount
     * and records a new value snapshot; creates new assets otherwise.
     * `type` is one of BANK, FD, MUTUAL_FUND, STOCKS, EPF_PPF, GOLD, CRYPTO,
     * PROPERTY, CASH, OTHER, LOAN (defaults to OTHER).
     */
    suspend fun importHoldingsCsv(uri: Uri): Int = withContext(Dispatchers.IO) {
        val rows = readCsv(uri)
        require(rows.isNotEmpty()) { "Empty CSV" }
        val header = rows.first().map { it.trim().lowercase() }
        fun col(name: String) = header.indexOf(name)
        val nameIdx = col("name"); val platformIdx = col("platform"); val typeIdx = col("type")
        val investedIdx = col("invested"); val currentIdx = col("current")
        require(nameIdx >= 0 && currentIdx >= 0) { "CSV must have 'name' and 'current' columns" }

        val existing = repository.assetsOnce()
        var updated = 0
        for (row in rows.drop(1)) {
            if (row.all { it.isBlank() }) continue
            fun cell(i: Int) = if (i in row.indices) row[i].trim() else ""
            val name = cell(nameIdx)
            if (name.isBlank()) continue
            fun money(i: Int): Long? = runCatching {
                java.math.BigDecimal(cell(i).replace(",", "").replace("₹", "")).movePointRight(2).longValueExact()
            }.getOrNull()
            val current = money(currentIdx) ?: continue
            val invested = if (investedIdx >= 0) money(investedIdx) ?: 0L else 0L
            val typeRaw = cell(typeIdx).uppercase().replace(' ', '_')
            val type = com.neeraj.fin.data.db.AssetType.all.map { it.first }
                .firstOrNull { it == typeRaw } ?: com.neeraj.fin.data.db.AssetType.OTHER

            val match = existing.firstOrNull { it.name.equals(name, true) }
            if (match != null) {
                repository.updateAsset(match.copy(investedMinor = if (invested > 0) invested else match.investedMinor))
                repository.addAssetValue(match.id, current)
            } else {
                repository.addAsset(
                    Asset(
                        name = name, type = type, platform = cell(platformIdx),
                        isLiability = type == com.neeraj.fin.data.db.AssetType.LOAN,
                        investedMinor = invested
                    ),
                    current
                )
            }
            updated++
        }
        updated
    }

    private fun parseCsvDate(raw: String): java.time.LocalDateTime? {
        val patterns = listOf("yyyy-MM-dd HH:mm", "yyyy-MM-dd", "dd/MM/yyyy", "dd-MM-yyyy")
        for (p in patterns) {
            runCatching {
                return if (p.contains("HH")) {
                    java.time.LocalDateTime.parse(raw, java.time.format.DateTimeFormatter.ofPattern(p))
                } else {
                    java.time.LocalDate.parse(raw, java.time.format.DateTimeFormatter.ofPattern(p)).atTime(12, 0)
                }
            }
        }
        return null
    }

    /** Minimal CSV reader with quote support. */
    private fun readCsv(uri: Uri): List<List<String>> {
        val text = context.contentResolver.openInputStream(uri)?.use {
            it.readBytes().toString(Charsets.UTF_8)
        } ?: throw IllegalStateException("Cannot open file")
        val rows = mutableListOf<List<String>>()
        var field = StringBuilder()
        var row = mutableListOf<String>()
        var inQuotes = false
        var i = 0
        while (i < text.length) {
            val c = text[i]
            when {
                inQuotes && c == '"' && i + 1 < text.length && text[i + 1] == '"' -> { field.append('"'); i++ }
                c == '"' -> inQuotes = !inQuotes
                c == ',' && !inQuotes -> { row.add(field.toString()); field = StringBuilder() }
                (c == '\n' || c == '\r') && !inQuotes -> {
                    if (c == '\r' && i + 1 < text.length && text[i + 1] == '\n') i++
                    row.add(field.toString()); field = StringBuilder()
                    if (row.any { it.isNotBlank() }) rows.add(row)
                    row = mutableListOf()
                }
                else -> field.append(c)
            }
            i++
        }
        row.add(field.toString())
        if (row.any { it.isNotBlank() }) rows.add(row)
        return rows
    }

    private fun deriveKey(passphrase: CharArray, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(passphrase, salt, 200_000, 256)
        val key = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec)
        return SecretKeySpec(key.encoded, "AES")
    }
}
