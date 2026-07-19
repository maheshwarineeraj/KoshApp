package com.neeraj.fin.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

class SettingsStore(private val context: Context) {
    private val currencyKey = stringPreferencesKey("currency_code")
    private val autoCaptureKey = booleanPreferencesKey("sms_auto_capture")
    private val appLockKey = booleanPreferencesKey("app_lock")
    private val notificationsKey = booleanPreferencesKey("notifications_enabled")
    private val privacyAcceptedKey = booleanPreferencesKey("privacy_accepted")
    private val blockScreenshotsKey = booleanPreferencesKey("block_screenshots")
    private val notifCaptureKey = booleanPreferencesKey("notification_capture")
    private val markersKey = stringSetPreferencesKey("notification_markers")

    // Scheduled auto-export of encrypted backups to a user-chosen SAF folder.
    // The passphrase must be stored for unattended encryption; it lives in
    // app-private storage — same trust level as the database it protects.
    private val autoBackupFolderKey = stringPreferencesKey("auto_backup_folder")
    private val autoBackupPassKey = stringPreferencesKey("auto_backup_pass")
    private val autoBackupFreqKey = stringPreferencesKey("auto_backup_freq")
    private val autoBackupLastKey = stringPreferencesKey("auto_backup_last")

    val currencyCode: Flow<String> = context.dataStore.data.map { it[currencyKey] ?: "INR" }
    val smsAutoCapture: Flow<Boolean> = context.dataStore.data.map { it[autoCaptureKey] ?: true }
    val appLock: Flow<Boolean> = context.dataStore.data.map { it[appLockKey] ?: false }
    val notificationsEnabled: Flow<Boolean> = context.dataStore.data.map { it[notificationsKey] ?: true }
    val privacyAccepted: Flow<Boolean> = context.dataStore.data.map { it[privacyAcceptedKey] ?: false }
    val blockScreenshots: Flow<Boolean> = context.dataStore.data.map { it[blockScreenshotsKey] ?: true }
    val notificationCapture: Flow<Boolean> = context.dataStore.data.map { it[notifCaptureKey] ?: false }
    val autoBackupFolder: Flow<String?> = context.dataStore.data.map { it[autoBackupFolderKey] }
    val autoBackupPassphrase: Flow<String?> = context.dataStore.data.map { it[autoBackupPassKey] }
    val autoBackupFrequency: Flow<String> = context.dataStore.data.map { it[autoBackupFreqKey] ?: "WEEKLY" }
    val autoBackupLast: Flow<String?> = context.dataStore.data.map { it[autoBackupLastKey] }

    suspend fun setCurrencyCode(code: String) {
        context.dataStore.edit { it[currencyKey] = code }
    }

    suspend fun setSmsAutoCapture(enabled: Boolean) {
        context.dataStore.edit { it[autoCaptureKey] = enabled }
    }

    suspend fun setAppLock(enabled: Boolean) {
        context.dataStore.edit { it[appLockKey] = enabled }
    }

    suspend fun setNotificationsEnabled(enabled: Boolean) {
        context.dataStore.edit { it[notificationsKey] = enabled }
    }

    suspend fun setPrivacyAccepted(accepted: Boolean) {
        context.dataStore.edit { it[privacyAcceptedKey] = accepted }
    }

    suspend fun setBlockScreenshots(enabled: Boolean) {
        context.dataStore.edit { it[blockScreenshotsKey] = enabled }
    }

    suspend fun setNotificationCapture(enabled: Boolean) {
        context.dataStore.edit { it[notifCaptureKey] = enabled }
    }

    suspend fun enableAutoBackup(folderUri: String, passphrase: String, frequency: String) {
        context.dataStore.edit {
            it[autoBackupFolderKey] = folderUri
            it[autoBackupPassKey] = passphrase
            it[autoBackupFreqKey] = frequency
            it.remove(autoBackupLastKey)
        }
    }

    suspend fun disableAutoBackup() {
        context.dataStore.edit {
            it.remove(autoBackupFolderKey)
            it.remove(autoBackupPassKey)
            it.remove(autoBackupFreqKey)
            it.remove(autoBackupLastKey)
        }
    }

    suspend fun setAutoBackupLast(key: String?) {
        context.dataStore.edit {
            if (key == null) it.remove(autoBackupLastKey) else it[autoBackupLastKey] = key
        }
    }

    // Search vocabulary learned from behavior: "term>merchant" pairs recorded
    // when a search result is opened whose text didn't literally contain the
    // term (e.g. "cab>uber"). Used to widen future matches. Local only.
    private val searchSynKey = stringSetPreferencesKey("search_synonyms")
    val searchSynonyms: Flow<Set<String>> = context.dataStore.data.map { it[searchSynKey] ?: emptySet() }

    suspend fun learnSearchSynonym(term: String, merchant: String) {
        if (term.length < 3 || merchant.isBlank()) return
        context.dataStore.edit { prefs ->
            val cur = prefs[searchSynKey] ?: emptySet()
            prefs[searchSynKey] = (cur + "$term>$merchant").takeLast(200).toSet()
        }
    }

    // One-shot markers so alerts fire once per period (e.g. "budget:3:2026-07-01:80").
    suspend fun isMarkerSet(marker: String): Boolean =
        context.dataStore.data.first()[markersKey]?.contains(marker) == true

    suspend fun setMarker(marker: String) {
        context.dataStore.edit { prefs ->
            val current = prefs[markersKey] ?: emptySet()
            // Keep the set from growing unboundedly
            prefs[markersKey] = (current + marker).takeLast(200).toSet()
        }
    }

    private fun Set<String>.takeLast(n: Int): List<String> = toList().takeLast(n)
}
