package com.muzik.calar

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
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
    private var audioManager: AudioManager? = null
    private var deviceCallback: AudioDeviceCallback? = null

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
        val audioAttributes = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .setUsage(C.USAGE_MEDIA)
            .build()
        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .build()
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
        watchAudioOutput(player)
    }

    // Kulaklık veya Bluetooth bağlantısı koptuğunda müziği duraklatır.
    private fun watchAudioOutput(player: ExoPlayer) {
        val am = getSystemService(AUDIO_SERVICE) as AudioManager
        audioManager = am
        val callback = object : AudioDeviceCallback() {
            override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) {
                val lostOutput = removedDevices.any { isHeadsetOrBluetooth(it) }
                if (lostOutput && player.isPlaying) {
                    player.pause()
                }
            }
        }
        deviceCallback = callback
        am.registerAudioDeviceCallback(callback, Handler(Looper.getMainLooper()))
    }

    private fun isHeadsetOrBluetooth(device: AudioDeviceInfo): Boolean =
        when (device.type) {
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
            AudioDeviceInfo.TYPE_WIRED_HEADSET,
            AudioDeviceInfo.TYPE_USB_HEADSET -> true
            else -> false
        }

    override fun onGetSession(
        controllerInfo: MediaSession.ControllerInfo
    ): MediaSession? = session

    override fun onDestroy() {
        deviceCallback?.let { audioManager?.unregisterAudioDeviceCallback(it) }
        deviceCallback = null
        session?.run {
            player.release()
            release()
        }
        session = null
        super.onDestroy()
    }
}
