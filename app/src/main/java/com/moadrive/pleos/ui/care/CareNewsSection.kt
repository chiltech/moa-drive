package com.moadrive.pleos.ui.care

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moadrive.pleos.care.news.*
import com.moadrive.pleos.ui.*
import com.moadrive.pleos.ui.common.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

// A screen-entry cache, never a background poller. It carries publication and fetch times.
private data class CareNewsCacheEntry(val items: List<CareNewsItem>, val fetchedAt: Long)
private val careNewsCache = mutableMapOf<CareNewsKind, CareNewsCacheEntry>()
enum class CareNewsTopic { CHILD, PET }

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CareNewsSection(topic: CareNewsTopic, enabled: Boolean = true) {
    val kind = if (topic == CareNewsTopic.CHILD) CareNewsKind.CHILD else CareNewsKind.PET
    val scope = rememberCoroutineScope()
    var cache by remember(topic) { mutableStateOf(careNewsCache[kind]) }
    var loading by remember(topic) { mutableStateOf(false) }
    var failure by remember(topic) { mutableStateOf<String?>(null) }
    var selected by remember(topic) { mutableStateOf<CareNewsItem?>(null) }
    var portalOpen by remember(topic) { mutableStateOf(false) }
    suspend fun refresh() {
        if (loading) return
        loading = true
        failure = null
        try {
            val items = fetchCareNews(kind)
            cache = CareNewsCacheEntry(items, System.currentTimeMillis()).also { careNewsCache[kind] = it }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            failure = "공식 소식을 불러오지 못했어요. 잠시 후 다시 확인해 주세요."
        } finally { loading = false }
    }
    LaunchedEffect(topic) {
        if (cache == null || System.currentTimeMillis() - cache!!.fetchedAt >= 15 * 60_000L) refresh()
    }
    LaunchedEffect(enabled) {
        if (!enabled) {
            selected = null
            portalOpen = false
        }
    }
    DetailCard {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val compact = maxWidth < 420.dp
            val refreshButton: @Composable () -> Unit = {
                TextButton(onClick = { scope.launch { refresh() } }, enabled = enabled && !loading,
                    modifier = Modifier.heightIn(min = 48.dp), contentPadding = PaddingValues(horizontal = 8.dp)) {
                    Text(if (loading) "확인 중" else "새로고침", fontSize = 13.sp)
                }
            }
            Column(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CareSectionIcon(CareSectionSymbol.NEWS)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(if (topic == CareNewsTopic.CHILD) "아이와 가족의 소식" else "반려 가족의 소식",
                            color = Ink, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                        Text("공식 발표를 핵심 제목으로 간추렸어요", color = Secondary, fontSize = 13.sp)
                    }
                    if (!compact) refreshButton()
                }
                if (compact) Box(Modifier.align(Alignment.End)) { refreshButton() }
            }
        }
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth(), color = Blue, trackColor = PaleBlue)
        failure?.let { Text(it, color = OrangeInk, fontSize = 13.sp, lineHeight = 20.sp) }
        val articles = cache?.items.orEmpty().take(4)
        if (!loading && articles.isEmpty()) {
            Text(if (failure == null) "새 소식이 아직 없어요" else "공식 누리집에서 바로 확인할 수 있어요",
                color = Ink, fontSize = 17.sp, fontWeight = FontWeight.Medium)
            Text(if (topic == CareNewsTopic.CHILD) "교육·돌봄·학교생활과 관련된 발표를 모아요."
                else "반려동물의 행사·건강·정책 소식을 모아요.", color = Secondary, fontSize = 14.sp, lineHeight = 21.sp)
        }
        articles.forEachIndexed { index, item ->
            if (index > 0) HorizontalDivider(color = Line.copy(alpha = .5f))
            TextButton(onClick = { selected = item }, enabled = enabled,
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 12.dp),
                modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(item.category, Modifier.background(PaleBlue, RoundedCornerShape(5.dp)).padding(horizontal = 7.dp, vertical = 3.dp),
                            color = Blue, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                        Text(item.publishedDate, Modifier.align(Alignment.CenterVertically), color = Secondary, fontSize = 12.sp)
                    }
                    Text(item.title, color = Ink, fontSize = 16.sp, lineHeight = 23.sp, fontWeight = FontWeight.Medium)
                    Text(item.source, color = Secondary, fontSize = 12.sp)
                }
                Text("›", color = Blue, fontSize = 24.sp, modifier = Modifier.padding(start = 12.dp))
            }
        }
        cache?.let { entry ->
            val time = DateTimeFormatter.ofPattern("M/d HH:mm").withZone(ZoneId.of("Asia/Seoul")).format(Instant.ofEpochMilli(entry.fetchedAt))
            Text("${if (failure != null) "이전 조회 · " else ""}$time 확인 · 출처별 공개 발표", color = Secondary, fontSize = 12.sp)
        }
        TextButton(onClick = { portalOpen = true }, enabled = enabled,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("공식 소식 더 보기", fontSize = 14.sp) }
    }
    selected?.takeIf { enabled }?.let { item ->
        CareNewsWebDialog(item.title, item.source, item.publishedDate, item.url,
            preferDocumentPreview = topic == CareNewsTopic.CHILD, onDismiss = { selected = null })
    }
    if (portalOpen && enabled) {
        CareNewsWebDialog(
            title = "공식 소식 더 보기",
            source = if (topic == CareNewsTopic.CHILD) "교육부" else "농림축산식품부",
            publishedDate = "",
            url = careNewsPortal(kind),
            onDismiss = { portalOpen = false },
        )
    }
}
