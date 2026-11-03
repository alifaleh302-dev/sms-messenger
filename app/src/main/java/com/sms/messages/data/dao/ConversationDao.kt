package com.sms.messages.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.sms.messages.data.ConversationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ConversationDao {

    @Query(
        """
        SELECT * FROM conversations
        WHERE archived = 0 AND trashedAt = 0
        ORDER BY pinned DESC, lastMessageAt DESC
        """
    )
    fun observeActive(): Flow<List<ConversationEntity>>

    @Query(
        """
        SELECT * FROM conversations
        WHERE archived = 1 AND trashedAt = 0
        ORDER BY lastMessageAt DESC
        """
    )
    fun observeArchived(): Flow<List<ConversationEntity>>

    /** Conversations sitting in trash, newest first, awaiting purge or restore. */
    @Query("SELECT * FROM conversations WHERE trashedAt > 0 ORDER BY trashedAt DESC")
    fun observeTrashed(): Flow<List<ConversationEntity>>

    /** Every conversation, archived included. Use [observeActive] for the visible list. */
    @Query("SELECT * FROM conversations ORDER BY lastMessageAt DESC")
    suspend fun getAll(): List<ConversationEntity>

    @Query("SELECT * FROM conversations WHERE trashedAt > 0 AND trashedAt <= :cutoff")
    suspend fun getTrashedBefore(cutoff: Long): List<ConversationEntity>

    @Query("SELECT COUNT(*) FROM conversations WHERE trashedAt > 0")
    fun observeTrashCount(): Flow<Int>

    @Query("SELECT * FROM conversations WHERE id = :id")
    fun observeById(id: Long): Flow<ConversationEntity?>

    @Query("SELECT * FROM conversations WHERE id = :id")
    suspend fun getById(id: Long): ConversationEntity?

    @Query("SELECT * FROM conversations WHERE address = :address LIMIT 1")
    suspend fun getByAddress(address: String): ConversationEntity?

    @Query("SELECT COALESCE(SUM(unreadCount), 0) FROM conversations")
    fun observeTotalUnread(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(conversation: ConversationEntity): Long

    @Update
    suspend fun update(conversation: ConversationEntity)

    @Query("DELETE FROM conversations WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("UPDATE conversations SET unreadCount = :count WHERE id = :id")
    suspend fun setUnreadCount(id: Long, count: Int)

    @Query("UPDATE conversations SET archived = :archived WHERE id = :id")
    suspend fun setArchived(id: Long, archived: Boolean)

    @Query("UPDATE conversations SET pinned = :pinned WHERE id = :id")
    suspend fun setPinned(id: Long, pinned: Boolean)

    @Query("UPDATE conversations SET muted = :muted WHERE id = :id")
    suspend fun setMuted(id: Long, muted: Boolean)

    @Query("UPDATE conversations SET draft = :draft WHERE id = :id")
    suspend fun setDraft(id: Long, draft: String)

    @Query("UPDATE conversations SET trashedAt = :trashedAt WHERE id = :id")
    suspend fun setTrashedAt(id: Long, trashedAt: Long)

    @Query("UPDATE conversations SET unreadCount = :count, trashedAt = 0, archived = 0 WHERE id = :id")
    suspend fun restoreFromTrash(id: Long, count: Int)

    @Query("UPDATE conversations SET displayName = :name WHERE id = :id")
    suspend fun setDisplayName(id: Long, name: String?)

    @Query(
        """
        UPDATE conversations
        SET snippet = :snippet, lastMessageAt = :timestamp
        WHERE id = :id
        """
    )
    suspend fun touch(id: Long, snippet: String, timestamp: Long)
}
