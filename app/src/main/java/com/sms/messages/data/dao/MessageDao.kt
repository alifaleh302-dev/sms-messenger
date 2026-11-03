package com.sms.messages.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.sms.messages.data.MessageEntity
import com.sms.messages.data.MessageStatus
import kotlinx.coroutines.flow.Flow

data class SearchHit(
    val id: Long,
    val conversationId: Long,
    val address: String,
    val displayName: String?,
    val body: String,
    val timestamp: Long,
)

@Dao
interface MessageDao {

    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY timestamp ASC, id ASC")
    fun observeForConversation(conversationId: Long): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE id = :id")
    suspend fun getById(id: Long): MessageEntity?

    @Query("SELECT * FROM messages WHERE id = :id")
    fun observeById(id: Long): Flow<MessageEntity?>

    @Query("SELECT * FROM messages WHERE externalId = :externalId LIMIT 1")
    suspend fun getByExternalId(externalId: Long): MessageEntity?

    @Query("SELECT * FROM messages WHERE status IN (0, 1) ORDER BY timestamp ASC")
    suspend fun getUnfinished(): List<MessageEntity>

    @Query("SELECT * FROM messages WHERE address = :address ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestForAddress(address: String): MessageEntity?

    @Insert
    suspend fun insert(message: MessageEntity): Long

    @Update
    suspend fun update(message: MessageEntity)

    @Query("DELETE FROM messages WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM messages WHERE conversationId = :conversationId")
    suspend fun deleteForConversation(conversationId: Long)

    @Query("UPDATE messages SET read = 1 WHERE conversationId = :conversationId AND read = 0")
    suspend fun markConversationRead(conversationId: Long)

    @Query("UPDATE messages SET status = :status, errorCode = :errorCode WHERE id = :id")
    suspend fun updateStatus(id: Long, status: MessageStatus, errorCode: Int?)

    @Query(
        """
        SELECT m.id AS id, m.conversationId AS conversationId, m.address AS address,
               c.displayName AS displayName, m.body AS body, m.timestamp AS timestamp
        FROM messages m
        LEFT JOIN conversations c ON c.id = m.conversationId
        WHERE m.body LIKE '%' || :query || '%'
           OR m.address LIKE '%' || :query || '%'
           OR IFNULL(c.displayName, '') LIKE '%' || :query || '%'
        ORDER BY m.timestamp DESC
        LIMIT :limit
        """
    )
    fun search(query: String, limit: Int = 200): Flow<List<SearchHit>>

    @Query("SELECT COUNT(*) FROM messages WHERE conversationId = :conversationId")
    suspend fun countForConversation(conversationId: Long): Int

    @Query("SELECT COUNT(*) FROM messages")
    fun observeTotalCount(): Flow<Int>
}
