package com.sms.messages.util

/**
 * Pulls one-time codes out of message text.
 *
 * Verification codes arrive in wildly different shapes, so this leans on the surrounding
 * words first and only then falls back to a bare digit run. A message with no code word at
 * all returns null, which keeps ordinary numbers (prices, dates, phone numbers) from being
 * offered as "copy code".
 */
object VerificationCode {

    /** Words that mark a message as carrying a code, in English and Arabic. */
    private val KEYWORDS = listOf(
        "code", "otp", "pin", "password", "passcode", "verification", "verify",
        "one-time", "one time", "token", "authentication",
        "رمز", "الرمز", "كود", "التحقق", "تحقق", "التحقق من", "كلمة المرور", "المرور",
    )

    private val CODE = Regex("""(?<!\d)(\d{4,8})(?!\d)""")

    /** The code contained in [body], or null when the message does not look like a code. */
    fun extract(body: String): String? {
        if (body.isBlank()) return null
        val lower = body.lowercase()
        if (KEYWORDS.none { lower.contains(it) }) return null
        return CODE.find(body)?.groupValues?.get(1)
    }
}
