package com.neeraj.fin.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.neeraj.fin.FinApp
import com.neeraj.fin.MainActivity
import com.neeraj.fin.data.db.TxnType
import com.neeraj.fin.util.Format
import com.neeraj.fin.util.PeriodKind
import com.neeraj.fin.util.Periods
import kotlinx.coroutines.flow.first
import java.time.LocalDate

object Notifications {

    private const val CHANNEL_ALERTS = "budget_alerts"
    private const val CHANNEL_REVIEW = "review_reminders"
    private const val CHANNEL_SUMMARY = "monthly_summary"
    private const val CHANNEL_REMINDERS = "payment_reminders"

    fun ensureChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ALERTS, "Budget alerts", NotificationManager.IMPORTANCE_DEFAULT)
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_REVIEW, "SMS review reminders", NotificationManager.IMPORTANCE_LOW)
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_SUMMARY, "Monthly summary", NotificationManager.IMPORTANCE_LOW)
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_REMINDERS, "Payment reminders", NotificationManager.IMPORTANCE_DEFAULT)
        )
    }

    fun canPost(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    private fun openAppIntent(context: Context): PendingIntent =
        PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private fun post(context: Context, channel: String, id: Int, title: String, text: String) {
        if (!canPost(context)) return
        val notification = NotificationCompat.Builder(context, channel)
            .setSmallIcon(android.R.drawable.stat_notify_more)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(openAppIntent(context))
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(id, notification)
    }

    /**
     * Budget alerts at 80% and 100% of a category's monthly limit.
     * Fires each threshold at most once per category per month.
     */
    suspend fun checkBudgets(app: FinApp) {
        if (!app.settings.notificationsEnabled.first()) return
        val month = Periods.rangeFor(PeriodKind.MONTH, LocalDate.now())
        val txns = app.repository.txnsBetweenOnce(month.startMillis, month.endMillis)
        val snap = app.repository.snapshot()
        val currency = app.settings.currencyCode.first()
        val catById = snap.categories.associateBy { it.id }
        val monthKey = LocalDate.now().withDayOfMonth(1).toString()

        for (budget in snap.budgets) {
            val cat = catById[budget.categoryId] ?: continue
            val spent = txns.filter { it.type == TxnType.EXPENSE && it.categoryId == budget.categoryId && it.pocketId == null }
                .sumOf { it.amountMinor }
            val pct = if (budget.monthlyLimitMinor > 0) spent * 100 / budget.monthlyLimitMinor else 0
            val level = when {
                pct >= 100 -> 100
                pct >= 80 -> 80
                else -> continue
            }
            val marker = "budget:${budget.categoryId}:$monthKey:$level"
            if (app.settings.isMarkerSet(marker)) continue
            app.settings.setMarker(marker)
            post(
                app, CHANNEL_ALERTS, (1000 + budget.categoryId).toInt(),
                if (level == 100) "${cat.emoji} ${cat.name} budget exceeded" else "${cat.emoji} ${cat.name} at $pct% of budget",
                "Spent ${Format.money(spent, currency)} of ${Format.money(budget.monthlyLimitMinor, currency)} this month"
            )
        }
    }

    /** Nudge about SMS waiting for review (at most one per day, from the daily worker). */
    suspend fun reviewNudge(app: FinApp) {
        if (!app.settings.notificationsEnabled.first()) return
        val count = app.repository.pendingSms.first().size
        if (count == 0) return
        val marker = "review:${LocalDate.now()}"
        if (app.settings.isMarkerSet(marker)) return
        app.settings.setMarker(marker)
        post(
            app, CHANNEL_REVIEW, 2000,
            "Transactions waiting for review",
            "$count SMS transaction${if (count == 1) "" else "s"} detected — tap to approve or reject"
        )
    }

    /** Last month's summary, posted once on the 1st of each month. */
    suspend fun monthlySummary(app: FinApp) {
        if (!app.settings.notificationsEnabled.first()) return
        val today = LocalDate.now()
        if (today.dayOfMonth != 1) return
        val marker = "summary:${today.withDayOfMonth(1)}"
        if (app.settings.isMarkerSet(marker)) return
        app.settings.setMarker(marker)

        val lastMonth = Periods.rangeFor(PeriodKind.MONTH, today.minusMonths(1))
        val txns = app.repository.txnsBetweenOnce(lastMonth.startMillis, lastMonth.endMillis)
        val currency = app.settings.currencyCode.first()
        val income = txns.filter { it.type == TxnType.INCOME }.sumOf { it.amountMinor }
        val expense = txns.filter { it.type == TxnType.EXPENSE }.sumOf { it.amountMinor }
        post(
            app, CHANNEL_SUMMARY, 3000,
            "Your ${lastMonth.label} summary",
            "Income ${Format.money(income, currency)} · Spent ${Format.money(expense, currency)} · Net ${Format.money(income - expense, currency)}"
        )
    }

    /**
     * Nudge to refresh stale wealth values, tuned to each asset type's
     * volatility: markets/accounts ~monthly, deposits ~quarterly, property
     * ~yearly. Fires at most once per calendar month.
     */
    suspend fun staleAssetNudge(app: FinApp) {
        if (!app.settings.notificationsEnabled.first()) return
        val monthKey = LocalDate.now().let { "%04d-%02d".format(it.year, it.monthValue) }
        val marker = "staleAssets:$monthKey"
        if (app.settings.isMarkerSet(marker)) return

        val now = System.currentTimeMillis()
        val day = 24L * 60 * 60 * 1000
        fun thresholdFor(type: String): Long = when (type) {
            com.neeraj.fin.data.db.AssetType.MUTUAL_FUND,
            com.neeraj.fin.data.db.AssetType.STOCKS,
            com.neeraj.fin.data.db.AssetType.CRYPTO,
            com.neeraj.fin.data.db.AssetType.BANK,
            com.neeraj.fin.data.db.AssetType.CASH,
            com.neeraj.fin.data.db.AssetType.CREDIT_CARD -> 35 * day
            com.neeraj.fin.data.db.AssetType.PROPERTY,
            com.neeraj.fin.data.db.AssetType.OTHER -> 370 * day
            else -> 100 * day // FD, EPF/PPF, gold, loans, other dues
        }
        val latestByAsset = app.repository.valuesOnce().groupBy { it.assetId }
            .mapValues { (_, vs) -> vs.maxOf { it.timestamp } }
        val stale = app.repository.assetsOnce().filter { a ->
            val last = latestByAsset[a.id] ?: return@filter false
            now - last > thresholdFor(a.type)
        }
        if (stale.isEmpty()) return
        app.settings.setMarker(marker)
        val names = stale.take(3).joinToString(", ") { it.name } +
            if (stale.size > 3) " +${stale.size - 3} more" else ""
        post(
            app, CHANNEL_SUMMARY, 4000,
            "Wealth values need a refresh",
            "$names — update them so your net worth trend stays accurate."
        )
    }

    /** Due payment reminders: once per reminder per period, on/after the due day. */
    suspend fun reminderNudge(app: FinApp) {
        if (!app.settings.notificationsEnabled.first()) return
        val today = LocalDate.now()
        val due = app.repository.remindersOnce().filter { it.enabled && isDue(it, today) }
        for (r in due) {
            val marker = "reminder:${r.id}:${periodKey(r, today)}"
            if (app.settings.isMarkerSet(marker)) continue
            app.settings.setMarker(marker)
            val amount = if (r.amountMinor > 0)
                " of ${Format.money(r.amountMinor, app.settings.currencyCode.first())}" else ""
            post(
                app, CHANNEL_REMINDERS, (5000 + r.id).toInt(),
                "Payment due: ${r.title}",
                "Your ${r.title}$amount is due — mark it done in Kosh once paid."
            )
        }
    }

    fun isDue(r: com.neeraj.fin.data.db.Reminder, today: LocalDate): Boolean = when (r.recurrence) {
        com.neeraj.fin.data.db.ReminderRecurrence.MONTHLY ->
            today.dayOfMonth >= r.dayOfMonth && r.lastDoneKey != "%04d-%02d".format(today.year, today.monthValue)
        com.neeraj.fin.data.db.ReminderRecurrence.YEARLY ->
            (today.monthValue > r.monthOfYear ||
                (today.monthValue == r.monthOfYear && today.dayOfMonth >= r.dayOfMonth)) &&
                r.lastDoneKey != today.year.toString()
        else -> r.lastDoneKey == null && r.dueMillis != null &&
            !Format.toLocalDate(r.dueMillis).isAfter(today)
    }

    fun periodKey(r: com.neeraj.fin.data.db.Reminder, today: LocalDate): String = when (r.recurrence) {
        com.neeraj.fin.data.db.ReminderRecurrence.MONTHLY -> "%04d-%02d".format(today.year, today.monthValue)
        com.neeraj.fin.data.db.ReminderRecurrence.YEARLY -> today.year.toString()
        else -> "once"
    }
}
