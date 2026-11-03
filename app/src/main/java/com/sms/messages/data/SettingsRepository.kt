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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/** Bounds for the undo-send window; the requirement is three to five seconds. */
const val MIN_UNDO_SEND_SECONDS = 3
const val MAX_UNDO_SEND_SECONDS = 5
const val DEFAULT_UNDO_SEND_SECONDS = 4

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
    /** Seconds to hold an outgoing message before it reaches the network; 0 disables it. */
    val undoSendSeconds: Int = DEFAULT_UNDO_SEND_SECONDS,
    /** Delete one-time-code messages a day after they arrive. */
    val autoDeleteOtp: Boolean = false,
    /** Require a fingerprint or PIN before the conversation list is shown. */
    val appLockEnabled: Boolean = false,
    /** Allow the fingerprint prompt; when off the PIN is always used. */
    val biometricUnlock: Boolean = true,
    /** True when a PIN has been set, which is what makes the lock usable at all. */
    val hasPin: Boolean = false,
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
        val UNDO_SEND_SECONDS = intPreferencesKey("undo_send_seconds")
        val AUTO_DELETE_OTP = booleanPreferencesKey("auto_delete_otp")
        val APP_LOCK = booleanPreferencesKey("app_lock_enabled")
        val BIOMETRIC_UNLOCK = booleanPreferencesKey("biometric_unlock")
        val PIN_HASH = stringPreferencesKey("app_lock_pin_hash")
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
            undoSendSeconds = prefs[Keys.UNDO_SEND_SECONDS] ?: DEFAULT_UNDO_SEND_SECONDS,
            autoDeleteOtp = prefs[Keys.AUTO_DELETE_OTP] ?: false,
            appLockEnabled = prefs[Keys.APP_LOCK] ?: false,
            biometricUnlock = prefs[Keys.BIOMETRIC_UNLOCK] ?: true,
            hasPin = !prefs[Keys.PIN_HASH].isNullOrBlank(),
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

    suspend fun setUndoSendSeconds(seconds: Int) = edit {
        it[Keys.UNDO_SEND_SECONDS] = seconds.coerceIn(0, MAX_UNDO_SEND_SECONDS)
    }

    suspend fun setAutoDeleteOtp(enabled: Boolean) = edit { it[Keys.AUTO_DELETE_OTP] = enabled }

    suspend fun setAppLockEnabled(enabled: Boolean) = edit { it[Keys.APP_LOCK] = enabled }

    suspend fun setBiometricUnlock(enabled: Boolean) = edit { it[Keys.BIOMETRIC_UNLOCK] = enabled }

    /**
     * Stores the lock PIN as a salted hash rather than the digits themselves, so a backup
     * of the settings file does not hand over the code.
     */
    suspend fun setPin(pin: String) = edit { prefs ->
        if (pin.isBlank()) {
            prefs.remove(Keys.PIN_HASH)
        } else {
            prefs[Keys.PIN_HASH] = PinHasher.hash(pin)
        }
    }

    /** Checks a PIN against the stored hash. False when no PIN has been set. */
    suspend fun verifyPin(pin: String): Boolean {
        val stored = context.dataStore.data.first()[Keys.PIN_HASH]
        return !stored.isNullOrBlank() && PinHasher.verify(pin, stored)
    }

    suspend fun clearPin() = edit { it.remove(Keys.PIN_HASH) }



    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.dataStore.edit(block)
    }
}
