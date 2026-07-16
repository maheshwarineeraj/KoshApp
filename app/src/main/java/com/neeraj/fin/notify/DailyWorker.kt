package com.neeraj.fin.notify

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.neeraj.fin.FinApp
import com.neeraj.fin.widget.updateKoshWidget

/**
 * Daily housekeeping: post due recurring transactions, budget alerts,
 * review-queue nudge, month-start summary, and refresh the home widget.
 */
class DailyWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as FinApp
        runCatching { app.repository.applyDueRecurringRules() }
        runCatching { app.backupManager.autoBackupIfDue() }
        runCatching { Notifications.checkBudgets(app) }
        runCatching { Notifications.reviewNudge(app) }
        runCatching { Notifications.monthlySummary(app) }
        runCatching { Notifications.staleAssetNudge(app) }
        runCatching { Notifications.reminderNudge(app) }
        runCatching { updateKoshWidget(applicationContext) }
        return Result.success()
    }
}
