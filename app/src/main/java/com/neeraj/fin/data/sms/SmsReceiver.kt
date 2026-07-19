package com.neeraj.fin.data.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.neeraj.fin.FinApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Captures incoming SMS in real time and queues parsed transactions for review. */
class SmsReceiver : BroadcastReceiver() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        // Multipart messages arrive as fragments from the same sender — stitch them.
        val bySender = messages.filter { it.originatingAddress != null }
            .groupBy { it.originatingAddress!! }

        val app = context.applicationContext as FinApp
        val pendingResult = goAsync()
        scope.launch {
            try {
                if (!app.settings.smsAutoCapture.first()) return@launch
                for ((sender, parts) in bySender) {
                    val body = parts.joinToString("") { it.messageBody ?: "" }
                    val timestamp = parts.first().timestampMillis
                    val parsed = SmsParser.parse(sender, body)
                    if (parsed != null) {
                        app.repository.offerParsedSms(sender, body, timestamp, parsed)
                    } else {
                        BillDueParser.parse(sender, body)?.let { bill ->
                            com.neeraj.fin.util.BillDueExtractor.dueDateMillis(body)?.let { due ->
                                app.repository.offerBillDueReminder(bill.title, bill.amountMinor, due, bill.dueDayHint)
                            }
                        }
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
