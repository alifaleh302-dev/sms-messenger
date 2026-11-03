package com.sms.messages

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import dagger.hilt.android.AndroidEntryPoint
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.sms.messages.R
import com.sms.messages.data.AppLanguage
import com.sms.messages.ui.screens.ArchivedScreen
import com.sms.messages.ui.screens.BlockedScreen
import com.sms.messages.ui.screens.ChatScreen
import com.sms.messages.ui.screens.ComposeMessageScreen
import com.sms.messages.ui.screens.ConversationsScreen
import com.sms.messages.ui.screens.LockScreen
import com.sms.messages.ui.screens.PermissionScreen
import com.sms.messages.ui.screens.ScheduledScreen
import com.sms.messages.ui.screens.SearchScreen
import com.sms.messages.ui.screens.StarredScreen
import com.sms.messages.ui.screens.SettingsScreen
import com.sms.messages.ui.screens.TemplatesScreen
import com.sms.messages.ui.screens.TrashScreen
import com.sms.messages.ui.theme.SmsTheme
import com.sms.messages.ui.viewmodel.SettingsViewModel
import com.sms.messages.util.Permissions

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { }

    private val pendingConversationId = mutableStateOf<Long?>(null)
    private val pendingAddress = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        readIntent(intent)

        val requestedPermissions = buildList {
            addAll(Permissions.SMS_PERMISSIONS)
            addAll(Permissions.NOTIFICATION_PERMISSION)
            addAll(arrayOf(Manifest.permission.READ_CONTACTS))
        }.toTypedArray()

        if (!Permissions.hasSms(this)) {
            permissionLauncher.launch(requestedPermissions)
        }

        setContent {
            val settingsViewModel: SettingsViewModel = hiltViewModel()
            val settings by settingsViewModel.settings.collectAsStateWithLifecycle()
            var hasSms by remember { mutableStateOf(Permissions.hasSms(this)) }
            var requested by remember { mutableStateOf(false) }

            LaunchedEffect(Unit) {
                settingsViewModel.refreshDefaultRole()
            }

            LaunchedEffect(settings.language) {
                val locales = when (settings.language) {
                    AppLanguage.SYSTEM -> null
                    AppLanguage.ENGLISH -> LocaleListCompat.forLanguageTags("en")
                    AppLanguage.ARABIC -> LocaleListCompat.forLanguageTags("ar")
                }
                if (AppCompatDelegate.getApplicationLocales() != locales) {
                    AppCompatDelegate.setApplicationLocales(locales ?: LocaleListCompat.getEmptyLocaleList())
                }
            }

            // The lock is evaluated before anything is drawn, so a locked app never shows
            // a conversation even for a frame. Unlocking lasts for the process lifetime.
            var unlocked by remember { mutableStateOf(!settings.appLockEnabled) }
            LaunchedEffect(settings.appLockEnabled) {
                if (!settings.appLockEnabled) unlocked = true
            }

            SmsTheme(themeMode = settings.themeMode) {
                if (settings.appLockEnabled && !unlocked) {
                    LockScreen(
                        pinSet = settings.hasPin,
                        biometricEnabled = settings.biometricUnlock,
                        onVerifyPin = settingsViewModel::verifyPin,
                        onUnlock = { unlocked = true },
                        onDisableLock = {
                            // The lock cannot be satisfied without a PIN, so leaving it on
                            // would lock the user out permanently. Turning it off is the only
                            // way back in; they can set a PIN and re-enable it afterwards.
                            unlocked = true
                            settingsViewModel.setAppLock(false)
                        },
                    )
                } else if (hasSms) {
                    MessagesApp(
                        initialConversationId = pendingConversationId.value,
                        initialAddress = pendingAddress.value,
                    )
                } else {
                    Column(Modifier.fillMaxSize()) {
                        PermissionScreen(
                            message = if (hasSms) stringResource(R.string.permission_body) else stringResource(R.string.permission_sms_denied),
                            onRequest = {
                                requested = true
                                permissionLauncher.launch(requestedPermissions)
                            },
                        )
                        LaunchedEffect(requested) {
                            if (requested) hasSms = Permissions.hasSms(this@MainActivity)
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        readIntent(intent)
    }

    /** Pulls a conversation id or a recipient out of the launching intent, if present. */
    private fun readIntent(intent: Intent?) {
        intent ?: return
        val conversationId = intent.getLongExtra(EXTRA_CONVERSATION_ID, -1L)
        if (conversationId > 0) pendingConversationId.value = conversationId

        val extraAddress = intent.getStringExtra(EXTRA_ADDRESS)
        val dataAddress = intent.data?.schemeSpecificPart?.trim()
        val recipient = extraAddress?.takeIf { it.isNotBlank() }
            ?: dataAddress?.takeIf { it.isNotBlank() }
        if (!recipient.isNullOrBlank()) {
            pendingConversationId.value = null
            pendingAddress.value = recipient
        }
    }

    companion object {
        const val EXTRA_CONVERSATION_ID = "extra_conversation_id"
        const val EXTRA_ADDRESS = "extra_address"
    }
}

private object Routes {
    const val CONVERSATIONS = "conversations"
    const val CHAT = "chat/{conversationId}"
    const val COMPOSE = "compose?address={address}"
    const val SEARCH = "search"
    const val ARCHIVED = "archived"
    const val TEMPLATES = "templates"
    const val SCHEDULED = "scheduled"
    const val BLOCKED = "blocked"
    const val SETTINGS = "settings"
    const val STARRED = "starred"
    const val TRASH = "trash"

    fun chat(id: Long) = "chat/$id"
    fun compose(address: String? = null) =
        if (address.isNullOrBlank()) {
            "compose?address="
        } else {
            "compose?address=${Uri.encode(address)}"
        }
}

@Composable
private fun MessagesApp(
    initialConversationId: Long?,
    initialAddress: String?,
) {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route

    LaunchedEffect(initialConversationId, initialAddress) {
        when {
            initialConversationId != null -> navController.navigate(Routes.chat(initialConversationId))
            !initialAddress.isNullOrBlank() -> navController.navigate(Routes.compose(initialAddress))
        }
    }

    Scaffold { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            NavHost(navController = navController, startDestination = Routes.CONVERSATIONS) {
                composable(Routes.CONVERSATIONS) {
                    ConversationsScreen(
                        onOpenConversation = { navController.navigate(Routes.chat(it)) },
                        onNewMessage = { navController.navigate(Routes.compose()) },
                        onOpenArchived = { navController.navigate(Routes.ARCHIVED) },
                        onOpenSearch = { navController.navigate(Routes.SEARCH) },
                        onOpenBlocked = { navController.navigate(Routes.BLOCKED) },
                        onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                        onOpenScheduled = { navController.navigate(Routes.SCHEDULED) },
                        onOpenTemplates = { navController.navigate(Routes.TEMPLATES) },
                        onOpenStarred = { navController.navigate(Routes.STARRED) },
                        onOpenTrash = { navController.navigate(Routes.TRASH) },
                    )
                }
                composable(
                    route = Routes.CHAT,
                    arguments = listOf(navArgument("conversationId") { type = NavType.StringType }),
                ) {
                    ChatScreen(onBack = { navController.popBackStack() })
                }
                composable(
                    route = Routes.COMPOSE,
                    arguments = listOf(navArgument("address") {
                        type = NavType.StringType
                        defaultValue = ""
                    }),
                ) {
                    ComposeMessageScreen(onBack = { navController.popBackStack() })
                }
                composable(Routes.SEARCH) {
                    SearchScreen(
                        onBack = { navController.popBackStack() },
                        onOpenConversation = { navController.navigate(Routes.chat(it)) },
                    )
                }
                composable(Routes.ARCHIVED) {
                    ArchivedScreen(
                        onBack = { navController.popBackStack() },
                        onOpenConversation = { navController.navigate(Routes.chat(it)) },
                    )
                }
                composable(Routes.TEMPLATES) {
                    TemplatesScreen(onBack = { navController.popBackStack() })
                }
                composable(Routes.SCHEDULED) {
                    ScheduledScreen(onBack = { navController.popBackStack() })
                }
                composable(Routes.BLOCKED) {
                    BlockedScreen(onBack = { navController.popBackStack() })
                }
                composable(Routes.SETTINGS) {
                    SettingsScreen(
                        onOpenBlocked = { navController.navigate(Routes.BLOCKED) },
                        onOpenTrash = { navController.navigate(Routes.TRASH) },
                    )
                }
                composable(Routes.STARRED) {
                    StarredScreen(
                        onBack = { navController.popBackStack() },
                        onOpenConversation = { navController.navigate(Routes.chat(it)) },
                    )
                }
                composable(Routes.TRASH) {
                    TrashScreen(onBack = { navController.popBackStack() })
                }
            }
        }
    }
}
