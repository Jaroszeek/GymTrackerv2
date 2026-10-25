package com.jaroszeek.gymtracker.domain.routine

import com.jaroszeek.gymtracker.domain.exercise.Exercise
import kotlinx.serialization.Serializable

/**
 * Editable copy of a routine. Saved blocks and variants keep their database id (> 0); new ones get
 * a negative temporary id, unique within the draft, which also serves as a stable list key.
 * Serializable, so the editor can keep an unsaved draft in saved state across process death.
 */
@Serializable
data class RoutineDraft(val name: String = "", val blocks: List<RoutineBlock> = emptyList()) {

    val isValid: Boolean get() = name.isNotBlank() && blocks.isNotEmpty()

    fun rename(name: String) = copy(name = name)

    fun addBlock(exercise: Exercise): RoutineDraft {
        val blockId = nextTempId()
        val variant = RoutineVariant(blockId - 1, exercise, RoutineTargets())
        return copy(blocks = blocks + RoutineBlock(blockId, listOf(variant)))
    }

    /** New variants start with the targets of the block's first variant. */
    fun addVariant(blockId: Long, exercise: Exercise) = updateBlock(blockId) { block ->
        block.copy(variants = block.variants + RoutineVariant(nextTempId(), exercise, block.variants.first().targets))
    }

    /** Removing the last variant removes the whole block. */
    fun removeVariant(blockId: Long, variantId: Long): RoutineDraft = copy(
        blocks = blocks.mapNotNull { block ->
            if (block.id != blockId) return@mapNotNull block
            block.copy(variants = block.variants.filterNot { it.id == variantId }).takeIf { it.variants.isNotEmpty() }
        },
    )

    fun moveBlock(from: Int, to: Int): RoutineDraft {
        if (from !in blocks.indices || to !in blocks.indices) return this
        return copy(blocks = blocks.toMutableList().apply { add(to, removeAt(from)) })
    }

    fun updateTargets(blockId: Long, variantId: Long, targets: RoutineTargets) = updateBlock(blockId) { block ->
        block.copy(variants = block.variants.map { if (it.id == variantId) it.copy(targets = targets.normalized()) else it })
    }

    private fun updateBlock(blockId: Long, change: (RoutineBlock) -> RoutineBlock) =
        copy(blocks = blocks.map { if (it.id == blockId) change(it) else it })

    private fun nextTempId(): Long {
        val ids = blocks.flatMap { block -> block.variants.map { it.id } + block.id }
        return minOf(0L, ids.minOrNull() ?: 0L) - 1
    }
}

fun Routine.toDraft() = RoutineDraft(name, blocks)

/** At least one set and one rep; a reversed range is swapped. */
fun RoutineTargets.normalized(): RoutineTargets {
    val low = repsMin.coerceAtLeast(1)
    val high = repsMax.coerceAtLeast(1)
    return copy(
        sets = sets.coerceAtLeast(1),
        repsMin = minOf(low, high),
        repsMax = maxOf(low, high),
        weightKg = weightKg?.takeIf { it > 0 },
        restSeconds = restSeconds?.takeIf { it > 0 },
    )
}
