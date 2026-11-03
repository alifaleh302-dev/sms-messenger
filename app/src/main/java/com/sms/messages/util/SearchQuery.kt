package com.sms.messages.util

/**
 * Prepares user input for a SQL `LIKE` comparison. `%` and `_` are wildcards, so a
 * user searching for "50%" or "a_b" would otherwise match far more than intended.
 * Queries using this must declare `ESCAPE '\'`.
 */
object SearchQuery {

    fun escapeLike(input: String): String = buildString(input.length) {
        for (character in input) {
            when (character) {
                '\\', '%', '_' -> {
                    append('\\')
                    append(character)
                }
                else -> append(character)
            }
        }
    }
}
