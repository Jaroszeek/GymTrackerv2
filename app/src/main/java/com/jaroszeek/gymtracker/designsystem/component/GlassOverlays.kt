package com.jaroszeek.gymtracker.designsystem.component

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animate
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.DraggableState
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import com.jaroszeek.gymtracker.R
import com.jaroszeek.gymtracker.designsystem.theme.GtMotion
import com.jaroszeek.gymtracker.designsystem.theme.GtShapes
import com.jaroszeek.gymtracker.designsystem.theme.GtSizes
import com.jaroszeek.gymtracker.designsystem.theme.GtSpacing
import com.jaroszeek.gymtracker.designsystem.theme.GtTheme
import kotlin.math.roundToInt

private const val DIALOG_ENTER_SCALE = 0.92f

/**
 * Glass dialog drawn as an own layer above the bars (not a platform window), so it can blur
 * the screen behind it (D-006). Keep it in composition and drive [visible] to get the exit animation.
 */
@Composable
fun GlassDialog(
    visible: Boolean,
    onDismissRequest: () -> Unit,
    title: String,
    modifier: Modifier = Modifier,
    text: String? = null,
    confirmButton: @Composable () -> Unit,
    dismissButton: (@Composable () -> Unit)? = null,
) {
    OverlayLayer(visible, onDismissRequest, contentAlignment = Alignment.Center) { state ->
        AnimatedVisibility(
            visibleState = state,
            enter = fadeIn() + scaleIn(GtMotion.Standard, initialScale = DIALOG_ENTER_SCALE),
            exit = fadeOut() + scaleOut(targetScale = DIALOG_ENTER_SCALE),
        ) {
            GlassSurface(
                modifier
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(GtSpacing.xxl)
                    .widthIn(max = GtSizes.DialogMaxWidth)
                    .fillMaxWidth()
                    .semantics { paneTitle = title }
                    .consumeTaps(),
                shape = GtShapes.Card,
            ) {
                Column(Modifier.padding(GtSpacing.xxl), verticalArrangement = Arrangement.spacedBy(GtSpacing.m)) {
                    Text(title, Modifier.semantics { heading() }, style = GtTheme.typography.headline)
                    // A long text (or a large font) scrolls, so the buttons stay on screen
                    if (text != null) {
                        Text(
                            text,
                            Modifier
                                .weight(1f, fill = false)
                                .verticalScroll(rememberScrollState()),
                            color = GtTheme.colors.textSecondary,
                            style = GtTheme.typography.body,
                        )
                    }
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(top = GtSpacing.s),
                        horizontalArrangement = Arrangement.spacedBy(GtSpacing.s, Alignment.End),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        dismissButton?.invoke()
                        confirmButton()
                    }
                }
            }
        }
    }
}

/**
 * Floating glass sheet anchored to the bottom, inset from the edges. Dismissed by dragging it down,
 * the scrim or back.
 */
@Composable
fun GlassBottomSheet(
    visible: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    OverlayLayer(visible, onDismissRequest, contentAlignment = Alignment.BottomCenter) { state ->
        AnimatedVisibility(
            visibleState = state,
            enter = slideInVertically(GtMotion.Slide) { it },
            exit = slideOutVertically { it },
        ) {
            // Inside the animated content, so every opening starts in place
            val onDismiss by rememberUpdatedState(onDismissRequest)
            val density = LocalDensity.current
            val drag = remember(density) {
                with(density) { SheetDrag(GtSizes.SheetDismissDrag.toPx(), GtMotion.SheetDismissVelocity.toPx()) { onDismiss() } }
            }
            GlassSurface(
                modifier
                    // Above the keyboard too: sheets can hold text and number fields
                    .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime))
                    .padding(GtSpacing.s)
                    .widthIn(max = GtSizes.ContentMaxWidth)
                    .fillMaxWidth()
                    .offset { IntOffset(0, drag.offset.roundToInt()) }
                    .nestedScroll(drag.connection)
                    .draggable(drag.state, Orientation.Vertical, onDragStopped = { drag.settle(it) })
                    .consumeTaps(),
                shape = GtShapes.Sheet,
            ) {
                Column(Modifier.padding(GtSpacing.xxl), verticalArrangement = Arrangement.spacedBy(GtSpacing.m), content = content)
            }
        }
    }
}

/**
 * Full-screen glass layer above everything, e.g. the rest timer: the screen behind stays visible,
 * blurred. Back calls [onDismissRequest]; [content] gets the safe drawing area.
 */
@Composable
fun GlassFullScreenOverlay(
    visible: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    OverlayLayer(visible, onDismissRequest, contentAlignment = Alignment.Center, scrim = false) { state ->
        AnimatedVisibility(visibleState = state, enter = fadeIn(), exit = fadeOut()) {
            GlassSurface(
                modifier
                    .fillMaxSize()
                    .consumeTaps(),
                shape = RectangleShape,
                shadow = false,
            ) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .windowInsetsPadding(WindowInsets.safeDrawing)
                        .animateEnterExit(
                            enter = scaleIn(GtMotion.Standard, initialScale = DIALOG_ENTER_SCALE),
                            exit = scaleOut(targetScale = DIALOG_ENTER_SCALE),
                        ),
                    content = content,
                )
            }
        }
    }
}

@Composable
private fun OverlayLayer(
    visible: Boolean,
    onDismissRequest: () -> Unit,
    contentAlignment: Alignment,
    scrim: Boolean = true,
    content: @Composable BoxScope.(MutableTransitionState<Boolean>) -> Unit,
) {
    val state = remember { MutableTransitionState(false) }
    state.targetState = visible
    BackHandler(enabled = visible, onBack = onDismissRequest)
    // Stays in the host only while shown or animating out
    if (!state.currentState && !state.targetState && state.isIdle) return
    Overlay {
        val dismiss = stringResource(R.string.action_close)
        Box(Modifier.fillMaxSize(), contentAlignment = contentAlignment) {
            if (scrim) {
                AnimatedVisibility(visibleState = state, enter = fadeIn(), exit = fadeOut()) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(GtTheme.colors.scrim)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClickLabel = dismiss,
                                onClick = onDismissRequest,
                            ),
                    )
                }
            }
            content(state)
        }
    }
}

/**
 * Drag down to dismiss of [GlassBottomSheet], in px with down positive. The sheet follows the finger
 * down, never above its place. Scrolling content keeps scrolling: the sheet takes only the drag the
 * content leaves at its top, and gives it back first when the content scrolls up again.
 */
private class SheetDrag(private val distance: Float, private val velocity: Float, private val onDismiss: () -> Unit) {
    var offset by mutableFloatStateOf(0f)
        private set

    val state = DraggableState { pull(it) }

    // Moves the sheet and returns the part of delta it used
    private fun pull(delta: Float): Float {
        val new = (offset + delta).coerceAtLeast(0f)
        return (new - offset).also { offset = new }
    }

    /** Past the distance or flung down dismisses, unless flung back up; anything else springs back. */
    suspend fun settle(speed: Float) {
        if (speed > velocity || (speed > -velocity && offset > distance)) {
            onDismiss()
        } else {
            // A new drag cancels the spring and takes the sheet from where it is
            state.drag { animate(offset, 0f, speed, GtMotion.Standard) { value, _ -> dragBy(value - offset) } }
        }
    }

    // Only the finger moves the sheet: a fling of the content stops at its top
    val connection = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset =
            if (available.y < 0f && source == NestedScrollSource.UserInput) Offset(0f, pull(available.y)) else Offset.Zero

        override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset =
            if (available.y > 0f && source == NestedScrollSource.UserInput) Offset(0f, pull(available.y)) else Offset.Zero

        override suspend fun onPreFling(available: Velocity): Velocity {
            if (offset == 0f) return Velocity.Zero
            settle(available.y)
            return available
        }
    }
}

// Taps inside the panel must not reach the scrim (no click semantics, so TalkBack ignores it)
private fun Modifier.consumeTaps() = pointerInput(Unit) { detectTapGestures { } }
