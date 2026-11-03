package com.sms.messages.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.sms.messages.data.MessageEntity
import com.sms.messages.data.MessageStatus
import com.sms.messages.data.MessageType
import kotlinx.coroutines.flow.Flow

data class SearchHit(
    val id: Long,
    val conversationId: Long,
    val address: String,
    val displayName: String?,
    val body: String,
    val timestamp: Long,
    val starred: Boolean = false,
    val read: Boolean = true,
)

@Dao
interface MessageDao {

    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY timestamp ASC, id ASC")
    fun observeForConversation(conversationId: Long): Flow<List<MessageEntity>>

    /**
     * Starred messages across every conversation, newest first. Starred rows are surfaced
     * through the account menu and the search filters rather than in the conversation list.
     */
    @Query("SELECT * FROM messages WHERE starred = 1 ORDER BY timestamp DESC LIMIT :limit")
    fun observeStarred(limit: Int = 500): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE starred = 1 AND conversationId = :conversationId ORDER BY timestamp DESC")
    fun observeStarredIn(conversationId: Long): Flow<List<MessageEntity>>

    @Query("SELECT COUNT(*) FROM messages WHERE starred = 1")
    fun observeStarredCount(): Flow<Int>

    @Query("SELECT * FROM messages WHERE id = :id")
    suspend fun getById(id: Long): MessageEntity?

    @Query("SELECT * FROM messages WHERE externalId = :externalId LIMIT 1")
    suspend fun getByExternalId(externalId: Long): MessageEntity?

    @Query("SELECT * FROM messages WHERE status IN (0, 1) ORDER BY timestamp ASC")
    suspend fun getUnfinished(): List<MessageEntity>

    /** Outgoing messages that failed recently, oldest first, for automatic retry. */
    @Query(
        """
        SELECT * FROM messages
        WHERE status = :failed AND type != :inbox AND timestamp >= :since
        ORDER BY timestamp ASC LIMIT :limit
        """
    )
    suspend fun getRecentlyFailed(
        failed: MessageStatus,
        inbox: MessageType,
        since: Long,
        limit: Int,
    ): List<MessageEntity>

    @Query("SELECT * FROM messages WHERE address = :address ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestForAddress(address: String): MessageEntity?

    @Query(
        """
        SELECT * FROM messages
        WHERE conversationId = :conversationId AND type = :inbox
        ORDER BY timestamp DESC, id DESC LIMIT 1
        """
    )
    suspend fun getLatestInbound(conversationId: Long, inbox: MessageType): MessageEntity?

    @Insert
    suspend fun insert(message: MessageEntity): Long

    @Update
    suspend fun update(message: MessageEntity)

    @Query("DELETE FROM messages WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM messages WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    @Query("DELETE FROM messages WHERE conversationId = :conversationId")
    suspend fun deleteForConversation(conversationId: Long)

    @Query("UPDATE messages SET starred = :starred WHERE id = :id")
    suspend fun setStarred(id: Long, starred: Boolean)

    @Query("UPDATE messages SET replyToId = :replyToId WHERE id = :id")
    suspend fun setReplyTo(id: Long, replyToId: Long?)

    @Query("UPDATE messages SET read = 1 WHERE conversationId = :conversationId AND read = 0")
    suspend fun markConversationRead(conversationId: Long)

    @Query("UPDATE messages SET status = :status, errorCode = :errorCode WHERE id = :id")
    suspend fun updateStatus(id: Long, status: MessageStatus, errorCode: Int?)

    @Query(
        """
        SELECT m.id AS id, m.conversationId AS conversationId, m.address AS address,
               c.displayName AS displayName, m.body AS body, m.timestamp AS timestamp,
               m.starred AS starred, m.read AS read
        FROM messages m
        LEFT JOIN conversations c ON c.id = m.conversationId
        WHERE (m.body LIKE '%' || :query || '%' ESCAPE '\'
           OR m.address LIKE '%' || :query || '%' ESCAPE '\'
           OR IFNULL(c.displayName, '') LIKE '%' || :query || '%' ESCAPE '\')
          AND (:starredOnly = 0 OR m.starred = 1)
          AND (:unreadOnly = 0 OR m.read = 0)
        ORDER BY m.timestamp DESC
        LIMIT :limit
        """
    )
    fun search(
        query: String,
        starredOnly: Boolean = false,
        unreadOnly: Boolean = false,
        limit: Int = 200,
    ): Flow<List<SearchHit>>

    /** Used when a filter is active but no text has been typed yet. */
    @Query(
        """
        SELECT m.id AS id, m.conversationId AS conversationId, m.address AS address,
               c.displayName AS displayName, m.body AS body, m.timestamp AS timestamp,
               m.starred AS starred, m.read AS read
        FROM messages m
        LEFT JOIN conversations c ON c.id = m.conversationId
        WHERE (:starredOnly = 0 OR m.starred = 1)
          AND (:unreadOnly = 0 OR m.read = 0)
          AND (:starredOnly = 1 OR :unreadOnly = 1)
        ORDER BY m.timestamp DESC
        LIMIT :limit
        """
    )
    fun filterOnly(
        starredOnly: Boolean,
        unreadOnly: Boolean,
        limit: Int = 200,
    ): Flow<List<SearchHit>>

    @Query("SELECT COUNT(*) FROM messages WHERE conversationId = :conversationId AND read = 0")
    suspend fun countUnread(conversationId: Long): Int

    // ------------------------------------------------------------ one-time codes

    @Query("UPDATE messages SET otp = :otp WHERE id = :id")
    suspend fun setOtp(id: Long, otp: Boolean)

    /** Expired one-time codes, oldest first, so the sweep can delete them in batches. */
    @Query("SELECT * FROM messages WHERE otp = 1 AND timestamp <= :cutoff ORDER BY timestamp ASC")
    suspend fun getExpiredOtp(cutoff: Long): List<MessageEntity>

    @Query("SELECT COUNT(*) FROM messages WHERE otp = 1 AND timestamp <= :cutoff")
    fun observeExpiredOtpCount(cutoff: Long): Flow<Int>

    /** The bodies of every message in a conversation, oldest first, for tab classification. */
    @Query("SELECT body FROM messages WHERE conversationId = :conversationId ORDER BY timestamp ASC")
    suspend fun bodiesForConversation(conversationId: Long): List<String>
}
