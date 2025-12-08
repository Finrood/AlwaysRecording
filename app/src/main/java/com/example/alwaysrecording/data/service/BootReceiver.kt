package com.example.alwaysrecording.data.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.example.alwaysrecording.data.settings.DataStoreSettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private const val TAG = "BootReceiver"

class BootReceiver : BroadcastReceiver() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            Log.d(TAG, "Boot completed or Package Replaced received: ${intent.action}")
            
            // Clean up temp files
            val cacheDir = context.externalCacheDir
            cacheDir?.listFiles()?.forEach { file ->
                if (file.name.endsWith(".tmp")) {
                    file.delete()
                }
            }

            // Check auto-start setting
            val pendingResult = goAsync()
            scope.launch {
                try {
                    val settingsRepo = DataStoreSettingsRepository(context)
                    val autoStart = settingsRepo.autoStartEnabled.first()
                    
                    if (autoStart) {
                        Log.i(TAG, "Auto-start enabled. Starting ReplayRecorderService.")
                        val serviceIntent = ReplayRecorderService.newStartIntent(context)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            context.startForegroundService(serviceIntent)
                        } else {
                            context.startService(serviceIntent)
                        }
                    } else {
                        Log.i(TAG, "Auto-start disabled. Skipping service start.")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error checking auto-start setting", e)
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }
}
