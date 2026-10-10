package com.ferdyan.audioepub.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Binder
import android.os.Build
import android.os.IBinder
import com.ferdyan.audioepub.MainActivity

class EpubAudioService : Service() {

    inner class LocalBinder : Binder() {
        fun getService(): EpubAudioService = this@EpubAudioService
    }

    private val binder = LocalBinder()
    private var mediaSession: MediaSession? = null
    private var notificationManager: NotificationManager? = null

    var onPlayPauseAction: (() -> Unit)? = null
    var onNextParagraphAction: (() -> Unit)? = null
    var onPreviousParagraphAction: (() -> Unit)? = null
    var onStopAction: (() -> Unit)? = null

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel()

        // Inicializar MediaSession de Android (por defecto inactiva para no acaparar el audio del sistema)
        mediaSession = MediaSession(this, "AudioEpubMediaSession").apply {
            setCallback(object : MediaSession.Callback() {
                override fun onPlay() {
                    onPlayPauseAction?.invoke()
                }

                override fun onPause() {
                    onPlayPauseAction?.invoke()
                }

                override fun onSkipToNext() {
                    onNextParagraphAction?.invoke()
                }

                override fun onSkipToPrevious() {
                    onPreviousParagraphAction?.invoke()
                }

                override fun onStop() {
                    onStopAction?.invoke()
                    stopSelf()
                }
            })
            isActive = false
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.action?.let { action ->
            when (action) {
                ACTION_PLAY_PAUSE -> onPlayPauseAction?.invoke()
                ACTION_NEXT -> onNextParagraphAction?.invoke()
                ACTION_PREVIOUS -> onPreviousParagraphAction?.invoke()
                ACTION_STOP -> {
                    onStopAction?.invoke()
                    stopForegroundService()
                }
            }
        }
        return START_STICKY
    }

    fun updateMediaState(
        bookTitle: String,
        chapterTitle: String,
        paragraphIndex: Int,
        totalParagraphs: Int,
        isPlaying: Boolean
    ) {
        val session = mediaSession ?: return

        // Activar la sesión de medios únicamente si la lectura está en curso
        session.isActive = isPlaying

        // Metadatos para la pantalla de bloqueo y barra de estado
        val metadata = MediaMetadata.Builder()
            .putString(MediaMetadata.METADATA_KEY_TITLE, chapterTitle)
            .putString(MediaMetadata.METADATA_KEY_ARTIST, bookTitle)
            .putString(MediaMetadata.METADATA_KEY_ALBUM, "Párrafo ${paragraphIndex + 1} de $totalParagraphs")
            .build()
        session.setMetadata(metadata)

        // Estado de la reproducción
        val playbackStateInt = if (isPlaying) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED
        val stateBuilder = PlaybackState.Builder()
            .setActions(
                PlaybackState.ACTION_PLAY or
                        PlaybackState.ACTION_PAUSE or
                        PlaybackState.ACTION_PLAY_PAUSE or
                        PlaybackState.ACTION_SKIP_TO_NEXT or
                        PlaybackState.ACTION_SKIP_TO_PREVIOUS or
                        PlaybackState.ACTION_STOP
            )
            .setState(playbackStateInt, PlaybackState.PLAYBACK_POSITION_UNKNOWN, 1.0f)
        session.setPlaybackState(stateBuilder.build())

        // Publicar o actualizar la notificación
        val notification = buildNotification(bookTitle, chapterTitle, paragraphIndex, totalParagraphs, isPlaying)
        if (isPlaying) {
            startForeground(NOTIFICATION_ID, notification)
        } else {
            stopForeground(STOP_FOREGROUND_DETACH)
            notificationManager?.notify(NOTIFICATION_ID, notification)
        }
    }

    fun stopForegroundService() {
        mediaSession?.isActive = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun buildNotification(
        bookTitle: String,
        chapterTitle: String,
        paragraphIndex: Int,
        totalParagraphs: Int,
        isPlaying: Boolean
    ): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            this, 0, openAppIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val prevIntent = PendingIntent.getService(
            this, 1, Intent(this, EpubAudioService::class.java).apply { action = ACTION_PREVIOUS },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val playPauseIntent = PendingIntent.getService(
            this, 2, Intent(this, EpubAudioService::class.java).apply { action = ACTION_PLAY_PAUSE },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val nextIntent = PendingIntent.getService(
            this, 3, Intent(this, EpubAudioService::class.java).apply { action = ACTION_NEXT },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val playPauseIcon = if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
        val playPauseTitle = if (isPlaying) "Pausar" else "Reproducir"

        val builder = Notification.Builder(this, CHANNEL_ID)
            .setContentTitle(chapterTitle)
            .setContentText("$bookTitle • Párrafo ${paragraphIndex + 1}/$totalParagraphs")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(contentPendingIntent)
            .setOngoing(isPlaying)
            .setCategory(Notification.CATEGORY_TRANSPORT)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .addAction(Notification.Action.Builder(android.R.drawable.ic_media_previous, "Anterior", prevIntent).build())
            .addAction(Notification.Action.Builder(playPauseIcon, playPauseTitle, playPauseIntent).build())
            .addAction(Notification.Action.Builder(android.R.drawable.ic_media_next, "Siguiente", nextIntent).build())

        val mediaStyle = Notification.MediaStyle()
            .setMediaSession(mediaSession?.sessionToken)
            .setShowActionsInCompactView(0, 1, 2)
        builder.style = mediaStyle

        return builder.build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "AudioEPUB Reproducción",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Controles multimedia de reproducción de audiolibro"
                setSound(null, null)
            }
            notificationManager?.createNotificationChannel(channel)
        }
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {
        mediaSession?.isActive = false
        mediaSession?.release()
        mediaSession = null
        super.onDestroy()
    }

    companion object {
        const val CHANNEL_ID = "audioepub_media_channel"
        const val NOTIFICATION_ID = 1001

        const val ACTION_PLAY_PAUSE = "com.ferdyan.audioepub.ACTION_PLAY_PAUSE"
        const val ACTION_NEXT = "com.ferdyan.audioepub.ACTION_NEXT"
        const val ACTION_PREVIOUS = "com.ferdyan.audioepub.ACTION_PREVIOUS"
        const val ACTION_STOP = "com.ferdyan.audioepub.ACTION_STOP"
    }
}
