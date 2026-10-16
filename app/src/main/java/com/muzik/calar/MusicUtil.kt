package com.muzik.calar

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController

data class Song(
    val id: Long,
    val title: String,
    val artist: String,
    val uri: Uri,
    val dateAdded: Long
)

enum class SongSort { NAME, DATE }

fun Song.toMediaItem(): MediaItem =
    MediaItem.Builder()
        .setMediaId(id.toString())
        .setUri(uri)
        .setMediaMetadata(
            MediaMetadata.Builder().setTitle(title).setArtist(artist).build()
        )
        .build()

fun loadSongs(context: Context): List<Song> {
    val result = mutableListOf<Song>()
    val collection = if (Build.VERSION.SDK_INT >= 29)
        MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
    else MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
    val projection = arrayOf(
        MediaStore.Audio.Media._ID,
        MediaStore.Audio.Media.TITLE,
        MediaStore.Audio.Media.ARTIST,
        MediaStore.Audio.Media.DATE_ADDED
    )
    val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND " +
        "${MediaStore.Audio.Media.DURATION} > 30000"
    val order = "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC"
    context.contentResolver.query(collection, projection, selection, null, order)?.use { c ->
        val idCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
        val titleCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
        val artistCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
        val dateCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
        while (c.moveToNext()) {
            val id = c.getLong(idCol)
            val artist = c.getString(artistCol)
            result.add(
                Song(
                    id = id,
                    title = c.getString(titleCol) ?: "Bilinmeyen",
                    artist = if (artist == null || artist == "<unknown>") "Bilinmeyen sanatçı" else artist,
                    uri = ContentUris.withAppendedId(
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id
                    ),
                    dateAdded = c.getLong(dateCol)
                )
            )
        }
    }
    return result
}

fun formatTime(ms: Long): String {
    val s = ms / 1000
    return "%d:%02d".format(s / 60, s % 60)
}

// 0 = Sırayla, 1 = Karışık, 2 = Tek şarkıyı tekrarla
fun modeIcon(mode: Int) = when (mode) {
    1 -> Icons.Rounded.Shuffle
    2 -> Icons.Rounded.RepeatOne
    else -> Icons.Rounded.List
}

fun modeLabel(mode: Int) = when (mode) {
    1 -> "Karışık"
    2 -> "Tek şarkıyı tekrarla"
    else -> "Sırayla"
}

fun applyMode(c: MediaController, mode: Int) {
    c.shuffleModeEnabled = mode == 1
    c.repeatMode = when (mode) {
        2 -> Player.REPEAT_MODE_ONE
        1 -> Player.REPEAT_MODE_ALL
        else -> Player.REPEAT_MODE_OFF
    }
}
