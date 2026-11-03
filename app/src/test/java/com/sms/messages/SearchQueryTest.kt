package com.sms.messages

import com.sms.messages.util.SearchQuery
import org.junit.Assert.assertEquals
import org.junit.Test

class SearchQueryTest {

    @Test
    fun `plain text is unchanged`() {
        assertEquals("hello", SearchQuery.escapeLike("hello"))
        assertEquals("محمد", SearchQuery.escapeLike("محمد"))
        assertEquals("", SearchQuery.escapeLike(""))
    }

    @Test
    fun `wildcards are escaped so the search stays literal`() {
        assertEquals("100\\%", SearchQuery.escapeLike("100%"))
        assertEquals("a\\_b", SearchQuery.escapeLike("a_b"))
        assertEquals("\\%\\_", SearchQuery.escapeLike("%_"))
    }

    @Test
    fun `a backslash is escaped before the wildcards it would otherwise escape`() {
        // The backslash must be doubled first, so the result is unambiguous.
        assertEquals("\\\\\\%", SearchQuery.escapeLike("\\%"))
        assertEquals("c:\\\\path", SearchQuery.escapeLike("c:\\path"))
    }
}
