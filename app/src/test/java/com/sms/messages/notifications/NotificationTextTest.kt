package com.sms.messages.notifications

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.sms.messages.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale

/**
 * The app ships English and Arabic. These assertions read the real resource tables through
 * Robolectric, so a missing or untranslated string fails the build rather than showing up
 * as English text inside an Arabic UI.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class NotificationTextTest {

    private lateinit var context: Application

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    private fun androidContext(language: String) =
        context.createConfigurationContext(
            android.content.res.Configuration(context.resources.configuration).apply {
                setLocale(Locale(language))
            }
        )

    @Test
    fun `the time labels are translated into arabic`() {
        val arabic = androidContext("ar")
        assertEquals("الآن", arabic.getString(R.string.time_now))
        assertEquals("اليوم", arabic.getString(R.string.time_today))
        assertEquals("أمس", arabic.getString(R.string.time_yesterday))
    }

    @Test
    fun `the time labels differ between english and arabic`() {
        val english = androidContext("en")
        val arabic = androidContext("ar")
        assertEquals("now", english.getString(R.string.time_now))
        assertEquals("Today", english.getString(R.string.time_today))
        assertEquals("Yesterday", english.getString(R.string.time_yesterday))
        assertTrue(
            "the Arabic labels must not fall back to English",
            english.getString(R.string.time_today) != arabic.getString(R.string.time_today),
        )
    }

    @Test
    fun `user visible messaging strings are translated`() {
        val arabic = androidContext("ar")
        assertEquals("رسالة جديدة", arabic.getString(R.string.new_message_title))
        assertEquals("إرسال", arabic.getString(R.string.action_send))
        assertEquals("تعذر إرسال الرسالة", arabic.getString(R.string.error_send_failed))
        assertEquals("رسالة وسائط", arabic.getString(R.string.mms_placeholder))
    }

    @Test
    fun `placeholder arguments survive translation`() {
        val arabic = androidContext("ar")
        // Arabic renders digits as Arabic-Indic numerals, which is the correct local form.
        val imported = arabic.resources.getQuantityString(R.plurals.settings_import_result, 5, 5)
        assertTrue("the count was not substituted: $imported", imported.contains("\u0665"))
        assertTrue("the surrounding words were lost: $imported", imported.contains("رسائل"))

        val more = arabic.getString(R.string.recipients_more, 3)
        assertTrue("the count was not substituted: $more", more.contains("\u0663"))
    }

    @Test
    fun `english keeps western digits in placeholders`() {
        val english = androidContext("en")
        assertEquals("Imported 5 messages", english.resources.getQuantityString(R.plurals.settings_import_result, 5, 5))
        assertEquals("+3", english.getString(R.string.recipients_more, 3))
    }

    @Test
    fun `the app name is localised`() {
        assertEquals("Messages", androidContext("en").getString(R.string.app_name))
        assertEquals("الرسائل", androidContext("ar").getString(R.string.app_name))
    }

    @Test
    fun `conversation notification ids are unique per conversation`() {
        val first = Notifications.idForConversation(1L)
        val second = Notifications.idForConversation(2L)
        assertTrue(first != second)
        // The id must stay stable for the same conversation so newer messages replace older.
        assertEquals(first, Notifications.idForConversation(1L))
    }

    @Test
    fun `the summary id does not collide with a conversation id`() {
        assertTrue(Notifications.idForConversation(1L) != Notifications.SUMMARY_ID)
    }
}
