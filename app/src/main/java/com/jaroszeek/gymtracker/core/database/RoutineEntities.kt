package com.jaroszeek.gymtracker.core.database

import androidx.room3.ColumnInfo
import androidx.room3.Embedded
import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.PrimaryKey
import androidx.room3.Relation

@Entity(tableName = "routines")
data class RoutineEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** Epoch millis UTC. */
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

@Entity(
    tableName = "routine_blocks",
    foreignKeys = [
        ForeignKey(entity = RoutineEntity::class, parentColumns = ["id"], childColumns = ["routine_id"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index(value = ["routine_id"])],
)
data class RoutineBlockEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "routine_id") val routineId: Long,
    val position: Int,
)

/** One exercise option of a block with its targets; a block with several variants offers a choice during the workout. */
@Entity(
    tableName = "routine_variants",
    foreignKeys = [
        ForeignKey(entity = RoutineBlockEntity::class, parentColumns = ["id"], childColumns = ["block_id"], onDelete = ForeignKey.CASCADE),
        // An exercise used in a routine cannot be deleted
        ForeignKey(entity = ExerciseEntity::class, parentColumns = ["id"], childColumns = ["exercise_id"], onDelete = ForeignKey.RESTRICT),
    ],
    indices = [Index(value = ["block_id"]), Index(value = ["exercise_id"])],
)
data class RoutineVariantEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "block_id") val blockId: Long,
    @ColumnInfo(name = "exercise_id") val exerciseId: Long,
    val position: Int,
    @ColumnInfo(name = "target_sets") val targetSets: Int,
    @ColumnInfo(name = "reps_min") val repsMin: Int,
    @ColumnInfo(name = "reps_max") val repsMax: Int,
    @ColumnInfo(name = "target_weight_kg") val targetWeightKg: Double?,
    /** Null = the global default rest. */
    @ColumnInfo(name = "rest_seconds") val restSeconds: Int?,
)

data class BlockWithVariants(
    @Embedded val block: RoutineBlockEntity,
    @Relation(parentColumns = ["id"], entityColumns = ["block_id"]) val variants: List<RoutineVariantEntity>,
)

data class RoutineWithBlocks(
    @Embedded val routine: RoutineEntity,
    @Relation(entity = RoutineBlockEntity::class, parentColumns = ["id"], entityColumns = ["routine_id"]) val blocks: List<BlockWithVariants>,
)
