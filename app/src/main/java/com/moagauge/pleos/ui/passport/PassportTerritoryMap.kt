package com.moagauge.pleos.ui.passport

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import com.moagauge.pleos.ui.common.SoftSurface as Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moagauge.pleos.passport.*
import com.moagauge.pleos.ui.*
import kotlin.math.cos

private const val MAP_LONGITUDE_ORIGIN = 128.0
private const val MAP_LATITUDE_ORIGIN = 36.0
private val longitudeScale = cos(Math.toRadians(MAP_LATITUDE_ORIGIN))
private fun project(point: CityBoundaryPoint) = Offset(((point.longitude - MAP_LONGITUDE_ORIGIN) * longitudeScale).toFloat(), (MAP_LATITUDE_ORIGIN - point.latitude).toFloat())
private data class AtlasShape(val city: PassportCity, val path: Path, val outline: Path, val left: Float, val top: Float, val right: Float, val bottom: Float)
private data class AtlasFrame(val scale: Float, val x: Float, val y: Float)
private fun frame(size: Size, focus: List<AtlasShape>): AtlasFrame {
    val left = focus.minOfOrNull { it.left } ?: -3f
    val top = focus.minOfOrNull { it.top } ?: -3f
    val right = focus.maxOfOrNull { it.right } ?: 3f
    val bottom = focus.maxOfOrNull { it.bottom } ?: 3f
    val width = (right-left).coerceAtLeast(.35f)
    val height = (bottom-top).coerceAtLeast(.35f)
    val inset = minOf(size.width, size.height) * .09f
    val scale = minOf((size.width-2*inset)/width, (size.height-2*inset)/height).coerceAtLeast(.1f)
    return AtlasFrame(scale, size.width/2f-(left+right)/2f*scale, size.height/2f-(top+bottom)/2f*scale)
}


/** Offline atlas of actual municipal geometry. Even-odd filling preserves islands and holes. */
@Composable
internal fun PassportTerritoryMap(atlas: PassportCityAtlas, visitedIds: Set<String>, selectedId: String?,
    interactive: Boolean, modifier: Modifier = Modifier, onSelect: (String?) -> Unit) {
    val dark = IsDarkTheme
    val shapes = remember(atlas) { atlas.cities.map { city ->
        val vertices = city.polygons.flatMap { it.outer }.map(::project)
        val path = Path().apply {
            fillType = PathFillType.EvenOdd
            city.polygons.forEach { polygon -> (listOf(polygon.outer) + polygon.holes).forEach { ring ->
                ring.map(::project).forEachIndexed { index, point -> if(index==0) moveTo(point.x,point.y) else lineTo(point.x,point.y) }
                close()
            } }
        }
        // Keep holes in the fill/visit geometry, but do not emphasize narrow seams left
        // by the source's independently simplified district boundaries as city borders.
        val outline = Path().apply {
            city.polygons.forEach { polygon ->
                polygon.outer.map(::project).forEachIndexed { index, point ->
                    if(index==0) moveTo(point.x,point.y) else lineTo(point.x,point.y)
                }
                close()
            }
        }
        AtlasShape(city,path,outline,vertices.minOf { it.x },vertices.minOf { it.y },vertices.maxOf { it.x },vertices.maxOf { it.y })
    } }
    val focus = remember(shapes, selectedId) { shapes.firstOrNull { it.city.id == selectedId }?.let(::listOf) ?: shapes }
    val selected = atlas.cities.firstOrNull { it.id == selectedId }
    val labels = remember(shapes, visitedIds, selectedId) {
        if(selectedId==null) shapes.filter { it.city.id in visitedIds }.take(8) else emptyList()
    }
    val labelHits = remember { mutableListOf<Pair<Rect,String>>() }
    val labelTextSize = with(LocalDensity.current) { 11.sp.toPx() }
    val labelPaint = remember(labelTextSize,dark) { android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        textSize=labelTextSize
        typeface=android.graphics.Typeface.create("sans-serif",android.graphics.Typeface.BOLD)
        color=if(dark) android.graphics.Color.WHITE else 0xFF284766.toInt()
    } }
    val latestSelect by rememberUpdatedState(onSelect)
    val sea = if (dark) Color(0xFF17283A) else Color(0xFFE0EBF6)
    val seaEnd = if (dark) Color(0xFF1C3345) else Color(0xFFEEF4F8)
    val land = if (dark) Color(0xFF344857) else Color(0xFFD6DDE6)
    val border = if (dark) Color(0xFF607586) else Color(0xFFF1F4F8)
    val colors = listOf(Blue, AccentOrange, AccentGreen, AccentYellow)
    val selection = Blue
    fun territoryColor(id: String): Color =
        lerp(land, colors[Math.floorMod(id.hashCode(), colors.size)], if (dark) .85f else .76f)
    Box(modifier.background(sea).testTag("passport.cityTerritoryMap")) {
        Canvas(Modifier.fillMaxSize().semantics { contentDescription = "대한민국 행정구역 지도, ${visitedIds.size}개 방문 도시와 군" }
            .pointerInput(atlas,focus,interactive) {
                if(interactive) detectTapGestures { tap ->
                    labelHits.firstOrNull { it.first.contains(tap) }?.let { latestSelect(it.second); return@detectTapGestures }
                    val frame = frame(Size(size.width.toFloat(),size.height.toFloat()),focus)
                    val x=(tap.x-frame.x)/frame.scale
                    val y=(tap.y-frame.y)/frame.scale
                    val city=atlas.resolve(MAP_LATITUDE_ORIGIN-y,MAP_LONGITUDE_ORIGIN+x/longitudeScale)
                    latestSelect(city?.id)
                }
            }) {
            drawRect(Brush.verticalGradient(listOf(sea, seaEnd)))
            val frame = frame(size,focus)
            withTransform({ translate(frame.x,frame.y); scale(frame.scale,frame.scale,Offset.Zero) }) {
                shapes.forEach { shape ->
                    val visited = shape.city.id in visitedIds
                    val fill = if(visited) territoryColor(shape.city.id) else land
                    drawPath(shape.path,fill.copy(alpha=if(selectedId!=null && shape.city.id!=selectedId && visited) .55f else 1f))
                    drawPath(shape.outline,border,style = Stroke(.55.dp.toPx() / frame.scale))
                }
                shapes.firstOrNull { it.city.id == selectedId }?.let { shape ->
                    drawPath(shape.outline,if(dark) Color.White else selection,style=Stroke(2.2.dp.toPx()/frame.scale))
                }
            }
            labelHits.clear()
            labels.forEach { shape ->
                val projected=project(shape.city.center)
                val pin=Offset(frame.x+projected.x*frame.scale,frame.y+projected.y*frame.scale)
                val padding=7.dp.toPx()
                val textWidth=labelPaint.measureText(shape.city.name)
                val textHeight=labelPaint.descent()-labelPaint.ascent()
                val left=(pin.x+12.dp.toPx()).coerceIn(4.dp.toPx(),(size.width-textWidth-2*padding-4.dp.toPx()).coerceAtLeast(4.dp.toPx()))
                var top=(pin.y-textHeight/2-padding).coerceIn(65.dp.toPx(),(size.height-textHeight-2*padding-34.dp.toPx()).coerceAtLeast(65.dp.toPx()))
                var box=Rect(left,top,left+textWidth+2*padding,top+textHeight+2*padding)
                if(labelHits.any { it.first.overlaps(box) }) {
                    top=(top+textHeight+2*padding+4.dp.toPx()).coerceAtMost(size.height-textHeight-2*padding-32.dp.toPx())
                    box=Rect(left,top,left+textWidth+2*padding,top+textHeight+2*padding)
                }
                drawCircle(Color.White,5.dp.toPx(),pin)
                drawCircle(territoryColor(shape.city.id),3.5.dp.toPx(),pin)
                if(labelHits.none { it.first.overlaps(box) }) {
                    drawLine(territoryColor(shape.city.id),pin,Offset(box.left,box.center.y),1.dp.toPx())
                    drawRoundRect(if(dark) Color(0xFF22394E) else Color.White,Offset(box.left,box.top),Size(box.width,box.height),CornerRadius(8.dp.toPx()))
                    drawContext.canvas.nativeCanvas.drawText(shape.city.name,box.left+padding,box.top+padding-labelPaint.ascent(),labelPaint)
                    labelHits += box to shape.city.id
                }
            }
        }
        if (selected != null) Surface(Modifier.align(Alignment.TopStart).padding(14.dp),
            shape = RoundedCornerShape(12.dp), color = SurfaceColor.copy(alpha = .94f)) {
            Column(Modifier.padding(horizontal = 12.dp, vertical = 9.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(selected.name, color = Ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Text(if (selected.id in visitedIds) "방문한 지역" else "아직 방문하지 않은 지역", color = Secondary, fontSize = 12.sp)
            }
        }
        if(selectedId!=null && interactive) Surface(Modifier.align(Alignment.TopEnd).padding(14.dp),shape=RoundedCornerShape(12.dp),color=SurfaceColor.copy(alpha=.96f)) {
            TextButton({onSelect(null)}) { Text("전체 지도",color=Blue,fontSize=13.sp) }
        }
        Row(Modifier.align(Alignment.BottomStart).padding(14.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp)) {
            Box(Modifier.size(8.dp).background(AccentOrange, CircleShape))
            Text("방문한 도시·군", color = Ink, fontSize = 12.sp)
        }
    }
}
