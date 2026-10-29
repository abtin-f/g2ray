package com.abtin.tglass.features.chat

import android.icu.lang.UCharacter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * Keyword search over the emoji keyboard. The names come from the Unicode character names that Android ships (ICU),
 * so "sunglasses" finds the smiling face with sunglasses; flags use the English country name. The index is built
 * lazily, once, on a background thread.
 */
internal object EmojiSearch {
    private class Entry(val emoji: String, val name: String)

    @Volatile
    private var index: List<Entry>? = null

    /** A few everyday words that do not appear in the Unicode names. */
    private val Aliases = mapOf(
        "lol" to "laughing", "haha" to "laughing", "funny" to "laughing", "happy" to "smiling", "smile" to "smiling",
        "sad" to "crying", "cry" to "crying", "love" to "heart", "cool" to "sunglasses", "thanks" to "folded",
        "pray" to "folded", "please" to "folded", "yes" to "thumbs up", "like" to "thumbs up", "mad" to "angry",
        "sleep" to "sleepy", "wow" to "astonished", "scared" to "fearful", "poop" to "pile of poo", "tired" to "weary",
        "party" to "party popper", "ok" to "ok hand", "cash" to "money", "dog" to "dog face", "cat" to "cat face",
    )

    suspend fun search(query: String): List<String> = withContext(Dispatchers.Default) {
        val idx = index ?: build().also { index = it }
        val tokens = query.lowercase(Locale.ROOT).split(' ').filter { it.isNotBlank() }
        if (tokens.isEmpty()) return@withContext emptyList()
        val alternatives = tokens.map { t ->
            listOfNotNull(t, Aliases[t], stem(t).takeIf { it.length >= 3 && it != t })
        }
        val hits = ArrayList<String>()
        for (e in idx) {
            if (alternatives.all { alts -> alts.any { e.name.contains(it) } }) {
                hits.add(e.emoji)
                if (hits.size >= 240) break
            }
        }
        hits
    }

    /** "smile" -> "smil", so it also finds "smiling". */
    private fun stem(t: String) = t.removeSuffix("ing").removeSuffix("s").removeSuffix("e")

    private fun build(): List<Entry> {
        val seen = HashSet<String>()
        val out = ArrayList<Entry>(2000)
        for (cat in EmojiCategories) {
            for (e in cat.emoji) {
                if (!seen.add(e)) continue
                out.add(Entry(e, nameOf(e).lowercase(Locale.ROOT)))
            }
        }
        return out
    }

    private fun nameOf(emoji: String): String {
        val cps = ArrayList<Int>(8)
        var i = 0
        while (i < emoji.length) {
            val cp = emoji.codePointAt(i)
            cps.add(cp)
            i += Character.charCount(cp)
        }
        // Flags: two regional indicators.
        if (cps.size == 2 && cps.all { it in 0x1F1E6..0x1F1FF }) {
            val code = String(CharArray(2) { ('A' + (cps[it] - 0x1F1E6)) })
            return Locale("", code).getDisplayCountry(Locale.ENGLISH) + " flag"
        }
        // Keycaps: digit / # / * + FE0F + 20E3.
        if (cps.size >= 2 && cps.last() == 0x20E3) return "keycap " + String(Character.toChars(cps.first()))
        val parts = LinkedHashSet<String>()
        for (cp in cps) {
            if (cp == 0xFE0F || cp == 0x200D || cp in 0x1F3FB..0x1F3FF) continue
            val n = runCatching { UCharacter.getName(cp) }.getOrNull()
            if (!n.isNullOrBlank()) parts.add(n)
        }
        return parts.joinToString(" ")
    }
}
