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
    private val blockScreenshotsKey = booleanPreferencesKey("block_screenshots")
    private val notifCaptureKey = booleanPreferencesKey("notification_capture")
    private val markersKey = stringSetPreferencesKey("notification_markers")

    val currencyCode: Flow<String> = context.dataStore.data.map { it[currencyKey] ?: "INR" }
    val smsAutoCapture: Flow<Boolean> = context.dataStore.data.map { it[autoCaptureKey] ?: true }
    val appLock: Flow<Boolean> = context.dataStore.data.map { it[appLockKey] ?: false }
    val notificationsEnabled: Flow<Boolean> = context.dataStore.data.map { it[notificationsKey] ?: false }
    val blockScreenshots: Flow<Boolean> = context.dataStore.data.map { it[blockScreenshotsKey] ?: true }
    val notificationCapture: Flow<Boolean> = context.dataStore.data.map { it[notifCaptureKey] ?: false }

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

    suspend fun setBlockScreenshots(enabled: Boolean) {
        context.dataStore.edit { it[blockScreenshotsKey] = enabled }
    }

    suspend fun setNotificationCapture(enabled: Boolean) {
        context.dataStore.edit { it[notifCaptureKey] = enabled }
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
