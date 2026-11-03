package com.sms.messages.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val language: AppLanguage = AppLanguage.SYSTEM,
    val notificationsEnabled: Boolean = true,
    val vibrate: Boolean = true,
    val soundEnabled: Boolean = true,
    val deliveryReports: Boolean = false,
    val autoRetry: Boolean = true,
    val signature: String = "",
    val defaultSubscriptionId: Int = -1,
)

class SettingsRepository(private val context: Context) {

    private object Keys {
        val THEME = stringPreferencesKey("theme_mode")
        val LANGUAGE = stringPreferencesKey("language")
        val NOTIFICATIONS = booleanPreferencesKey("notifications_enabled")
        val VIBRATE = booleanPreferencesKey("vibrate")
        val SOUND = booleanPreferencesKey("sound")
        val DELIVERY_REPORTS = booleanPreferencesKey("delivery_reports")
        val AUTO_RETRY = booleanPreferencesKey("auto_retry")
        val SIGNATURE = stringPreferencesKey("signature")
        val DEFAULT_SUBSCRIPTION = intPreferencesKey("default_subscription")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        AppSettings(
            themeMode = prefs[Keys.THEME]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
                ?: ThemeMode.SYSTEM,
            language = prefs[Keys.LANGUAGE]?.let { runCatching { AppLanguage.valueOf(it) }.getOrNull() }
                ?: AppLanguage.SYSTEM,
            notificationsEnabled = prefs[Keys.NOTIFICATIONS] ?: true,
            vibrate = prefs[Keys.VIBRATE] ?: true,
            soundEnabled = prefs[Keys.SOUND] ?: true,
            deliveryReports = prefs[Keys.DELIVERY_REPORTS] ?: false,
            autoRetry = prefs[Keys.AUTO_RETRY] ?: true,
            signature = prefs[Keys.SIGNATURE] ?: "",
            defaultSubscriptionId = prefs[Keys.DEFAULT_SUBSCRIPTION] ?: -1,
        )
    }

    suspend fun setTheme(mode: ThemeMode) = edit { it[Keys.THEME] = mode.name }

    suspend fun setLanguage(language: AppLanguage) = edit { it[Keys.LANGUAGE] = language.name }

    suspend fun setNotificationsEnabled(enabled: Boolean) = edit { it[Keys.NOTIFICATIONS] = enabled }

    suspend fun setVibrate(enabled: Boolean) = edit { it[Keys.VIBRATE] = enabled }

    suspend fun setSoundEnabled(enabled: Boolean) = edit { it[Keys.SOUND] = enabled }

    suspend fun setDeliveryReports(enabled: Boolean) = edit { it[Keys.DELIVERY_REPORTS] = enabled }

    suspend fun setAutoRetry(enabled: Boolean) = edit { it[Keys.AUTO_RETRY] = enabled }

    suspend fun setSignature(value: String) = edit { it[Keys.SIGNATURE] = value }

    suspend fun setDefaultSubscription(id: Int) = edit { it[Keys.DEFAULT_SUBSCRIPTION] = id }

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.dataStore.edit(block)
    }
}
