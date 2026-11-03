package com.sms.messages.data

import android.content.Context
import android.net.Uri
import android.util.Log

/**
 * One-time (and on-demand) import of the messages the platform stored in the shared
 * telephony provider, so a user switching to this app keeps their history.
 *
 * Only rows that are not already present (matched by the provider row id) are copied, which
 * makes the import safe to run repeatedly.
 */
class TelephonyImporter(private val context: Context) {

    suspend fun importAll(): Int {
        val database = AppDatabase.get(context)
        var imported = 0
        imported += importFrom(TelephonyContract.INBOX_URI, MessageType.INBOX, database)
        imported += importFrom(TelephonyContract.SENT_URI, MessageType.SENT, database)
        Log.i(TAG, "Imported $imported messages from the telephony provider")
        return imported
    }

    private suspend fun importFrom(
        uri: Uri,
        type: MessageType,
        database: AppDatabase,
    ): Int {
        val projection = arrayOf(
            TelephonyContract.COLUMN_ID,
            TelephonyContract.COLUMN_ADDRESS,
            TelephonyContract.COLUMN_BODY,
            TelephonyContract.COLUMN_DATE,
            TelephonyContract.COLUMN_SUBSCRIPTION_ID,
        )
        var count = 0
        runCatching {
            context.contentResolver.query(uri, projection, null, null, "${TelephonyContract.COLUMN_DATE} ASC")
                ?.use { cursor ->
                    val idIdx = cursor.getColumnIndexOrThrow(TelephonyContract.COLUMN_ID)
                    val addressIdx = cursor.getColumnIndexOrThrow(TelephonyContract.COLUMN_ADDRESS)
                    val bodyIdx = cursor.getColumnIndexOrThrow(TelephonyContract.COLUMN_BODY)
                    val dateIdx = cursor.getColumnIndexOrThrow(TelephonyContract.COLUMN_DATE)
                    val subIdx = cursor.getColumnIndex(TelephonyContract.COLUMN_SUBSCRIPTION_ID)

                    while (cursor.moveToNext()) {
                        val externalId = cursor.getLong(idIdx)
                        if (database.messages().getByExternalId(externalId) != null) continue
                        val address = cursor.getString(addressIdx) ?: continue
                        val body = cursor.getString(bodyIdx).orEmpty()
                        val date = cursor.getLong(dateIdx)
                        val subscription = if (subIdx >= 0) cursor.getInt(subIdx) else -1
                        database.messages().insert(
                            MessageEntity(
                                conversationId = ensureConversation(database, address),
                                address = address,
                                body = body,
                                timestamp = date,
                                type = type,
                                status = MessageStatus.NONE,
                                read = type == MessageType.SENT,
                                subscriptionId = subscription,
                                externalId = externalId,
                            )
                        )
                        count++
                    }
                }
        }.onFailure { Log.w(TAG, "Telephony import from $uri failed", it) }
        return count
    }

    private suspend fun ensureConversation(database: AppDatabase, address: String): Long {
        database.conversations().getByAddress(address)?.let { return it.id }
        val id = database.conversations().insert(ConversationEntity(address = address))
        if (id > 0) return id
        return database.conversations().getByAddress(address)?.id ?: 0
    }

    private object TelephonyContract {
        const val COLUMN_ID = "_id"
        const val COLUMN_ADDRESS = "address"
        const val COLUMN_BODY = "body"
        const val COLUMN_DATE = "date"
        const val COLUMN_SUBSCRIPTION_ID = "sub_id"

        val INBOX_URI: Uri = Uri.parse("content://sms/inbox")
        val SENT_URI: Uri = Uri.parse("content://sms/sent")
    }

    private companion object {
        const val TAG = "TelephonyImporter"
    }
}
