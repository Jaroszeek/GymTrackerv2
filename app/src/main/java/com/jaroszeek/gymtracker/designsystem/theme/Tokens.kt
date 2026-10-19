package com.jaroszeek.gymtracker.designsystem.theme

import androidx.compose.animation.core.spring
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.kyant.shapes.Capsule
import com.kyant.shapes.RoundedRectangle

/** 4-pt spacing scale. */
object GtSpacing {
    val xxs = 2.dp
    val xs = 4.dp
    val s = 8.dp
    val m = 12.dp
    val l = 16.dp
    val xl = 20.dp
    val xxl = 24.dp
    val xxxl = 32.dp
    val huge = 48.dp
}

/** Continuous-curvature corners (squircle-like). */
object GtShapes {
    val Pill: Shape = Capsule()
    val Small: Shape = RoundedRectangle(14.dp)
    val Medium: Shape = RoundedRectangle(20.dp)
    val Card: Shape = RoundedRectangle(28.dp)
    val Sheet: Shape = RoundedRectangle(36.dp)
}

object GtBlur {
    val Bar = 18.dp
    val Panel = 28.dp
    val RefractionHeight = 20.dp
    val RefractionAmount = 28.dp
}

/** Soft, wide shadows; color comes from [GtColors.shadow]. */
object GtElevation {
    val Floating = 32.dp
    val Card = 20.dp
}

object GtSizes {
    val Hairline = 1.dp
    val MinTouch = 48.dp
    val Icon = 24.dp
    val IconSmall = 20.dp
    val IconLarge = 28.dp
    val NavBarHeight = 64.dp
    val NavBarMaxWidth = 400.dp
    val NavBarMargin = 16.dp
    val NavBarGap = 12.dp
    val HideBarThreshold = 48.dp
    val TopBarHeight = 56.dp
    val ButtonHeight = 52.dp
    val ChipHeight = 36.dp
    val BadgeHeight = 22.dp
    val DialogMaxWidth = 360.dp
    val ContentMaxWidth = 560.dp
    val Thumbnail = 56.dp
}

object GtAlpha {
    /** Accent tint behind accent text or icons (badges, droplet, icon wells). */
    const val ACCENT_WASH = 0.16f
    const val DISABLED = 0.4f
}

object GtMotion {
    /** Press feedback of tappable elements. */
    val Press = spring<Float>(dampingRatio = 0.45f, stiffness = 600f)

    /** Edges of the navbar droplet and the switch thumb: the leading edge is fast, the trailing one lags. */
    val LiquidLeading = spring<Float>(dampingRatio = 0.62f, stiffness = 900f)
    val LiquidTrailing = spring<Float>(dampingRatio = 0.8f, stiffness = 260f)

    /** Overlays (dialog, sheet) and general enter/exit. */
    val Standard = spring<Float>(dampingRatio = 0.85f, stiffness = 380f)
    val Slide = spring(dampingRatio = 0.85f, stiffness = 420f, visibilityThreshold = IntOffset.VisibilityThreshold)

    const val PRESSED_SCALE = 0.96f
    const val NAV_PRESSED_SCALE = 0.88f
    const val AURORA_PERIOD_MILLIS = 75_000L
    const val FADE_IN_MILLIS = 200
    const val FADE_OUT_MILLIS = 150
}
