package com.sms.messages.screenshot

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.sms.messages.data.AppDatabase
import com.sms.messages.data.CategoryEntity
import com.sms.messages.data.CategoryMemberEntity
import com.sms.messages.data.ConversationEntity
import com.sms.messages.data.MessageEntity
import com.sms.messages.data.MessageStatus
import com.sms.messages.data.MessageType
import com.sms.messages.data.ScheduledMessageEntity
import com.sms.messages.data.SettingsRepository
import com.sms.messages.data.SmsRepository
import com.sms.messages.data.TemplateEntity
import com.sms.messages.notifications.MessageNotifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

/**
 * A populated in-memory database for the screenshots.
 *
 * The screens are driven by their real view models reading this data, so what is captured is
 * the same layout path the app takes: the conversation rows, the tab counts and the chat
 * bubbles are all produced by the production mapping rather than by a hand-built state.
 *
 * The data is chosen to make layout problems visible — a pinned row, a muted row, an
 * unnamed sender, unread counts in single and double digits, a long snippet that must
 * truncate, a draft, and a thread that mixes both directions, every delivery status, a
 * quoted reply and a long line that has to wrap.
 */
object SeedData {

    private const val MINUTE = 60_000L
    private const val HOUR = 60 * MINUTE
    private const val DAY = 24 * HOUR

    lateinit var database: AppDatabase
        private set
    lateinit var repository: SmsRepository
        private set
    lateinit var settings: SettingsRepository
        private set
    lateinit var notifier: MessageNotifier
        private set
    lateinit var context: Context
        private set

    /** Id of the thread the chat screen renders. */
    var chatConversationId: Long = 0
        private set

    fun build(): SeedData {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = SmsRepository(context, database)
        settings = SettingsRepository(context)
        notifier = MessageNotifier(
            context,
            repository,
            settings,
            CoroutineScope(Dispatchers.Unconfined),
        )
        runBlocking { seed() }
        return this
    }

    private fun now() = System.currentTimeMillis()

    private suspend fun seed() {
        val work = database.categories().insert(
            CategoryEntity(name = "Work", iconIndex = 0, colorIndex = 2, position = 1)
        )
        val family = database.categories().insert(
            CategoryEntity(name = "Family", iconIndex = 1, colorIndex = 5, position = 2)
        )

        val t = now()
        val conversations = listOf(
            ConversationEntity(
                address = "+966500000001",
                displayName = "نورة العتيبي",
                snippet = "وصلت الفاتورة، شكرًا لك",
                lastMessageAt = t - 4 * MINUTE,
                unreadCount = 3,
                pinned = true,
                colorIndex = 2,
            ),
            ConversationEntity(
                address = "8888",
                displayName = "AlRajhiBank",
                snippet = "Your verification code is 445566",
                lastMessageAt = t - 26 * MINUTE,
                unreadCount = 1,
                colorIndex = 0,
            ),
            ConversationEntity(
                address = "+966555000111",
                displayName = "Mohammed Al-Harbi",
                snippet = "See you at the office tomorrow morning",
                lastMessageAt = t - 5 * HOUR,
                muted = true,
                draft = "I will bring the",
                colorIndex = 5,
            ),
            ConversationEntity(
                address = "+966533444555",
                displayName = null,
                snippet = "تم إرسال الطلب رقم ٩٩٣٤",
                lastMessageAt = t - 2 * DAY,
                colorIndex = 7,
            ),
            ConversationEntity(
                address = "+966577888999",
                displayName = "سارة",
                snippet = "المكالمة كانت قصيرة لكن مفيدة جدًا، أرسلي لي الملخص",
                lastMessageAt = t - 3 * DAY,
                unreadCount = 12,
                colorIndex = 3,
            ),
        )
        val ids = conversations.map { database.conversations().insert(it) }

        // File two threads under custom tabs so the strip shows real counts.
        database.categories().addMember(CategoryMemberEntity(work, ids[0]))
        database.categories().addMember(CategoryMemberEntity(work, ids[2]))
        database.categories().addMember(CategoryMemberEntity(family, ids[4]))

        chatConversationId = ids[0]
        seedThread(ids[0])

        // A second thread with inbound text so the smart tabs have something to classify.
        database.messages().insert(
            MessageEntity(
                conversationId = ids[1],
                address = "8888",
                body = "Your verification code is 445566",
                timestamp = t - 26 * MINUTE,
                type = MessageType.INBOX,
                status = MessageStatus.NONE,
                read = false,
                otp = true,
            )
        )

        database.templates().insert(TemplateEntity(title = "Greeting", body = "Hello! How are you?"))
        database.templates().insert(
            TemplateEntity(title = "On my way", body = "I am on my way, see you soon.", useCount = 4)
        )
        database.scheduled().insert(
            ScheduledMessageEntity(
                address = "+966555000111",
                displayName = "Mohammed Al-Harbi",
                body = "Reminder: the meeting starts at 9.",
                scheduledAt = t + 2 * HOUR,
            )
        )
        database.blocked().insert(
            com.sms.messages.data.BlockedNumberEntity(address = "+966500000099", displayName = "Spam")
        )
    }

    private suspend fun seedThread(conversationId: Long) {
        val t = now()
        fun inbound(id: Long, body: String, at: Long, otp: Boolean = false) = MessageEntity(
            id = id,
            conversationId = conversationId,
            address = "+966500000001",
            body = body,
            timestamp = at,
            type = MessageType.INBOX,
            status = MessageStatus.NONE,
            read = true,
            otp = otp,
        )
        fun outbound(id: Long, body: String, at: Long, status: MessageStatus) = MessageEntity(
            id = id,
            conversationId = conversationId,
            address = "+966500000001",
            body = body,
            timestamp = at,
            type = MessageType.OUTBOX,
            status = status,
            read = true,
        )

        val thread = listOf(
            inbound(1, "مرحبًا! كيف حالك؟", t - 3 * HOUR),
            outbound(2, "بخير، شكرًا لك", t - 3 * HOUR + MINUTE, MessageStatus.DELIVERED),
            inbound(
                3,
                "Great, that works. I will send over the full report with the numbers " +
                    "and the breakdown first thing in the morning.",
                t - 2 * HOUR,
            ),
            outbound(4, "تمام، بانتظار التقرير", t - 2 * HOUR + MINUTE, MessageStatus.SENT),
            inbound(5, "Your verification code is 445566", t - 40 * MINUTE, otp = true),
            outbound(6, "وصلت الفاتورة، شكرًا لك", t - 30 * MINUTE, MessageStatus.PENDING),
            outbound(7, "هذه رسالة لم تُرسل بعد", t - 20 * MINUTE, MessageStatus.FAILED),
            inbound(8, "آخر رسالة في المحادثة", t - 5 * MINUTE),
        )
        thread.forEach { database.messages().insert(it) }
    }

    /** A thread id for a conversation that has no messages, to exercise the empty state. */
    suspend fun emptyConversation(): Long = database.conversations().insert(
        ConversationEntity(
            address = "+966512000000",
            displayName = "Empty Thread",
            snippet = "",
            lastMessageAt = now() - 10 * DAY,
        )
    )
}
