package com.sms.messages.util

/**
 * Works out which built-in tab a conversation belongs in.
 *
 * The three built-in tabs are derived, never stored: "All" is everything, "Personal" is
 * everyone who is not a transaction sender, and "Transactions" is the senders whose
 * messages look like bank traffic or carry a one-time code. Deriving them means a new
 * message lands in the right tab immediately, with nothing to keep in sync.
 *
 * A conversation is judged as a whole rather than message by message, so a bank thread
 * stays under Transactions even while it holds an ordinary "your statement is ready" line.
 */
enum class SmartTab { ALL, PERSONAL, TRANSACTIONS }

object SmartCategories {

    /**
     * Senders that are almost always automated. Matching on the whole sender is deliberate:
     * a short-code or bank ID is a much stronger signal than anything in the text.
     */
    private val SENDER_HINTS = listOf(
        "bank", "banque", "mada", "stc pay", "stcpay", "paypal", "visa", "mastercard",
        "alrajhi", "al-rajhi", "riyad", "snb", "alinma", "sabb", "anb", "bsf", "bankalbilad",
        "urpay", "mobily", "zain", "verif", "otp", "2factor", "auth", "secure",
        "alert", "no-reply", "noreply", "donotreply", "info", "notify", "notification",
        "البنك", "مصرف", "راجحي", "الأهلي", "الرياض", "مصرفية", "تحويل", "مدى", "الراجحي",
        "الإنماء", "البلاد", "سامبا", "الاستثمار", "باي بال", "دفع", "فاتورة",
    )

    /** Words that mark a message as a transaction or a one-time code. */
    private val TRANSACTION_WORDS = listOf(
        "otp", "one-time", "one time", "verification code", "security code", "passcode",
        "authentication code", "do not share", "do not disclose", "expires in", "valid for",
        "transaction", "transferred", "transfer of", "purchase", "payment", "paid", "invoice",
        "debit", "credit", "balance", "statement", "account ending", "card ending", "sar",
        "withdrawn", "deposit", "refund", "recharged", "top-up", "topup", "salary",
        "رمز", "الرمز", "كود", "التحقق", "تحقق", "كلمة المرور", "لا تشارك", "لا تفصح",
        "صالح", "تنتهي", "عملية", "تحويل", "حوالة", "شراء", "دفع", "فاتورة", "رصيد",
        "المبلغ", "خصم", "إيداع", "سحب", "راتب", "شحن", "بطاقة", "حساب",
    )

    /**
     * Short codes are the usual carriers of bank alerts and one-time codes. They are short
     * and are never written in international form, so the leading "+" is what separates a
     * short code from an ordinary number and keeps a friend's landline out of Transactions.
     */
    private fun looksLikeShortCode(address: String): Boolean {
        if (address.startsWith("+")) return false
        val digits = address.filter { it.isDigit() }
        return digits.length in 3..6
    }

    private fun containsAny(haystack: String, needles: List<String>): Boolean {
        val lower = haystack.lowercase()
        return needles.any { lower.contains(it) }
    }

    /**
     * Classifies one conversation from its sender and the text of its messages.
     *
     * [bodies] is every message body in the thread. The sender alone is enough for a known
     * bank ID, and the bodies cover the rest, which keeps the decision stable as a thread
     * grows instead of flipping when one unusual message arrives.
     */
    fun classify(address: String, bodies: List<String>): SmartTab {
        if (looksLikeShortCode(address)) return SmartTab.TRANSACTIONS
        if (containsAny(address, SENDER_HINTS)) return SmartTab.TRANSACTIONS

        val combined = bodies.joinToString(" ").take(MAX_SCAN)
        if (containsAny(combined, TRANSACTION_WORDS)) return SmartTab.TRANSACTIONS

        return SmartTab.PERSONAL
    }

    /** Convenience for a single message, used when only the newest body is available. */
    fun classify(address: String, body: String): SmartTab =
        classify(address, listOf(body))

    /**
     * How strongly a conversation matches the Transactions tab, used to break ties when
     * ordering results. Higher is a better match.
     */
    fun transactionScore(address: String, bodies: List<String>): Int {
        var score = 0
        if (looksLikeShortCode(address)) score += 3
        if (containsAny(address, SENDER_HINTS)) score += 2
        val combined = bodies.joinToString(" ").take(MAX_SCAN).lowercase()
        if (combined.isNotEmpty()) {
            score += TRANSACTION_WORDS.count { combined.contains(it) }.coerceAtMost(3)
        }
        return score
    }

    /** Only the head of a thread is scanned; a long thread says nothing extra. */
    private const val MAX_SCAN = 4_000
}
