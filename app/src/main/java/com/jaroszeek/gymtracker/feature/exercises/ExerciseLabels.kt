package com.jaroszeek.gymtracker.feature.exercises

import androidx.annotation.StringRes
import com.jaroszeek.gymtracker.R
import com.jaroszeek.gymtracker.domain.exercise.BodyPart
import com.jaroszeek.gymtracker.domain.exercise.Equipment
import com.jaroszeek.gymtracker.domain.exercise.EquipmentGroup
import com.jaroszeek.gymtracker.domain.exercise.ExerciseCategory
import com.jaroszeek.gymtracker.domain.exercise.ExerciseSource
import com.jaroszeek.gymtracker.domain.exercise.Level
import com.jaroszeek.gymtracker.domain.exercise.Mechanic
import com.jaroszeek.gymtracker.domain.exercise.Muscle

// Polish labels for the domain vocabulary; exercise names themselves stay in English (A5.5)

@get:StringRes
val BodyPart.label: Int
    get() = when (this) {
        BodyPart.Chest -> R.string.body_part_chest
        BodyPart.Back -> R.string.body_part_back
        BodyPart.Shoulders -> R.string.body_part_shoulders
        BodyPart.Arms -> R.string.body_part_arms
        BodyPart.Abs -> R.string.body_part_abs
        BodyPart.Glutes -> R.string.body_part_glutes
        BodyPart.Legs -> R.string.body_part_legs
        BodyPart.Neck -> R.string.body_part_neck
    }

@get:StringRes
val EquipmentGroup.label: Int
    get() = when (this) {
        EquipmentGroup.Barbell -> R.string.equipment_group_barbell
        EquipmentGroup.Dumbbell -> R.string.equipment_group_dumbbell
        EquipmentGroup.Cable -> R.string.equipment_group_cable
        EquipmentGroup.Machine -> R.string.equipment_group_machine
        EquipmentGroup.BodyWeight -> R.string.equipment_group_body_weight
        EquipmentGroup.Kettlebell -> R.string.equipment_group_kettlebell
        EquipmentGroup.Bands -> R.string.equipment_group_bands
        EquipmentGroup.Other -> R.string.equipment_group_other
    }

@get:StringRes
val Equipment.label: Int
    get() = when (this) {
        Equipment.Barbell -> R.string.equipment_barbell
        Equipment.EzBar -> R.string.equipment_ez_bar
        Equipment.Dumbbell -> R.string.equipment_dumbbell
        Equipment.Cable -> R.string.equipment_cable
        Equipment.Machine -> R.string.equipment_machine
        Equipment.BodyOnly -> R.string.equipment_body_only
        Equipment.Kettlebells -> R.string.equipment_kettlebells
        Equipment.Bands -> R.string.equipment_bands
        Equipment.MedicineBall -> R.string.equipment_medicine_ball
        Equipment.ExerciseBall -> R.string.equipment_exercise_ball
        Equipment.FoamRoll -> R.string.equipment_foam_roll
        Equipment.Other -> R.string.equipment_other
    }

@get:StringRes
val Muscle.label: Int
    get() = when (this) {
        Muscle.Abdominals -> R.string.muscle_abdominals
        Muscle.Abductors -> R.string.muscle_abductors
        Muscle.Adductors -> R.string.muscle_adductors
        Muscle.Biceps -> R.string.muscle_biceps
        Muscle.Calves -> R.string.muscle_calves
        Muscle.Chest -> R.string.muscle_chest
        Muscle.Forearms -> R.string.muscle_forearms
        Muscle.Glutes -> R.string.muscle_glutes
        Muscle.Hamstrings -> R.string.muscle_hamstrings
        Muscle.Lats -> R.string.muscle_lats
        Muscle.LowerBack -> R.string.muscle_lower_back
        Muscle.MiddleBack -> R.string.muscle_middle_back
        Muscle.Neck -> R.string.muscle_neck
        Muscle.Quadriceps -> R.string.muscle_quadriceps
        Muscle.Shoulders -> R.string.muscle_shoulders
        Muscle.Traps -> R.string.muscle_traps
        Muscle.Triceps -> R.string.muscle_triceps
    }

@get:StringRes
val Level.label: Int
    get() = when (this) {
        Level.Beginner -> R.string.level_beginner
        Level.Intermediate -> R.string.level_intermediate
        Level.Expert -> R.string.level_expert
    }

@get:StringRes
val Mechanic.label: Int
    get() = when (this) {
        Mechanic.Compound -> R.string.mechanic_compound
        Mechanic.Isolation -> R.string.mechanic_isolation
    }

@get:StringRes
val ExerciseCategory.label: Int
    get() = when (this) {
        ExerciseCategory.Strength -> R.string.category_strength
        ExerciseCategory.Stretching -> R.string.category_stretching
        ExerciseCategory.Plyometrics -> R.string.category_plyometrics
        ExerciseCategory.Powerlifting -> R.string.category_powerlifting
        ExerciseCategory.OlympicWeightlifting -> R.string.category_olympic_weightlifting
        ExerciseCategory.Strongman -> R.string.category_strongman
        ExerciseCategory.Cardio -> R.string.category_cardio
    }

@get:StringRes
val ExerciseSource.label: Int
    get() = when (this) {
        ExerciseSource.Builtin -> R.string.detail_source_builtin
        ExerciseSource.Nippard -> R.string.detail_source_nippard
        ExerciseSource.Custom -> R.string.detail_source_custom
    }
