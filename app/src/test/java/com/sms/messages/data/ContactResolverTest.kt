package com.sms.messages.data

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The address book is empty in this environment, so every lookup returns "no name".
 * That is the common real-world case and it used to crash: the name cache is an
 * LruCache, which rejects null values, and a null was written for every unknown number.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class ContactResolverTest {

    private lateinit var resolver: ContactResolver

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        resolver = ContactResolver(context)
    }

    @Test
    fun `an unknown number resolves to no name without crashing`() = runTest {
        assertNull(resolver.displayName("+966512345678"))
    }

    @Test
    fun `repeated lookups of an unknown number stay null`() = runTest {
        // The second call hits the cache, which is where the crash used to happen.
        assertNull(resolver.displayName("+966512345678"))
        assertNull(resolver.displayName("+966512345678"))
        assertNull(resolver.displayName("+966 51 234 5678"))
    }

    @Test
    fun `blank input is rejected without a query`() = runTest {
        assertNull(resolver.displayName(""))
        assertNull(resolver.displayName("   "))
    }

    @Test
    fun `short codes resolve to no name`() = runTest {
        assertNull(resolver.displayName("900"))
    }

    @Test
    fun `the cache can be invalidated and reused`() = runTest {
        assertNull(resolver.displayName("+966500000000"))
        resolver.invalidate()
        assertNull(resolver.displayName("+966500000000"))
    }

    @Test
    fun `loading the address book returns an empty list rather than failing`() = runTest {
        assertEquals(emptyList<Contact>(), resolver.loadContacts())
    }
}
