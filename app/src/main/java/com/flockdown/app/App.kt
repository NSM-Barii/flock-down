package com.flockdown.app

import android.app.Application
import org.osmdroid.config.Configuration
import java.io.File

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        val config = Configuration.getInstance()
        config.load(applicationContext, getSharedPreferences("osmdroid", MODE_PRIVATE))
        config.userAgentValue = packageName
        // use external files dir — required for Android 11+ scoped storage
        val osmBase = File(getExternalFilesDir(null), "osmdroid")
        osmBase.mkdirs()
        config.osmdroidBasePath = osmBase
        config.osmdroidTileCache = File(osmBase, "tiles")
    }
}
