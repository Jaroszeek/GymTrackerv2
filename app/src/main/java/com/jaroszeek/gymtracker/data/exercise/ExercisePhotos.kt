package com.jaroszeek.gymtracker.data.exercise

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import com.jaroszeek.gymtracker.core.database.GtDatabase
import com.jaroszeek.gymtracker.core.di.IoDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * The user's own exercise photos (D-114): copied from the photo picker into the app's files, which
 * Android's own backup and device transfer take along (the app's backup file holds only the database).
 * Stored in the exercise as `file://` URIs, so Coil and the notification load them like any photo.
 */
@Singleton
class ExercisePhotos internal constructor(
    private val dir: File,
    private val database: GtDatabase,
    private val io: CoroutineDispatcher,
    private val contentResolver: () -> ContentResolver,
) {
    @Inject
    constructor(
        @ApplicationContext context: Context,
        database: GtDatabase,
        @IoDispatcher io: CoroutineDispatcher,
    ) : this(File(context.filesDir, DIR), database, io, { context.contentResolver })

    /**
     * Copies a picked photo, scaled down to [MAX_SIDE] (a phone photo is several MB) and turned
     * upright (ImageDecoder applies the EXIF orientation); returns what the exercise stores.
     */
    suspend fun import(uri: Uri): String = withContext(io) {
        val bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(contentResolver(), uri)) { decoder, info, _ ->
            val scale = MAX_SIDE.toFloat() / maxOf(info.size.width, info.size.height)
            if (scale < 1f) decoder.setTargetSize((info.size.width * scale).roundToInt(), (info.size.height * scale).roundToInt())
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
        dir.mkdirs()
        val file = File(dir, "${UUID.randomUUID()}.webp")
        // WebP keeps the transparency of a PNG, which JPEG would turn black
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.WEBP_LOSSY, QUALITY, it) }
        bitmap.recycle()
        SCHEME + file.absolutePath
    }

    /**
     * Deletes the photos no custom exercise lists: removed in the editor, of a deleted exercise or
     * picked and never saved. The editor runs it when it opens, before it can import anything.
     */
    // ponytail: garbage waits for the next editor opening; sweep on delete too if storage ever matters
    suspend fun sweep() = withContext(io) {
        val kept = database.exerciseDao().customImages().flatMap { it.images }.map { File(it).name }.toSet()
        dir.listFiles()?.filter { it.name !in kept }?.forEach { it.delete() }
    }

    companion object {
        const val SCHEME = "file://"
        private const val DIR = "exercise_photos"
        private const val MAX_SIDE = 1280
        private const val QUALITY = 85
    }
}
