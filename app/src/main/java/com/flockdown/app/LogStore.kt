package com.flockdown.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

data class LogEntry(
    val cameraId: Long,
    val lat: Double,
    val lon: Double,
    val isFlock: Boolean,
    val timestamp: Long
)

object LogStore {
    private const val PREFS = "flockdown_log"
    private const val KEY = "entries"
    private const val MAX_ENTRIES = 500
    private val fmt = SimpleDateFormat("MMM d  h:mm a", Locale.US)

    fun add(context: Context, entry: LogEntry) {
        val arr = load(context)
        val obj = JSONObject().apply {
            put("id", entry.cameraId)
            put("lat", entry.lat)
            put("lon", entry.lon)
            put("flock", entry.isFlock)
            put("ts", entry.timestamp)
        }
        val newArr = JSONArray()
        newArr.put(obj)
        for (i in 0 until minOf(arr.length(), MAX_ENTRIES - 1)) newArr.put(arr.get(i))
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY, newArr.toString()).apply()
    }

    fun getAll(context: Context): List<LogEntry> {
        val arr = load(context)
        val list = mutableListOf<LogEntry>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            list.add(LogEntry(
                cameraId = o.getLong("id"),
                lat = o.getDouble("lat"),
                lon = o.getDouble("lon"),
                isFlock = o.getBoolean("flock"),
                timestamp = o.getLong("ts")
            ))
        }
        return list
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().remove(KEY).apply()
    }

    fun formatTime(ts: Long): String = fmt.format(Date(ts))

    private fun load(context: Context): JSONArray {
        val str = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, null) ?: return JSONArray()
        return JSONArray(str)
    }
}
