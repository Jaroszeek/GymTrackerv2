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
    ],
    version = 2,
    // 1 -> 2: routine tables (Etap 3)
    autoMigrations = [AutoMigration(from = 1, to = 2)],
)
@ColumnTypeConverters(StringListConverter::class)
abstract class GtDatabase : RoomDatabase() {
    abstract fun exerciseDao(): ExerciseDao

    abstract fun routineDao(): RoutineDao

    companion object {
        const val NAME = "gymtracker.db"
    }
}

/** Lists of strings (instructions, image paths, aliases) are stored as a JSON array. */
class StringListConverter {
    @ColumnTypeConverter
    fun toJson(values: List<String>): String = Json.encodeToString(values)

    @ColumnTypeConverter
    fun fromJson(json: String): List<String> = Json.decodeFromString(json)
}
