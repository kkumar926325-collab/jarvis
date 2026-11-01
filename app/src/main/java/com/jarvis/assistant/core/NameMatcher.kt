package com.jarvis.assistant.core

sealed class Match<out T> {
    data class Single<T>(val item: T) : Match<T>()
    data class Many<T>(val items: List<T>) : Match<T>()
    data object None : Match<Nothing>()
}

/** Forgiving name matching shared by contacts, apps and folders. Works with Devanagari too. */
object NameMatcher {
    private fun isWordChar(c: Char): Boolean {
        if (c.isLetterOrDigit()) return true
        // Combining marks (e.g. Devanagari matras) are not letters but belong to the word.
        val t = Character.getType(c)
        return t == Character.NON_SPACING_MARK.toInt() ||
            t == Character.COMBINING_SPACING_MARK.toInt() ||
            t == Character.ENCLOSING_MARK.toInt()
    }

    fun normalize(s: String): String {
        val sb = StringBuilder(s.length)
        for (c in s.lowercase()) sb.append(if (isWordChar(c)) c else ' ')
        return sb.toString().trim().replace(Regex("\\s+"), " ")
    }

    fun score(name: String, query: String): Int {
        val n = normalize(name)
        val q = normalize(query)
        if (n.isEmpty() || q.isEmpty()) return 0
        if (n == q) return 100
        if (n.startsWith(q)) return 80
        if (n.split(' ').any { it.startsWith(q) }) return 60
        if (n.contains(q)) return 40
        if (q.split(' ').all { n.contains(it) }) return 20
        return 0
    }

    fun <T> rank(items: List<T>, query: String, nameOf: (T) -> String): List<Pair<T, Int>> =
        items.map { it to score(nameOf(it), query) }
            .filter { it.second > 0 }
            .sortedWith(compareByDescending<Pair<T, Int>> { it.second }.thenBy { nameOf(it.first).lowercase() })

    /** One clear winner -> Single. Several equally good matches -> Many. Nothing -> None. */
    fun <T> resolve(items: List<T>, query: String, nameOf: (T) -> String): Match<T> {
        val ranked = rank(items, query, nameOf)
        if (ranked.isEmpty()) return Match.None
        if (ranked.size == 1) return Match.Single(ranked[0].first)
        val top = ranked[0].second
        if (top > ranked[1].second) return Match.Single(ranked[0].first)
        return Match.Many(ranked.filter { it.second == top }.map { it.first })
    }
}
