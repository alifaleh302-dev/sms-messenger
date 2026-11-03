package com.sms.messages.di

import android.content.Context
import androidx.room.Room
import com.sms.messages.data.AppDatabase
import com.sms.messages.data.ContactResolver
import com.sms.messages.data.MIGRATION_1_2
import com.sms.messages.data.MIGRATION_2_3
import com.sms.messages.data.SettingsRepository
import com.sms.messages.data.SmsRepository
import com.sms.messages.sms.SmsSender
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Qualifier
import javax.inject.Singleton

/** The application-wide scope used for work that must outlive any single screen. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.NAME)
            // Never destructive: the app now ships real migrations, and a fallback would
            // silently erase every stored message the moment a version is added.
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
            .build()

    @Provides
    @Singleton
    fun provideSmsRepository(
        @ApplicationContext context: Context,
        database: AppDatabase,
    ): SmsRepository = SmsRepository(context, database)

    @Provides
    @Singleton
    fun provideSettingsRepository(@ApplicationContext context: Context): SettingsRepository =
        SettingsRepository(context)

    @Provides
    @Singleton
    fun provideContactResolver(@ApplicationContext context: Context): ContactResolver =
        ContactResolver(context)

    @Provides
    @Singleton
    fun provideSmsSender(@ApplicationContext context: Context): SmsSender = SmsSender(context)

    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(): CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.IO)
}
