package uz.jarvis

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class Item(val pkg: String, val title: String, val text: String, val time: Long)

object NotifStore {
    private const val KEY = "msgs"

    private fun prefs(ctx: Context) =
        ctx.applicationContext.getSharedPreferences("jarvis", Context.MODE_PRIVATE)

    fun all(ctx: Context): List<Item> {
        return try {
            val arr = JSONArray(prefs(ctx).getString(KEY, "[]"))
            val out = ArrayList<Item>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                out.add(Item(o.getString("p"), o.getString("t"), o.getString("x"), o.getLong("m")))
            }
            out
        } catch (e: Exception) {
            emptyList()
        }
    }

    @Synchronized
    fun add(ctx: Context, item: Item) {
        val list = all(ctx).toMutableList()
        val last = list.lastOrNull()
        if (last != null && last.pkg == item.pkg && last.title == item.title && last.text == item.text) return
        list.add(item)
        val arr = JSONArray()
        for (x in list.takeLast(40)) {
            arr.put(JSONObject().put("p", x.pkg).put("t", x.title).put("x", x.text).put("m", x.time))
        }
        prefs(ctx).edit().putString(KEY, arr.toString()).apply()
    }
}
