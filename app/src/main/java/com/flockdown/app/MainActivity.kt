package com.flockdown.app

import android.Manifest
import android.content.*
import android.content.pm.PackageManager
import android.graphics.*
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.tasks.await
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.cachemanager.CacheManager
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay

class MainActivity : AppCompatActivity() {

    private lateinit var map: MapView
    private lateinit var locationOverlay: MyLocationNewOverlay
    private lateinit var tvSpeed: TextView
    private lateinit var tvNearest: TextView
    private lateinit var tvAlert: TextView
    private lateinit var tvFlockDist: TextView
    private lateinit var tvSession: TextView
    private lateinit var tvTotal: TextView
    private lateinit var layoutFlockAlert: View
    private lateinit var btnFlockOnly: android.widget.Button
    private lateinit var prefs: SharedPreferences

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var flockOnly = false
    private var cameraOverlay: CameraOverlay? = null

    private val alertReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                ProximityService.ACTION_SPEED -> {
                    val mph = intent.getIntExtra(ProximityService.EXTRA_SPEED, 0)
                    tvSpeed.text = "$mph mph"
                }
                ProximityService.ACTION_COUNT -> {
                    tvSession.text = intent.getIntExtra(ProximityService.EXTRA_SESSION, 0).toString()
                    tvTotal.text = intent.getIntExtra(ProximityService.EXTRA_LIFETIME, 0).toString()
                }
                ProximityService.ACTION_ALERT -> {
                    val dist = intent.getDoubleExtra(ProximityService.EXTRA_DIST, 0.0)
                    val urgent = intent.getBooleanExtra(ProximityService.EXTRA_URGENT, false)
                    val isFlock = intent.getBooleanExtra(ProximityService.EXTRA_IS_FLOCK, false)
                    showAlert(dist, urgent, isFlock)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setContentView(R.layout.activity_main)

        prefs = getSharedPreferences(ProximityService.PREFS, MODE_PRIVATE)

        map = findViewById(R.id.map)
        tvSpeed = findViewById(R.id.tvSpeed)
        tvNearest = findViewById(R.id.tvNearest)
        tvAlert = findViewById(R.id.tvAlert)
        tvFlockDist = findViewById(R.id.tvFlockDist)
        tvSession = findViewById(R.id.tvSession)
        tvTotal = findViewById(R.id.tvTotal)
        layoutFlockAlert = findViewById(R.id.layoutFlockAlert)
        btnFlockOnly = findViewById(R.id.switchFlockOnly)

        flockOnly = prefs.getBoolean(ProximityService.PREF_FLOCK_ONLY, false)
        tvTotal.text = prefs.getInt(ProximityService.PREF_LIFETIME_COUNT, 0).toString()

        applyCounterVisibility()
        updateFlockToggle()

        btnFlockOnly.setOnClickListener {
            flockOnly = !flockOnly
            prefs.edit().putBoolean(ProximityService.PREF_FLOCK_ONLY, flockOnly).apply()
            updateFlockToggle()
            reloadOverlay()
        }

        findViewById<android.widget.Button>(R.id.btnSettings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        findViewById<android.widget.Button>(R.id.btnLog).setOnClickListener {
            startActivity(Intent(this, LogActivity::class.java))
        }
        findViewById<android.widget.Button>(R.id.btnRecenter).setOnClickListener {
            recenterToMyLocation()
        }
        findViewById<android.widget.Button>(R.id.btnCache).setOnClickListener {
            cacheCurrentArea()
        }

        setupMap()
        checkPermissionsAndStart()
    }

    private fun setupMap() {
        map.setMultiTouchControls(true)
        map.isTilesScaledToDpi = true
        map.setTileSource(TileSourceFactory.MAPNIK)

        // default to center of US until GPS locks
        map.controller.setZoom(4.0)
        map.controller.setCenter(GeoPoint(39.5, -98.35))

        val locationProvider = GpsMyLocationProvider(this).apply {
            addLocationSource(android.location.LocationManager.NETWORK_PROVIDER)
        }
        locationOverlay = MyLocationNewOverlay(locationProvider, map)
        locationOverlay.enableMyLocation()
        locationOverlay.enableFollowLocation()
        locationOverlay.runOnFirstFix {
            runOnUiThread {
                locationOverlay.myLocation?.let {
                    map.controller.setZoom(16.0)
                    map.controller.setCenter(it)
                }
            }
        }
        map.overlays.add(locationOverlay)
    }

    private fun reloadOverlay() {
        cameraOverlay?.let { map.overlays.remove(it) }
        loadCameraOverlay()
    }

    private fun loadCameraOverlay() {
        val currentFlockOnly = flockOnly
        scope.launch {
            CameraStore.load(this@MainActivity)
            val cameras = CameraStore.getAll(currentFlockOnly)
            val overlay = CameraOverlay(cameras) { cam -> showCameraInfo(cam) }
            withContext(Dispatchers.Main) {
                cameraOverlay = overlay
                map.overlays.add(0, overlay)
                map.invalidate()
            }
        }
    }

    private var locCallback: com.google.android.gms.location.LocationCallback? = null

    private fun recenterToMyLocation() {
        // if overlay already has a fix, snap back immediately
        locationOverlay.myLocation?.let {
            map.controller.setZoom(16.0)
            map.controller.animateTo(it)
            locationOverlay.enableFollowLocation()
            return
        }
        // no cached fix yet — request one
        try {
            val fusedClient = LocationServices.getFusedLocationProviderClient(this)
            locCallback?.let { fusedClient.removeLocationUpdates(it) }
            val req = com.google.android.gms.location.LocationRequest.Builder(
                Priority.PRIORITY_HIGH_ACCURACY, 1000L
            ).setMaxUpdates(1).build()
            locCallback = object : com.google.android.gms.location.LocationCallback() {
                override fun onLocationResult(result: com.google.android.gms.location.LocationResult) {
                    result.lastLocation?.let { loc ->
                        fusedClient.removeLocationUpdates(this)
                        runOnUiThread {
                            map.controller.setZoom(16.0)
                            map.controller.animateTo(GeoPoint(loc.latitude, loc.longitude))
                            locationOverlay.enableFollowLocation()
                        }
                    }
                }
            }
            fusedClient.requestLocationUpdates(req, locCallback!!, mainLooper)
        } catch (e: SecurityException) { }
    }

    private fun showCameraInfo(cam: Camera) {
        val type = if (cam.isFlock) "Flock Safety ALPR" else "ALPR Camera"
        val coords = "%.5f, %.5f".format(cam.lat, cam.lon)
        val mapsUrl = "https://maps.google.com/?q=${cam.lat},${cam.lon}"

        AlertDialog.Builder(this)
            .setTitle(type)
            .setMessage("ID: ${cam.id}\nCoordinates: $coords")
            .setPositiveButton("Open in Maps") { _, _ ->
                val intent = android.content.Intent(
                    android.content.Intent.ACTION_VIEW,
                    android.net.Uri.parse(mapsUrl)
                )
                startActivity(intent)
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun updateFlockToggle() {
        if (flockOnly) {
            btnFlockOnly.text = "FLOCK ONLY"
            btnFlockOnly.backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#CC FF0000".replace(" ", "")))
            btnFlockOnly.setTextColor(Color.WHITE)
        } else {
            btnFlockOnly.text = "ALL CAMERAS"
            btnFlockOnly.backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#AA000000"))
            btnFlockOnly.setTextColor(Color.parseColor("#AAAAAA"))
        }
    }

    private fun applyCounterVisibility() {
        val showSession = prefs.getBoolean(SettingsActivity.PREF_SHOW_SESSION, true)
        val showLifetime = prefs.getBoolean(SettingsActivity.PREF_SHOW_LIFETIME, true)
        (tvSession.parent as? View)?.visibility = if (showSession || showLifetime) View.VISIBLE else View.GONE
        findViewById<View>(R.id.tvSessionLabel).visibility = if (showSession) View.VISIBLE else View.GONE
        tvSession.visibility = if (showSession) View.VISIBLE else View.GONE
        findViewById<View>(R.id.tvTotalLabel).visibility = if (showLifetime) View.VISIBLE else View.GONE
        tvTotal.visibility = if (showLifetime) View.VISIBLE else View.GONE
    }

    private fun showAlert(distMeters: Double, urgent: Boolean, isFlock: Boolean) {
        val distFt = (distMeters * 3.28084).toInt()
        tvNearest.text = if (isFlock) "FLOCK: ${distFt}ft" else "ALPR: ${distFt}ft"
        if (isFlock) {
            tvFlockDist.text = "${distFt} ft away"
            layoutFlockAlert.visibility = View.VISIBLE
            layoutFlockAlert.postDelayed({ layoutFlockAlert.visibility = View.GONE }, if (urgent) 6000 else 4000)
        } else {
            tvAlert.text = "⚠ ALPR camera ~${distFt}ft"
            tvAlert.visibility = View.VISIBLE
            tvAlert.postDelayed({ tvAlert.visibility = View.GONE }, 6000)
        }
    }

    private fun checkPermissionsAndStart() {
        val perms = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        val needed = perms.filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
        if (needed.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, needed.toTypedArray(), 1)
        } else {
            onPermissionsGranted()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) onPermissionsGranted()
    }

    private fun onPermissionsGranted() {
        loadCameraOverlay()
        startService(Intent(this, ProximityService::class.java))
    }

    override fun onResume() {
        super.onResume()
        map.onResume()
        locationOverlay.enableMyLocation()
        applyCounterVisibility()
        val filter = IntentFilter().apply {
            addAction(ProximityService.ACTION_ALERT)
            addAction(ProximityService.ACTION_SPEED)
            addAction(ProximityService.ACTION_COUNT)
        }
        registerReceiver(alertReceiver, filter, RECEIVER_NOT_EXPORTED)
    }

    override fun onPause() {
        super.onPause()
        map.onPause()
        unregisterReceiver(alertReceiver)
    }

    private fun cacheCurrentArea() {
        val box = map.boundingBox ?: return
        val latSpan = (box.latNorth - box.latSouth) * 0.5
        val lonSpan = (box.lonEast - box.lonWest) * 0.5
        val expanded = BoundingBox(
            box.latNorth + latSpan,
            box.lonEast + lonSpan,
            box.latSouth - latSpan,
            box.lonWest - lonSpan
        )
        val cacheManager = CacheManager(map)
        val tileCount = cacheManager.possibleTilesInArea(expanded, 10, 17)
        if (tileCount > 25000) {
            android.widget.Toast.makeText(this, "Area too large (~$tileCount tiles). Zoom in first.", android.widget.Toast.LENGTH_LONG).show()
            return
        }
        android.widget.Toast.makeText(this, "Caching ~$tileCount tiles for offline use...", android.widget.Toast.LENGTH_LONG).show()
        cacheManager.downloadAreaAsync(this, expanded, 10, 17, object : CacheManager.CacheManagerCallback {
            override fun onTaskComplete() {
                runOnUiThread { android.widget.Toast.makeText(this@MainActivity, "Map cached! Drive safe offline.", android.widget.Toast.LENGTH_SHORT).show() }
            }
            override fun onTaskFailed(errors: Int) {
                runOnUiThread { android.widget.Toast.makeText(this@MainActivity, "Cache incomplete ($errors tiles failed).", android.widget.Toast.LENGTH_SHORT).show() }
            }
            override fun updateProgress(progress: Int, currentZoomLevel: Int, zoomMin: Int, zoomMax: Int) {}
            override fun downloadStarted() {}
            override fun setPossibleTilesInArea(total: Int) {}
        })
    }

    override fun onDestroy() {
        locCallback?.let { LocationServices.getFusedLocationProviderClient(this).removeLocationUpdates(it) }
        scope.cancel()
        super.onDestroy()
    }
}
