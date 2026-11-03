package com.sms.messages.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.sms.messages.data.CategoryEntity
import com.sms.messages.data.CategoryMemberEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao {

    @Query("SELECT * FROM categories ORDER BY position ASC, createdAt ASC")
    fun observeAll(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories ORDER BY position ASC, createdAt ASC")
    suspend fun getAll(): List<CategoryEntity>

    @Query("SELECT * FROM categories WHERE id = :id")
    suspend fun getById(id: Long): CategoryEntity?

    @Query("SELECT * FROM categories WHERE name = :name LIMIT 1")
    suspend fun getByName(name: String): CategoryEntity?

    /** New tabs sort after every existing one, so the strip keeps insertion order. */
    @Query("SELECT COALESCE(MAX(position), 0) + 1 FROM categories")
    suspend fun nextPosition(): Int

    @Insert
    suspend fun insert(category: CategoryEntity): Long

    @Query("UPDATE categories SET name = :name, iconIndex = :iconIndex, colorIndex = :colorIndex WHERE id = :id")
    suspend fun update(id: Long, name: String, iconIndex: Int, colorIndex: Int)

    @Query("DELETE FROM categories WHERE id = :id")
    suspend fun deleteById(id: Long)

    // ------------------------------------------------------------ membership

    /** Conversation ids filed under one tab. */
    @Query("SELECT conversationId FROM category_members WHERE categoryId = :categoryId")
    fun observeMemberIds(categoryId: Long): Flow<List<Long>>

    /** Every (tab, conversation) pair, so the list can label rows without a query per tab. */
    @Query("SELECT * FROM category_members")
    fun observeAllMembers(): Flow<List<CategoryMemberEntity>>

    @Query("SELECT COUNT(*) FROM category_members WHERE categoryId = :categoryId")
    fun observeMemberCount(categoryId: Long): Flow<Int>

    @Query(
        """
        SELECT conversationId FROM category_members
        WHERE categoryId = :categoryId AND conversationId = :conversationId
        LIMIT 1
        """
    )
    suspend fun findMember(categoryId: Long, conversationId: Long): Long?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addMember(member: CategoryMemberEntity)

    @Query("DELETE FROM category_members WHERE categoryId = :categoryId AND conversationId = :conversationId")
    suspend fun removeMember(categoryId: Long, conversationId: Long)

    @Query("DELETE FROM category_members WHERE categoryId = :categoryId")
    suspend fun clearMembers(categoryId: Long)
}
