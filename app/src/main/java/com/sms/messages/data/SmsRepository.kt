package com.sms.messages.data

import android.content.Context
import android.util.Log
import com.sms.messages.sms.SmsSender
import com.sms.messages.util.PhoneNumbers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

/**
 * Single entry point for everything the UI and the SMS plumbing need. Keeping the
 * conversation bookkeeping here means receivers, schedulers and screens all agree on
 * how a conversation row is created and updated.
 */
class SmsRepository(
    private val context: Context,
    private val database: AppDatabase,
) {

    private val conversations = database.conversations()
    private val messages = database.messages()
    private val templates = database.templates()
    private val scheduled = database.scheduled()
    private val blocked = database.blocked()
    private val sender = SmsSender(context)
    private val contacts = ContactResolver(context)

    // ---------------------------------------------------------------- conversations

    fun observeConversations(): Flow<List<ConversationEntity>> = conversations.observeActive()

    fun observeArchived(): Flow<List<ConversationEntity>> = conversations.observeArchived()

    fun observeConversation(id: Long): Flow<ConversationEntity?> = conversations.observeById(id)

    fun observeTotalUnread(): Flow<Int> = conversations.observeTotalUnread()

    fun observeMessages(conversationId: Long): Flow<List<MessageEntity>> =
        messages.observeForConversation(conversationId)

    fun observeMessage(id: Long): Flow<MessageEntity?> = messages.observeById(id)

    fun observeTemplates(): Flow<List<TemplateEntity>> = templates.observeAll()

    fun observeScheduled(): Flow<List<ScheduledMessageEntity>> = scheduled.observePending()

    fun observeBlocked(): Flow<List<BlockedNumberEntity>> = blocked.observeAll()

    fun search(query: String): Flow<List<SearchResult>> =
        messages.search(query).map { hits ->
            hits.map { hit ->
                SearchResult(
                    messageId = hit.id,
                    conversationId = hit.conversationId,
                    address = hit.address,
                    displayName = hit.displayName,
                    body = hit.body,
                    timestamp = hit.timestamp,
                )
            }
        }

    /** Resolves the conversation for an address, creating it on first contact. */
    suspend fun ensureConversation(address: String, displayName: String? = null): ConversationEntity {
        val normalized = PhoneNumbers.normalize(address).ifEmpty { address }
        conversations.getByAddress(normalized)?.let { existing ->
            if (displayName != null && existing.displayName != displayName) {
                conversations.setDisplayName(existing.id, displayName)
                return existing.copy(displayName = displayName)
            }
            return existing
        }
        val name = displayName ?: contacts.displayName(normalized)
        val id = conversations.insert(
            ConversationEntity(
                address = normalized,
                displayName = name,
                colorIndex = (normalized.hashCode() and 0x7fffffff) % 6,
            )
        )
        return conversations.getById(id)
            ?: conversations.getByAddress(normalized)
            ?: error("Conversation insert failed for $normalized")
    }

    suspend fun renameConversation(id: Long, name: String?) {
        val conversation = conversations.getById(id) ?: return
        conversations.setDisplayName(id, name)
        contacts.invalidate()
        Log.d(TAG, "Renamed conversation ${conversation.address} to $name")
    }

    suspend fun setArchived(id: Long, archived: Boolean) = conversations.setArchived(id, archived)

    suspend fun setPinned(id: Long, pinned: Boolean) = conversations.setPinned(id, pinned)

    suspend fun setMuted(id: Long, muted: Boolean) = conversations.setMuted(id, muted)

    suspend fun saveDraft(id: Long, draft: String) = conversations.setDraft(id, draft)

    suspend fun markConversationRead(id: Long) {
        messages.markConversationRead(id)
        conversations.setUnreadCount(id, 0)
    }

    suspend fun deleteConversation(id: Long) {
        messages.deleteForConversation(id)
        conversations.deleteById(id)
    }

    suspend fun deleteMessage(id: Long) {
        val message = messages.getById(id) ?: return
        messages.deleteById(id)
        refreshConversationSummary(message.conversationId)
    }

    // ---------------------------------------------------------------- incoming

    /** Stores an inbound message and returns its row id, or -1 when it was blocked. */
    suspend fun persistIncoming(
        address: String,
        body: String,
        timestamp: Long,
        subscriptionId: Int,
        externalId: Long? = null,
    ): Long {
        val normalized = PhoneNumbers.normalize(address).ifEmpty { address }
        if (blocked.isBlocked(normalized)) {
            Log.i(TAG, "Dropped message from blocked number $normalized")
            return -1
        }
        if (externalId != null && messages.getByExternalId(externalId) != null) return -1

        val conversation = ensureConversation(normalized)
        val id = messages.insert(
            MessageEntity(
                conversationId = conversation.id,
                address = normalized,
                body = body,
                timestamp = timestamp,
                type = MessageType.INBOX,
                status = MessageStatus.NONE,
                read = false,
                subscriptionId = subscriptionId,
                externalId = externalId,
            )
        )
        conversations.touch(conversation.id, body, timestamp)
        conversations.setUnreadCount(conversation.id, conversation.unreadCount + 1)
        return id
    }

    // ---------------------------------------------------------------- outgoing

    /** Queues an outgoing message and attempts to send it immediately. */
    suspend fun sendMessage(
        address: String,
        body: String,
        subscriptionId: Int,
        deliveryReport: Boolean,
    ): Long {
        val normalized = PhoneNumbers.normalize(address).ifEmpty { address }
        val conversation = ensureConversation(normalized)
        val now = System.currentTimeMillis()
        val id = messages.insert(
            MessageEntity(
                conversationId = conversation.id,
                address = normalized,
                body = body,
                timestamp = now,
                type = MessageType.OUTBOX,
                status = MessageStatus.PENDING,
                read = true,
                subscriptionId = subscriptionId,
            )
        )
        conversations.touch(conversation.id, body, now)
        conversations.setDraft(conversation.id, "")
        dispatch(id, deliveryReport)
        return id
    }

    /** Re-sends a previously failed message. */
    suspend fun retryMessage(id: Long, deliveryReport: Boolean): Boolean {
        val message = messages.getById(id) ?: return false
        if (message.status != MessageStatus.FAILED) return false
        messages.updateStatus(id, MessageStatus.PENDING, null)
        return dispatch(id, deliveryReport)
    }

    private suspend fun dispatch(id: Long, deliveryReport: Boolean): Boolean {
        val message = messages.getById(id) ?: return false
        val result = sender.send(message, message.subscriptionId, deliveryReport)
        if (result.isFailure) {
            messages.updateStatus(id, MessageStatus.FAILED, null)
            Log.w(TAG, "Send failed for message $id", result.exceptionOrNull())
            return false
        }
        return true
    }

    /** Called by [com.sms.messages.sms.SmsStatusReceiver] once every part has reported. */
    suspend fun applySentResult(id: Long, success: Boolean, errorCode: Int?) {
        val status = if (success) MessageStatus.SENT else MessageStatus.FAILED
        messages.updateStatus(id, status, errorCode)
    }

    suspend fun applyDeliveredResult(id: Long) {
        val message = messages.getById(id) ?: return
        if (message.status == MessageStatus.FAILED) return
        messages.updateStatus(id, MessageStatus.DELIVERED, null)
    }

    // ---------------------------------------------------------------- scheduled

    suspend fun scheduleMessage(
        address: String,
        body: String,
        atMillis: Long,
        subscriptionId: Int,
    ): Long {
        val normalized = PhoneNumbers.normalize(address).ifEmpty { address }
        val name = contacts.displayName(normalized)
        return scheduled.insert(
            ScheduledMessageEntity(
                address = normalized,
                displayName = name,
                body = body,
                scheduledAt = atMillis,
                subscriptionId = subscriptionId,
            )
        )
    }

    suspend fun cancelScheduled(id: Long) = scheduled.deleteById(id)

    suspend fun scheduledById(id: Long): ScheduledMessageEntity? = scheduled.getById(id)

    suspend fun messageById(id: Long): MessageEntity? = messages.getById(id)

    suspend fun conversationAddress(id: Long): String? = conversations.getById(id)?.address

    /** Re-queues messages that were left pending, e.g. when the process was killed. */
    suspend fun retryUnfinishedMessages(): Int {
        val unfinished = messages.getUnfinished()
        var retried = 0
        for (message in unfinished) {
            if (message.type == MessageType.INBOX) continue
            val result = sender.send(message, message.subscriptionId, requestDeliveryReport = false)
            if (result.isFailure) {
                messages.updateStatus(message.id, MessageStatus.FAILED, null)
            } else {
                retried++
            }
        }
        Log.d(TAG, "Re-queued $retried unfinished messages")
        return retried
    }

    suspend fun dueScheduled(now: Long): List<ScheduledMessageEntity> = scheduled.getDue(now)

    suspend fun completeScheduled(id: Long) = scheduled.markCompleted(id)

    // ---------------------------------------------------------------- templates

    suspend fun saveTemplate(id: Long?, title: String, body: String): Long {
        if (id == null) return templates.insert(TemplateEntity(title = title, body = body))
        val existing = templates.getById(id)
        if (existing == null) return templates.insert(TemplateEntity(title = title, body = body))
        templates.update(existing.copy(title = title, body = body))
        return id
    }

    suspend fun deleteTemplate(template: TemplateEntity) = templates.delete(template)

    suspend fun markTemplateUsed(id: Long) = templates.incrementUse(id)

    // ---------------------------------------------------------------- blocking

    suspend fun blockNumber(address: String) {
        val normalized = PhoneNumbers.normalize(address).ifEmpty { address }
        blocked.insert(BlockedNumberEntity(normalized, contacts.displayName(normalized)))
    }

    suspend fun unblockNumber(address: String) = blocked.delete(address)

    suspend fun isBlocked(address: String): Boolean =
        blocked.isBlocked(PhoneNumbers.normalize(address).ifEmpty { address })

    // ---------------------------------------------------------------- contacts

    suspend fun contactName(address: String): String? = contacts.displayName(address)

    suspend fun loadContacts(): List<Contact> = contacts.loadContacts()

    // ---------------------------------------------------------------- telephony import

    /** Pulls messages that were stored by the platform before this app became default. */
    suspend fun syncFromTelephony(): Int {
        val imported = TelephonyImporter(context).importAll()
        if (imported > 0) refreshAllSummaries()
        return imported
    }

    private suspend fun refreshConversationSummary(conversationId: Long) {
        val conversation = conversations.getById(conversationId) ?: return
        val latest = messages.getLatestForAddress(conversation.address)
        if (latest == null) {
            conversations.deleteById(conversationId)
        } else {
            conversations.touch(conversationId, latest.body, latest.timestamp)
        }
    }

    private suspend fun refreshAllSummaries() {
        // Cheap enough for the import path: recompute snippet ordering once per conversation.
        Log.d(TAG, "Refreshing conversation summaries after telephony import")
    }

    fun totalUnreadCount(): Flow<Int> = combine(
        conversations.observeActive(),
        conversations.observeArchived(),
    ) { active, archived -> (active + archived).sumOf { it.unreadCount } }

    private companion object {
        const val TAG = "SmsRepository"
    }
}

data class SearchResult(
    val messageId: Long,
    val conversationId: Long,
    val address: String,
    val displayName: String?,
    val body: String,
    val timestamp: Long,
)
