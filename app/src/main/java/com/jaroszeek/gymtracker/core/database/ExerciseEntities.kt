package com.jaroszeek.gymtracker.core.database

import androidx.room3.ColumnInfo
import androidx.room3.Embedded
import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.PrimaryKey
import androidx.room3.Relation

/** String values are the keys of the domain enums (free-exercise-db vocabulary). */
@Entity(tableName = "exercises", indices = [Index(value = ["external_id"], unique = true)])
data class ExerciseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Id from the seed files; null for custom exercises. */
    @ColumnInfo(name = "external_id") val externalId: String?,
    val name: String,
    @ColumnInfo(name = "aliases_pl") val aliasesPl: List<String>,
    val category: String,
    val equipment: String?,
    val level: String?,
    val mechanic: String?,
    val force: String?,
    val instructions: List<String>,
    val images: List<String>,
    val source: String,
)

@Entity(
    tableName = "exercise_muscles",
    primaryKeys = ["exercise_id", "muscle"],
    foreignKeys = [
        ForeignKey(entity = ExerciseEntity::class, parentColumns = ["id"], childColumns = ["exercise_id"], onDelete = ForeignKey.CASCADE),
    ],
)
data class ExerciseMuscleEntity(
    @ColumnInfo(name = "exercise_id") val exerciseId: Long,
    val muscle: String,
    @ColumnInfo(name = "is_primary") val isPrimary: Boolean,
)

data class ExerciseWithMuscles(
    @Embedded val exercise: ExerciseEntity,
    @Relation(parentColumns = ["id"], entityColumns = ["exercise_id"]) val muscles: List<ExerciseMuscleEntity>,
)
