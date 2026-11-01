package com.jarvis.assistant.core

import java.util.Locale

object PhoneNumbers {
    private val numberLike = Regex("^[+()\\d\\s.-]{3,}$")

    /** Keeps ASCII digits and a leading '+'. Everything else is dropped. */
    fun sanitize(raw: String): String {
        val sb = StringBuilder()
        for (c in raw) {
            if (c in '0'..'9') sb.append(c) else if (c == '+' && sb.isEmpty()) sb.append(c)
        }
        return sb.toString()
    }

    fun looksLikeNumber(text: String): Boolean =
        numberLike.matches(text.trim()) && sanitize(text).count { it in '0'..'9' } >= 3
}

object Format {
    fun bytes(bytes: Long): String {
        val gb = bytes / 1_073_741_824.0
        val mb = bytes / 1_048_576.0
        return when {
            bytes >= 1_073_741_824L -> String.format(Locale.US, "%.1f GB", gb)
            bytes >= 1_048_576L -> String.format(Locale.US, "%.0f MB", mb)
            bytes >= 1024L -> String.format(Locale.US, "%.0f KB", bytes / 1024.0)
            else -> "$bytes B"
        }
    }
}
