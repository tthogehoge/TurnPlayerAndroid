package com.tthhfugapiyo.turnplayer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Binder
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat

class PlaybackService : Service(), MediaPlayer.OnPreparedListener, MediaPlayer.OnCompletionListener, MediaPlayer.OnErrorListener, MediaPlayer.OnBufferingUpdateListener {

    private var mediaPlayer: MediaPlayer? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null
    private val binder = LocalBinder()

    var onPreparedCallback: (() -> Unit)? = null
    var onCompletionCallback: (() -> Unit)? = null
    var onErrorCallback: ((Int, Int) -> Unit)? = null
    var onBufferingUpdateCallback: ((Int) -> Unit)? = null

    inner class LocalBinder : Binder() {
        fun getService(): PlaybackService = this@PlaybackService
    }

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    private fun createNotificationChannel() {
        val channelId = "playback_channel"
        val channel = NotificationChannel(
            channelId,
            "Media Playback",
            NotificationManager.IMPORTANCE_LOW
        )
        val manager = getSystemService(NotificationManager::class.java)
        manager?.createNotificationChannel(channel)
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "TurnPlayer::PlaybackWakeLock").apply {
            acquire(4 * 60 * 60 * 1000L) // 4 hours max timeout safety
        }

        val wifiManager = applicationContext.getSystemService(WIFI_SERVICE) as WifiManager
        wifiLock = wifiManager.createWifiLock(WifiManager.WIFI_MODE_FULL, "TurnPlayer::WifiLock").apply {
            acquire()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(1, createNotification("再生中"))
        return START_NOT_STICKY
    }

    private fun createNotification(text: String): Notification {
        val channelId = "playback_channel"
        return NotificationCompat.Builder(this, channelId)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(text)
            .setSmallIcon(R.drawable.baseline_play_arrow_24)
            .setOngoing(true)
            .build()
    }

    fun play(uri: Uri?, url: String?) {
        try {
            if (mediaPlayer == null) {
                mediaPlayer = MediaPlayer()
            } else {
                mediaPlayer?.reset()
            }

            mediaPlayer?.apply {
                setOnPreparedListener(this@PlaybackService)
                setOnCompletionListener(this@PlaybackService)
                setOnErrorListener(this@PlaybackService)
                setOnBufferingUpdateListener(this@PlaybackService)

                if (uri != null) {
                    setDataSource(applicationContext, uri)
                } else if (url != null) {
                    setDataSource(url)
                }
                prepareAsync()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun pause() {
        mediaPlayer?.let {
            if (it.isPlaying) {
                it.pause()
            }
        }
    }

    fun start() {
        mediaPlayer?.let {
            if (!it.isPlaying) {
                it.start()
            }
        }
    }

    fun stop() {
        mediaPlayer?.let {
            try {
                it.stop()
                it.reset()
            } catch (_: Exception) {}
        }
    }

    fun seekTo(msec: Int) {
        mediaPlayer?.seekTo(msec)
    }

    val isPlaying: Boolean
        get() = mediaPlayer?.isPlaying ?: false

    val currentPosition: Int
        get() = mediaPlayer?.currentPosition ?: 0

    val duration: Int
        get() = mediaPlayer?.duration ?: 0

    override fun onPrepared(mp: MediaPlayer?) {
        onPreparedCallback?.invoke()
    }

    override fun onCompletion(mp: MediaPlayer?) {
        onCompletionCallback?.invoke()
    }

    override fun onError(mp: MediaPlayer?, what: Int, extra: Int): Boolean {
        onErrorCallback?.invoke(what, extra)
        return true
    }

    override fun onBufferingUpdate(mp: MediaPlayer?, percent: Int) {
        onBufferingUpdateCallback?.invoke(percent)
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaPlayer?.release()
        mediaPlayer = null
        try {
            wakeLock?.let { if (it.isHeld) it.release() }
            wifiLock?.let { if (it.isHeld) it.release() }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        stop()
        stopSelf()
    }
}
