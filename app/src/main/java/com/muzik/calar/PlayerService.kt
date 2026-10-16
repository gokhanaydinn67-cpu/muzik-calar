package com.muzik.calar

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import java.io.ByteArrayOutputStream

class PlayerService : MediaSessionService() {

    private var session: MediaSession? = null

    // Uygulama ikonu 128x128'e küçültülerek tek sefer hazırlanır.
    private val appIcon: ByteArray by lazy {
        val raw = BitmapFactory.decodeResource(resources, R.mipmap.ic_launcher)
        val small = Bitmap.createScaledBitmap(raw, 128, 128, true)
        val out = ByteArrayOutputStream()
        small.compress(Bitmap.CompressFormat.PNG, 100, out)
        out.toByteArray()
    }

    override fun onCreate() {
        super.onCreate()
        val player = ExoPlayer.Builder(this).build()
        session = MediaSession.Builder(this, player)
            .setCallback(object : MediaSession.Callback {
                override fun onAddMediaItems(
                    mediaSession: MediaSession,
                    controller: MediaSession.ControllerInfo,
                    mediaItems: MutableList<MediaItem>
                ): ListenableFuture<MutableList<MediaItem>> {
                    val withIcon = mediaItems.map { item ->
                        item.buildUpon()
                            .setMediaMetadata(
                                item.mediaMetadata.buildUpon()
                                    .setArtworkData(appIcon, MediaMetadata.PICTURE_TYPE_FRONT_COVER)
                                    .build()
                            )
                            .build()
                    }.toMutableList()
                    return Futures.immediateFuture(withIcon)
                }
            })
            .build()
    }

    override fun onGetSession(
        controllerInfo: MediaSession.ControllerInfo
    ): MediaSession? = session

    override fun onDestroy() {
        session?.run {
            player.release()
            release()
        }
        session = null
        super.onDestroy()
    }
}
