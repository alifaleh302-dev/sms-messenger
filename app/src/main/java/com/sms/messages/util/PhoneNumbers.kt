package com.sms.messages.util

import android.telephony.PhoneNumberUtils
import java.util.Locale

/**
 * Normalises phone numbers so that the same contact always maps to one conversation,
 * regardless of whether the number arrived as "+966512345678", "00966512345678"
 * or "0512345678".
 */
object PhoneNumbers {

    private const val DEFAULT_REGION = "SA"

    fun normalize(raw: String?): String {
        val input = raw?.trim().orEmpty()
        if (input.isEmpty()) return ""
        // Short codes (e.g. 900, 1234) are not real phone numbers and must be kept as-is.
        if (input.all { it.isDigit() } && input.length <= 6) return input

        val stripped = input.filter { it.isDigit() || it == '+' }
        val digits = stripped.replace("+", "")
        if (digits.isEmpty()) return input

        val e164 = runCatching {
            PhoneNumberUtils.formatNumberToE164(digits, DEFAULT_REGION)
        }.getOrNull()

        return when {
            !e164.isNullOrBlank() -> e164
            stripped.startsWith("+") -> "+$digits"
            digits.startsWith("00") && digits.length > 4 -> "+${digits.substring(2)}"
            else -> digits
        }
    }

    /** True when the value can be used as a recipient for an SMS. */
    fun isValid(raw: String?): Boolean {
        val value = raw?.trim().orEmpty()
        if (value.isEmpty()) return false
        if (value.any { it.isLetter() }) return false
        val digits = value.count { it.isDigit() }
        return digits >= 3
    }

    /** Human readable form for display; falls back to the stored value. */
    fun format(raw: String?): String {
        val value = raw?.trim().orEmpty()
        if (value.isEmpty()) return ""
        if (value.length <= 6 && value.all { it.isDigit() }) return value
        return runCatching { PhoneNumberUtils.formatNumber(value, DEFAULT_REGION) }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?: value
    }

    /** Groups numbers by their last significant digits to detect duplicates. */
    fun matches(a: String?, b: String?): Boolean {
        val left = normalize(a)
        val right = normalize(b)
        if (left.isEmpty() || right.isEmpty()) return false
        if (left == right) return true
        val leftDigits = left.takeLast(9).lowercase(Locale.ROOT)
        val rightDigits = right.takeLast(9).lowercase(Locale.ROOT)
        return leftDigits.length >= 7 && leftDigits == rightDigits
    }
}
