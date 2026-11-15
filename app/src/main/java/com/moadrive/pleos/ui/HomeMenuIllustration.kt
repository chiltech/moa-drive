package com.moadrive.pleos.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform

/** The approved simple illustration family, drawn on a transparent 64-unit canvas. */
@Composable
internal fun HomeMenuIllustration(
    kind: MoaIconKind,
    modifier: Modifier = Modifier,
    tint: Color? = null,
    detailColor: Color = SurfaceColor,
) {
    val dark = IsDarkTheme
    val blue = if (dark) Color(0xFF83A5FF) else Color(0xFF2E63EF)
    val color = tint ?: when (kind) {
        MoaIconKind.TRIPS -> if (dark) Color(0xFF78D3B3) else Color(0xFF51B996)
        MoaIconKind.PASSPORT, MoaIconKind.CHILD_CARE -> if (dark) Color(0xFFFFB16A) else Color(0xFFFF8A35)
        MoaIconKind.BATTERY, MoaIconKind.PET_CARE -> if (dark) Color(0xFF6ED0A6) else Color(0xFF32A374)
        MoaIconKind.SAFETY -> if (dark) Color(0xFFFFD36A) else Color(0xFFF1BA37)
        else -> blue
    }
    val colors = MenuIllustrationColors(
        main = color,
        light = if (tint != null) color.copy(alpha = .48f) else color.mixWithWhite(.30f),
        shade = if (tint != null) color else color.mixWithBlack(.10f),
        detail = if (tint != null) detailColor else Color.White,
        route = tint ?: if (dark) Color(0xFFB7CAFF) else Color(0xFF89ACFA),
        routeFill = if (tint != null) color.copy(alpha = .20f) else if (dark) Color(0xFF385488) else Color(0xFFDCE7FF),
    )
    Canvas(modifier) {
        val side = size.minDimension
        withTransform({
            translate((size.width - side) / 2f, (size.height - side) / 2f)
            scale(side / 64f, side / 64f, pivot = Offset.Zero)
        }) {
            when (kind) {
                MoaIconKind.DRIVE -> menuDrive(colors)
                MoaIconKind.TRIPS -> menuTrips(colors)
                MoaIconKind.CONDITION_NOTE -> menuCondition(colors)
                MoaIconKind.PASSPORT -> menuPassport(colors)
                MoaIconKind.BATTERY -> menuBudget(colors)
                MoaIconKind.SAFETY -> menuSafety(colors)
                MoaIconKind.CHILD_CARE -> menuChild(colors)
                MoaIconKind.PET_CARE -> menuPet(colors)
                MoaIconKind.SOUND -> menuSound(colors)
                MoaIconKind.INSURANCE -> menuInsurance(colors)
                else -> Unit // Home and settings retain their existing rail symbols.
            }
        }
    }
}

private data class MenuIllustrationColors(
    val main: Color, val light: Color, val shade: Color, val detail: Color,
    val route: Color, val routeFill: Color,
)

private fun Color.mixWithWhite(amount: Float) = Color(
    red + (1f - red) * amount, green + (1f - green) * amount,
    blue + (1f - blue) * amount, alpha,
)

private fun Color.mixWithBlack(amount: Float) = Color(
    red * (1f - amount), green * (1f - amount), blue * (1f - amount), alpha,
)

private fun menuStroke(width: Float = 4.2f) = Stroke(width, cap = StrokeCap.Round, join = StrokeJoin.Round)

private fun DrawScope.menuPath(color: Color, stroke: Float? = null, shape: Path.() -> Unit) {
    val path = Path().apply(shape)
    if (stroke == null) drawPath(path, color) else drawPath(path, color, style = menuStroke(stroke))
}

private fun DrawScope.menuLine(color: Color, x1: Float, y1: Float, x2: Float, y2: Float, width: Float = 4.2f) =
    drawLine(color, Offset(x1, y1), Offset(x2, y2), width, StrokeCap.Round)

private fun DrawScope.menuDrive(c: MenuIllustrationColors) {
    drawArc(c.main, 137f, 333f, false, Offset(5f, 5f), Size(54f, 54f), style = menuStroke(4.6f))
    menuPath(c.main) {
        moveTo(30f, 17f); quadraticTo(32f, 13f, 34f, 17f)
        lineTo(47f, 45f); quadraticTo(48f, 48f, 45f, 46.5f)
        lineTo(32f, 39.8f); lineTo(19f, 46.5f)
        quadraticTo(16f, 48f, 17f, 45f); close()
    }
    menuPath(c.shade) { moveTo(32f, 17f); lineTo(32f, 39.8f); lineTo(19f, 46.5f); close() }
}

private fun DrawScope.menuTrips(c: MenuIllustrationColors) {
    // Stop the connecting strokes at the hollow nodes so their centers stay transparent.
    menuLine(c.main, 14f, 44.5f, 20f, 37f, 4.7f)
    menuLine(c.main, 30.3f, 34.8f, 31.8f, 35.6f, 4.7f)
    menuLine(c.main, 42f, 31.3f, 48f, 20.3f, 4.7f)
    listOf(Offset(10f, 51f), Offset(24f, 31.5f), Offset(38f, 38f), Offset(52f, 13f)).forEach {
        drawCircle(c.main, 6.8f, it, style = menuStroke(4.3f))
    }
}

private fun DrawScope.menuCondition(c: MenuIllustrationColors) {
    drawRoundRect(c.main, Offset(12f, 12f), Size(40f, 46f), CornerRadius(6f))
    menuPath(c.shade) {
        moveTo(46f, 12f); quadraticTo(52f, 12f, 52f, 18f)
        lineTo(52f, 52f); quadraticTo(52f, 58f, 46f, 58f)
        lineTo(44f, 58f); lineTo(44f, 12f); close()
    }
    listOf(22f, 32f, 42f).forEach { x ->
        menuLine(c.main, x, 6f, x, 16f, 4.2f)
        menuLine(c.light, x, 13f, x, 17f, 4.2f)
    }
    menuPath(c.detail, 3.9f) {
        moveTo(19.5f, 37f); lineTo(26f, 37f); lineTo(30.5f, 28.5f)
        lineTo(35.5f, 45f); lineTo(40f, 35f); lineTo(45f, 35f)
    }
}

private fun DrawScope.menuPassport(c: MenuIllustrationColors) {
    menuPath(c.main) {
        moveTo(8f, 25f); quadraticTo(8f, 23f, 10f, 22.5f)
        lineTo(24f, 18f); lineTo(40f, 24f); lineTo(53f, 19f)
        quadraticTo(55f, 18f, 55.5f, 21f); lineTo(59f, 52f)
        quadraticTo(59f, 54f, 57f, 54.5f)
        lineTo(43f, 59f); lineTo(25f, 52f); lineTo(10f, 57f)
        quadraticTo(8f, 57.7f, 8f, 55f); close()
    }
    menuPath(c.light) { moveTo(10f, 22.5f); lineTo(24f, 18f); lineTo(25f, 52f); lineTo(10f, 57f); lineTo(8f, 55f); lineTo(8f, 25f); close() }
    menuPath(c.shade) { moveTo(24f, 18f); lineTo(40f, 24f); lineTo(43f, 59f); lineTo(25f, 52f); close() }
    menuLine(c.detail, 24f, 34f, 25f, 47f, 2.9f)
    menuLine(c.detail, 41f, 36f, 43f, 51f, 2.9f)
    menuPath(c.main) {
        moveTo(32f, 35f)
        cubicTo(27f, 28f, 21.7f, 20.5f, 21.7f, 15.5f)
        cubicTo(21.7f, 1.7f, 42.3f, 1.7f, 42.3f, 15.5f)
        cubicTo(42.3f, 20.5f, 37f, 28f, 32f, 35f); close()
    }
    drawCircle(c.detail, 4.7f, Offset(32f, 15f))
}

private fun DrawScope.menuBudget(c: MenuIllustrationColors) {
    drawRoundRect(c.main, Offset(23f, 5f), Size(15f, 7f), CornerRadius(2f))
    drawRoundRect(c.main, Offset(13f, 11f), Size(35f, 47f), CornerRadius(6f))
    drawRoundRect(c.detail, Offset(16.3f, 14.3f), Size(28.4f, 40.4f), CornerRadius(3.2f))
    drawRoundRect(c.main, Offset(18.3f, 16.3f), Size(24.4f, 36.4f), CornerRadius(1.6f))
    menuPath(c.detail) {
        moveTo(32f, 22f); lineTo(23f, 36f); quadraticTo(22f, 37.5f, 24f, 37.5f)
        lineTo(29f, 37.5f); lineTo(27f, 48f); lineTo(38f, 32f)
        quadraticTo(39f, 30.5f, 37f, 30.5f); lineTo(31f, 30.5f); close()
    }
    menuPath(c.routeFill) {
        moveTo(39f, 58f); cubicTo(39f, 46f, 46f, 39f, 55f, 39f)
        lineTo(53f, 34.5f); lineTo(61f, 40.5f); lineTo(54f, 46f)
        lineTo(55f, 42f); cubicTo(48f, 43f, 44f, 48f, 44f, 58f); close()
    }
    menuPath(c.route, 2.4f) {
        moveTo(40f, 57f); cubicTo(40f, 46f, 46f, 40f, 55f, 40f)
        moveTo(53f, 35f); lineTo(60f, 40f); lineTo(55f, 45f)
    }
}

private fun DrawScope.menuSafety(c: MenuIllustrationColors) {
    menuPath(c.main) {
        moveTo(32f, 5f); quadraticTo(32f, 5f, 34f, 6f)
        lineTo(55f, 15f); lineTo(53.5f, 33f)
        cubicTo(52f, 45f, 44f, 53f, 32f, 60f)
        cubicTo(20f, 53f, 12f, 45f, 10.5f, 33f)
        lineTo(9f, 15f); close()
    }
    menuPath(c.shade) {
        moveTo(49f, 13f); lineTo(55f, 15f); lineTo(53.5f, 33f)
        cubicTo(52f, 45f, 44f, 53f, 32f, 60f); lineTo(32f, 55f)
        cubicTo(43f, 47f, 47f, 41f, 48f, 31f); close()
    }
    menuPath(c.detail, 4.4f) { moveTo(21f, 33f); lineTo(29f, 41f); lineTo(44f, 25f) }
}

private fun DrawScope.menuChild(c: MenuIllustrationColors) {
    menuPath(c.main, 3.6f) {
        moveTo(9f, 58f); cubicTo(11f, 40f, 40f, 40f, 43f, 58f)
        moveTo(12f, 27f); cubicTo(5f, 27f, 7f, 33f, 13f, 32f)
        moveTo(40f, 27f); cubicTo(47f, 27f, 45f, 33f, 40f, 32f)
    }
    drawCircle(c.main, 15f, Offset(26f, 26f), style = menuStroke(3.6f))
    menuPath(c.main, 3.1f) { moveTo(20f, 12.5f); cubicTo(22f, 19.5f, 30f, 19.5f, 33f, 13f) }
    drawCircle(c.main, 1.7f, Offset(21f, 26f)); drawCircle(c.main, 1.7f, Offset(31f, 26f))
    menuPath(c.main, 2.7f) { moveTo(22f, 33f); quadraticTo(26f, 37f, 30f, 33f) }
    menuPath(c.main) {
        moveTo(51f, 53f); cubicTo(44f, 47.5f, 43f, 44f, 45.5f, 41.5f)
        cubicTo(47f, 40f, 49.5f, 40f, 51f, 42.3f)
        cubicTo(53.5f, 40.3f, 56f, 41f, 57f, 43f)
        cubicTo(59f, 46f, 54f, 50.5f, 51f, 53f); close()
    }
}

private fun DrawScope.menuPet(c: MenuIllustrationColors) {
    drawOval(c.main, Offset(18f, 5f), Size(11f, 16f))
    drawOval(c.main, Offset(35f, 5f), Size(11f, 16f))
    withTransform({ rotate(-24f, Offset(12f, 25f)) }) { drawOval(c.main, Offset(6f, 17f), Size(12f, 16f)) }
    withTransform({ rotate(24f, Offset(52f, 25f)) }) { drawOval(c.main, Offset(46f, 17f), Size(12f, 16f)) }
    menuPath(c.main) {
        moveTo(32f, 27f); cubicTo(25f, 27f, 23f, 33f, 18f, 39f)
        cubicTo(11f, 47f, 13f, 58f, 22f, 58f)
        cubicTo(27f, 58f, 28f, 54f, 32f, 54f)
        cubicTo(36f, 54f, 37f, 58f, 42f, 58f)
        cubicTo(51f, 58f, 53f, 47f, 46f, 39f)
        cubicTo(41f, 33f, 39f, 27f, 32f, 27f); close()
    }
}

private fun DrawScope.menuSound(c: MenuIllustrationColors) {
    menuPath(c.main) {
        moveTo(7f, 23f); lineTo(16f, 23f); lineTo(29f, 13f)
        quadraticTo(32f, 10f, 32f, 14f); lineTo(32f, 50f)
        quadraticTo(32f, 54f, 29f, 51f); lineTo(16f, 41f); lineTo(7f, 41f)
        quadraticTo(4f, 41f, 4f, 38f); lineTo(4f, 26f)
        quadraticTo(4f, 23f, 7f, 23f); close()
    }
    drawRoundRect(c.light, Offset(12f, 27f), Size(3f, 10f), CornerRadius(1f))
    menuPath(c.routeFill) { moveTo(33f, 24f); quadraticTo(44f, 18f, 44f, 32f); quadraticTo(44f, 46f, 33f, 40f); close() }
    menuPath(c.main, 3.8f) {
        moveTo(42f, 20f); cubicTo(49f, 26f, 49f, 38f, 42f, 44f)
        moveTo(51f, 12f); cubicTo(62f, 23f, 62f, 41f, 51f, 52f)
    }
}

private fun DrawScope.menuInsurance(c: MenuIllustrationColors) {
    menuPath(c.main, 4f) {
        moveTo(32f, 6f); quadraticTo(43f, 12f, 54f, 15f)
        lineTo(52.5f, 33f); cubicTo(51f, 45f, 43f, 53f, 32f, 59f)
        cubicTo(21f, 53f, 13f, 45f, 11.5f, 33f); lineTo(10f, 15f)
        quadraticTo(21f, 12f, 32f, 6f); close()
    }
    menuPath(c.main) {
        moveTo(18f, 33f); lineTo(23f, 23f); quadraticTo(23.7f, 21.5f, 26f, 21.5f)
        lineTo(38f, 21.5f); quadraticTo(40f, 21.5f, 41f, 23f)
        lineTo(46f, 33f); lineTo(46f, 42f); quadraticTo(46f, 44f, 43f, 44f)
        lineTo(21f, 44f); quadraticTo(18f, 44f, 18f, 42f); close()
    }
    menuPath(c.detail) { moveTo(25.5f, 25f); lineTo(38.5f, 25f); lineTo(41.7f, 32f); lineTo(22.3f, 32f); close() }
    drawCircle(c.detail, 2f, Offset(23f, 37.7f)); drawCircle(c.detail, 2f, Offset(41f, 37.7f))
    menuLine(c.main, 17f, 32f, 20f, 32f, 2.8f); menuLine(c.main, 44f, 32f, 47f, 32f, 2.8f)
    // A small check rests over the car without changing the shield silhouette.
    menuPath(c.detail, 5.2f) { moveTo(35.5f, 43f); lineTo(39.3f, 47f); lineTo(47.5f, 38.5f) }
    menuPath(c.main, 2.6f) { moveTo(35.5f, 43f); lineTo(39.3f, 47f); lineTo(47.5f, 38.5f) }
}
