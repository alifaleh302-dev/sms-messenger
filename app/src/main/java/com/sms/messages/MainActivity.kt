package com.sms.messages

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TextSnippet
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.sms.messages.ui.screens.ArchivedScreen
import com.sms.messages.ui.screens.BlockedScreen
import com.sms.messages.ui.screens.ChatScreen
import com.sms.messages.ui.screens.ComposeMessageScreen
import com.sms.messages.ui.screens.ConversationsScreen
import com.sms.messages.ui.screens.PermissionScreen
import com.sms.messages.ui.screens.ScheduledScreen
import com.sms.messages.ui.screens.SearchScreen
import com.sms.messages.ui.screens.SettingsScreen
import com.sms.messages.ui.screens.TemplatesScreen
import com.sms.messages.ui.theme.SmsTheme
import com.sms.messages.ui.viewmodel.SettingsViewModel
import com.sms.messages.ui.viewmodel.VmFactory
import com.sms.messages.util.Permissions

class MainActivity : ComponentActivity() {

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
            val settingsViewModel: SettingsViewModel = viewModel(factory = VmFactory.Factory)
            val settings by settingsViewModel.settings.collectAsStateWithLifecycle()
            var hasSms by remember { mutableStateOf(Permissions.hasSms(this)) }
            var requested by remember { mutableStateOf(false) }

            LaunchedEffect(Unit) {
                settingsViewModel.refreshDefaultRole()
            }

            SmsTheme(themeMode = settings.themeMode) {
                if (hasSms) {
                    MessagesApp(
                        initialConversationId = pendingConversationId.value,
                        initialAddress = pendingAddress.value,
                    )
                } else {
                    Column(Modifier.fillMaxSize()) {
                        PermissionScreen(
                            message = "To send and read text messages this app needs SMS permission.",
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

    val tabs = listOf(
        Triple(Routes.CONVERSATIONS, "Chats", Icons.Filled.ChatBubble),
        Triple(Routes.SCHEDULED, "Scheduled", Icons.Filled.Schedule),
        Triple(Routes.TEMPLATES, "Templates", Icons.Filled.TextSnippet),
        Triple(Routes.SETTINGS, "Settings", Icons.Filled.Settings),
    )

    LaunchedEffect(initialConversationId, initialAddress) {
        when {
            initialConversationId != null -> navController.navigate(Routes.chat(initialConversationId))
            !initialAddress.isNullOrBlank() -> navController.navigate(Routes.compose(initialAddress))
        }
    }

    Scaffold(
        bottomBar = {
            if (currentRoute in tabs.map { it.first }) {
                NavigationBar {
                    tabs.forEach { (route, label, icon) ->
                        NavigationBarItem(
                            selected = currentRoute == route,
                            onClick = {
                                navController.navigate(route) {
                                    popUpTo(Routes.CONVERSATIONS) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(icon, contentDescription = label) },
                            label = { Text(label) },
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            if (currentRoute == Routes.CONVERSATIONS) {
                FloatingActionButton(onClick = { navController.navigate(Routes.compose()) }) {
                    Icon(Icons.Filled.Edit, contentDescription = "New message")
                }
            }
        },
    ) { padding ->
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
                composable(Routes.TEMPLATES) { TemplatesScreen() }
                composable(Routes.SCHEDULED) { ScheduledScreen() }
                composable(Routes.BLOCKED) { BlockedScreen() }
                composable(Routes.SETTINGS) {
                    SettingsScreen(onOpenBlocked = { navController.navigate(Routes.BLOCKED) })
                }
            }
        }
    }
}
