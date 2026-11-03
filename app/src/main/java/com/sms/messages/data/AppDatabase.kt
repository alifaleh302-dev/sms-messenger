package com.sms.messages.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.sms.messages.data.dao.BlockedNumberDao
import com.sms.messages.data.dao.CategoryDao
import com.sms.messages.data.dao.ConversationDao
import com.sms.messages.data.dao.MessageDao
import com.sms.messages.data.dao.ReactionDao
import com.sms.messages.data.dao.ScheduledMessageDao
import com.sms.messages.data.dao.TemplateDao

@Database(
    entities = [
        ConversationEntity::class,
        MessageEntity::class,
        ReactionEntity::class,
        TemplateEntity::class,
        ScheduledMessageEntity::class,
        BlockedNumberEntity::class,
        CategoryEntity::class,
        CategoryMemberEntity::class,
    ],
    version = 3,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun conversations(): ConversationDao
    abstract fun messages(): MessageDao
    abstract fun reactions(): ReactionDao
    abstract fun templates(): TemplateDao
    abstract fun scheduled(): ScheduledMessageDao
    abstract fun blocked(): BlockedNumberDao
    abstract fun categories(): CategoryDao

    companion object {
        const val NAME = "sms_messenger.db"

        @Volatile private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                NAME
            )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .build()
                .also { instance = it }
        }
    }
}
