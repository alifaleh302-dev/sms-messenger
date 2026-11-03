package com.sms.messages.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "conversations",
    indices = [Index(value = ["address"], unique = true)]
)
data class ConversationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val address: String,
    val displayName: String? = null,
    val snippet: String = "",
    val lastMessageAt: Long = 0,
    val unreadCount: Int = 0,
    val archived: Boolean = false,
    val pinned: Boolean = false,
    val muted: Boolean = false,
    val draft: String = "",
    val colorIndex: Int = 0,
    /** When the conversation was moved to trash, or 0 while it is live. */
    val trashedAt: Long = 0,
)

@Entity(
    tableName = "messages",
    foreignKeys = [
        ForeignKey(
            entity = ConversationEntity::class,
            parentColumns = ["id"],
            childColumns = ["conversationId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("conversationId"),
        Index("address"),
        Index("timestamp"),
        Index("starred"),
        Index("otp"),
    ]
)
data class MessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val conversationId: Long,
    val address: String,
    val body: String = "",
    val timestamp: Long,
    val type: MessageType = MessageType.INBOX,
    val status: MessageStatus = MessageStatus.NONE,
    val read: Boolean = false,
    val subscriptionId: Int = -1,
    val errorCode: Int? = null,
    val attachmentUri: String? = null,
    val attachmentMime: String? = null,
    val attachmentName: String? = null,
    val externalId: Long? = null,
    val starred: Boolean = false,
    /**
     * The message this one quotes, for the "reply to a specific message" flow. A plain
     * column rather than a foreign key on purpose: the quoted message may be deleted
     * while the reply stays, and losing the quote must not delete the reply.
     */
    val replyToId: Long? = null,
    /**
     * True when this message carried a one-time code. Recorded on arrival so the
     * auto-delete sweep can find expired codes with an index instead of re-scanning
     * every body.
     */
    val otp: Boolean = false,
)

/**
 * One row per (message, emoji) pair. Keeping a counter instead of a row per reaction means
 * an inbound burst of reactions from a group chat stays a single row to update.
 */
@Entity(
    tableName = "reactions",
    primaryKeys = ["messageId", "emoji"],
    foreignKeys = [
        ForeignKey(
            entity = MessageEntity::class,
            parentColumns = ["id"],
            childColumns = ["messageId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("messageId")]
)
data class ReactionEntity(
    val messageId: Long,
    val emoji: String,
    val count: Int = 1,
    /** True when this device added the reaction, which is the only one it may remove. */
    val mine: Boolean = false,
)

@Entity(tableName = "templates")
data class TemplateEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val body: String,
    val useCount: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "scheduled_messages", indices = [Index("scheduledAt")])
data class ScheduledMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val address: String,
    val displayName: String? = null,
    val body: String,
    val scheduledAt: Long,
    val createdAt: Long = System.currentTimeMillis(),
    val subscriptionId: Int = -1,
    val completed: Boolean = false,
)

@Entity(tableName = "blocked_numbers")
data class BlockedNumberEntity(
    @PrimaryKey val address: String,
    val displayName: String? = null,
    val blockedAt: Long = System.currentTimeMillis(),
)

/**
 * A user-created tab. The three built-in tabs (All, Personal, Transactions) are computed
 * from message content and are not rows here, so only genuinely custom tabs live in this
 * table and the built-ins can never be renamed or deleted by accident.
 */
@Entity(tableName = "categories", indices = [Index(value = ["name"], unique = true)])
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** Index into the icon list offered when editing a tab, so the choice survives renames. */
    val iconIndex: Int = 0,
    val colorIndex: Int = 0,
    /** Sort order in the tab strip; gaps are fine, new tabs go at the end. */
    val position: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
)

/** Which conversations the user has filed under a custom tab. */
@Entity(
    tableName = "category_members",
    primaryKeys = ["categoryId", "conversationId"],
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = ConversationEntity::class,
            parentColumns = ["id"],
            childColumns = ["conversationId"],
            onDelete = ForeignKey.CASCADE
        ),
    ],
    indices = [Index("categoryId"), Index("conversationId")]
)
data class CategoryMemberEntity(
    val categoryId: Long,
    val conversationId: Long,
)
