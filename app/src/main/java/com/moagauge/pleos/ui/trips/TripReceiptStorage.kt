package com.moagauge.pleos.ui.trips

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.IOException
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

internal data class StoredTripReceipt(val relativePath: String, val displayName: String)

/** Own Downloads files need no storage permission on Android 10+. */
@RequiresApi(Build.VERSION_CODES.Q)
internal suspend fun saveReceiptToDownloads(context: Context, receipt: TripReceiptSnapshot): StoredTripReceipt =
    withContext(Dispatchers.IO) {
        currentCoroutineContext().ensureActive()
        val bytes = receiptPdf(receipt)
        currentCoroutineContext().ensureActive()
        val resolver = context.applicationContext.contentResolver
        val stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HHmmss"))
        val name = receipt.fileName.removeSuffix(".pdf") + "-$stamp-${UUID.randomUUID().toString().take(6)}.pdf"
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, name)
            put(MediaStore.Downloads.MIME_TYPE, "application/pdf")
            put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/MOA DRIVE/")
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), values)
            ?: throw IOException("Could not create a Downloads entry")
        var published = false
        try {
            currentCoroutineContext().ensureActive()
            resolver.openOutputStream(uri, "w")?.use { output -> output.write(bytes) }
                ?: throw IOException("Could not open the receipt file")
            currentCoroutineContext().ensureActive()
            // Query the final name: the provider may resolve a name collision on our behalf.
            val saved = resolver.query(uri, arrayOf(MediaStore.Downloads.RELATIVE_PATH, MediaStore.Downloads.DISPLAY_NAME),
                null, null, null)?.use { cursor ->
                if (!cursor.moveToFirst()) throw IOException("Receipt entry is missing")
                StoredTripReceipt(cursor.getString(0), cursor.getString(1))
            } ?: throw IOException("Could not verify the receipt entry")
            currentCoroutineContext().ensureActive()
            val updated = resolver.update(uri, ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }, null, null)
            if (updated != 1) throw IOException("Could not publish the receipt")
            // Publication is the commit point. A later screen cancellation must not erase a complete file.
            published = true
            saved
        } finally {
            if (!published) {
                // Synchronous cleanup also runs in a cancelled coroutine; never expose a partial PDF.
                runCatching { resolver.delete(uri, null, null) }
            }
        }
    }
