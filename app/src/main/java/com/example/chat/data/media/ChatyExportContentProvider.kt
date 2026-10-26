package com.example.chat.data.media

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import java.io.FileNotFoundException

/**
 * Read-only, registry-gated provider for plaintext exports (content://<pkg>.exportprovider/x/<tokenId>).
 * exported=false: an external app needs an explicit per-URI grant from us. Every open re-checks expiry, session
 * and source policy in [ExportRegistry]; there is no path in the URI, so no traversal. Already-open descriptors
 * and bytes already read cannot be revoked.
 */
class ChatyExportContentProvider : ContentProvider() {
    override fun onCreate(): Boolean = true

    private fun entryFor(uri: Uri): ExportRegistry.Entry? {
        val seg = uri.pathSegments
        if (seg.size != 2 || seg[0] != "x") return null
        return runCatching { com.example.chat.ServiceLocator.mediaCache.exportRegistry.open(seg[1]) }.getOrNull()
    }

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        if (mode != "r") throw FileNotFoundException("read only")
        val e = entryFor(uri) ?: throw FileNotFoundException("unavailable")
        return ParcelFileDescriptor.open(e.file, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun query(uri: Uri, projection: Array<String>?, selection: String?, selectionArgs: Array<String>?, sortOrder: String?): Cursor? {
        val e = entryFor(uri) ?: return null
        val cols = projection ?: arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)
        val row = cols.map { when (it) { OpenableColumns.DISPLAY_NAME -> e.name; OpenableColumns.SIZE -> e.file.length(); else -> null } }
        return MatrixCursor(cols).also { it.addRow(row) }
    }

    override fun getType(uri: Uri): String? = entryFor(uri)?.mime
    override fun insert(uri: Uri, values: ContentValues?): Uri? = throw UnsupportedOperationException()
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<String>?): Int = throw UnsupportedOperationException()
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<String>?): Int = throw UnsupportedOperationException()
}
