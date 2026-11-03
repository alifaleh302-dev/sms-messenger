package com.sms.messages.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.sms.messages.data.dao.BlockedNumberDao
import com.sms.messages.data.dao.ConversationDao
import com.sms.messages.data.dao.MessageDao
import com.sms.messages.data.dao.ScheduledMessageDao
import com.sms.messages.data.dao.TemplateDao

@Database(
    entities = [
        ConversationEntity::class,
        MessageEntity::class,
        TemplateEntity::class,
        ScheduledMessageEntity::class,
        BlockedNumberEntity::class,
    ],
    version = 1,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun conversations(): ConversationDao
    abstract fun messages(): MessageDao
    abstract fun templates(): TemplateDao
    abstract fun scheduled(): ScheduledMessageDao
    abstract fun blocked(): BlockedNumberDao

    companion object {
        const val NAME = "sms_messenger.db"

        @Volatile private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                NAME
            )
                .fallbackToDestructiveMigration()
                .build()
                .also { instance = it }
        }
    }
}
