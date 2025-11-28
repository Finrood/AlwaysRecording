package com.example.alwaysrecording.data.service

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@MediumTest
@RunWith(AndroidJUnit4::class)
class BootReceiverTest {

    private lateinit var context: Context
    private lateinit var cacheDir: File

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        cacheDir = context.externalCacheDir ?: throw IllegalStateException("External cache directory not available")
        // Ensure cache directory exists
        cacheDir.mkdirs()
    }

    @After
    fun tearDown() {
        // Clean up any files created during tests
        cacheDir.listFiles()?.forEach { it.delete() }
    }

    @Test
    fun onReceive_bootCompleted_cleansUpTempFiles() {
        // Create some dummy temp files
        val tempFile1 = File(cacheDir, "temp1.tmp")
        val tempFile2 = File(cacheDir, "temp2.tmp")
        val regularFile = File(cacheDir, "regular.txt")

        tempFile1.createNewFile()
        tempFile2.createNewFile()
        regularFile.createNewFile()

        assertTrue(tempFile1.exists())
        assertTrue(tempFile2.exists())
        assertTrue(regularFile.exists())

        val bootIntent = Intent(Intent.ACTION_BOOT_COMPLETED)
        val receiver = BootReceiver()
        receiver.onReceive(context, bootIntent)

        assertFalse(tempFile1.exists())
        assertFalse(tempFile2.exists())
        assertTrue(regularFile.exists()) // Regular file should not be deleted
    }

    @Test
    fun onReceive_otherAction_doesNothing() {
        // Create some dummy temp files
        val tempFile = File(cacheDir, "temp.tmp")
        tempFile.createNewFile()
        assertTrue(tempFile.exists())

        val otherIntent = Intent("com.example.alwaysrecording.OTHER_ACTION")
        val receiver = BootReceiver()
        receiver.onReceive(context, otherIntent)

        assertTrue(tempFile.exists()) // File should not be deleted
    }

    @Test
    fun onReceive_bootCompleted_noTempFiles_doesNothing() {
        // Ensure no temp files exist initially
        cacheDir.listFiles()?.forEach { it.delete() }

        val bootIntent = Intent(Intent.ACTION_BOOT_COMPLETED)
        val receiver = BootReceiver()
        receiver.onReceive(context, bootIntent)

        // No files should be created or deleted
        assertEquals(0, cacheDir.listFiles()?.size ?: 0)
    }
}
