package vn.lucbao.tv.data

import java.util.Locale

object Format {
    /** 1234 -> "1,2 N", 1_200_000 -> "1,2 Tr" */
    fun count(n: Long): String = when {
        n < 0 -> ""
        n < 1_000 -> n.toString()
        n < 1_000_000 -> short(n / 1_000.0) + " N"
        n < 1_000_000_000 -> short(n / 1_000_000.0) + " Tr"
        else -> short(n / 1_000_000_000.0) + " T"
    }

    private fun short(v: Double): String {
        val s = if (v >= 100) String.format(Locale.ROOT, "%.0f", v)
        else String.format(Locale.ROOT, "%.1f", v)
        return s.removeSuffix(".0").replace('.', ',')
    }

    fun views(n: Long): String = if (n < 0) "" else "${count(n)} lượt xem"

    /** seconds -> "3:07" / "1:02:03" */
    fun duration(seconds: Long): String {
        if (seconds <= 0) return ""
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        return if (h > 0) String.format(Locale.ROOT, "%d:%02d:%02d", h, m, s)
        else String.format(Locale.ROOT, "%d:%02d", m, s)
    }

    fun millis(ms: Long): String = duration(ms / 1000).ifEmpty { "0:00" }

    /** Joins non-empty parts with " · " */
    fun dot(vararg parts: String?): String =
        parts.filterNot { it.isNullOrBlank() }.joinToString(" · ")

    /** Parses an ISO-8601 instant ("2026-10-03T12:00:00Z"); 0 when it is not one. */
    fun parseInstant(s: String?): Long {
        if (s.isNullOrBlank() || s.length < 10 || !s[0].isDigit() || s[4] != '-') return 0
        // Without java.time (Android 7 has none): "2026-10-03T12:00:00[.123](Z|+07:00)".
        val m = ISO.matchEntire(s.trim()) ?: return 0
        return runCatching {
            val g = m.groupValues
            val cal = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"))
            cal.clear()
            cal.set(g[1].toInt(), g[2].toInt() - 1, g[3].toInt(), g[4].toInt(), g[5].toInt(), g[6].toInt())
            val millis = g[7].takeIf { it.isNotEmpty() }?.padEnd(3, '0')?.take(3)?.toLong() ?: 0L
            val offset = when (val z = g[8]) {
                "", "Z", "z" -> 0L
                else -> {
                    val sign = if (z[0] == '-') -1 else 1
                    val hm = z.substring(1).replace(":", "")
                    sign * (hm.take(2).toLong() * 60 + hm.drop(2).ifEmpty { "0" }.toLong()) * 60_000L
                }
            }
            cal.timeInMillis + millis - offset
        }.getOrDefault(0L)
    }

    private val ISO = Regex(
        "(\\d{4})-(\\d{2})-(\\d{2})[T ](\\d{2}):(\\d{2}):(\\d{2})(?:[.,](\\d+))?(Z|z|[+-]\\d{2}:?\\d{2})?"
    )

    /** "5 phút trước", "3 ngày trước"… */
    fun ago(millis: Long): String {
        val diff = (System.currentTimeMillis() - millis).coerceAtLeast(0) / 1000
        return when {
            diff < 3600 -> "${(diff / 60).coerceAtLeast(1)} phút trước"
            diff < 86_400 -> "${diff / 3600} giờ trước"
            diff < 7 * 86_400 -> "${diff / 86_400} ngày trước"
            diff < 30 * 86_400 -> "${diff / (7 * 86_400)} tuần trước"
            diff < 365 * 86_400 -> "${diff / (30 * 86_400)} tháng trước"
            else -> "${diff / (365 * 86_400)} năm trước"
        }
    }

    fun hours(ms: Long): String {
        val minutes = ms / 60_000
        return if (minutes < 60) "$minutes phút" else String.format(
            Locale.ROOT, "%.1f giờ", minutes / 60.0
        ).replace(".0 ", " ").replace('.', ',')
    }
}
