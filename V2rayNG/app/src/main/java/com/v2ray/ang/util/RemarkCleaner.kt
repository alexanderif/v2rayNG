package com.v2ray.ang.util

/**
 * Subscription panels often append the account's traffic and time left to every config name,
 * e.g. `STARS-⭐-613-6.70GB📊-26D,1H⏳`. That information is shown on the subscription card, so it
 * is removed from the name to keep the name short: `STARS-⭐-613`.
 */
object RemarkCleaner {
    private const val SEPARATORS = "[\\s\\-_|•·,،]"

    // 6.70GB, 512 MB, 12 گیگ ...
    private const val TRAFFIC =
        "\\d+(?:[.,]\\d+)?\\s?(?:[KMGTP]i?B|گیگ|مگ|ترا)"

    // 26D,1H / 26 Days / 30 روز, and a lone "1H" only when it is followed by an emoji marker.
    private const val DAYS_AND_HOURS =
        "\\d+\\s?(?:Days?|D|روز)" +
            "(?:\\s*[,،]?\\s*\\d+\\s?(?:Hours?|H|ساعت))?"
    private const val HOURS_ONLY =
        "\\d+\\s?(?:Hours?|H|ساعت)(?=[\\p{So}\\p{Sk}\\uFE0F\\u200D])"

    // 📊 ⏳ ⌛ ... right after a figure.
    private const val MARKERS = "[\\p{So}\\p{Sk}\\uFE0F\\u200D]*"

    private val usageSegments = Regex(
        "(?:" + SEPARATORS + "*(?<![\\p{L}\\d.])(?:" + TRAFFIC + "|" + DAYS_AND_HOURS + "|" + HOURS_ONLY +
            ")(?![\\p{L}\\d])" + MARKERS + ")+",
        RegexOption.IGNORE_CASE
    )
    private val edgeSeparators = Regex("^$SEPARATORS+|$SEPARATORS+$")

    /** Returns [remarks] without traffic/time segments; the original text if nothing would be left. */
    fun clean(remarks: String): String {
        if (remarks.isEmpty()) return remarks
        val cleaned = usageSegments.replace(remarks, "").replace(edgeSeparators, "")
        return cleaned.ifBlank { remarks }
    }
}
