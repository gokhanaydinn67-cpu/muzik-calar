package com.muzik.calar

import android.content.Context

// Çalma listeleri ve dinlenme sayaçları telefonda saklanır.
object MusicStore {
    private const val PREFS = "music_data"
    private const val KEY_LISTS = "lists"
    private const val KEY_COUNTS = "counts"

    fun loadLists(context: Context): Map<String, List<Long>> {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val names = prefs.getStringSet(KEY_LISTS, emptySet()) ?: emptySet()
        val result = LinkedHashMap<String, List<Long>>()
        for (name in names.sorted()) {
            val raw = prefs.getString("list_$name", "") ?: ""
            result[name] = raw.split(",").filter { it.isNotBlank() }.map { it.toLong() }
        }
        return result
    }

    fun saveLists(context: Context, data: Map<String, List<Long>>) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val editor = prefs.edit()
        val old = prefs.getStringSet(KEY_LISTS, emptySet()) ?: emptySet()
        for (name in old) {
            editor.remove("list_$name")
        }
        editor.putStringSet(KEY_LISTS, data.keys.toSet())
        for ((name, ids) in data) {
            editor.putString("list_$name", ids.joinToString(","))
        }
        editor.apply()
    }

    fun loadCounts(context: Context): Map<Long, Int> {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_COUNTS, "") ?: ""
        val result = HashMap<Long, Int>()
        for (part in raw.split(";")) {
            val pieces = part.split(":")
            if (pieces.size == 2) {
                val id = pieces[0].toLongOrNull()
                val n = pieces[1].toIntOrNull()
                if (id != null && n != null) {
                    result[id] = n
                }
            }
        }
        return result
    }

    fun incrementCount(context: Context, songId: Long) {
        val counts = loadCounts(context).toMutableMap()
        counts[songId] = (counts[songId] ?: 0) + 1
        val raw = counts.entries.joinToString(";") { "${it.key}:${it.value}" }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_COUNTS, raw)
            .apply()
    }
}

// En çok dinlenen şarkıları, dinlenme sayısına göre sıralar.
fun topSongs(songs: List<Song>, counts: Map<Long, Int>, limit: Int): List<Song> =
    songs.filter { (counts[it.id] ?: 0) > 0 }
        .sortedByDescending { counts[it.id] ?: 0 }
        .take(limit)
