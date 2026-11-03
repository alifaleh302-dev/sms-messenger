package com.sms.messages.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.sms.messages.data.ReactionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ReactionDao {

    @Query("SELECT * FROM reactions WHERE messageId = :messageId ORDER BY count DESC, emoji ASC")
    fun observeForMessage(messageId: Long): Flow<List<ReactionEntity>>

    /**
     * Every reaction in a conversation in one query, so a long thread does not need one
     * subscription per bubble.
     */
    @Query(
        """
        SELECT r.* FROM reactions r
        INNER JOIN messages m ON m.id = r.messageId
        WHERE m.conversationId = :conversationId
        ORDER BY r.count DESC, r.emoji ASC
        """
    )
    fun observeForConversation(conversationId: Long): Flow<List<ReactionEntity>>

    @Query("SELECT * FROM reactions WHERE messageId = :messageId")
    suspend fun getForMessage(messageId: Long): List<ReactionEntity>

    @Query("SELECT * FROM reactions WHERE messageId = :messageId AND emoji = :emoji LIMIT 1")
    suspend fun get(messageId: Long, emoji: String): ReactionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(reaction: ReactionEntity)

    @Update
    suspend fun update(reaction: ReactionEntity)

    @Query("DELETE FROM reactions WHERE messageId = :messageId AND emoji = :emoji")
    suspend fun delete(messageId: Long, emoji: String)
}
