package com.flockdown.app

import android.content.SharedPreferences
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import org.osmdroid.tileprovider.cachemanager.CacheManager
import org.osmdroid.util.BoundingBox
import org.osmdroid.views.MapView

class SettingsActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences
    private lateinit var map: MapView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        prefs = getSharedPreferences(ProximityService.PREFS, MODE_PRIVATE)

        // hidden map view needed to access CacheManager tile source config
        map = MapView(this)
        map.setTileSource(org.osmdroid.tileprovider.tilesource.TileSourceFactory.MAPNIK)

        val switchSession = findViewById<Switch>(R.id.switchShowSession)
        val switchLifetime = findViewById<Switch>(R.id.switchShowLifetime)
        val btnResetLifetime = findViewById<Button>(R.id.btnResetLifetime)
        val btnClearLog = findViewById<Button>(R.id.btnClearLog)
        val spinnerState = findViewById<Spinner>(R.id.spinnerState)
        val btnDownloadState = findViewById<Button>(R.id.btnDownloadState)
        val tvStatus = findViewById<TextView>(R.id.tvDownloadStatus)

        switchSession.isChecked = prefs.getBoolean(PREF_SHOW_SESSION, true)
        switchLifetime.isChecked = prefs.getBoolean(PREF_SHOW_LIFETIME, true)

        switchSession.setOnCheckedChangeListener { _, checked ->
            prefs.edit().putBoolean(PREF_SHOW_SESSION, checked).apply()
        }
        switchLifetime.setOnCheckedChangeListener { _, checked ->
            prefs.edit().putBoolean(PREF_SHOW_LIFETIME, checked).apply()
        }

        btnResetLifetime.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Reset Lifetime Count")
                .setMessage("Are you sure? This cannot be undone.")
                .setPositiveButton("Reset") { _, _ ->
                    prefs.edit().putInt(ProximityService.PREF_LIFETIME_COUNT, 0).apply()
                    Toast.makeText(this, "Lifetime count reset", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        btnClearLog.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Clear Camera Log")
                .setMessage("Delete all log entries? This cannot be undone.")
                .setPositiveButton("Clear") { _, _ ->
                    LogStore.clear(this)
                    Toast.makeText(this, "Log cleared", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        val stateNames = STATE_BOXES.map { it.first }
        spinnerState.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, stateNames)

        btnDownloadState.setOnClickListener {
            val idx = spinnerState.selectedItemPosition
            val (name, box) = STATE_BOXES[idx]
            val cacheManager = CacheManager(map)
            val tileCount = cacheManager.possibleTilesInArea(box, 5, 10)
            tvStatus.text = "Downloading ~$tileCount tiles for $name..."
            btnDownloadState.isEnabled = false

            cacheManager.downloadAreaAsync(this, box, 5, 10, object : CacheManager.CacheManagerCallback {
                override fun onTaskComplete() {
                    runOnUiThread {
                        tvStatus.text = "$name cached!"
                        btnDownloadState.isEnabled = true
                    }
                }
                override fun onTaskFailed(errors: Int) {
                    runOnUiThread {
                        tvStatus.text = "Done with $errors failed tiles."
                        btnDownloadState.isEnabled = true
                    }
                }
                override fun updateProgress(progress: Int, currentZoomLevel: Int, zoomMin: Int, zoomMax: Int) {
                    runOnUiThread { tvStatus.text = "Downloading $name... zoom $currentZoomLevel ($progress%)" }
                }
                override fun downloadStarted() {}
                override fun setPossibleTilesInArea(total: Int) {}
            })
        }
    }

    override fun onDestroy() {
        map.onDetach()
        super.onDestroy()
    }

    companion object {
        const val PREF_SHOW_SESSION = "show_session"
        const val PREF_SHOW_LIFETIME = "show_lifetime"

        // (name, BoundingBox(north, east, south, west))
        val STATE_BOXES = listOf(
            Pair("Alabama",        BoundingBox(35.008, -84.889, 30.137, -88.473)),
            Pair("Alaska",         BoundingBox(71.539, -129.979, 54.774, -179.148)),
            Pair("Arizona",        BoundingBox(37.004, -109.045, 31.332, -114.817)),
            Pair("Arkansas",       BoundingBox(36.500, -89.644, 33.004, -94.618)),
            Pair("California",     BoundingBox(42.009, -114.131, 32.534, -124.409)),
            Pair("Colorado",       BoundingBox(41.003, -102.041, 36.993, -109.060)),
            Pair("Connecticut",    BoundingBox(42.050, -71.786, 40.950, -73.727)),
            Pair("Delaware",       BoundingBox(39.839, -74.984, 38.451, -75.789)),
            Pair("Florida",        BoundingBox(31.001, -79.974, 24.396, -87.635)),
            Pair("Georgia",        BoundingBox(35.001, -80.840, 30.357, -85.605)),
            Pair("Hawaii",         BoundingBox(22.235, -154.806, 18.910, -160.247)),
            Pair("Idaho",          BoundingBox(49.001, -111.043, 41.988, -117.243)),
            Pair("Illinois",       BoundingBox(42.508, -87.494, 36.970, -91.513)),
            Pair("Indiana",        BoundingBox(41.761, -84.784, 37.771, -88.099)),
            Pair("Iowa",           BoundingBox(43.501, -90.140, 40.375, -96.639)),
            Pair("Kansas",         BoundingBox(40.003, -94.588, 36.993, -102.052)),
            Pair("Kentucky",       BoundingBox(39.147, -81.964, 36.497, -89.571)),
            Pair("Louisiana",      BoundingBox(33.019, -88.817, 28.855, -94.043)),
            Pair("Maine",          BoundingBox(47.460, -66.949, 42.977, -71.084)),
            Pair("Maryland",       BoundingBox(39.723, -74.986, 37.911, -79.488)),
            Pair("Massachusetts",  BoundingBox(42.887, -69.928, 41.187, -73.508)),
            Pair("Michigan",       BoundingBox(48.306, -82.413, 41.696, -90.418)),
            Pair("Minnesota",      BoundingBox(49.384, -89.483, 43.499, -97.239)),
            Pair("Mississippi",    BoundingBox(34.996, -88.097, 30.013, -91.655)),
            Pair("Missouri",       BoundingBox(40.614, -89.099, 35.995, -95.774)),
            Pair("Montana",        BoundingBox(49.001, -104.040, 44.358, -116.049)),
            Pair("Nebraska",       BoundingBox(43.001, -95.308, 39.999, -104.053)),
            Pair("Nevada",         BoundingBox(42.002, -114.039, 35.001, -120.006)),
            Pair("New Hampshire",  BoundingBox(45.305, -70.610, 42.697, -72.557)),
            Pair("New Jersey",     BoundingBox(41.357, -73.893, 38.928, -75.560)),
            Pair("New Mexico",     BoundingBox(37.000, -103.001, 31.332, -109.050)),
            Pair("New York",       BoundingBox(45.015, -71.856, 40.496, -79.762)),
            Pair("North Carolina", BoundingBox(36.588, -75.460, 33.842, -84.322)),
            Pair("North Dakota",   BoundingBox(49.001, -96.554, 45.935, -104.049)),
            Pair("Ohio",           BoundingBox(41.977, -80.518, 38.403, -84.820)),
            Pair("Oklahoma",       BoundingBox(37.002, -94.430, 33.615, -103.002)),
            Pair("Oregon",         BoundingBox(46.236, -116.463, 41.992, -124.566)),
            Pair("Pennsylvania",   BoundingBox(42.269, -74.689, 39.719, -80.519)),
            Pair("Rhode Island",   BoundingBox(42.019, -71.120, 41.146, -71.908)),
            Pair("South Carolina", BoundingBox(35.215, -78.541, 32.034, -83.354)),
            Pair("South Dakota",   BoundingBox(45.945, -96.436, 42.479, -104.058)),
            Pair("Tennessee",      BoundingBox(36.678, -81.646, 34.982, -90.310)),
            Pair("Texas",          BoundingBox(36.500, -93.508, 25.837, -106.646)),
            Pair("Utah",           BoundingBox(42.001, -109.041, 36.998, -114.053)),
            Pair("Vermont",        BoundingBox(45.017, -71.465, 42.726, -73.437)),
            Pair("Virginia",       BoundingBox(39.466, -75.242, 36.540, -83.675)),
            Pair("Washington",     BoundingBox(49.002, -116.916, 45.543, -124.733)),
            Pair("West Virginia",  BoundingBox(40.638, -77.719, 37.201, -82.644)),
            Pair("Wisconsin",      BoundingBox(47.309, -86.249, 42.491, -92.889)),
            Pair("Wyoming",        BoundingBox(45.006, -104.052, 40.994, -111.056))
        )
    }
}
