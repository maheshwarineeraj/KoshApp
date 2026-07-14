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
            val spent = txns.filter { it.type == TxnType.EXPENSE && it.categoryId == budget.categoryId }
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
}
