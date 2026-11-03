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
