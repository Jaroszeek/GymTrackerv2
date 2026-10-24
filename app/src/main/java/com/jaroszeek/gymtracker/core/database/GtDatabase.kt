package com.jaroszeek.gymtracker.core.database

import androidx.room3.AutoMigration
import androidx.room3.ColumnTypeConverter
import androidx.room3.ColumnTypeConverters
import androidx.room3.Database
import androidx.room3.RoomDatabase
import kotlinx.serialization.json.Json

/**
 * Every schema change is a new version with a migration and a migration test (CLAUDE.md);
 * never `fallbackToDestructiveMigration`. Schemas are exported to `app/schemas`.
 */
@Database(
    entities = [
        ExerciseEntity::class,
        ExerciseMuscleEntity::class,
        RoutineEntity::class,
        RoutineBlockEntity::class,
        RoutineVariantEntity::class,
        WorkoutEntity::class,
        WorkoutExerciseEntity::class,
        WorkoutSetEntity::class,
        BodyMeasurementEntity::class,
    ],
    version = GtDatabase.VERSION,
    autoMigrations = [
        // 1 -> 2: routine tables (Etap 3)
        AutoMigration(from = 1, to = 2),
        // 2 -> 3: workout tables (Etap 4)
        AutoMigration(from = 2, to = 3),
        // 3 -> 4: the watch session linked to a workout (Etap 7)
        AutoMigration(from = 3, to = 4),
        // 4 -> 5: body values entered by hand (Etap 8)
        AutoMigration(from = 4, to = 5),
    ],
)
@ColumnTypeConverters(StringListConverter::class)
abstract class GtDatabase : RoomDatabase() {
    abstract fun exerciseDao(): ExerciseDao

    abstract fun routineDao(): RoutineDao

    abstract fun workoutDao(): WorkoutDao

    abstract fun bodyDao(): BodyDao

    companion object {
        const val NAME = "gymtracker.db"
        const val VERSION = 5
    }
}

/** Lists of strings (instructions, image paths, aliases) are stored as a JSON array. */
class StringListConverter {
    @ColumnTypeConverter
    fun toJson(values: List<String>): String = Json.encodeToString(values)

    @ColumnTypeConverter
    fun fromJson(json: String): List<String> = Json.decodeFromString(json)
}
