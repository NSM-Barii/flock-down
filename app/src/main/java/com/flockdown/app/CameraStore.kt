package com.flockdown.app

import android.content.Context
import java.io.BufferedReader
import java.io.InputStreamReader

data class Camera(val id: Long, val lat: Double, val lon: Double, val isFlock: Boolean)

object CameraStore {
    private var cameras: List<Camera> = emptyList()

    fun load(context: Context) {
        if (cameras.isNotEmpty()) return
        val list = mutableListOf<Camera>()
        context.resources.openRawResource(R.raw.alpr_cameras).use { stream ->
            BufferedReader(InputStreamReader(stream)).use { reader ->
                reader.readLine() // skip header
                reader.forEachLine { line ->
                    val parts = line.split(',')
                    if (parts.size == 4) {
                        list.add(Camera(
                            id = parts[0].toLong(),
                            lat = parts[1].toDouble(),
                            lon = parts[2].toDouble(),
                            isFlock = parts[3] == "1"
                        ))
                    }
                }
            }
        }
        cameras = list
    }

    fun getNearby(lat: Double, lon: Double, radiusMeters: Double, flockOnly: Boolean): List<Pair<Camera, Double>> {
        return cameras
            .filter { !flockOnly || it.isFlock }
            .map { it to haversine(lat, lon, it.lat, it.lon) }
            .filter { it.second <= radiusMeters }
            .sortedBy { it.second }
    }

    fun getAll(flockOnly: Boolean): List<Camera> =
        if (flockOnly) cameras.filter { it.isFlock } else cameras

    private fun haversine(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = Math.sin(dLat / 2).let { it * it } +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(dLon / 2).let { it * it }
        return r * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
    }
}
