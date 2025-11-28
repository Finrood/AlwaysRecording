package com.example.alwaysrecording.data.storage

import android.content.Context
import android.os.StatFs
import java.io.File

interface StorageChecker {
    fun isStorageAvailable(context: Context, directory: File?): Boolean
}

class DefaultStorageChecker : StorageChecker {
    override fun isStorageAvailable(context: Context, directory: File?): Boolean {
        if (directory == null) return false
        val stat = StatFs(directory.path)
        val bytesAvailable = stat.blockSizeLong * stat.availableBlocksLong
        val megabytesAvailable = bytesAvailable / (1024 * 1024)
        return megabytesAvailable > 10 // Arbitrary 10MB threshold
    }
}
