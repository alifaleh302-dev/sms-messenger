package com.sms.messages.ui.screens

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import com.sms.messages.R
import kotlinx.coroutines.launch

/** Length bounds for the lock PIN, matching the setting's hint. */
private const val MIN_PIN = 4
private const val MAX_PIN = 6

/**
 * The gate shown before the conversation list when app lock is on.
 *
 * The fingerprint prompt is offered first when the device supports it and the user has not
 * turned it off; the PIN is always available as the fallback, so a phone with no enrolled
 * fingerprint is still usable. When no PIN has been set the screen shows a message and an
 * escape button rather than trapping the user behind a lock they never configured.
 */
@Composable
fun LockScreen(
    pinSet: Boolean,
    biometricEnabled: Boolean,
    onVerifyPin: suspend (String) -> Boolean,
    onUnlock: () -> Unit,
    /** Offered only when no PIN is set, so the user is never stuck behind an unusable lock. */
    onDisableLock: () -> Unit,
) {
    val context = LocalContext.current
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    var showPinField by remember { mutableStateOf(!biometricEnabled || !pinSet) }
    val scope = rememberCoroutineScope()

    val canUseBiometric = remember(biometricEnabled, pinSet) {
        biometricEnabled && pinSet &&
            BiometricManager.from(context)
                .canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) ==
            BiometricManager.BIOMETRIC_SUCCESS
    }

    val activity = context as? FragmentActivity
    if (canUseBiometric && activity != null) {
        LaunchedEffect(Unit) {
            val prompt = BiometricPrompt(
                activity,
                androidx.core.content.ContextCompat.getMainExecutor(context),
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        onUnlock()
                    }

                    override fun onAuthenticationError(code: Int, message: CharSequence) {
                        // A cancelled or unavailable prompt must not lock the user out, so the
                        // PIN field is revealed instead.
                        showPinField = true
                    }
                },
            )
            prompt.authenticate(
                BiometricPrompt.PromptInfo.Builder()
                    .setTitle(context.getString(R.string.lock_title))
                    .setSubtitle(context.getString(R.string.lock_use_fingerprint))
                    .setNegativeButtonText(context.getString(R.string.action_cancel))
                    .build()
            )
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.Lock,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.lock_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.lock_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (!pinSet) {
            Spacer(Modifier.height(24.dp))
            Text(
                text = stringResource(R.string.settings_pin_required),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
            Spacer(Modifier.height(12.dp))
            Button(onClick = onDisableLock) {
                Text(stringResource(R.string.lock_turn_off))
            }
            return@Column
        }

        if (showPinField) {
            Spacer(Modifier.height(24.dp))
            OutlinedTextField(
                value = pin,
                onValueChange = {
                    if (it.length <= MAX_PIN && it.all(Char::isDigit)) {
                        pin = it
                        error = false
                    }
                },
                label = { Text(stringResource(R.string.lock_enter_pin)) },
                singleLine = true,
                isError = error,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.NumberPassword,
                    imeAction = ImeAction.Done,
                ),
                supportingText = if (error) {
                    { Text(stringResource(R.string.lock_wrong_pin)) }
                } else {
                    null
                },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = {
                    // Verification is a suspend call because the hash is read from DataStore,
                    // so it runs on the screen's scope and reports back into state.
                    scope.launch {
                        if (onVerifyPin(pin)) {
                            onUnlock()
                        } else {
                            error = true
                            pin = ""
                        }
                    }
                },
                enabled = pin.length in MIN_PIN..MAX_PIN,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.lock_unlock))
            }
            if (canUseBiometric) {
                TextButton(onClick = { showPinField = false }) {
                    Text(stringResource(R.string.lock_use_fingerprint))
                }
            }
        }
    }
}
