package com.neeraj.fin.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.neeraj.fin.FinApp
import com.neeraj.fin.data.db.Asset
import com.neeraj.fin.data.db.AssetValue
import com.neeraj.fin.data.db.Category
import com.neeraj.fin.data.db.EventBudget
import com.neeraj.fin.data.db.Goal
import com.neeraj.fin.data.db.GoalContribution
import com.neeraj.fin.data.db.PendingSms
import com.neeraj.fin.data.db.RecurringRule
import com.neeraj.fin.data.db.Txn
import com.neeraj.fin.notify.Notifications
import com.neeraj.fin.widget.updateKoshWidget
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AppViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as FinApp
    private val repo = app.repository

    val categories: StateFlow<List<Category>> =
        repo.categories.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val transactions: StateFlow<List<Txn>> =
        repo.transactions.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val pendingSms: StateFlow<List<PendingSms>> =
        repo.pendingSms.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val pendingCount: StateFlow<Int> =
        repo.pendingCount.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val budgets = repo.budgets.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val assets: StateFlow<List<Asset>> =
        repo.assets.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val assetValues: StateFlow<List<AssetValue>> =
        repo.assetValues.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val goals: StateFlow<List<Goal>> =
        repo.goals.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val goalContributions: StateFlow<List<GoalContribution>> =
        repo.goalContributions.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val eventBudgets: StateFlow<List<EventBudget>> =
        repo.eventBudgets.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recurringRules: StateFlow<List<RecurringRule>> =
        repo.recurringRules.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val appLock: StateFlow<Boolean> =
        app.settings.appLock.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val notificationsEnabled: StateFlow<Boolean> =
        app.settings.notificationsEnabled.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val currencyCode: StateFlow<String> =
        app.settings.currencyCode.stateIn(viewModelScope, SharingStarted.Eagerly, "INR")

    val smsAutoCapture: StateFlow<Boolean> =
        app.settings.smsAutoCapture.stateIn(viewModelScope, SharingStarted.Eagerly, true)

    val blockScreenshots: StateFlow<Boolean> =
        app.settings.blockScreenshots.stateIn(viewModelScope, SharingStarted.Eagerly, true)

    val notificationCapture: StateFlow<Boolean> =
        app.settings.notificationCapture.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message
    fun consumeMessage() { _message.value = null }
    private fun toast(msg: String) { _message.value = msg }

    fun txnsBetween(from: Long, to: Long): Flow<List<Txn>> = repo.txnsBetween(from, to)

    private suspend fun afterTxnChange() {
        runCatching { Notifications.checkBudgets(app) }
        runCatching { updateKoshWidget(app) }
    }

    fun saveTxn(txn: Txn) = viewModelScope.launch {
        if (txn.id == 0L) repo.addTxn(txn) else repo.updateTxn(txn)
        toast("Saved")
        afterTxnChange()
    }

    fun deleteTxn(txn: Txn) = viewModelScope.launch {
        repo.deleteTxn(txn)
        toast("Transaction deleted")
        afterTxnChange()
    }

    fun splitTxn(original: Txn, parts: List<Pair<Long, Long?>>) = viewModelScope.launch {
        repo.splitTxn(original, parts)
        toast("Split into ${parts.size} transactions")
        afterTxnChange()
    }

    fun saveRecurringRule(rule: RecurringRule) = viewModelScope.launch {
        repo.saveRecurringRule(rule)
        val applied = repo.applyDueRecurringRules()
        toast(if (applied > 0) "Saved — $applied transaction(s) posted" else "Saved")
        afterTxnChange()
    }

    fun deleteRecurringRule(id: Long) = viewModelScope.launch {
        repo.deleteRecurringRule(id)
        toast("Recurring transaction deleted")
    }

    fun setAppLock(enabled: Boolean) = viewModelScope.launch { app.settings.setAppLock(enabled) }

    fun setNotificationsEnabled(enabled: Boolean) =
        viewModelScope.launch { app.settings.setNotificationsEnabled(enabled) }

    fun setBlockScreenshots(enabled: Boolean) =
        viewModelScope.launch { app.settings.setBlockScreenshots(enabled) }

    fun setNotificationCapture(enabled: Boolean) =
        viewModelScope.launch { app.settings.setNotificationCapture(enabled) }

    fun importTransactionsCsv(uri: Uri) = viewModelScope.launch {
        runCatching { app.backupManager.importTransactionsCsv(uri) }
            .onSuccess { (imported, skipped) ->
                toast("Imported $imported transaction(s)" + if (skipped > 0) ", skipped $skipped" else "")
                afterTxnChange()
            }
            .onFailure { toast("Import failed: ${it.message}") }
    }

    fun importHoldingsCsv(uri: Uri) = viewModelScope.launch {
        runCatching { app.backupManager.importHoldingsCsv(uri) }
            .onSuccess { toast("Updated $it holding(s)") }
            .onFailure { toast("Import failed: ${it.message}") }
    }

    fun saveCategory(category: Category) = viewModelScope.launch {
        if (category.id == 0L) repo.addCategory(category) else repo.updateCategory(category)
    }

    fun deleteCategory(category: Category) = viewModelScope.launch {
        repo.deleteCategory(category)
        toast("Category deleted; its transactions are now uncategorized")
    }

    fun setBudget(categoryId: Long, limitMinor: Long?) = viewModelScope.launch {
        if (limitMinor == null || limitMinor <= 0) repo.removeBudget(categoryId)
        else repo.setBudget(categoryId, limitMinor)
    }

    fun approvePending(item: PendingSms, amountMinor: Long, type: String, categoryId: Long?, merchant: String, note: String) =
        viewModelScope.launch {
            repo.approvePending(item, amountMinor, type, categoryId, merchant, note)
            toast("Added to transactions")
            afterTxnChange()
        }

    fun rejectPending(item: PendingSms) = viewModelScope.launch { repo.rejectPending(item) }

    fun scanInbox(days: Int) = viewModelScope.launch {
        runCatching { app.smsImporter.scanInbox(days) }
            .onSuccess { toast(if (it == 0) "No new transaction SMS found" else "$it transaction(s) queued for review") }
            .onFailure { toast("Scan failed: ${it.message}") }
    }

    fun addAsset(asset: Asset, initialValueMinor: Long) = viewModelScope.launch {
        repo.addAsset(asset, initialValueMinor)
        toast("Added to your portfolio")
    }

    fun updateAsset(asset: Asset, newValueMinor: Long?) = viewModelScope.launch {
        repo.updateAsset(asset)
        if (newValueMinor != null) repo.addAssetValue(asset.id, newValueMinor)
        toast("Updated")
    }

    fun deleteAsset(assetId: Long) = viewModelScope.launch {
        repo.deleteAsset(assetId)
        toast("Removed from portfolio")
    }

    fun saveEventBudget(budget: EventBudget) = viewModelScope.launch { repo.saveEventBudget(budget) }

    fun deleteEventBudget(id: Long) = viewModelScope.launch {
        repo.deleteEventBudget(id)
        toast("Budget deleted; its expenses are untagged")
    }

    fun saveGoal(goal: Goal) = viewModelScope.launch { repo.saveGoal(goal) }

    fun deleteGoal(goalId: Long) = viewModelScope.launch {
        repo.deleteGoal(goalId)
        toast("Goal deleted")
    }

    fun addContribution(goalId: Long, amountMinor: Long, note: String) = viewModelScope.launch {
        repo.addContribution(goalId, amountMinor, note)
        toast("Contribution added")
    }

    fun setCurrency(code: String) = viewModelScope.launch { app.settings.setCurrencyCode(code) }
    fun setSmsAutoCapture(enabled: Boolean) = viewModelScope.launch { app.settings.setSmsAutoCapture(enabled) }

    fun exportBackup(uri: Uri, passphrase: String) = viewModelScope.launch {
        runCatching { app.backupManager.export(uri, passphrase.toCharArray()) }
            .onSuccess { toast("Encrypted backup saved") }
            .onFailure { toast("Backup failed: ${it.message}") }
    }

    fun importBackup(uri: Uri, passphrase: String) = viewModelScope.launch {
        runCatching { app.backupManager.import(uri, passphrase.toCharArray()) }
            .onSuccess { toast("Restored $it transactions") }
            .onFailure { toast("Restore failed: ${it.message}") }
    }

    fun exportCsv(uri: Uri) = viewModelScope.launch {
        runCatching { app.backupManager.exportCsv(uri) }
            .onSuccess { toast("CSV exported") }
            .onFailure { toast("Export failed: ${it.message}") }
    }
}
