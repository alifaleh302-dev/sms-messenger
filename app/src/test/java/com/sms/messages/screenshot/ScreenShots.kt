package com.sms.messages.screenshot

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.SavedStateHandle
import com.sms.messages.ui.screens.ChatScreen
import com.sms.messages.ui.screens.ConversationsScreen
import com.sms.messages.ui.screens.LockScreen
import com.sms.messages.ui.screens.SettingsScreen
import com.sms.messages.ui.viewmodel.ChatViewModel
import com.sms.messages.ui.viewmodel.ConversationsViewModel
import com.sms.messages.ui.viewmodel.SettingsViewModel
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The main screens, shot in both languages and both themes.
 *
 * Each screen is driven by its real view model over a seeded in-memory database, so the
 * pictures show the production layout rather than a mock-up. The view models are passed in
 * directly, which is why the screens themselves need no test-only entry point.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = ScreenshotTestBase.ENGLISH)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ScreenshotEnglishTest : ScreenShots()

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = ScreenshotTestBase.ARABIC)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ScreenshotArabicTest : ScreenShots()

/** The shots themselves, shared by the locale-specific subclasses. */
abstract class ScreenShots : ScreenshotTestBase() {

    // ------------------------------------------------------------ conversation list

    @Test
    fun conversationsList() {
        val seed = SeedData.build()
        val conversations = ConversationsViewModel(seed.context, seed.repository)
        shootBoth("conversations", ready = { conversations.uiState.value.items.isNotEmpty() }) {
            Screen { ConversationsList(conversations) }
        }
    }

    @Test
    fun conversationsListCustomTab() {
        val seed = SeedData.build()
        val conversations = ConversationsViewModel(seed.context, seed.repository)
        val workKey = "category:1"
        conversations.selectTab(workKey)

        shootOne("conversations-work-tab", ready = { conversations.uiState.value.selectedTabKey == workKey }) {
            Screen { ConversationsList(conversations) }
        }
    }

    // ------------------------------------------------------------ chat

    @Test
    fun chatThread() {
        val seed = SeedData.build()
        val chat = ChatViewModel(
            repository = seed.repository,
            settingsRepository = seed.settings,
            context = seed.context,
            notifier = seed.notifier,
            savedStateHandle = SavedStateHandle(
                mapOf("conversationId" to seed.chatConversationId.toString())
            ),
        )

        shootBoth("chat", ready = { chat.uiState.value.messages.isNotEmpty() }) {
            Screen { ChatScreen(onBack = {}, viewModel = chat) }
        }
    }

    // ------------------------------------------------------------ settings

    @Test
    fun settings() {
        val seed = SeedData.build()
        val viewModel = SettingsViewModel(seed.context, seed.repository, seed.settings)

        shootBoth("settings") {
            Screen { SettingsScreen(onOpenBlocked = {}, onOpenTrash = {}, viewModel = viewModel) }
        }
    }

    // ------------------------------------------------------------ app lock

    @Test
    fun lockScreen() {
        shootBoth("lock") { Screen { PinLock(biometric = false) } }
    }

    @Test
    fun lockScreenBiometric() {
        shootOne("lock-biometric") { Screen { PinLock(biometric = true) } }
    }

    @Test
    fun lockScreenWithoutPin() {
        shootOne("lock-no-pin") {
            Screen {
                LockScreen(
                    pinSet = false,
                    biometricEnabled = false,
                    onVerifyPin = { false },
                    onUnlock = {},
                    onDisableLock = {},
                )
            }
        }
    }

    // ------------------------------------------------------------ helpers

    @Composable
    private fun ConversationsList(viewModel: ConversationsViewModel) {
        ConversationsScreen(
            onOpenConversation = {},
            onNewMessage = {},
            onOpenArchived = {},
            onOpenSearch = {},
            onOpenBlocked = {},
            onOpenSettings = {},
            onOpenScheduled = {},
            onOpenTemplates = {},
            viewModel = viewModel,
        )
    }

    @Composable
    private fun PinLock(biometric: Boolean) {
        LockScreen(
            pinSet = true,
            biometricEnabled = biometric,
            onVerifyPin = { it == "1234" },
            onUnlock = {},
            onDisableLock = {},
        )
    }

    /**
     * No wrapper surface here on purpose. A surface would paint a correct background over
     * whatever the app actually leaves on screen, which is exactly the bug these shots exist
     * to catch.
     */
    @Composable
    private fun Screen(content: @Composable () -> Unit) {
        content()
    }
}
