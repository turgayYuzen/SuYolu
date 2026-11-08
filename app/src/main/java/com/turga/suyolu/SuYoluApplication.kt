package com.turga.suyolu

import android.app.Application
import android.content.Context
import org.osmdroid.config.Configuration
import java.io.File

class SuYoluApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        // 1. Initialize OSMDroid configuration before ANY MapView or Activity loads
        try {
            val prefs = getSharedPreferences("osmdroid_prefs", Context.MODE_PRIVATE)
            Configuration.getInstance().load(this, prefs)

            // CRITICAL for Android 10/11/12/13/14/15 Scoped Storage:
            // Explicitly set internal cache directories so TileWriter NEVER touches /sdcard/
            val osmCacheDir = File(cacheDir, "osmdroid")
            if (!osmCacheDir.exists()) {
                osmCacheDir.mkdirs()
            }
            val osmTileCache = File(osmCacheDir, "tiles")
            if (!osmTileCache.exists()) {
                osmTileCache.mkdirs()
            }

            Configuration.getInstance().osmdroidBasePath = osmCacheDir
            Configuration.getInstance().osmdroidTileCache = osmTileCache
            Configuration.getInstance().userAgentValue = packageName
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 2. Global Crash Guard: Prevent unexpected tile provider or background thread uncaught exceptions
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val isTileOrOsmError = throwable.stackTrace.any {
                    it.className.contains("osmdroid") || it.className.contains("tileprovider")
                }
                if (isTileOrOsmError) {
                    throwable.printStackTrace()
                    return@setDefaultUncaughtExceptionHandler
                }
            } catch (_: Exception) { }
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }
}
