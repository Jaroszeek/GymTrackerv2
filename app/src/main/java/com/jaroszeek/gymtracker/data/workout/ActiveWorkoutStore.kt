package com.jaroszeek.gymtracker.data.workout

import android.util.Log
import com.jaroszeek.gymtracker.domain.workout.ActiveWorkout
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/** The workout in progress as one JSON file, written atomically (temp file, then rename). Blocking: call off the main thread. */
class ActiveWorkoutStore(private val file: File) {
    // Defaults are written out, so changing a default in code never changes a saved workout
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun read(): ActiveWorkout? {
        if (!file.exists()) return null
        return try {
            json.decodeFromString<ActiveWorkout>(file.readText())
        } catch (e: IOException) {
            Log.e(TAG, "Reading the active workout failed", e)
            null
        } catch (e: SerializationException) {
            // A file from a broken write or an incompatible version; it is replaced by the next workout
            Log.e(TAG, "The saved active workout is unreadable", e)
            null
        }
    }

    /** Null deletes the file. */
    fun write(workout: ActiveWorkout?) {
        if (workout == null) {
            file.delete()
            return
        }
        val temp = File(file.parentFile, "${file.name}.tmp")
        // On the disk before the swap, so a power cut cannot leave an empty file in place of the workout
        FileOutputStream(temp).use { out ->
            out.write(json.encodeToString(workout).toByteArray())
            out.fd.sync()
        }
        Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    }

    private companion object {
        const val TAG = "Workout"
    }
}
