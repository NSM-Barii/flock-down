package com.flockdown.app

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Point
import android.view.MotionEvent
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Overlay

class CameraOverlay(
    private val cameras: List<Camera>,
    private val onCameraTapped: (Camera) -> Unit
) : Overlay() {

    private val paintFlock = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.RED
        style = Paint.Style.FILL
    }
    private val paintFlockStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FF6666")
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }
    private val paintOther = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FF8800")
        style = Paint.Style.FILL
    }

    private val dotRadius = 10f
    private val tapRadius = 40f

    // spatial grid: bucket cameras by 1-degree lat/lon cells
    private val grid = HashMap<Long, MutableList<Camera>>(2048)

    init {
        for (cam in cameras) {
            val key = gridKey(cam.lat, cam.lon)
            grid.getOrPut(key) { mutableListOf() }.add(cam)
        }
    }

    private fun gridKey(lat: Double, lon: Double): Long {
        val latCell = lat.toInt() + 90
        val lonCell = lon.toInt() + 180
        return latCell * 1000L + lonCell
    }

    override fun draw(canvas: Canvas, mapView: MapView, shadow: Boolean) {
        if (shadow) return
        if (mapView.zoomLevelDouble < 11.0) return

        val box = mapView.boundingBox ?: return
        val proj = mapView.projection
        val point = Point()

        val latMin = box.latSouth.toInt() - 1
        val latMax = box.latNorth.toInt() + 1
        val lonMin = box.lonWest.toInt() - 1
        val lonMax = box.lonEast.toInt() + 1

        for (latCell in latMin..latMax) {
            for (lonCell in lonMin..lonMax) {
                val key = (latCell + 90) * 1000L + (lonCell + 180)
                val bucket = grid[key] ?: continue
                for (cam in bucket) {
                    if (!box.contains(cam.lat, cam.lon)) continue
                    proj.toPixels(GeoPoint(cam.lat, cam.lon), point)
                    val x = point.x.toFloat()
                    val y = point.y.toFloat()
                    if (cam.isFlock) {
                        canvas.drawCircle(x, y, dotRadius, paintFlock)
                        canvas.drawCircle(x, y, dotRadius, paintFlockStroke)
                    } else {
                        canvas.drawCircle(x, y, dotRadius, paintOther)
                    }
                }
            }
        }
    }

    override fun onSingleTapConfirmed(e: MotionEvent, mapView: MapView): Boolean {
        val proj = mapView.projection
        val point = Point()
        var closest: Camera? = null
        var closestDist = tapRadius

        val box = mapView.boundingBox ?: return false
        val latMin = box.latSouth.toInt() - 1
        val latMax = box.latNorth.toInt() + 1
        val lonMin = box.lonWest.toInt() - 1
        val lonMax = box.lonEast.toInt() + 1

        for (latCell in latMin..latMax) {
            for (lonCell in lonMin..lonMax) {
                val key = (latCell + 90) * 1000L + (lonCell + 180)
                val bucket = grid[key] ?: continue
                for (cam in bucket) {
                    proj.toPixels(GeoPoint(cam.lat, cam.lon), point)
                    val dx = e.x - point.x
                    val dy = e.y - point.y
                    val dist = Math.sqrt((dx * dx + dy * dy).toDouble()).toFloat()
                    if (dist < closestDist) {
                        closestDist = dist
                        closest = cam
                    }
                }
            }
        }

        closest?.let {
            onCameraTapped(it)
            return true
        }
        return false
    }
}
