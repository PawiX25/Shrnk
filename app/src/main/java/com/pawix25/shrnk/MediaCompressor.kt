package com.pawix25.shrnk

import android.content.ContentResolver
import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.transformer.TransformationRequest
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.TransformationResult
import androidx.media3.transformer.TransformationException
import androidx.media3.effect.Presentation
import androidx.media3.transformer.Effects
import java.io.FileInputStream
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import android.media.MediaMetadataRetriever
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.VideoEncoderSettings

enum class VideoPreset(val targetHeight: Int, val targetBitrateMbps: Int) {
    VERY_LOW(360, 1),
    LOW(480, 2),
    MEDIUM(720, 4),
    HIGH(1080, 6)
}

object MediaCompressor {
    suspend fun compressImage(
        context: Context,
        sourceUri: Uri,
        destUri: Uri,
        quality: Int = 60
    ): Boolean = withContext(Dispatchers.IO) {
        return@withContext try {
            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                val bitmap = BitmapFactory.decodeStream(input) ?: return@withContext false
                context.contentResolver.openFileDescriptor(destUri, "w")?.use { pfd ->
                    FileOutputStream(pfd.fileDescriptor).use { out ->
                        bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, quality, out)
                        out.flush()
                    }
                }
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun compressVideo(
        context: Context,
        sourceUri: Uri,
        destUri: Uri,
        targetHeight: Int = 720,
        maxFileSizeMb: Int? = null,
        preset: VideoPreset? = null,
        onProgress: (Float) -> Unit = {}
    ): Boolean = withContext(Dispatchers.IO) {
        val tempOut = withContext(Dispatchers.IO) {
            File.createTempFile("shrnk_transcoded", ".mp4", context.cacheDir)
        }

        val deferred = CompletableDeferred<Boolean>()

        val listener = object : Transformer.Listener {
            override fun onTransformationCompleted(
                mediaItem: MediaItem,
                result: TransformationResult
            ) {
                val copyJob = CoroutineScope(Dispatchers.IO).launch {
                    try {
                        context.contentResolver.openOutputStream(destUri, "w")?.use { out ->
                            FileInputStream(tempOut).copyTo(out)
                        }
                        tempOut.delete()
                        deferred.complete(true)
                    } catch (e: Exception) {
                        tempOut.delete()
                        deferred.complete(false)
                    }
                }
            }

            override fun onTransformationError(
                mediaItem: MediaItem,
                exception: TransformationException
            ) {
                tempOut.delete()
                deferred.completeExceptionally(exception)
            }

        }

        val (finalHeight, targetBitrate) = run {
            if (maxFileSizeMb != null) {
                val retriever = MediaMetadataRetriever().apply { setDataSource(context, sourceUri) }
                val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                retriever.release()

                val bitsTotal = maxFileSizeMb.toLong() * 8 * 1024 * 1024L
                val bitrate = if (durationMs > 0) ((bitsTotal * 1000) / durationMs).toInt() else 1_000_000
                Pair(targetHeight, bitrate)
            } else if (preset != null) {
                Pair(preset.targetHeight, preset.targetBitrateMbps * 1_000_000)
            } else {
                Pair(targetHeight, 4_000_000)
            }
        }

        withContext(Dispatchers.Main) {
            val request = TransformationRequest.Builder()
                .setVideoMimeType(MimeTypes.VIDEO_H264)
                .build()

            val encoderFactory = DefaultEncoderFactory.Builder(context)
                .setRequestedVideoEncoderSettings(
                    VideoEncoderSettings.Builder()
                        .setBitrate(targetBitrate)
                        .build()
                )
                .build()

            val transformer = Transformer.Builder(context)
                .setTransformationRequest(request)
                .setEncoderFactory(encoderFactory)
                .addListener(listener)
                .build()

            val baseItem = MediaItem.fromUri(sourceUri)

            val edited = androidx.media3.transformer.EditedMediaItem.Builder(baseItem)
                .setEffects(
                    Effects(
                        /* audioProcessors = */ emptyList(),
                        /* videoEffects = */ listOf(Presentation.createForHeight(finalHeight))
                    )
                )
                .build()

            transformer.start(edited, tempOut.absolutePath)

            CoroutineScope(Dispatchers.Main).launch {
                val holder = androidx.media3.transformer.ProgressHolder()
                while (!deferred.isCompleted) {
                    val state = transformer.getProgress(holder)
                    if (state == Transformer.PROGRESS_STATE_AVAILABLE) {
                        onProgress(holder.progress / 100f)
                    }
                    kotlinx.coroutines.delay(200)
                }
            }
        }

        deferred.await()
    }
} 