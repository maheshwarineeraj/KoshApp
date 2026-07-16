package com.neeraj.fin

import android.app.Application
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.neeraj.fin.data.FinRepository
import com.neeraj.fin.data.SettingsStore
import com.neeraj.fin.data.backup.BackupManager
import com.neeraj.fin.data.db.AppDatabase
import com.neeraj.fin.data.sms.SmsImporter
import com.neeraj.fin.notify.DailyWorker
import com.neeraj.fin.notify.Notifications
import com.neeraj.fin.widget.updateKoshWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class FinApp : Application() {

    val database: AppDatabase by lazy { AppDatabase.get(this) }
    val repository: FinRepository by lazy { FinRepository(database) }
    val settings: SettingsStore by lazy { SettingsStore(this) }
    val backupManager: BackupManager by lazy { BackupManager(this, repository, settings) }
    val smsImporter: SmsImporter by lazy { SmsImporter(this, repository) }

    /** App-lock session state: true once the user has authenticated this session. */
    val unlocked = MutableStateFlow(false)

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        Notifications.ensureChannels(this)
        appScope.launch {
            repository.seedDefaultsIfEmpty()
            repository.applyDueRecurringRules()
            backupManager.autoBackupIfDue()
            updateKoshWidget(this@FinApp)
        }

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "kosh-daily",
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<DailyWorker>(24, TimeUnit.HOURS).build()
        )

        // Re-lock only after the app has been in the background for a while.
        // Quick round-trips (camera scan, photo picker, notification shade)
        // should not force another unlock.
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            private var backgroundedAt = 0L
            override fun onStop(owner: LifecycleOwner) {
                backgroundedAt = System.currentTimeMillis()
            }
            override fun onStart(owner: LifecycleOwner) {
                if (backgroundedAt > 0 && System.currentTimeMillis() - backgroundedAt > 60_000) {
                    unlocked.value = false
                }
            }
        })
    }
}
