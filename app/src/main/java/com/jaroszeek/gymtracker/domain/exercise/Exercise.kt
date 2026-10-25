package com.jaroszeek.gymtracker.domain.exercise

import kotlinx.serialization.Serializable

/** Keys match free-exercise-db, which is also how they are stored. */
enum class Muscle(val key: String) {
    Abdominals("abdominals"),
    Abductors("abductors"),
    Adductors("adductors"),
    Biceps("biceps"),
    Calves("calves"),
    Chest("chest"),
    Forearms("forearms"),
    Glutes("glutes"),
    Hamstrings("hamstrings"),
    Lats("lats"),
    LowerBack("lower back"),
    MiddleBack("middle back"),
    Neck("neck"),
    Quadriceps("quadriceps"),
    Shoulders("shoulders"),
    Traps("traps"),
    Triceps("triceps"),
    ;

    companion object {
        fun fromKey(key: String): Muscle = entries.first { it.key == key }
    }
}

/** Body part filter; matches an exercise by its primary muscles. */
enum class BodyPart(val muscles: Set<Muscle>) {
    Chest(setOf(Muscle.Chest)),
    Back(setOf(Muscle.Lats, Muscle.MiddleBack, Muscle.LowerBack, Muscle.Traps)),
    Shoulders(setOf(Muscle.Shoulders)),
    Arms(setOf(Muscle.Biceps, Muscle.Triceps, Muscle.Forearms)),
    Abs(setOf(Muscle.Abdominals)),
    Glutes(setOf(Muscle.Glutes)),
    Legs(setOf(Muscle.Quadriceps, Muscle.Hamstrings, Muscle.Calves, Muscle.Adductors, Muscle.Abductors)),
    Neck(setOf(Muscle.Neck)),
    ;

    companion object {
        /** Every muscle once, grouped by body part: a natural order for pickers. */
        val orderedMuscles: List<Muscle> = entries.flatMap { it.muscles }
    }
}

enum class Equipment(val key: String, val group: EquipmentGroup) {
    Barbell("barbell", EquipmentGroup.Barbell),
    EzBar("e-z curl bar", EquipmentGroup.Barbell),
    Dumbbell("dumbbell", EquipmentGroup.Dumbbell),
    Cable("cable", EquipmentGroup.Cable),
    Machine("machine", EquipmentGroup.Machine),
    BodyOnly("body only", EquipmentGroup.BodyWeight),
    Kettlebells("kettlebells", EquipmentGroup.Kettlebell),
    Bands("bands", EquipmentGroup.Bands),
    MedicineBall("medicine ball", EquipmentGroup.Other),
    ExerciseBall("exercise ball", EquipmentGroup.Other),
    FoamRoll("foam roll", EquipmentGroup.Other),
    Other("other", EquipmentGroup.Other),
    ;

    companion object {
        fun fromKey(key: String): Equipment = entries.first { it.key == key }
    }
}

/** Equipment filter chips; exercises without equipment fall under [Other]. */
enum class EquipmentGroup { Barbell, Dumbbell, Cable, Machine, BodyWeight, Kettlebell, Bands, Other }

enum class ExerciseCategory(val key: String) {
    Strength("strength"),
    Stretching("stretching"),
    Plyometrics("plyometrics"),
    Powerlifting("powerlifting"),
    OlympicWeightlifting("olympic weightlifting"),
    Strongman("strongman"),
    Cardio("cardio"),
    ;

    companion object {
        fun fromKey(key: String): ExerciseCategory = entries.first { it.key == key }
    }
}

enum class Level(val key: String) {
    Beginner("beginner"),
    Intermediate("intermediate"),
    Expert("expert"),
    ;

    companion object {
        fun fromKey(key: String): Level = entries.first { it.key == key }
    }
}

enum class Mechanic(val key: String) {
    Compound("compound"),
    Isolation("isolation"),
    ;

    companion object {
        fun fromKey(key: String): Mechanic = entries.first { it.key == key }
    }
}

enum class ExerciseSource(val key: String) {
    /** free-exercise-db */
    Builtin("builtin"),

    /** Variants from Jeff Nippard's programs missing in free-exercise-db */
    Nippard("nippard"),
    Custom("custom"),
    ;

    companion object {
        fun fromKey(key: String): ExerciseSource = entries.first { it.key == key }
    }
}

/** Serializable so a routine draft holding it survives process death (saved state). */
@Serializable
data class Exercise(
    val id: Long,
    val name: String,
    val aliases: List<String>,
    val category: ExerciseCategory,
    val equipment: Equipment?,
    val level: Level?,
    val mechanic: Mechanic?,
    val primaryMuscles: Set<Muscle>,
    val secondaryMuscles: Set<Muscle>,
    val instructions: List<String>,
    /** Photo URLs (start and end position); empty for exercises without photos. */
    val images: List<String>,
    val source: ExerciseSource,
) {
    val isCustom: Boolean get() = source == ExerciseSource.Custom
}

/** What the user enters for a custom exercise. */
data class ExerciseDraft(
    val name: String = "",
    val equipment: Equipment? = null,
    val primaryMuscles: Set<Muscle> = emptySet(),
    val secondaryMuscles: Set<Muscle> = emptySet(),
    val notes: String = "",
) {
    val isValid: Boolean get() = name.isNotBlank() && primaryMuscles.isNotEmpty()
}

fun Exercise.toDraft() = ExerciseDraft(
    name = name,
    equipment = equipment,
    primaryMuscles = primaryMuscles,
    secondaryMuscles = secondaryMuscles,
    notes = instructions.joinToString(separator = "\n"),
)
