package com.example.alwaysrecording.data.recorder

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import com.example.alwaysrecording.ui.RecordingFormat
import java.io.File

interface MediaRecorderFactory {
    fun create(context: Context, format: RecordingFormat, outputFile: File): MediaRecorder
}

class DefaultMediaRecorderFactory : MediaRecorderFactory {
    override fun create(context: Context, format: RecordingFormat, outputFile: File): MediaRecorder {
        val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            MediaRecorder()
        }
        recorder.apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(format.outputFormat)
            setAudioEncoder(format.audioEncoder)
            setOutputFile(outputFile.absolutePath)
        }
        return recorder
    }
}