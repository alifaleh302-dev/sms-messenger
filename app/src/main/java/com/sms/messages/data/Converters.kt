package com.sms.messages.data

import androidx.room.TypeConverter

enum class MessageType { INBOX, SENT, DRAFT, OUTBOX }

enum class MessageStatus { NONE, PENDING, SENT, DELIVERED, FAILED }

enum class ThemeMode { SYSTEM, LIGHT, DARK }

enum class AppLanguage { SYSTEM, ENGLISH, ARABIC }

class Converters {
    @TypeConverter fun toMessageType(value: Int): MessageType = MessageType.entries[value]
    @TypeConverter fun fromMessageType(value: MessageType): Int = value.ordinal

    @TypeConverter fun toMessageStatus(value: Int): MessageStatus = MessageStatus.entries[value]
    @TypeConverter fun fromMessageStatus(value: MessageStatus): Int = value.ordinal

    @TypeConverter fun toThemeMode(value: String): ThemeMode =
        runCatching { ThemeMode.valueOf(value) }.getOrDefault(ThemeMode.SYSTEM)

    @TypeConverter fun fromThemeMode(value: ThemeMode): String = value.name

    @TypeConverter fun toAppLanguage(value: String): AppLanguage =
        runCatching { AppLanguage.valueOf(value) }.getOrDefault(AppLanguage.SYSTEM)

    @TypeConverter fun fromAppLanguage(value: AppLanguage): String = value.name
}
