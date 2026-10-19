package com.jaroszeek.gymtracker.navigation

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.navigation3.ui.LocalNavAnimatedContentScope

/** Provided around [androidx.navigation3.ui.NavDisplay]; null in previews and the gallery. */
val LocalSharedTransitionScope = staticCompositionLocalOf<SharedTransitionScope?> { null }

/** Morphs this element into the one with the same [key] on the next screen (A4: exercise → details). */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.sharedElement(key: Any): Modifier {
    val scope = LocalSharedTransitionScope.current ?: return this
    val animatedScope = LocalNavAnimatedContentScope.current
    return with(scope) { this@sharedElement.sharedElement(rememberSharedContentState(key), animatedScope) }
}

/** Morphs this container into the one with the same [key] on the next screen, even when the content differs. */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.sharedBounds(key: Any): Modifier {
    val scope = LocalSharedTransitionScope.current ?: return this
    val animatedScope = LocalNavAnimatedContentScope.current
    return with(scope) { this@sharedBounds.sharedBounds(rememberSharedContentState(key), animatedScope) }
}
