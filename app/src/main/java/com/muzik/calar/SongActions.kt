package com.muzik.calar

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.net.Uri
import android.os.Build
import android.provider.MediaStore

fun shareSong(context: Context, uri: Uri) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "audio/*"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Paylaş"))
}

// Dosya adını değiştirir, uzantıyı korur.
fun renameSong(context: Context, uri: Uri, newBase: String) {
    val resolver = context.contentResolver
    val current = resolver.query(
        uri,
        arrayOf(MediaStore.Audio.Media.DISPLAY_NAME),
        null,
        null,
        null
    )?.use { c ->
        if (c.moveToFirst()) c.getString(0) else null
    } ?: return
    val ext = current.substringAfterLast('.', "")
    val finalName = if (ext.isEmpty()) newBase else "$newBase.$ext"
    val values = ContentValues().apply {
        put(MediaStore.Audio.Media.DISPLAY_NAME, finalName)
    }
    resolver.update(uri, values, null, null)
}

// Android 11 ve üzeri için silme onayı isteyen intent. Eski sürümlerde null döner.
fun deleteSongIntentSender(context: Context, uri: Uri): IntentSender? =
    if (Build.VERSION.SDK_INT >= 30) {
        MediaStore.createDeleteRequest(context.contentResolver, listOf(uri)).intentSender
    } else {
        null
    }

// Eski sürümlerde doğrudan silme.
fun deleteSongDirect(context: Context, uri: Uri) {
    try {
        context.contentResolver.delete(uri, null, null)
    } catch (e: Exception) {
    }
}
