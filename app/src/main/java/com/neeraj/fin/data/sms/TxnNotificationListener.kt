package com.neeraj.fin.data.sms

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.neeraj.fin.FinApp
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Captures transactions from notifications instead of SMS — the path that is
 * publishable on Google Play, where READ_SMS/RECEIVE_SMS are not allowed.
 *
 * Bank-SMS content still reaches us here through the messaging app's own
 * notification, and bank/UPI apps post their own transaction notifications.
 * Everything goes through the same parser, dedupe, and approval queue as the
 * SMS path, so enabling both never double-counts (same-amount reports within
 * 10 minutes are skipped).
 *
 * Limitation: a listener only sees notifications posted while it is enabled.
 * There is no way to read past messages — history comes from an SMS inbox
 * scan (when that permission is available) or from a backup restore.
 */
class TxnNotificationListener : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName == packageName) return
        if (sbn.isOngoing) return
        val n = sbn.notification ?: return
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return

        val extras = n.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
            ?: extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
            ?: return

        val app = applicationContext as? FinApp ?: return
        app.appScope.launch {
            if (!app.settings.notificationCapture.first()) return@launch
            // Title is the SMS sender for messaging apps ("VM-HDFCBK") or the
            // bank's own name for its app; personal numbers are filtered out
            // by the parser just like SMS senders.
            val sender = title.ifBlank { sbn.packageName }
            val parsed = SmsParser.parse(sender, text)
            if (parsed != null) {
                app.repository.offerParsedSms(sender, text, sbn.postTime, parsed)
            } else {
                BillDueParser.parse(sender, text)?.let { bill ->
                    com.neeraj.fin.util.BillDueExtractor.dueDateMillis(text)?.let { due ->
                        app.repository.offerBillDueReminder(bill.title, bill.amountMinor, due, bill.dueDayHint)
                    }
                }
            }
        }
    }
}
