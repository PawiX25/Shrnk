package com.pawix25.shrnk

import android.content.Context
import android.graphics.Bitmap
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
import android.media.ExifInterface
import android.media.MediaMetadataRetriever

import androidx.media3.common.util.UnstableApi

import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.VideoEncoderSettings
import kotlin.math.roundToInt

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
        quality: Int = 80,
        copyMetadata: Boolean = true
    ): Boolean = withContext(Dispatchers.IO) {
        return@withContext try {
            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                val originalBitmap = BitmapFactory.decodeStream(input) ?: return@withContext false

                val (newWidth, newHeight) = getScaledDimensions(
                    originalBitmap.width,
                    originalBitmap.height
                )
                val bitmap = Bitmap.createScaledBitmap(originalBitmap, newWidth, newHeight, true)

                context.contentResolver.openFileDescriptor(destUri, "w")?.use { pfd ->
                    FileOutputStream(pfd.fileDescriptor).use { out ->
                        bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, quality, out)
                        out.flush()
                    }
                }
                if (copyMetadata) {
                    copyExif(context, sourceUri, destUri)
                }
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun getScaledDimensions(width: Int, height: Int): Pair<Int, Int> {
        val maxDimension = maxOf(width, height)
        val scaleFactor = when {
            maxDimension <= 100 -> 1.0
            maxDimension <= 320 -> 320.0 / maxDimension
            maxDimension <= 800 -> 800.0 / maxDimension
            maxDimension <= 1280 -> 1280.0 / maxDimension
            else -> 1280.0 / maxDimension
        }
        var newWidth = (width * scaleFactor).roundToInt()
        var newHeight = (height * scaleFactor).roundToInt()

        if (newWidth % 2 != 0) newWidth++
        if (newHeight % 2 != 0) newHeight++

        return Pair(newWidth, newHeight)
    }

    private fun copyExif(context: Context, sourceUri: Uri, destUri: Uri) {
        try {
            context.contentResolver.openInputStream(sourceUri)?.use { sourceInputStream ->
                context.contentResolver.openFileDescriptor(destUri, "rw")?.use { destPfd ->
                    val sourceExif = ExifInterface(sourceInputStream)
                    val destExif = ExifInterface(destPfd.fileDescriptor)

                    val attributes = arrayOf(
                        ExifInterface.TAG_APERTURE_VALUE,
                        ExifInterface.TAG_DATETIME,
                        ExifInterface.TAG_DATETIME_DIGITIZED,
                        ExifInterface.TAG_DATETIME_ORIGINAL,
                        ExifInterface.TAG_EXPOSURE_TIME,
                        ExifInterface.TAG_FLASH,
                        ExifInterface.TAG_FOCAL_LENGTH,
                        ExifInterface.TAG_GPS_ALTITUDE,
                        ExifInterface.TAG_GPS_ALTITUDE_REF,
                        ExifInterface.TAG_GPS_DATESTAMP,
                        ExifInterface.TAG_GPS_LATITUDE,
                        ExifInterface.TAG_GPS_LATITUDE_REF,
                        ExifInterface.TAG_GPS_LONGITUDE,
                        ExifInterface.TAG_GPS_LONGITUDE_REF,
                        ExifInterface.TAG_GPS_PROCESSING_METHOD,
                        ExifInterface.TAG_GPS_TIMESTAMP,
                        ExifInterface.TAG_IMAGE_LENGTH,
                        ExifInterface.TAG_IMAGE_WIDTH,
                        ExifInterface.TAG_ISO_SPEED_RATINGS,
                        ExifInterface.TAG_MAKE,
                        ExifInterface.TAG_MODEL,
                        ExifInterface.TAG_ORIENTATION,
                        ExifInterface.TAG_WHITE_BALANCE
                    )

                    for (attribute in attributes) {
                        val value = sourceExif.getAttribute(attribute)
                        if (value != null) {
                            destExif.setAttribute(attribute, value)
                        }
                    }
                    destExif.saveAttributes()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }


    @androidx.annotation.OptIn(UnstableApi::class)
    suspend fun compressVideo(
        context: Context,
        sourceUri: Uri,
        destUri: Uri,
        targetHeight: Int = 720,
        maxFileSizeMb: Int? = null,
        preset: VideoPreset? = null,
        onProgress: (Float) -> Unit = {},
        transformer: Transformer? = null
    ): Boolean = withContext(Dispatchers.IO) {
        val tempOut = File.createTempFile("shrnk_transcoded", ".mp4", context.cacheDir)
        val deferred = CompletableDeferred<Boolean>()

        val listener = object : Transformer.Listener {
            override fun onTransformationCompleted(
                mediaItem: MediaItem,
                result: TransformationResult
            ) {
                CoroutineScope(Dispatchers.IO).launch {
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
                deferred.complete(false)
            }
        }

        var tmpDuration = -1L
        var tmpWidth = -1
        var tmpHeight = -1
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, sourceUri)
            tmpDuration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: -1L
            tmpWidth = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: -1
            tmpHeight = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: -1
        } catch (_: Exception) {
        } finally {
            retriever.release()
        }

        val durationMs = if (tmpDuration > 0) tmpDuration else 10_000L
        val originalWidth = if (tmpWidth > 0) tmpWidth else 1280
        val originalHeight = if (tmpHeight > 0) tmpHeight else 720

                val (scaledWidth, scaledHeight) = getScaledDimensions(originalWidth, originalHeight)
        val newWidth = if (scaledWidth % 16 != 0) (scaledWidth / 16f).roundToInt() * 16 else scaledWidth
        val newHeight = if (scaledHeight % 16 != 0) (scaledHeight / 16f).roundToInt() * 16 else scaledHeight

        val bitrateCalc = when {
            maxFileSizeMb != null -> {
                val bitsTotal = maxFileSizeMb.toLong() * 8 * 1024 * 1024L
                if (durationMs > 0) ((bitsTotal * 1000) / durationMs).toInt() else 921_600
            }
            durationMs <= 2_000 -> 2_600_000
            durationMs <= 5_000 -> 2_200_000
            else -> 1_560_000
        }
        val safeBitrate = bitrateCalc.coerceAtLeast(300_000)
        val (finalWidth, finalHeight, targetBitrate) = Triple(newWidth, newHeight, safeBitrate)

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
                // Fallback to software encoders – avoids goldfish hardware codec crashes on emulator
                .setEnableFallback(true)
                .build()

                        val videoEffects = listOf(Presentation.createForHeight(finalHeight.coerceAtLeast(2)))


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
                        /* videoEffects = */ videoEffects
                    )
                )
                .build()

            transformer.start(edited, tempOut.absolutePath)

            CoroutineScope(Dispatchers.Main).launch {
                val holder = androidx.media3.transformer.ProgressHolder()
                while (!deferred.isCompleted) {
                    val state = transformer.getProgress(holder)
                    when (state) {
                        Transformer.PROGRESS_STATE_AVAILABLE -> onProgress(holder.progress / 100f)
                        Transformer.PROGRESS_STATE_NO_TRANSFORMATION -> onProgress(1f) // nothing to transform
                        Transformer.PROGRESS_STATE_UNAVAILABLE -> {
                            onProgress(0f)
                        }
                    }
                    kotlinx.coroutines.delay(200)
                }
            }
        }
        return@withContext try {
            deferred.await()
        } catch (_: Exception) {
            false
        }
    }
}