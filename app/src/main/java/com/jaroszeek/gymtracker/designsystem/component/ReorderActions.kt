package com.jaroszeek.gymtracker.designsystem.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import com.jaroszeek.gymtracker.R

/** TalkBack actions for a drag handle, since a screen reader cannot drag; null where the item cannot move that way. */
@Composable
fun reorderActions(onMoveUp: (() -> Unit)?, onMoveDown: (() -> Unit)?): List<CustomAccessibilityAction> {
    val up = stringResource(R.string.action_move_up)
    val down = stringResource(R.string.action_move_down)
    return listOfNotNull(
        onMoveUp?.let { move -> CustomAccessibilityAction(up) { move(); true } },
        onMoveDown?.let { move -> CustomAccessibilityAction(down) { move(); true } },
    )
}
