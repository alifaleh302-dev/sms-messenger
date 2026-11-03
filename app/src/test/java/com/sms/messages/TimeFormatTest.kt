package com.sms.messages

import com.sms.messages.util.TimeFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

class TimeFormatTest {

    private val now = 1_700_000_000_000L

    @Test
    fun `recent messages use compact relative labels`() {
        assertEquals("now", TimeFormat.relative(now - 10_000, now))
        assertEquals("5m", TimeFormat.relative(now - TimeUnit.MINUTES.toMillis(5), now))
        assertEquals("3h", TimeFormat.relative(now - TimeUnit.HOURS.toMillis(3), now))
        assertEquals("2d", TimeFormat.relative(now - TimeUnit.DAYS.toMillis(2), now))
    }

    @Test
    fun `older messages fall back to a date instead of an empty label`() {
        val label = TimeFormat.relative(now - TimeUnit.DAYS.toMillis(30), now)
        assertTrue(label.isNotEmpty())
        assertTrue(label.any { it.isDigit() })
    }

    @Test
    fun `day label recognises today and yesterday`() {
        assertEquals("Today", TimeFormat.dayLabel(now, now))
        assertEquals("Yesterday", TimeFormat.dayLabel(now - TimeUnit.DAYS.toMillis(1), now))
    }

    @Test
    fun `clock follows the device time format`() {
        // DateFormat.SHORT honours the 12/24-hour setting, so assert the shape
        // rather than a fixed width: either "H:mm", "HH:mm" or "h:mm AM".
        val clock = TimeFormat.clock(now)
        assertTrue("clock was blank", clock.isNotBlank())
        assertTrue("clock had no digits: $clock", clock.any { it.isDigit() })
    }

    @Test
    fun `relative and day labels accept localised wording`() {
        assertEquals("الآن", TimeFormat.relative(now - 1_000, now, nowLabel = "الآن"))
        assertEquals("اليوم", TimeFormat.dayLabel(now, now, todayLabel = "اليوم"))
        assertEquals(
            "أمس",
            TimeFormat.dayLabel(now - TimeUnit.DAYS.toMillis(1), now, yesterdayLabel = "أمس"),
        )
    }
}
