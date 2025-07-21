package com.pawix25.shrnk

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class CompressionService : Service() {

    private val scope = CoroutineScope(Dispatchers.IO)

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val sourceUri = intent?.getParcelableExtra<Uri>(EXTRA_SOURCE_URI)
        val destUri = intent?.getParcelableExtra<Uri>(EXTRA_DEST_URI)

        if (sourceUri == null || destUri == null) {
            stopSelf()
            return START_NOT_STICKY
        }

        startForeground(NOTIFICATION_ID, createNotification(0f))

        scope.launch {
            val mime = contentResolver.getType(sourceUri) ?: ""
            val success = if (mime.startsWith("image")) {
                MediaCompressor.compressImage(applicationContext, sourceUri, destUri)
            } else {
                MediaCompressor.compressVideo(applicationContext, sourceUri, destUri) { progress ->
                    updateNotification(progress)
                    sendProgressUpdate(progress)
                }
            }
            sendCompletionBroadcast(success, destUri)
            stopSelf()
        }

        return START_STICKY
    }

    private fun createNotification(progress: Float): Notification {
        createNotificationChannel()
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Compressing media")
            .setContentText("In progress... ${ (progress * 100).toInt() }%")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setProgress(100, (progress * 100).toInt(), false)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(progress: Float) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, createNotification(progress))
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Compression",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun sendProgressUpdate(progress: Float) {
        val intent = Intent(ACTION_PROGRESS_UPDATE).apply {
            putExtra(EXTRA_PROGRESS, progress)
        }
        sendBroadcast(intent)
    }

    private fun sendCompletionBroadcast(success: Boolean, uri: Uri) {
        val intent = Intent(ACTION_COMPRESSION_COMPLETE).apply {
            putExtra(EXTRA_SUCCESS, success)
            putExtra(EXTRA_DEST_URI, uri)
        }
        sendBroadcast(intent)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val CHANNEL_ID = "CompressionServiceChannel"
        const val NOTIFICATION_ID = 1
        const val ACTION_PROGRESS_UPDATE = "com.pawix25.shrnk.PROGRESS_UPDATE"
        const val ACTION_COMPRESSION_COMPLETE = "com.pawix25.shrnk.COMPRESSION_COMPLETE"
        const val EXTRA_SOURCE_URI = "source_uri"
        const val EXTRA_DEST_URI = "dest_uri"
        const val EXTRA_PROGRESS = "progress"
        const val EXTRA_SUCCESS = "success"
    }
}
