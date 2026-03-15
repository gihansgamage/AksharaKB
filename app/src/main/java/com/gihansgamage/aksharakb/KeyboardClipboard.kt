package com.gihansgamage.aksharakb

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray

class KeyboardClipboard(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("aksharakb_clipboard", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_ITEMS  = "clipboard_items"
        private const val MAX_ITEMS  = 20
    }

    /** All saved clipboard entries, newest first */
    fun getAll(): List<String> {
        val raw = prefs.getString(KEY_ITEMS, "[]") ?: "[]"
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { arr.getString(it) }
        } catch (_: Exception) { emptyList() }
    }

    /** Save a new item (deduplicates, trims to MAX_ITEMS) */
    fun save(text: String) {
        if (text.isBlank() || text.length > 500) return
        val current = getAll().toMutableList()
        current.remove(text)          // remove duplicate
        current.add(0, text)          // insert at top
        val trimmed = current.take(MAX_ITEMS)
        val arr = JSONArray(trimmed)
        prefs.edit().putString(KEY_ITEMS, arr.toString()).apply()
    }

    /** Delete one item */
    fun delete(text: String) {
        val current = getAll().toMutableList()
        current.remove(text)
        prefs.edit().putString(KEY_ITEMS, JSONArray(current).toString()).apply()
    }

    /** Clear all */
    fun clear() {
        prefs.edit().putString(KEY_ITEMS, "[]").apply()
    }
}