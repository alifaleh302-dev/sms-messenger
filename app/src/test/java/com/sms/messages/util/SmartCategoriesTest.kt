package com.sms.messages.util

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The built-in tabs are the whole "smart" part of the feature, so the rules are pinned
 * here: a wrong classification sends a bank alert into Personal, which is the visible
 * failure a user would notice.
 */
class SmartCategoriesTest {

    @Test
    fun `short code is a transaction`() {
        assertEquals(SmartTab.TRANSACTIONS, SmartCategories.classify("8888", "any text"))
        assertEquals(SmartTab.TRANSACTIONS, SmartCategories.classify("112233", "hi"))
    }

    @Test
    fun `international number with a code word is still not a short code by sender alone`() {
        // The "+" marks a real number, so the sender check must not claim it.
        assertEquals(
            SmartTab.PERSONAL,
            SmartCategories.classify("+966500000000", "see you tomorrow"),
        )
    }

    @Test
    fun `bank sender is a transaction even with an ordinary body`() {
        assertEquals(
            SmartTab.TRANSACTIONS,
            SmartCategories.classify("AlRajhiBank", "Your statement is ready"),
        )
        assertEquals(
            SmartTab.TRANSACTIONS,
            SmartCategories.classify("STCPay", "hello"),
        )
    }

    @Test
    fun `one time code wording marks a transaction`() {
        assertEquals(
            SmartTab.TRANSACTIONS,
            SmartCategories.classify("+966500000000", "Your verification code is 123456"),
        )
        assertEquals(
            SmartTab.TRANSACTIONS,
            SmartCategories.classify("+966500000000", "رمز التحقق 445566"),
        )
    }

    @Test
    fun `an ordinary conversation is personal`() {
        assertEquals(
            SmartTab.PERSONAL,
            SmartCategories.classify("+966500000000", "Are we still meeting at 5?"),
        )
    }

    @Test
    fun `a whole thread decides together, not the newest message`() {
        // The sender is a bank, so even a casual line keeps the thread in Transactions.
        val bodies = listOf("Your card ending 1234 was charged", "thanks!")
        assertEquals(
            SmartTab.TRANSACTIONS,
            SmartCategories.classify("+966500000000", bodies),
        )
    }

    @Test
    fun `transaction score ranks an obvious match above a weak one`() {
        val strong = SmartCategories.transactionScore("8888", listOf("Your OTP is 1234"))
        val weak = SmartCategories.transactionScore("+966500000000", listOf("hello"))
        assertEquals(true, strong > weak)
    }
}
