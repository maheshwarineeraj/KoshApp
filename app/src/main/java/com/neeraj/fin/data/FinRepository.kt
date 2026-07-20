package com.neeraj.fin.data

import com.neeraj.fin.data.db.AppDatabase
import com.neeraj.fin.data.db.Asset
import com.neeraj.fin.data.db.AssetValue
import com.neeraj.fin.data.db.Budget
import com.neeraj.fin.data.db.Category
import com.neeraj.fin.data.db.DefaultCategories
import com.neeraj.fin.data.db.EventBudget
import com.neeraj.fin.data.db.Goal
import com.neeraj.fin.data.db.GoalContribution
import com.neeraj.fin.data.db.PendingSms
import com.neeraj.fin.data.db.PendingStatus
import com.neeraj.fin.data.db.RecurringRule
import com.neeraj.fin.data.db.CreditCard
import com.neeraj.fin.data.db.Pocket
import com.neeraj.fin.data.db.Reminder
import com.neeraj.fin.data.db.Txn
import com.neeraj.fin.data.db.TxnSource
import com.neeraj.fin.data.db.TxnType
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import com.neeraj.fin.data.sms.ParsedSms
import com.neeraj.fin.data.sms.SmsParser
import kotlinx.coroutines.flow.Flow

class FinRepository(private val db: AppDatabase) {

    val categories: Flow<List<Category>> = db.categoryDao().all()
    val transactions: Flow<List<Txn>> = db.txnDao().all()
    val pendingSms: Flow<List<PendingSms>> = db.pendingSmsDao().pending()
    val pendingCount: Flow<Int> = db.pendingSmsDao().pendingCount()
    val budgets: Flow<List<Budget>> = db.budgetDao().all()
    val assets: Flow<List<Asset>> = db.assetDao().all()
    val assetValues: Flow<List<AssetValue>> = db.assetDao().values()
    val goals: Flow<List<Goal>> = db.goalDao().all()
    val goalContributions: Flow<List<GoalContribution>> = db.goalDao().contributions()
    val eventBudgets: Flow<List<EventBudget>> = db.eventBudgetDao().all()
    val reminders: Flow<List<Reminder>> = db.reminderDao().all()
    val pockets: Flow<List<Pocket>> = db.pocketDao().all()
    val cards: Flow<List<CreditCard>> = db.cardDao().all()
    val recurringRules: Flow<List<RecurringRule>> = db.recurringRuleDao().all()

    suspend fun seedDefaultsIfEmpty() {
        if (db.categoryDao().count() == 0) {
            db.categoryDao().insertAll(DefaultCategories.expense() + DefaultCategories.income())
        }
    }

    fun txnsBetween(from: Long, to: Long): Flow<List<Txn>> = db.txnDao().between(from, to)
    suspend fun txnsBetweenOnce(from: Long, to: Long): List<Txn> = db.txnDao().betweenOnce(from, to)

    suspend fun addTxn(txn: Txn) = db.txnDao().insert(txn)
    suspend fun updateTxn(txn: Txn) = db.txnDao().update(txn)
    suspend fun deleteTxn(txn: Txn) = db.txnDao().delete(txn)

    /** Replace a transaction with several parts (split across categories). */
    suspend fun splitTxn(original: Txn, parts: List<Pair<Long, Long?>>) {
        db.txnDao().delete(original)
        parts.forEach { (amountMinor, categoryId) ->
            db.txnDao().insert(
                original.copy(
                    id = 0,
                    amountMinor = amountMinor,
                    categoryId = categoryId,
                    note = listOf(original.note, "(split)").filter { it.isNotBlank() }.joinToString(" "),
                    smsHash = null
                )
            )
        }
    }

    // ----- Recurring rules -----

    suspend fun saveRecurringRule(rule: RecurringRule) =
        if (rule.id == 0L) db.recurringRuleDao().insert(rule)
        else { db.recurringRuleDao().update(rule); rule.id }

    suspend fun deleteRecurringRule(id: Long) = db.recurringRuleDao().delete(id)

    /**
     * Post transactions for every recurring rule that is due and not yet applied,
     * covering months missed while the app was closed. Returns number inserted.
     */
    suspend fun applyDueRecurringRules(today: LocalDate = LocalDate.now()): Int {
        val zone = ZoneId.systemDefault()
        var inserted = 0
        for (rule in db.recurringRuleDao().allOnce()) {
            val startMonth = YearMonth.from(
                java.time.Instant.ofEpochMilli(rule.startMillis).atZone(zone).toLocalDate()
            )
            var month = rule.lastAppliedKey?.let { YearMonth.parse(it).plusMonths(1) } ?: startMonth
            var lastApplied: YearMonth? = null
            while (!month.isAfter(YearMonth.from(today))) {
                val dueDay = rule.dayOfMonth.coerceAtMost(month.lengthOfMonth())
                val dueDate = month.atDay(dueDay)
                if (dueDate.isAfter(today)) break
                db.txnDao().insert(
                    Txn(
                        amountMinor = rule.amountMinor,
                        type = rule.type,
                        categoryId = rule.categoryId,
                        merchant = rule.merchant,
                        note = rule.note,
                        timestamp = dueDate.atTime(9, 0).atZone(zone).toInstant().toEpochMilli(),
                        source = TxnSource.RECURRING
                    )
                )
                inserted++
                lastApplied = month
                month = month.plusMonths(1)
            }
            if (lastApplied != null) {
                db.recurringRuleDao().update(rule.copy(lastAppliedKey = lastApplied.toString()))
            }
        }
        return inserted
    }

    suspend fun addCategory(category: Category) = db.categoryDao().insert(category)
    suspend fun updateCategory(category: Category) = db.categoryDao().update(category)
    suspend fun deleteCategory(category: Category) {
        db.txnDao().clearCategory(category.id)
        db.budgetDao().delete(category.id)
        db.categoryDao().delete(category)
    }

    suspend fun setBudget(categoryId: Long, limitMinor: Long) =
        db.budgetDao().upsert(Budget(categoryId, limitMinor))

    suspend fun removeBudget(categoryId: Long) = db.budgetDao().delete(categoryId)

    /**
     * Insert a parsed SMS into the review queue unless we've already seen it
     * (still pending, previously rejected, or already approved into a transaction).
     * Returns true if a new pending item was created.
     */
    suspend fun offerParsedSms(sender: String, body: String, timestamp: Long, parsed: ParsedSms): Boolean {
        val hash = SmsParser.smsHash(sender, body, timestamp)
        if (db.pendingSmsDao().existsByHash(hash)) return false
        if (db.txnDao().existsBySmsHash(hash)) return false
        // Banks often report one transaction twice (debit alert + payment confirmation,
        // bank SMS + card-network SMS). Same amount, same direction, within 10 minutes
        // of an already-seen SMS item ⇒ treat as a duplicate report and skip.
        val window = 10L * 60 * 1000
        if (db.pendingSmsDao().existsSimilar(parsed.amountMinor, parsed.type, timestamp - window, timestamp + window)) return false
        if (db.txnDao().existsSimilarSms(parsed.amountMinor, parsed.type, timestamp - window, timestamp + window)) return false
        val suggested = suggestCategory(parsed)
        val suggestedPocket = suggestPocket(parsed.accountTail, parsed.merchant)
        val row = PendingSms(
            sender = sender,
            body = body,
            timestamp = timestamp,
            amountMinor = parsed.amountMinor,
            type = parsed.type,
            merchant = parsed.merchant,
            accountTail = parsed.accountTail,
            suggestedCategoryId = suggested?.id,
            smsHash = hash,
            foreignCurrency = parsed.foreignCurrency,
            note = parsed.note,
            pocketId = suggestedPocket?.id
        )
        return db.pendingSmsDao().insert(row) != -1L
    }

    private suspend fun suggestCategory(parsed: ParsedSms): Category? {
        val all = db.categoryDao().allOnce()
        // The user's own history beats keyword guessing: reuse whatever category
        // they last gave this merchant.
        if (parsed.merchant.isNotBlank() && !parsed.merchant.equals("unknown", ignoreCase = true)) {
            val learned = db.txnDao().lastCategoryForMerchant(parsed.merchant)
                ?: db.txnDao().allOnce().firstOrNull {
                    it.categoryId != null &&
                        com.neeraj.fin.util.Merchants.same(it.merchant, parsed.merchant)
                }?.categoryId
            learned?.let { id ->
                all.firstOrNull { it.id == id && it.kind == parsed.type }?.let { return it }
            }
        }
        val name = SmsParser.suggestCategoryName(parsed) ?: return null
        return all.firstOrNull { it.name.equals(name, ignoreCase = true) && it.kind == parsed.type }
            ?: all.firstOrNull { it.name.equals(name, ignoreCase = true) }
    }

    suspend fun lastCategoryForMerchant(merchant: String): Long? =
        db.txnDao().lastCategoryForMerchant(merchant)

    /**
     * A credit-card bill payment (transfer) settles any open card-bill
     * reminder with a matching amount (within 2%) or matching card tail.
     */
    private suspend fun autoSettleCardBill(amountMinor: Long, body: String) {
        val today = java.time.LocalDate.now()
        val doneKey = "done"
        db.reminderDao().allOnce().filter {
            it.cardId != null && it.enabled && it.lastDoneKey == null &&
                it.recurrence == com.neeraj.fin.data.db.ReminderRecurrence.ONCE
        }.forEach { r ->
            val card = db.cardDao().allOnce().firstOrNull { it.id == r.cardId }
            val amountClose = r.amountMinor > 0 &&
                kotlin.math.abs(r.amountMinor - amountMinor) * 50 <= r.amountMinor // within 2%
            val tailMatch = card != null && card.last4.length == 4 && body.contains(card.last4)
            if (amountClose || tailMatch) {
                db.reminderDao().upsert(r.copy(lastDoneKey = doneKey))
            }
        }
    }

    suspend fun approvePending(item: PendingSms, amountMinor: Long, type: String, categoryId: Long?, merchant: String, note: String) {
        db.txnDao().insert(
            Txn(
                amountMinor = amountMinor,
                type = type,
                categoryId = categoryId,
                merchant = merchant,
                note = note,
                timestamp = item.timestamp,
                source = TxnSource.SMS,
                accountTail = item.accountTail,
                smsHash = item.smsHash,
                eventBudgetId = if (type == TxnType.EXPENSE) activeEventAt(item.timestamp)?.id else null,
                pocketId = item.pocketId
            )
        )
        if (type == TxnType.TRANSFER) autoSettleCardBill(amountMinor, item.body)
        db.pendingSmsDao().delete(item.id)
    }

    suspend fun rejectPending(item: PendingSms) {
        db.pendingSmsDao().reject(item.id)
    }

    /** Event budget whose trip window covers [atMillis], if any. */
    suspend fun activeEventAt(atMillis: Long): com.neeraj.fin.data.db.EventBudget? =
        db.eventBudgetDao().allOnce().firstOrNull {
            it.startMillis != null && it.endMillis != null &&
                atMillis >= it.startMillis && atMillis < it.endMillis + 24L * 60 * 60 * 1000
        }

    // ----- Pockets -----

    suspend fun savePocket(pocket: Pocket) = db.pocketDao().upsert(pocket)

    suspend fun deletePocket(id: Long) {
        db.pocketDao().clearTxnTags(id)
        db.pocketDao().delete(id)
    }

    /**
     * Smart pocket routing: 1) a pocket that claims this account/card tail,
     * 2) the pocket you last used for this merchant. Null = default Personal.
     */
    suspend fun suggestPocket(accountTail: String?, merchant: String): Pocket? {
        val pockets = db.pocketDao().allOnce()
        if (pockets.isEmpty()) return null
        if (!accountTail.isNullOrBlank()) {
            pockets.firstOrNull { p ->
                p.accountTails.split(',').map { it.trim() }.any { it.isNotEmpty() && it == accountTail }
            }?.let { return it }
        }
        if (merchant.isNotBlank() && !merchant.equals("unknown", ignoreCase = true)) {
            val learned = db.txnDao().allOnce().firstOrNull {
                it.pocketId != null && com.neeraj.fin.util.Merchants.same(it.merchant, merchant)
            }?.pocketId
            learned?.let { id -> return pockets.firstOrNull { it.id == id } }
        }
        return null
    }

    // ----- Card vault -----

    suspend fun saveCard(card: CreditCard) = db.cardDao().upsert(card)

    suspend fun deleteCard(id: Long) = db.cardDao().delete(id)

    // ----- Reminders -----

    suspend fun saveReminder(reminder: Reminder) = db.reminderDao().upsert(reminder)

    suspend fun deleteReminder(id: Long) = db.reminderDao().delete(id)

    suspend fun remindersOnce() = db.reminderDao().allOnce()

    /**
     * Queue a bill-due suggestion (from an SMS like "electricity bill of
     * Rs 1,240 due on 25-07"). Deduped on title + due month.
     */
    suspend fun offerBillDueReminder(title: String, amountMinor: Long, dueMillis: Long, sourceBody: String = ""): Boolean {
        // Link to a stored card when the message mentions its last-4.
        val card = db.cardDao().allOnce().firstOrNull { c ->
            c.last4.length == 4 && sourceBody.contains(c.last4)
        }
        val dueKey = com.neeraj.fin.util.Format.toLocalDate(dueMillis).withDayOfMonth(1)
        val exists = db.reminderDao().allOnce().any { r ->
            r.title.equals(title, ignoreCase = true) && r.dueMillis != null &&
                com.neeraj.fin.util.Format.toLocalDate(r.dueMillis).withDayOfMonth(1) == dueKey
        }
        if (exists) return false
        db.reminderDao().upsert(
            Reminder(
                title = card?.let { "${it.bankName} ·· ${it.last4} bill" } ?: title,
                amountMinor = amountMinor,
                recurrence = com.neeraj.fin.data.db.ReminderRecurrence.ONCE,
                dueMillis = dueMillis, enabled = card != null,
                source = com.neeraj.fin.data.db.ReminderSource.SMS,
                sourceBody = sourceBody,
                cardId = card?.id
            )
        )
        return true
    }

    // ----- Assets & net worth -----

    suspend fun addAsset(asset: Asset, initialValueMinor: Long): Long {
        val id = db.assetDao().insert(asset)
        db.assetDao().insertValue(AssetValue(assetId = id, valueMinor = initialValueMinor, timestamp = System.currentTimeMillis()))
        return id
    }

    suspend fun updateAsset(asset: Asset) = db.assetDao().update(asset)

    suspend fun deleteAsset(assetId: Long) {
        db.assetDao().deleteValues(assetId)
        db.assetDao().delete(assetId)
    }

    suspend fun addAssetValue(assetId: Long, valueMinor: Long) =
        db.assetDao().insertValue(AssetValue(assetId = assetId, valueMinor = valueMinor, timestamp = System.currentTimeMillis()))

    // ----- Event budgets -----

    suspend fun saveEventBudget(budget: EventBudget) =
        if (budget.id == 0L) db.eventBudgetDao().insert(budget) else { db.eventBudgetDao().update(budget); budget.id }

    suspend fun deleteEventBudget(id: Long) {
        db.txnDao().clearEventBudgetTag(id)
        db.eventBudgetDao().delete(id)
    }

    // ----- Goals -----

    suspend fun saveGoal(goal: Goal) =
        if (goal.id == 0L) db.goalDao().insert(goal) else { db.goalDao().update(goal); goal.id }

    suspend fun deleteGoal(goalId: Long) {
        db.txnDao().clearGoalTag(goalId)
        db.goalDao().deleteContributions(goalId)
        db.goalDao().delete(goalId)
    }

    suspend fun addContribution(goalId: Long, amountMinor: Long, note: String) =
        db.goalDao().insertContribution(
            GoalContribution(goalId = goalId, amountMinor = amountMinor, timestamp = System.currentTimeMillis(), note = note)
        )

    // ----- Backup support -----

    data class Snapshot(
        val categories: List<Category>,
        val txns: List<Txn>,
        val budgets: List<Budget>,
        val assets: List<Asset>,
        val assetValues: List<AssetValue>,
        val goals: List<Goal>,
        val goalContributions: List<GoalContribution>,
        val eventBudgets: List<EventBudget> = emptyList(),
        val recurringRules: List<RecurringRule> = emptyList(),
        val reminders: List<Reminder> = emptyList(),
        val pockets: List<Pocket> = emptyList(),
        val cards: List<CreditCard> = emptyList()
    )

    suspend fun snapshot(): Snapshot = Snapshot(
        db.categoryDao().allOnce(),
        db.txnDao().allOnce(),
        db.budgetDao().allOnce(),
        db.assetDao().allOnce(),
        db.assetDao().valuesOnce(),
        db.goalDao().allOnce(),
        db.goalDao().contributionsOnce(),
        db.eventBudgetDao().allOnce(),
        db.recurringRuleDao().allOnce(),
        db.reminderDao().allOnce(),
        db.pocketDao().allOnce(),
        db.cardDao().allOnce()
    )

    suspend fun replaceAll(data: Snapshot) {
        db.txnDao().clear()
        db.budgetDao().clear()
        db.categoryDao().clear()
        db.assetDao().clearValues()
        db.assetDao().clear()
        db.goalDao().clearContributions()
        db.goalDao().clear()
        db.eventBudgetDao().clear()
        db.recurringRuleDao().clear()
        db.reminderDao().clear()
        db.pocketDao().clear()
        db.cardDao().clear()
        db.categoryDao().insertAll(data.categories)
        db.txnDao().insertAll(data.txns)
        db.budgetDao().insertAll(data.budgets)
        db.assetDao().insertAll(data.assets)
        db.assetDao().insertValues(data.assetValues)
        db.goalDao().insertAll(data.goals)
        db.reminderDao().insertAll(data.reminders)
        db.pocketDao().insertAll(data.pockets)
        db.cardDao().insertAll(data.cards)
        db.goalDao().insertContributions(data.goalContributions)
        db.eventBudgetDao().insertAll(data.eventBudgets)
        db.recurringRuleDao().insertAll(data.recurringRules)
    }

    // ----- CSV import support -----

    suspend fun categoriesOnce(): List<Category> = db.categoryDao().allOnce()

    suspend fun txnExists(timestamp: Long, amountMinor: Long, merchant: String): Boolean =
        db.txnDao().allOnce().any {
            it.timestamp == timestamp && it.amountMinor == amountMinor &&
                it.merchant.equals(merchant, ignoreCase = true)
        }

    suspend fun assetsOnce() = db.assetDao().allOnce()

    suspend fun valuesOnce() = db.assetDao().valuesOnce()
}
