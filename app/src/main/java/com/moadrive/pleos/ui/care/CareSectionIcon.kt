package com.moadrive.pleos.ui.care

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.moadrive.pleos.ui.Blue
import com.moadrive.pleos.ui.IsDarkTheme

enum class CareSectionSymbol { SCHOOL, VACCINATION, NEWS, NEARBY, OUTING }

/** Decorative heading symbols; the adjacent text supplies the accessibility label. */
@Composable
fun CareSectionIcon(symbol: CareSectionSymbol, accent: Color = Blue, modifier: Modifier = Modifier) {
    Box(modifier.size(40.dp).background(accent.copy(alpha = if (IsDarkTheme) .17f else .09f), RoundedCornerShape(13.dp)),
        contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(25.dp)) {
            val s = size.width / 24f
            fun p(x: Float, y: Float) = Offset(x * s, y * s)
            fun line(a: Float, b: Float, c: Float, d: Float) = drawLine(accent, p(a,b), p(c,d), 1.7f*s, StrokeCap.Round)
            fun box(x: Float, y: Float, w: Float, h: Float, r: Float = 1f) = drawRoundRect(accent, p(x,y), Size(w*s,h*s), CornerRadius(r*s), style = Stroke(1.7f*s))
            when (symbol) {
                CareSectionSymbol.SCHOOL -> {
                    line(2f,10f,12f,3f); line(12f,3f,22f,10f)
                    box(5f,10f,14f,11f); box(10f,15f,4f,6f,.3f)
                    line(3f,21f,21f,21f); line(8f,12f,8f,13f); line(16f,12f,16f,13f)
                    drawCircle(accent, 1.4f*s, p(12f,9f))
                }
                CareSectionSymbol.VACCINATION -> {
                    val body = Path().apply { moveTo(6*s,13*s); lineTo(13*s,6*s); lineTo(18*s,11*s); lineTo(11*s,18*s); close() }
                    drawPath(body, accent.copy(alpha=.18f)); drawPath(body,accent, style=Stroke(1.7f*s))
                    line(3f,21f,7f,17f); line(6f,16f,8f,18f)
                    line(15.5f,8.5f,18.5f,5.5f); line(17f,4f,20f,7f)
                    line(10f,10f,12f,12f); line(8f,12f,10f,14f)
                    line(12f,4.5f,19.5f,12f)
                }
                CareSectionSymbol.NEWS -> {
                    box(3f,4f,15f,17f,2f)
                    line(18f,9f,21f,9f); line(21f,9f,21f,19f); line(21f,19f,19f,21f)
                    box(6f,7f,5f,5f,.5f); line(13.5f,8f,15f,8f); line(13.5f,11f,15f,11f)
                    line(6f,15f,15f,15f); line(6f,18f,15f,18f)
                }
                CareSectionSymbol.NEARBY -> {
                    val pin = Path().apply { moveTo(12*s,22*s); cubicTo(8*s,17*s,4*s,13*s,4*s,9*s); cubicTo(4*s,-1*s,20*s,-1*s,20*s,9*s); cubicTo(20*s,13*s,16*s,17*s,12*s,22*s) }
                    drawPath(pin,accent.copy(alpha=.12f)); drawPath(pin,accent,style=Stroke(1.7f*s))
                    line(9f,9f,15f,9f); line(12f,6f,12f,12f)
                }
                CareSectionSymbol.OUTING -> {
                    val mountain=Path().apply { moveTo(2*s,20*s); lineTo(9*s,8*s); lineTo(13*s,14*s); lineTo(17*s,8*s); lineTo(22*s,20*s); close() }
                    drawPath(mountain,accent.copy(alpha=.15f)); drawPath(mountain,accent,style=Stroke(1.7f*s))
                    drawCircle(accent,2f*s,p(7f,4f)); line(9f,8f,9f,12f); line(17f,8f,17f,12f)
                }
            }
        }
    }
}
