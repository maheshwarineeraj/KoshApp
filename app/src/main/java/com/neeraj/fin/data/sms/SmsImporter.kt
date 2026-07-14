package com.neeraj.fin.data.sms

import android.content.Context
import android.provider.Telephony
import com.neeraj.fin.data.FinRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Scans the SMS inbox for past transaction messages and queues them for review. */
class SmsImporter(private val context: Context, private val repository: FinRepository) {

    /** Returns the number of new transactions queued for review. */
    suspend fun scanInbox(days: Int): Int = withContext(Dispatchers.IO) {
        val since = System.currentTimeMillis() - days * 24L * 60 * 60 * 1000
        var added = 0
        val cursor = context.contentResolver.query(
            Telephony.Sms.Inbox.CONTENT_URI,
            arrayOf(Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE),
            "${Telephony.Sms.DATE} >= ?",
            arrayOf(since.toString()),
            "${Telephony.Sms.DATE} DESC"
        )
        cursor?.use {
            val addrIdx = it.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
            val bodyIdx = it.getColumnIndexOrThrow(Telephony.Sms.BODY)
            val dateIdx = it.getColumnIndexOrThrow(Telephony.Sms.DATE)
            while (it.moveToNext()) {
                val sender = it.getString(addrIdx) ?: continue
                val body = it.getString(bodyIdx) ?: continue
                val timestamp = it.getLong(dateIdx)
                val parsed = SmsParser.parse(sender, body) ?: continue
                if (repository.offerParsedSms(sender, body, timestamp, parsed)) added++
            }
        }
        added
    }
}
