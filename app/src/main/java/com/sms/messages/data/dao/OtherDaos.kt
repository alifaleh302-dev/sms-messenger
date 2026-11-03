package com.sms.messages.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.sms.messages.data.BlockedNumberEntity
import com.sms.messages.data.ScheduledMessageEntity
import com.sms.messages.data.TemplateEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TemplateDao {
    @Query("SELECT * FROM templates ORDER BY useCount DESC, createdAt DESC")
    fun observeAll(): Flow<List<TemplateEntity>>

    @Query("SELECT * FROM templates WHERE id = :id")
    suspend fun getById(id: Long): TemplateEntity?

    @Insert
    suspend fun insert(template: TemplateEntity): Long

    @Update
    suspend fun update(template: TemplateEntity)

    @Delete
    suspend fun delete(template: TemplateEntity)

    @Query("UPDATE templates SET useCount = useCount + 1 WHERE id = :id")
    suspend fun incrementUse(id: Long)
}

@Dao
interface ScheduledMessageDao {
    @Query("SELECT * FROM scheduled_messages WHERE completed = 0 ORDER BY scheduledAt ASC")
    fun observePending(): Flow<List<ScheduledMessageEntity>>

    @Query("SELECT * FROM scheduled_messages WHERE completed = 0 AND scheduledAt <= :now")
    suspend fun getDue(now: Long): List<ScheduledMessageEntity>

    @Query("SELECT * FROM scheduled_messages WHERE id = :id")
    suspend fun getById(id: Long): ScheduledMessageEntity?

    @Insert
    suspend fun insert(item: ScheduledMessageEntity): Long

    @Query("UPDATE scheduled_messages SET completed = 1 WHERE id = :id")
    suspend fun markCompleted(id: Long)

    @Query("DELETE FROM scheduled_messages WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM scheduled_messages WHERE completed = 1")
    suspend fun purgeCompleted()
}

@Dao
interface BlockedNumberDao {
    @Query("SELECT * FROM blocked_numbers ORDER BY blockedAt DESC")
    fun observeAll(): Flow<List<BlockedNumberEntity>>

    @Query("SELECT * FROM blocked_numbers WHERE address = :address LIMIT 1")
    suspend fun get(address: String): BlockedNumberEntity?

    @Query("SELECT EXISTS(SELECT 1 FROM blocked_numbers WHERE address = :address)")
    suspend fun isBlocked(address: String): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: BlockedNumberEntity)

    @Query("DELETE FROM blocked_numbers WHERE address = :address")
    suspend fun delete(address: String)
}
