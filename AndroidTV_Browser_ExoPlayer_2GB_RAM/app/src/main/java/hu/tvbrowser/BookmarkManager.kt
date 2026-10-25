package hu.tvbrowser

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

data class BookmarkItem(
    val id: String,
    val title: String,
    val url: String,
    val color: String
)

/**
 * Könnyűsúlyú könyvjelző menedzser, amely SharedPreferences-t használ,
 * így minimális a memórialábnyoma (nincs nehéz SQLite / Room terhelés).
 */
class BookmarkManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("tv_browser_bookmarks", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_BOOKMARKS = "saved_bookmarks"
    }

    init {
        // Alapértelmezett TV könyvjelzők inicializálása első indításkor
        if (!prefs.contains(KEY_BOOKMARKS)) {
            val defaultList = listOf(
                BookmarkItem("1", "YouTube", "https://m.youtube.com", "#E50914"),
                BookmarkItem("2", "Twitch", "https://m.twitch.tv", "#9146FF"),
                BookmarkItem("3", "Vimeo", "https://vimeo.com/watch", "#1AB7EA"),
                BookmarkItem("4", "Index Hírek", "https://index.hu", "#FF7700"),
                BookmarkItem("5", "Dailymotion", "https://www.dailymotion.com", "#0066DC"),
                BookmarkItem("6", "HLS Stream Demo", "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8", "#10B981")
            )
            saveBookmarks(defaultList)
        }
    }

    fun getBookmarks(): List<BookmarkItem> {
        val jsonStr = prefs.getString(KEY_BOOKMARKS, "[]") ?: "[]"
        val list = mutableListOf<BookmarkItem>()
        try {
            val jsonArray = JSONArray(jsonStr)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                list.add(
                    BookmarkItem(
                        id = obj.getString("id"),
                        title = obj.getString("title"),
                        url = obj.getString("url"),
                        color = obj.optString("color", "#3B82F6")
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }

    fun addBookmark(title: String, url: String) {
        val current = getBookmarks().toMutableList()
        val newBookmark = BookmarkItem(
            id = System.currentTimeMillis().toString(),
            title = title,
            url = url,
            color = "#2563EB"
        )
        current.add(0, newBookmark) // Új könyvjelző az elejére
        saveBookmarks(current)
    }

    fun removeBookmark(id: String) {
        val updated = getBookmarks().filter { it.id != id }
        saveBookmarks(updated)
    }

    private fun saveBookmarks(list: List<BookmarkItem>) {
        val jsonArray = JSONArray()
        for (item in list) {
            val obj = JSONObject()
            obj.put("id", item.id)
            obj.put("title", item.title)
            obj.put("url", item.url)
            obj.put("color", item.color)
            jsonArray.put(obj)
        }
        prefs.edit().putString(KEY_BOOKMARKS, jsonArray.toString()).apply()
    }
}
