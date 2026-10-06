package es.voghdev.chromecastsample

import android.app.Application
import android.util.Log
import es.voghdev.chromecastsample.persistence.AppDatabase
import es.voghdev.chromecastsample.persistence.CourseSeeder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class ChromecastApp : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        appScope.launch {
            try {
                val db = AppDatabase.get(this@ChromecastApp)
                val result = CourseSeeder.seedIfEmpty(this@ChromecastApp, db)
                if (result.skipped) {
                    Log.d(TAG, "DB already populated — seed skipped")
                } else {
                    Log.d(TAG, "DB seeded with ${result.inserted} courses in ${result.durationMs}ms")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Seed FAILED: ${e.javaClass.simpleName}: ${e.message}", e)
            }
        }
    }

    companion object {
        private const val TAG = "ChromecastApp"
    }
}
