package com.flockdown.app

import android.app.*
import android.content.Intent
import android.content.SharedPreferences
import android.media.ToneGenerator
import android.media.AudioManager
import android.os.*
import android.location.Location
import android.speech.tts.TextToSpeech
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.*
import java.util.Locale

class ProximityService : Service() {

    private lateinit var fusedClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private lateinit var prefs: SharedPreferences
    private lateinit var locationThread: HandlerThread
    private val toneGen by lazy { ToneGenerator(AudioManager.STREAM_MUSIC, 100) }
    private var tts: TextToSpeech? = null

    private var prevLocation: Location? = null
    private var lastBeepTime = 0L
    private val beepCooldownMs = 10_000L
    private val warnAt = 500.0
    private val alertAt = 150.0

    private var sessionCount = 0
    private val triggeredIds = mutableSetOf<Long>()

    override fun onCreate() {
        super.onCreate()
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        CameraStore.load(this)
        fusedClient = LocationServices.getFusedLocationProviderClient(this)
        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) tts?.language = Locale.US
        }
        startForeground(1, buildNotification())
        startLocationUpdates()
    }

    private fun startLocationUpdates() {
        val req = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000L)
            .setMinUpdateIntervalMillis(500L)
            .build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { onLocation(it) }
            }
        }

        try {
            locationThread = HandlerThread("LocationThread").also { it.start() }
            fusedClient.requestLocationUpdates(req, locationCallback, locationThread.looper)
        } catch (e: SecurityException) {
            stopSelf()
        }
    }

    private fun speedMph(loc: Location): Int {
        val speedMs = if (loc.speed > 0.5f) {
            loc.speed.toDouble()
        } else {
            val prev = prevLocation
            if (prev != null && loc.time > prev.time) {
                val dist = prev.distanceTo(loc).toDouble()
                val secs = (loc.time - prev.time) / 1000.0
                if (secs > 0) dist / secs else 0.0
            } else 0.0
        }
        prevLocation = loc
        return (speedMs * 2.23694).toInt()
    }

    private fun onLocation(loc: Location) {
        sendBroadcast(Intent(ACTION_SPEED).apply { putExtra(EXTRA_SPEED, speedMph(loc)) })

        val nearby = CameraStore.getNearby(loc.latitude, loc.longitude, warnAt, false)
        if (nearby.isEmpty()) return

        val (cam, dist) = nearby.first()
        val now = SystemClock.elapsedRealtime()
        if (now - lastBeepTime < beepCooldownMs) return
        lastBeepTime = now

        when {
            dist <= alertAt -> {
                if (cam.isFlock) {
                    toneGen.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 800)
                } else {
                    toneGen.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 400)
                }
                broadcastAlert(dist, urgent = true, isFlock = cam.isFlock)
                if (cam.isFlock && !triggeredIds.contains(cam.id)) {
                    tts?.speak("Flock detected", TextToSpeech.QUEUE_FLUSH, null, null)
                    triggeredIds.add(cam.id)
                    sessionCount++
                    val lifetime = prefs.getInt(PREF_LIFETIME_COUNT, 0) + 1
                    prefs.edit().putInt(PREF_LIFETIME_COUNT, lifetime).apply()
                    broadcastCounts(sessionCount, lifetime)
                    LogStore.add(this, LogEntry(
                        cameraId = cam.id,
                        lat = cam.lat,
                        lon = cam.lon,
                        isFlock = cam.isFlock,
                        timestamp = System.currentTimeMillis()
                    ))
                }
            }
            dist <= warnAt -> {
                if (cam.isFlock) {
                    toneGen.startTone(ToneGenerator.TONE_PROP_BEEP2, 400)
                } else {
                    toneGen.startTone(ToneGenerator.TONE_PROP_BEEP, 200)
                }
                broadcastAlert(dist, urgent = false, isFlock = cam.isFlock)
            }
        }
    }

    private fun broadcastAlert(distMeters: Double, urgent: Boolean, isFlock: Boolean) {
        sendBroadcast(Intent(ACTION_ALERT).apply {
            putExtra(EXTRA_DIST, distMeters)
            putExtra(EXTRA_URGENT, urgent)
            putExtra(EXTRA_IS_FLOCK, isFlock)
        })
    }

    private fun broadcastCounts(session: Int, lifetime: Int) {
        sendBroadcast(Intent(ACTION_COUNT).apply {
            putExtra(EXTRA_SESSION, session)
            putExtra(EXTRA_LIFETIME, lifetime)
        })
    }

    private fun buildNotification(): Notification {
        val channelId = "flockdown"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "FlockDown", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("Flock-Down")
            .setContentText("Monitoring for ALPR cameras")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .build()
    }

    override fun onBind(intent: Intent?) = null

    override fun onDestroy() {
        fusedClient.removeLocationUpdates(locationCallback)
        if (::locationThread.isInitialized) locationThread.quitSafely()
        toneGen.release()
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }

    companion object {
        const val ACTION_ALERT = "com.flockdown.ALERT"
        const val ACTION_SPEED = "com.flockdown.SPEED"
        const val ACTION_COUNT = "com.flockdown.COUNT"
        const val EXTRA_DIST = "dist"
        const val EXTRA_URGENT = "urgent"
        const val EXTRA_IS_FLOCK = "is_flock"
        const val EXTRA_SPEED = "speed"
        const val EXTRA_SESSION = "session"
        const val EXTRA_LIFETIME = "lifetime"
        const val PREFS = "flockdown_prefs"
        const val PREF_FLOCK_ONLY = "flock_only"
        const val PREF_LIFETIME_COUNT = "lifetime_count"
    }
}
