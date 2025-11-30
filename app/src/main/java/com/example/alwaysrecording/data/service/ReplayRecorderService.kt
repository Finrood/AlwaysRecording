package com.example.alwaysrecording.data.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import android.os.Process
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.distinctUntilChanged
import androidx.lifecycle.lifecycleScope
import com.example.alwaysrecording.R // Assuming R is generated in your project
import com.example.alwaysrecording.common.AppClock
import com.example.alwaysrecording.common.SystemClock // Default implementation
import com.example.alwaysrecording.data.buffer.AudioRingBuffer
import com.example.alwaysrecording.data.settings.DataStoreSettingsRepository // Default implementation
import com.example.alwaysrecording.data.settings.SettingsRepository
import com.example.alwaysrecording.data.storage.WavWriter
import com.example.alwaysrecording.domain.model.RecorderState
import com.example.alwaysrecording.ui.MainActivity // Your main UI entry point
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.flow.firstOrNull
import android.net.Uri // Added
import android.provider.Settings

private const val TAG = "ReplayRecorderService"

// Notification Constants
private const val NOTIFICATION_ID = 12345
private const val NOTIFICATION_CHANNEL_ID = "ReplayRecorderChannel"
private const val NOTIFICATION_CHANNEL_NAME = "Replay Recorder Service"

// Intent Actions
private const val ACTION_PREFIX = "com.example.alwaysrecording.action."
const val ACTION_START_RECORDING = "${ACTION_PREFIX}START_RECORDING"
const val ACTION_STOP_RECORDING = "${ACTION_PREFIX}STOP_RECORDING"
const val ACTION_SAVE_REPLAY = "${ACTION_PREFIX}SAVE_REPLAY"
const val ACTION_UPDATE_SETTINGS = "${ACTION_PREFIX}UPDATE_SETTINGS" // For external triggers if needed

class ReplayRecorderService : LifecycleService() {

    // --- Dependencies (Ideally Injected with Hilt/Koin) ---
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var clock: AppClock
    // ---

    private var audioRecord: AudioRecord? = null
    private var audioRingBuffer: AudioRingBuffer? = null
    private var recordingJob: Job? = null
    private val isSaving = AtomicBoolean(false) // Prevent concurrent save operations

    // --- Current Configuration (Loaded from SettingsRepository) ---
    private var currentSampleRate: Int = DataStoreSettingsRepository.DEFAULT_SAMPLE_RATE_HZ
    private var currentBufferMinutes: Int = DataStoreSettingsRepository.DEFAULT_BUFFER_LENGTH_MINUTES
    private var isAutoStartEnabled: Boolean = DataStoreSettingsRepository.DEFAULT_AUTO_START_ENABLED
    private var currentChannels: Int = DataStoreSettingsRepository.DEFAULT_CHANNELS // Added
    private var currentBitDepth: Int = DataStoreSettingsRepository.DEFAULT_BIT_DEPTH // Added

    // --- AudioRecord Configuration ---
    private val audioSource = MediaRecorder.AudioSource.VOICE_RECOGNITION // Or MIC
    private var channelConfig = AudioFormat.CHANNEL_IN_MONO // Changed to var
    private var audioFormat = AudioFormat.ENCODING_PCM_16BIT // Changed to var
    private var bytesPerFrame = 2 // For 16-bit mono (PCM_16BIT) // Changed to var

    // --- State Management ---
    private val _internalRecorderState = MutableStateFlow<RecorderState>(RecorderState.Idle)
    val recorderState: StateFlow<RecorderState> get() = _internalRecorderState.asStateFlow() // Expose to internal logic

    companion object {
        // Publicly observable state for UI or other components
        private val _publicServiceState = MutableStateFlow<RecorderState>(RecorderState.Idle)
        val publicServiceState: StateFlow<RecorderState> = _publicServiceState.asStateFlow()

        fun newStartIntent(context: Context): Intent =
            Intent(context, ReplayRecorderService::class.java).setAction(ACTION_START_RECORDING)

        fun newStopIntent(context: Context): Intent =
            Intent(context, ReplayRecorderService::class.java).setAction(ACTION_STOP_RECORDING)

        fun newSaveReplayIntent(context: Context): Intent =
            Intent(context, ReplayRecorderService::class.java).setAction(ACTION_SAVE_REPLAY)
    }

    // --- Lifecycle Methods ---
    override fun onCreate() {
        super.onCreate()
        // In production, use Dependency Injection (e.g., Hilt) for settingsRepository and clock.
        settingsRepository = DataStoreSettingsRepository(applicationContext)
        clock = SystemClock()

        Log.i(TAG, "Service Created. SDK: ${Build.VERSION.SDK_INT}")
        createNotificationChannel()
        observeSettings()

        // Update public state based on internal state changes
        lifecycleScope.launch {
            _internalRecorderState.collect { state ->
                _publicServiceState.value = state
                updateNotification(state) // Update notification whenever internal state changes
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        val action = intent?.action
        Log.d(TAG, "onStartCommand received: Action = $action, StartId = $startId")

        when (action) {
            ACTION_START_RECORDING -> handleStartRecording()
            ACTION_STOP_RECORDING -> handleStopRecording()
            ACTION_SAVE_REPLAY -> handleSaveReplay()
            ACTION_UPDATE_SETTINGS -> lifecycleScope.launch { loadSettingsAndApply() } // e.g. after settings change
            else -> {
                Log.w(TAG, "Unknown or null action received. Current autoStart: $isAutoStartEnabled")
                // If service is restarted by system (START_STICKY) and auto-start is on
                if (action == null && isAutoStartEnabled && _internalRecorderState.value is RecorderState.Idle) {
                    Log.i(TAG, "Service restarted by system, attempting auto-start.")
                    handleStartRecording()
                } else if (action == null && _internalRecorderState.value !is RecorderState.Recording) {
                    Log.i(TAG, "Service restarted by system, but not auto-starting or already handled.")
                    // If not recording and no action, maybe stopSelf if not sticky?
                    // For START_STICKY, it's expected to stay alive.
                }
            }
        }
        return START_STICKY // Keep service running until explicitly stopped.
    }

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent) // Required for LifecycleService
        Log.d(TAG, "onBind called. This service is primarily started, returning null.")
        return null // Not a bound service for external components
    }

    override fun onDestroy() {
        Log.i(TAG, "Service Destroyed")
        cleanUpRecordingResources()
        recordingJob?.cancel() // Ensure coroutine is cancelled
        _internalRecorderState.value = RecorderState.Idle // Final state update
        super.onDestroy()
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        Log.i(TAG, "onTrimMemory received with level: $level")
        // F-01 (Edge Case): Low-RAM device (trim ≥ MODERATE) → shrink buffer to half size.
        // This is complex as AudioRingBuffer is fixed size. A more practical approach:
        // 1. If recording, perhaps notify user or log.
        // 2. If TRIM_MEMORY_RUNNING_CRITICAL, consider stopping recording to save state.
        // 3. For future recordings, a smaller buffer size could be chosen if this state persists.
        if (level >= TRIM_MEMORY_MODERATE && _internalRecorderState.value == RecorderState.Recording) {
            Log.w(TAG, "Low memory detected (level $level). Current buffer may be too large. Consider reducing buffer length.")
            // For now, primarily logging. A more advanced strategy could involve
            // dynamically adjusting a preference for buffer size for the *next* recording session.
        }
        if (level == TRIM_MEMORY_COMPLETE || level == TRIM_MEMORY_RUNNING_CRITICAL) {
            Log.e(TAG, "Critically low memory (level $level). Attempting to save and adjust buffer.")
            if (_internalRecorderState.value == RecorderState.Recording) {
                Log.w(TAG, "Low memory critical: Stopping recording to prevent ANR/crash. Attempting save.")
                // Attempt to save before potential termination, but don't block on it
                lifecycleScope.launch {
                    if (!isSaving.get()) { // Only save if not already saving
                        handleSaveReplay()
                    }
                    handleStopRecording(stopService = false) // Stop recording, but keep service alive
                    adjustBufferLengthForLowMemory() // Adjust settings for future starts
                    _internalRecorderState.value = RecorderState.Error("Low memory. Recording stopped. Buffer size reduced for future sessions.")
                    // Notify user via notification or other means
                    updateNotification(_internalRecorderState.value)
                }
            }
        }
    }

    private suspend fun adjustBufferLengthForLowMemory() {
        val currentLength = settingsRepository.bufferLengthMinutes.first()
        val newLength = (currentLength / 2).coerceAtLeast(1) // Halve, but at least 1 minute
        if (newLength < currentLength) {
            Log.i(TAG, "Reducing buffer length from $currentLength to $newLength minutes due to low memory.")
            settingsRepository.setBufferLengthMinutes(newLength)
        }
    }

    // --- Settings Handling ---
    private fun observeSettings() {
        lifecycleScope.launch {
            settingsRepository.sampleRateHz.distinctUntilChanged().collect { hz ->
                Log.d(TAG, "Setting: Sample rate changed to $hz Hz")
                val oldRate = currentSampleRate
                currentSampleRate = hz
                if (oldRate != hz && _internalRecorderState.value == RecorderState.Recording) {
                    onRuntimeSettingChange("Sample Rate")
                }
            }
        }
        lifecycleScope.launch {
            settingsRepository.bufferLengthMinutes.distinctUntilChanged().collect { minutes ->
                Log.d(TAG, "Setting: Buffer length changed to $minutes minutes")
                val oldMinutes = currentBufferMinutes
                currentBufferMinutes = minutes
                if (oldMinutes != minutes && _internalRecorderState.value == RecorderState.Recording) {
                    onRuntimeSettingChange("Buffer Length")
                }
            }
        }
        lifecycleScope.launch {
            settingsRepository.autoStartEnabled.distinctUntilChanged().collect { enabled ->
                Log.d(TAG, "Setting: Auto-start changed to $enabled")
                isAutoStartEnabled = enabled
            }
        }
        lifecycleScope.launch {
            settingsRepository.channels.distinctUntilChanged().collect { channels ->
                Log.d(TAG, "Setting: Channels changed to $channels")
                val oldChannels = currentChannels
                currentChannels = channels
                channelConfig = if (currentChannels == 1) AudioFormat.CHANNEL_IN_MONO else AudioFormat.CHANNEL_IN_STEREO
                if (oldChannels != channels && _internalRecorderState.value == RecorderState.Recording) {
                    onRuntimeSettingChange("Channels")
                }
            }
        }
        lifecycleScope.launch {
            settingsRepository.bitDepth.distinctUntilChanged().collect { bitDepth ->
                Log.d(TAG, "Setting: Bit depth changed to $bitDepth")
                val oldBitDepth = currentBitDepth
                currentBitDepth = bitDepth
                audioFormat = if (currentBitDepth == 16) AudioFormat.ENCODING_PCM_16BIT else AudioFormat.ENCODING_PCM_FLOAT // Assuming 24-bit or higher uses float
                bytesPerFrame = currentBitDepth / 8 * currentChannels // Recalculate bytesPerFrame
                if (oldBitDepth != bitDepth && _internalRecorderState.value == RecorderState.Recording) {
                    onRuntimeSettingChange("Bit Depth")
                }
            }
        }
        // Initial load
        lifecycleScope.launch { loadSettingsAndApply() }
    }

    private suspend fun loadSettingsAndApply() {
        currentSampleRate = settingsRepository.sampleRateHz.first()
        currentBufferMinutes = settingsRepository.bufferLengthMinutes.first()
        isAutoStartEnabled = settingsRepository.autoStartEnabled.first()
        currentChannels = settingsRepository.channels.first()
        currentBitDepth = settingsRepository.bitDepth.first()
        Log.i(TAG, "Settings loaded: Rate=$currentSampleRate Hz, Buffer=$currentBufferMinutes min, AutoStart=$isAutoStartEnabled, Channels=$currentChannels, BitDepth=$currentBitDepth")
        // If not recording and settings changed, re-initialize buffer on next start automatically.
    }

    private fun onRuntimeSettingChange(settingName: String) {
        Log.i(TAG, "$settingName changed while recording. Restarting recording to apply changes.")
        // Stop recording, but keep service alive.
        handleStopRecording(stopService = false)
        // Immediately restart to apply new settings (buffer size, sample rate, etc.)
        handleStartRecording()
    }


    // --- Core Actions ---
    private fun handleStartRecording() {
        if (_internalRecorderState.value == RecorderState.Recording) {
            Log.d(TAG, "Start command received but already recording.")
            return
        }
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "Record audio permission not granted. Cannot start recording.")
            _internalRecorderState.value = RecorderState.PermissionNeeded
            // UI should prompt for permission. Service remains alive to show notification.
            return
        }

        Log.i(TAG, "Attempting to start recording... Rate=$currentSampleRate Hz, Buffer=$currentBufferMinutes min")
        _internalRecorderState.value = RecorderState.Recording // Set state before starting foreground

        // Initialize AudioRingBuffer based on current settings
        val bufferSizeBytes = calculateBufferSizeInBytes(currentSampleRate, currentBufferMinutes)
        audioRingBuffer = AudioRingBuffer(bufferSizeBytes, bytesPerFrame)
        Log.d(TAG, "AudioRingBuffer initialized. Capacity: $bufferSizeBytes bytes for $currentBufferMinutes minutes.")

        val minSystemBufferSize = AudioRecord.getMinBufferSize(currentSampleRate, channelConfig, audioFormat)
        if (minSystemBufferSize == AudioRecord.ERROR_BAD_VALUE || minSystemBufferSize == AudioRecord.ERROR) {
            Log.e(TAG, "Invalid AudioRecord parameters for getMinBufferSize. Rate: $currentSampleRate")
            _internalRecorderState.value = RecorderState.Error("Audio config error (minBufferSize)")
            cleanUpRecordingResources()
            return
        }
        // Use a buffer size for AudioRecord that's a multiple of minSystemBufferSize,
        // and ideally corresponds to a short duration (e.g., 100-200ms) for timely data delivery.
        val audioRecordInternalBufferSize = Math.max(minSystemBufferSize * 2, calculateFramesToBytes(currentSampleRate, 200)) // e.g. 200ms

        try {
            audioRecord = AudioRecord(
                audioSource,
                currentSampleRate,
                channelConfig,
                audioFormat,
                audioRecordInternalBufferSize
            )
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "Failed to create AudioRecord instance", e)
            _internalRecorderState.value = RecorderState.Error("AudioRecord creation failed: ${e.message}")
            cleanUpRecordingResources()
            return
        }


        if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
            Log.e(TAG, "AudioRecord initialization failed. State: ${audioRecord?.state}")
            _internalRecorderState.value = RecorderState.Error("AudioRecord init failed")
            cleanUpRecordingResources()
            return
        }

        // Start foreground service before starting actual recording operations
        // This is crucial for Android 8+ background restrictions
        startForegroundServiceNotification()

        recordingJob = lifecycleScope.launch(Dispatchers.IO) { // IO for blocking AudioRecord.read
            Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO) // Request higher priority for audio thread
            val readBuffer = ByteArray(minSystemBufferSize) // Read in chunks

            try {
                audioRecord?.startRecording()
                Log.i(TAG, "AudioRecord started. Recording...")
            } catch (e: IllegalStateException) {
                Log.e(TAG, "AudioRecord.startRecording() failed.", e)
                // This can happen if mic is already in use by a higher priority app or other issues.
                withContext(Dispatchers.Main) { // Switch to main for state update
                    _internalRecorderState.value = RecorderState.MicBusy // Or a more generic error
                    cleanUpRecordingResources()
                }
                return@launch // Exit coroutine
            }

            while (isActive && audioRecord?.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                val bytesRead = audioRecord?.read(readBuffer, 0, readBuffer.size) ?: -1
                when {
                    bytesRead > 0 -> audioRingBuffer?.write(readBuffer, 0, bytesRead)
                    bytesRead == 0 -> Log.v(TAG, "AudioRecord read 0 bytes, possibly buffer underrun or short silence.") // This is okay.
                    bytesRead < 0 -> { // Error codes
                        Log.e(TAG, "AudioRecord read error: $bytesRead")
                        withContext(Dispatchers.Main) {
                            _internalRecorderState.value = RecorderState.Error("Audio read error: $bytesRead")
                            cleanUpRecordingResources() // Stop and release on critical error
                        }
                        break // Exit loop
                    }
                }
            }
            Log.i(TAG, "Recording loop finished. isActive: $isActive, AR state: ${audioRecord?.recordingState}")
        }

        recordingJob?.invokeOnCompletion { throwable ->
            val finalState = _internalRecorderState.value // Capture state before it's potentially changed by cleanup
            if (throwable != null && throwable !is CancellationException) {
                Log.e(TAG, "Recording job failed or cancelled with error", throwable)
                if (finalState == RecorderState.Recording) { // Only update if it was an unexpected stop
                    _internalRecorderState.value = RecorderState.Error("Recording stopped: ${throwable.message}")
                }
            } else if (throwable is CancellationException) {
                Log.i(TAG, "Recording job explicitly cancelled.")
            } else {
                Log.i(TAG, "Recording job completed normally (e.g., stopRecording called).")
            }
            // If the job completes and we are still in "Recording" state, it implies an unexpected stop.
            // Explicit stopRecording calls should manage the state appropriately before job cancellation.
            if (finalState == RecorderState.Recording && throwable == null) {
                Log.w(TAG, "Recording job finished but state was still 'Recording'. Moving to Idle.")
                _internalRecorderState.value = RecorderState.Idle
            }
            cleanUpRecordingResources() // Ensure cleanup happens if job ends for any reason.
        }
    }

    private fun handleStopRecording(stopService: Boolean = true) {
        Log.i(TAG, "Attempting to stop recording. Stop service: $stopService")
        if (_internalRecorderState.value !is RecorderState.Recording && _internalRecorderState.value !is RecorderState.Error) {
            // If already idle or saving, or some other non-recording state, this might be a redundant call
            Log.d(TAG, "Stop command received but not in a recording or error state. Current: ${_internalRecorderState.value}")
            if (stopService && _internalRecorderState.value == RecorderState.Idle) { // If truly idle and asked to stop service
                stopSelf()
            }
            return
        }

        recordingJob?.cancel("Stop recording requested") // Cancel the recording coroutine first
        recordingJob = null // Nullify the job reference

        cleanUpRecordingResources() // Release AudioRecord, etc.

        _internalRecorderState.value = RecorderState.Idle

        if (stopService) {
            Log.d(TAG, "Stopping foreground service and self.")
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
        } else {
            // Service remains running, update notification to Idle state
            updateNotification(_internalRecorderState.value)
            Log.d(TAG, "Recording stopped, service remains active.")
        }
    }

    private fun handleSaveReplay() {
        if (_internalRecorderState.value == RecorderState.Idle && audioRingBuffer?.getCurrentSize() == 0) {
            Log.w(TAG, "Save command received, but recorder is idle and buffer is empty.")
            _internalRecorderState.value = RecorderState.Error("No data to save") // Or just flash a message
            lifecycleScope.launch { delay(2000); if (_internalRecorderState.value is RecorderState.Error) { _internalRecorderState.value = RecorderState.Idle } }
            return
        }
        if (audioRingBuffer == null || audioRingBuffer?.getCurrentSize() == 0) {
            Log.w(TAG, "Save command received, but no audio data in buffer.")
            _internalRecorderState.value = RecorderState.Error("Buffer empty, nothing to save")
            lifecycleScope.launch { delay(2000); if (_internalRecorderState.value is RecorderState.Error) { _internalRecorderState.value = RecorderState.Idle } }
            return
        }

        if (!isSaving.compareAndSet(false, true)) {
            Log.d(TAG, "Save operation already in progress.")
            return
        }

        val originalStateBeforeSave = _internalRecorderState.value // Cache current state
        _internalRecorderState.value = RecorderState.SavingReplay
        Log.i(TAG, "Attempting to save replay. Buffer size: ${audioRingBuffer?.getCurrentSize()} bytes.")

        lifecycleScope.launch(Dispatchers.IO) {
            // --- Storage quota enforcement ---
            val storageDir = getExternalFilesDir(android.os.Environment.DIRECTORY_MUSIC)
            val quotaMb = settingsRepository.storageQuotaMb.firstOrNull() ?: 100
            if (storageDir != null && storageDir.exists()) {
                val files = storageDir.listFiles { file -> file.name.endsWith(".wav") }?.sortedBy { it.lastModified() } ?: emptyList()
                var totalSize = files.sumOf { it.length().toLong() }
                val quotaBytes = quotaMb * 1024 * 1024L
                for (file in files) {
                    if (totalSize < quotaBytes) break
                    totalSize -= file.length()
                    file.delete()
                }
            }
            // --- End quota enforcement ---
            val currentBufferSnapshot = audioRingBuffer // Capture reference
            if (currentBufferSnapshot == null) {
                Log.e(TAG, "AudioRingBuffer was null during save operation.")
                withContext(Dispatchers.Main) {
                    _internalRecorderState.value = RecorderState.Error("Save failed: Internal error")
                    isSaving.set(false)
                }
                return@launch
            }
            val fileName = "replay_${clock.formattedNow("yyyyMMdd_HHmmssS")}.wav"
            if (storageDir == null) {
                Log.e(TAG, "External storage (MUSIC) not available or not permitted.")
                withContext(Dispatchers.Main) {
                    _internalRecorderState.value = RecorderState.Error("Storage not available for saving")
                    isSaving.set(false)
                }
                return@launch
            }
            if (!storageDir.exists() && !storageDir.mkdirs()) {
                Log.e(TAG, "Failed to create storage directory: "+storageDir.absolutePath)
                withContext(Dispatchers.Main) {
                    _internalRecorderState.value = RecorderState.Error("Cannot create storage folder")
                    isSaving.set(false)
                }
                return@launch
            }
            val outputFile = File(storageDir, fileName)

            val success = WavWriter.writeWavFile(
                outputFile,
                currentBufferSnapshot,
                currentSampleRate,
                currentChannels.toShort(),
                currentBitDepth.toShort()
            )

            // Check for custom save location (SAF)
            val saveUriString = settingsRepository.saveDirectoryUri.firstOrNull()
            var finalFile: File? = outputFile
            var savedToSaf = false

            if (success && !saveUriString.isNullOrEmpty()) {
                try {
                    val treeUri = Uri.parse(saveUriString)
                    val pickedDir = androidx.documentfile.provider.DocumentFile.fromTreeUri(applicationContext, treeUri)
                    if (pickedDir != null && pickedDir.exists() && pickedDir.isDirectory && pickedDir.canWrite()) {
                        val newFile = pickedDir.createFile("audio/wav", fileName)
                        if (newFile != null) {
                            contentResolver.openOutputStream(newFile.uri)?.use { outputStream ->
                                outputFile.inputStream().use { inputStream ->
                                    inputStream.copyTo(outputStream)
                                }
                            }
                            Log.i(TAG, "Replay copied to SAF: ${newFile.uri}")
                            outputFile.delete() // Delete internal copy
                            finalFile = null // It's now in SAF, not a direct File object we can easily ref for path logging if needed, or use newFile.uri
                            savedToSaf = true
                        } else {
                             Log.e(TAG, "Failed to create file in SAF directory.")
                        }
                    } else {
                         Log.e(TAG, "SAF directory invalid or not writable.")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error saving to SAF location", e)
                }
            }

            withContext(Dispatchers.Main) {
                if (success) {
                    val locationMsg = if (savedToSaf) "external storage" else outputFile.absolutePath
                    Log.i(TAG, "Replay saved successfully to $locationMsg")
                    // Decide if buffer should be cleared after save.
                    // For a continuous replay buffer, it should NOT be cleared by save.
                    // It's cleared/overwritten by new recordings or when settings change significantly.
                    // audioRingBuffer?.clear() // Generally NO for replay buffer.

                    // Update UI/Notification about success
                    // To show a temporary "Saved!" message in notification:
                    val tempState = _internalRecorderState.value // Should be SavingReplay
                    _publicServiceState.value = RecorderState.SavingReplay // Ensure notification shows "Saving"
                    updateNotificationWithTemporaryMessage("Saved: $fileName", originalStateBeforeSave)
                } else {
                    Log.e(TAG, "Failed to save replay to ${outputFile.absolutePath}")
                    _internalRecorderState.value = RecorderState.Error("Failed to save replay")
                }
                // Restore state to what it was before saving (e.g., Recording or Idle)
                // If it was Recording, it should resume that state visually.
                // If it was Idle (but buffer had data), it returns to Idle.
                if (_internalRecorderState.value !is RecorderState.Error) { // Don't override error state
                    _internalRecorderState.value = originalStateBeforeSave
                }
                isSaving.set(false)
            }
        }
    }

    // --- Utility and Resource Management ---
    private fun calculateBufferSizeInBytes(sampleRate: Int, durationMinutes: Int): Int {
        if (durationMinutes <= 0) return calculateFramesToBytes(sampleRate, 60 * 5) // Default 5s if invalid
        return sampleRate * bytesPerFrame * durationMinutes * 60
    }

    private fun calculateFramesToBytes(sampleRate: Int, durationMillis: Int): Int {
        return (sampleRate / 1000.0 * durationMillis * bytesPerFrame).toInt()
    }


    private fun cleanUpRecordingResources() {
        Log.d(TAG, "Cleaning up recording resources...")
        recordingJob?.cancel("Cleaning up resources") // Ensure job is cancelled
        recordingJob = null

        audioRecord?.let {
            try {
                if (it.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    it.stop()
                    Log.d(TAG, "AudioRecord stopped.")
                }
                it.release()
                Log.d(TAG, "AudioRecord released.")
            } catch (e: Exception) { // Catch broader exception on release/stop
                Log.e(TAG, "Exception during AudioRecord stop/release", e)
            }
        }
        audioRecord = null

        // audioRingBuffer is typically not cleared here unless explicitly needed.
        // It holds the data for "Save Replay". It's re-instantiated on next start.
        // If service stops and buffer should be gone, then: audioRingBuffer = null
        Log.d(TAG, "Recording resources cleanup complete.")
    }


    // --- Notification Handling ---
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                NOTIFICATION_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW // Low to avoid sound, but still visible.
            ).apply {
                description = "Notification channel for Replay Recorder background service."
                // setSound(null, null) // Ensure no sound
                enableLights(false)
                enableVibration(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
            Log.d(TAG, "Notification channel created/updated.")
        }
    }

    private fun startForegroundServiceNotification() {
        val notification = buildNotification(_internalRecorderState.value) // Build with current state
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceCompat.startForeground(
                    this,
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
            Log.i(TAG, "Service started in foreground successfully.")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting foreground service", e)
            // This can happen due to various reasons, including background restrictions
            // or issues with notification display on some OEM devices.
            _internalRecorderState.value = RecorderState.Error("Foreground service start failed: ${e.message}")
            cleanUpRecordingResources()
            stopSelf()
        }
    }

    private fun updateNotification(state: RecorderState) {
        // Only update if service is actually running as foreground.
        // This check prevents trying to update a non-existent notification if service is stopping.
        // However, startForegroundServiceNotification() should be called first.
        // This is more about subsequent updates.
        if (isServiceRunningInForeground()) { // You might need a flag set by startForeground
            val notification = buildNotification(state)
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.notify(NOTIFICATION_ID, notification)
            Log.d(TAG, "Notification updated for state: $state")
        } else {
            Log.d(TAG, "Skipped notification update as service is not in foreground or state indicates it shouldn't be.")
        }
    }

    private fun updateNotificationWithTemporaryMessage(message: String, stateToRevertTo: RecorderState, durationMillis: Long = 2500) {
        val tempNotification = buildNotification(null, message) // Build with custom message
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, tempNotification)

        lifecycleScope.launch {
            delay(durationMillis)
            // Revert to the notification based on the actual current state or the intended revert state
            updateNotification(if (_internalRecorderState.value is RecorderState.Error) _internalRecorderState.value else stateToRevertTo)
        }
    }

    private fun isServiceRunningInForeground(): Boolean {
        // A simple way is to check if audioRecord is not null and we think we are recording,
        // or rely on a flag set when startForeground is called.
        // For robustness, Android doesn't offer a direct API to check if *this* service
        // instance is the current foreground service. We manage this expectation.
        return _internalRecorderState.value == RecorderState.Recording || _internalRecorderState.value == RecorderState.SavingReplay
        // More accurately, you could set a flag:
        // private var isForeground = false;
        // In startForegroundServiceNotification(): isForeground = true
        // In handleStopRecording() or onDestroy(): isForeground = false
    }

    private fun buildNotification(state: RecorderState?, customMessage: String? = null): Notification {
        val currentContext = this
        val effectiveState = state ?: _internalRecorderState.value // Use passed state or current internal state

        val contentText = customMessage ?: when (effectiveState) {
            is RecorderState.Idle -> "Recorder Idle. Tap to start."
            is RecorderState.Recording -> "Recording: Last $currentBufferMinutes min stored."
            is RecorderState.SavingReplay -> "Saving replay buffer..."
            is RecorderState.Error -> "Error: ${effectiveState.message}"
            is RecorderState.MicBusy -> "Microphone busy or unavailable."
            is RecorderState.PermissionNeeded -> "Microphone permission required."
        }

        val notificationIntent = Intent(currentContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntentFlags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val contentPendingIntent = PendingIntent.getActivity(currentContext, 0, notificationIntent, pendingIntentFlags)

        val builder = NotificationCompat.Builder(currentContext, NOTIFICATION_CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name)) // Use app name from resources
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_notification_mic)
            .setContentIntent(contentPendingIntent)
            .setOngoing(effectiveState == RecorderState.Recording || effectiveState == RecorderState.SavingReplay) // Ongoing only when active
            .setSilent(true) // No sound for updates
            .setPriority(NotificationCompat.PRIORITY_LOW)

        when (effectiveState) {
            is RecorderState.Recording -> {
                val saveIntent = PendingIntent.getService(currentContext, 1, newSaveReplayIntent(currentContext), pendingIntentFlags)
                builder.addAction(R.drawable.ic_action_save, "Save Now", saveIntent)

                val stopIntent = PendingIntent.getService(currentContext, 2, newStopIntent(currentContext), pendingIntentFlags)
                builder.addAction(R.drawable.ic_action_stop, "Stop", stopIntent)
            }
            is RecorderState.Idle -> { // Only Idle state gets the start button if permission is granted
                if (ActivityCompat.checkSelfPermission(currentContext, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                    val startIntent = PendingIntent.getService(currentContext, 3, newStartIntent(currentContext), pendingIntentFlags)
                    builder.addAction(R.drawable.ic_action_start, "Start", startIntent)
                }
            }
            is RecorderState.MicBusy -> {
                val retryIntent = PendingIntent.getService(currentContext, 5, newStartIntent(currentContext), pendingIntentFlags)
                builder.addAction(R.drawable.ic_action_start, "Retry", retryIntent) // Use start icon for retry
            }
            is RecorderState.Error -> {
                // For generic errors, a dismiss action might be useful, or just rely on user clearing notification
                // For now, no specific action, as the error message should be informative.
                // If we had a specific "clear error" action, we could add it here.
            }
            is RecorderState.PermissionNeeded -> {
                val settingsIntent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", packageName, null)
                }
                val pendingSettingsIntent = PendingIntent.getActivity(currentContext, 4, settingsIntent, pendingIntentFlags)
                builder.addAction(android.R.drawable.ic_menu_preferences, "Grant Permission", pendingSettingsIntent)
            }
            is RecorderState.SavingReplay -> {
                // No actions typically during save, just informational.
            }
        }
        return builder.build()
    }
}