package com.moadrive.pleos.ui.passport

import android.app.Application
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import com.moadrive.pleos.ui.common.SoftSurface as Surface
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.kakaomobility.knsdk.KNSDK
import com.moadrive.pleos.navigation.NavigationDestination
import com.moadrive.pleos.navigation.kakao.KakaoNavigationProvider
import com.moadrive.pleos.passport.*
import com.moadrive.pleos.trips.TripRecord
import com.moadrive.pleos.ui.*
import com.moadrive.pleos.ui.common.DimmedDialog
import com.moadrive.pleos.ui.common.rememberDialogAction
import com.moadrive.pleos.ui.common.JourneyArt
import com.moadrive.pleos.ui.common.DetailPaneLayout
import com.moadrive.pleos.ui.common.InfoReveal
import com.moadrive.pleos.ui.common.LocalInfoRevealState
import com.moadrive.pleos.ui.common.rememberDetailPane
import com.moadrive.pleos.ui.common.rememberInfoRevealState
import com.moadrive.pleos.ui.common.JourneyArtwork
import com.moadrive.pleos.ui.common.SampleDataHeader
import com.moadrive.pleos.ui.drive.ServiceRail
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.coroutines.resume

private val PassportSurface: Color @Composable get() = CardColor
private val PassportDate = DateTimeFormatter.ofPattern("M월 d일", Locale.KOREAN)
private val PassportTripDate = DateTimeFormatter.ofPattern("M.d HH:mm", Locale.KOREAN)
private data class Stamp(val cell: PassportCell, val visits: List<PassportVisit>, val number: Int, val name: String) {
    val firstAt get() = visits.minOf { it.visitedAtMs }
    val lastAt get() = visits.maxOf { it.visitedAtMs }
    val count get() = visits.distinctBy { it.tripId }.size
}

/** Journey Atlas tablet composition with a separate native map pane and Pleos rail. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PassportScreen(
    visits: List<PassportVisit>,
    trips: List<TripRecord>,
    current: TripRecord?,
    recordingEnabled: Boolean,
    locationActive: Boolean,
    statusMessage: String,
    browsingAllowed: Boolean,
    onEnableLocation: () -> Unit,
    onHome: () -> Unit,
    onService: (HomeService) -> Unit,
    onCellNamed: (String, String) -> Unit = { _, _ -> },
    onRecommendCity: (String) -> Unit = {},
    onRecommendPlace: (NavigationDestination) -> Unit = {},
    navigationAllowed: Boolean = browsingAllowed,
) {
    val revealState = rememberInfoRevealState()
    var preview by rememberSaveable { mutableStateOf(false) }
    val previewData = remember { passportPreviewData() }
    val displayedVisits = if (preview) previewData.visits else visits
    val displayedTrips = if (preview) previewData.trips else trips
    val displayedCurrent = if (preview) null else current
    var selectedTripId by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedCellId by rememberSaveable { mutableStateOf<String?>(null) }
    var territory by rememberSaveable { mutableStateOf(true) }
    var selectedCityId by rememberSaveable { mutableStateOf<String?>(null) }
    var stampPressSequence by remember { mutableIntStateOf(0) }
    val mapRequester = remember { BringIntoViewRequester() }
    var playedStampSequence by remember { mutableIntStateOf(0) }
    var animatedStampKey by remember { mutableStateOf<String?>(null) }
    val stampImpression = remember { Animatable(1f) }
    // Visit updates never trigger this effect. Only a parked user's new selection does.
    LaunchedEffect(stampPressSequence, browsingAllowed) {
        val play = stampPressSequence != playedStampSequence
        playedStampSequence = stampPressSequence
        if (browsingAllowed && play) {
            stampImpression.snapTo(0f)
            stampImpression.animateTo(1f, tween(260, easing = FastOutSlowInEasing))
        } else stampImpression.snapTo(1f)
    }
    fun selectCity(id: String?) {
        if (!browsingAllowed) return
        if (id != null && id != selectedCityId) {
            animatedStampKey = "city:$id"
            stampPressSequence++
        }
        selectedCityId = id
    }
    fun selectCell(id: String?) {
        if (!browsingAllowed) return
        if (id != null && id != selectedCellId) {
            animatedStampKey = "cell:$id"
            stampPressSequence++
        }
        selectedCellId = id
    }
    val impression = if (browsingAllowed) stampImpression.value else 1f
    val cityImpression = if (animatedStampKey == "city:$selectedCityId") impression else 1f
    val cellImpression = if (animatedStampKey == "cell:$selectedCellId") impression else 1f
    var sourceOpen by remember { mutableStateOf(false) }
    var tripMenuOpen by remember { mutableStateOf(false) }
    val tripChoices = remember(displayedTrips, displayedCurrent) { (listOfNotNull(displayedCurrent) + displayedTrips).distinctBy { it.id }.sortedByDescending { it.startedAtMs } }
    val linkedTripCount = remember(displayedVisits) { displayedVisits.distinctBy { it.tripId }.size }
    val groups = remember(displayedVisits) { displayedVisits.distinctBy { it.tripId to it.cell.id }.groupBy { it.cell.id } }
    val numbers = remember(groups) { groups.values.sortedBy { list -> list.minOf { it.visitedAtMs } }.mapIndexed { index, list -> list.first().cell.id to index + 1 }.toMap() }
    val shown = remember(displayedVisits, selectedTripId, groups, numbers) {
        displayedVisits.filter { selectedTripId == null || it.tripId == selectedTripId }
            .distinctBy { it.tripId to it.cell.id }.groupBy { it.cell.id }.map { (id, found) ->
                Stamp(found.first().cell, found, numbers.getValue(id),
                    groups[id].orEmpty().firstNotNullOfOrNull { it.placeName?.takeIf(String::isNotBlank) } ?: found.first().cell.title)
            }.sortedBy { it.number }
    }
    val selected = shown.firstOrNull { it.cell.id == selectedCellId }
    val chosenTrip = tripChoices.firstOrNull { it.id == selectedTripId }
    LaunchedEffect(shown) { if (selectedCellId != null && selected == null) selectedCellId = null }
    LaunchedEffect(browsingAllowed) {
        if (!browsingAllowed) {
            tripMenuOpen = false
            preview = false
            selectedTripId = null
            selectedCellId = null
            selectedCityId = null
        }
    }
    BackHandler(enabled = !tripMenuOpen && !sourceOpen) {
        if (territory && selectedCityId != null) selectedCityId = null
        else if (!territory && selectedCellId != null) selectedCellId = null else onHome()
    }

    val context = LocalContext.current
    var atlas by remember { mutableStateOf<PassportCityAtlas?>(null) }
    var atlasError by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(context) {
        try { atlas = PassportCityAssets.load(context) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { atlasError = "도시 경계를 불러오지 못했어요. 스탬프 기록은 그대로 보관되어 있어요." }
    }
    val allCityVisits by produceState(emptyList<PassportCityVisit>(), atlas, displayedVisits) {
        value = withContext(Dispatchers.Default) { atlas?.visited(displayedVisits).orEmpty() }
    }
    val cityVisits = remember(allCityVisits, selectedTripId) {
        allCityVisits.mapNotNull { found ->
            val filtered = found.visits.filter { selectedTripId == null || it.tripId == selectedTripId }
            filtered.takeIf { it.isNotEmpty() }?.let { PassportCityVisit(found.city, it) }
        }
    }
    val recommendations by produceState(emptyList<PassportCity>(), atlas, displayedVisits) {
        value = withContext(Dispatchers.Default) { atlas?.recommend(displayedVisits).orEmpty() }
    }
    val selectedCity = atlas?.cities?.firstOrNull { it.id == selectedCityId }
    val selectedCityVisit = cityVisits.firstOrNull { it.city.id == selectedCityId }
    LaunchedEffect(browsingAllowed) { if (!browsingAllowed) sourceOpen = false }
    val provider = remember { KakaoNavigationProvider(context.applicationContext as Application) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var visible by remember { mutableStateOf(false) }
    val placeRecommender = remember(context) {
        PassportPlaceRecommender(KakaoNavigationProvider(context.applicationContext as Application))
    }
    val placeResults = remember { mutableStateMapOf<String, PassportCityPlaces>() }
    val loadingPlaces = remember { mutableStateMapOf<String, Boolean>() }
    val placeRefresh = remember { mutableStateMapOf<String, Int>() }
    recommendations.forEach { city ->
        key(city.id) {
            var handledRefresh by remember { mutableIntStateOf(0) }
            val refreshTicket = placeRefresh[city.id] ?: 0
            LaunchedEffect(city.id, visible, browsingAllowed, refreshTicket) {
                if (!visible || !browsingAllowed) return@LaunchedEffect
                loadingPlaces[city.id] = true
                try {
                    val refresh = refreshTicket != handledRefresh
                    handledRefresh = refreshTicket
                    placeResults[city.id] = placeRecommender.load(city, refresh)
                } finally { loadingPlaces[city.id] = false }
            }
        }
    }
    var mapReady by remember { mutableStateOf(false) }
    var mapLoading by remember { mutableStateOf(false) }
    var mapError by remember { mutableStateOf<String?>(null) }
    var retry by remember { mutableIntStateOf(0) }
    val latestCellNamed by rememberUpdatedState(onCellNamed)
    val attemptedNames = remember { mutableSetOf<String>() }
    DisposableEffect(provider, lifecycle, territory) {
        fun update() {
            // A visible split pane may be STARTED while another app owns input focus.
            visible = lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
            provider.foreground(visible && !territory)
        }
        val observer = LifecycleEventObserver { _, _ -> update() }
        lifecycle.addObserver(observer); update()
        onDispose { lifecycle.removeObserver(observer); provider.foreground(false) }
    }
    LaunchedEffect(provider, visible, retry, territory) {
        if (!visible || territory) return@LaunchedEffect
        mapLoading = true; mapError = null
        try { provider.initialize(); mapReady = true }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { mapReady = false; mapError = "카카오 지도를 연결하지 못했어요. 방문 기록은 보관되어 있어요." }
        finally { mapLoading = false }
    }
    LaunchedEffect(mapReady, visible, groups, territory, preview) {
        if (!mapReady || !visible || territory || preview) return@LaunchedEffect
        groups.values.sortedByDescending { it.maxOf { visit -> visit.visitedAtMs } }.forEach { group ->
            val cell = group.first().cell
            if (group.any { !it.placeName.isNullOrBlank() } || !attemptedNames.add(cell.id)) return@forEach
            val name = try {
                withTimeoutOrNull(3_000) {
                    suspendCancellableCoroutine<String?> { continuation ->
                        try {
                            KNSDK.reverseGeocodeWithPos(KNSDK.convertWGS84ToKATEC(cell.centerLongitude, cell.centerLatitude), true) { error, _, province, district, neighborhood ->
                                if (continuation.isActive) continuation.resume(if (error == null)
                                    listOfNotNull(province, district, neighborhood).map(String::trim).filter(String::isNotEmpty).distinct().joinToString(" ").takeIf(String::isNotBlank) else null)
                            }
                        } catch (_: Exception) { if (continuation.isActive) continuation.resume(null) }
                    }
                }
            } catch (cancelled: CancellationException) {
                // A new persisted visit/name restarts this effect; retry an unfinished cell.
                attemptedNames.remove(cell.id)
                throw cancelled
            }
            if (name != null) latestCellNamed(cell.id, name)
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(BackgroundColor).windowInsetsPadding(WindowInsets.safeDrawing)) {
        val railWidth = (maxWidth * .06f).coerceIn(60.dp, 72.dp)
        val expanded = DetailPaneLayout.isExpanded(maxWidth, LocalDensity.current.fontScale)
        val pagePadding = DetailPaneLayout.pagePadding(expanded)
        LaunchedEffect(stampPressSequence) {
            if (stampPressSequence > 0 && !expanded && browsingAllowed) {
                withFrameNanos { }
                mapRequester.bringIntoView()
            }
        }
        Row(Modifier.fillMaxSize()) {
            BoxWithConstraints(Modifier.weight(1f).fillMaxHeight().padding(pagePadding)) {
                val leftWidth = (maxWidth - DetailPaneLayout.Gap) * DetailPaneLayout.LeftWeight
                val artHeight = if (expanded) minOf((maxHeight * .42f).coerceIn(185.dp, 325.dp), leftWidth * .87f)
                    else (maxWidth * .32f).coerceIn(120.dp, 160.dp)
                val currentAreas = displayedCurrent?.let { trip -> displayedVisits.filter { it.tripId == trip.id }.distinctBy { it.cell.id }.size } ?: 0
                val left = rememberDetailPane { modifier ->
                    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        PassportHero(if (territory) allCityVisits.size else groups.size, linkedTripCount, artHeight,
                            if (territory) "방문한 도시·군" else "탐험 구역")
                        PassportRecordingStatus(
                            recordingEnabled, locationActive, if (preview) "샘플 여정 6회 · 실제 방문 기록은 그대로 유지돼요" else statusMessage,
                            displayedCurrent?.let { currentAreas },
                            browsingAllowed, chosenTrip, onFilter = { tripMenuOpen = true }, onEnableLocation = onEnableLocation,
                            preview = preview,
                        )
                        if (browsingAllowed) {
                            PassportCard {
                                Text(if (territory) "도시 컬렉션" else "나의 스탬프", color = Ink, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                                if (territory) {
                                    if (cityVisits.isEmpty()) {
                                        Text(if (selectedTripId == null) "첫 도시를 기다리고 있어요" else "이 주행에 확인된 도시가 없어요",
                                            color = Ink, fontSize = 16.sp)
                                        Text("실제 주행에서 방문한 도시와 군을 하나씩 채워요.", color = Secondary, fontSize = 13.sp, lineHeight = 20.sp)
                                    } else {
                                        CityCollection(cityVisits.take(3), selectedCityId, cityImpression) { id ->
                                            selectCity(if (selectedCityId == id) null else id)
                                        }
                                    }
                                } else {
                                    if (shown.isEmpty()) {
                                        Text(if (selectedTripId == null) "첫 스탬프를 기다리고 있어요" else "이 주행에 저장된 위치가 없어요", color = Ink, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                                        Text("위치가 확인된 주행에서 지나간 구역이 하나씩 쌓여요. 이전 기록의 위치는 새로 만들어 넣지 않아요.", color = Secondary, fontSize = 13.sp, lineHeight = 21.sp)
                                    }
                                    BoxWithConstraints(Modifier.fillMaxWidth()) {
                                        if (maxWidth >= 340.dp && LocalDensity.current.fontScale <= 1.2f) {
                                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                                shown.take(2).forEach { stamp ->
                                                    StampRow(stamp, stamp.cell.id == selectedCellId, Modifier.weight(1f), embedded = true, impression = cellImpression) {
                                                        selectCell(if (selectedCellId == stamp.cell.id) null else stamp.cell.id)
                                                    }
                                                }
                                            }
                                        } else Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                            shown.take(2).forEach { stamp ->
                                                    StampRow(stamp, stamp.cell.id == selectedCellId, embedded = true, impression = cellImpression) {
                                                        selectCell(if (selectedCellId == stamp.cell.id) null else stamp.cell.id)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            (if (territory) emptyList() else shown.drop(2)).forEach { stamp ->
                                StampRow(stamp, stamp.cell.id == selectedCellId, impression = cellImpression) { selectCell(if (selectedCellId == stamp.cell.id) null else stamp.cell.id) }
                            }
                            if (territory) cityVisits.drop(3).forEach { found ->
                                CityVisitRow(found, selectedCityId == found.city.id, cityImpression) { selectCity(if (selectedCityId == found.city.id) null else found.city.id) }
                            }
                            Text(if (territory) "기존 약 1km 위치 기록의 중심점으로 방문 도시를 판정해요."
                                else "탐험 구역은 약 1km의 위치 격자예요. 행정구역 경계와는 달라요.",
                                color = Secondary, fontSize = 13.sp, lineHeight = 19.sp)
                        }
                    }
                }
                val mapHeight = if (expanded) (maxHeight * .58f).coerceIn(260.dp, 460.dp)
                    else (maxWidth * .62f).coerceIn(280.dp, 400.dp)
                val right = rememberDetailPane { modifier ->
                    CompositionLocalProvider(LocalInfoRevealState provides revealState) {
                        Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            InfoReveal(0, Modifier.fillMaxWidth()) {
                                SampleDataHeader("나의 드라이브 지도",
                                    if (territory) "${cityVisits.size}개 도시·군을 채웠어요" else "${shown.size}개 탐험 구역",
                                    preview = preview, enabled = browsingAllowed,
                                    onToggle = {
                                        if (browsingAllowed) {
                                            preview = !preview
                                            selectedTripId = null
                                            selectedCellId = null
                                            selectedCityId = null
                                            tripMenuOpen = false
                                            animatedStampKey = null
                                        }
                                    }, previewDescription = "샘플 여정 6회 · 도시와 스탬프를 둘러보세요")
                            }
                            if (browsingAllowed) InfoReveal(1, Modifier.fillMaxWidth()) {
                                Surface(shape=RoundedCornerShape(50),color=CardColor) {
                                    Row(Modifier.selectableGroup().padding(4.dp),horizontalArrangement=Arrangement.spacedBy(4.dp)) {
                                        ModePill("도시 영역",territory,Modifier.weight(1f)) { territory=true }
                                        ModePill("스탬프",!territory,Modifier.weight(1f)) { territory=false }
                                    }
                                }
                            }
                            // Keep the native stamp map's SurfaceView outside graphics-layer motion.
                            run {
                                Box(Modifier.height(mapHeight).fillMaxWidth().bringIntoViewRequester(mapRequester).clip(RoundedCornerShape(24.dp)).background(PassportSurface)) {
                                    if(territory) {
                                        val boundaries=atlas
                                        if(boundaries!=null) PassportTerritoryMap(boundaries,cityVisits.map { it.city.id }.toSet(),selectedCityId,
                                            browsingAllowed,Modifier.fillMaxSize()) { selectCity(it) }
                                        else Column(Modifier.align(Alignment.Center).padding(24.dp),horizontalAlignment=Alignment.CenterHorizontally,
                                            verticalArrangement=Arrangement.spacedBy(12.dp)) {
                                            if(atlasError==null) CircularProgressIndicator(Modifier.size(30.dp),color=Blue)
                                            Text(atlasError ?: "도시 지도를 준비하고 있어요",color=Secondary,fontSize=15.sp)
                                        }
                                    } else {
                                        if(mapReady && visible) key(retry) {
                                            PassportMap(shown.map { it.cell },numbers,selectedCellId,false,browsingAllowed,
                                                Modifier.fillMaxSize(),onError={mapError=it},onReady={mapError=null})
                                        }
                                        if(!mapReady || mapError!=null) Column(Modifier.align(Alignment.Center).padding(24.dp),
                                            horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(14.dp)) {
                                            if(mapLoading) CircularProgressIndicator(Modifier.size(30.dp),color=Blue)
                                            Text(if(mapLoading) "방문 지도를 준비하고 있어요" else mapError ?: "지도 연결 대기",color=Secondary,fontSize=15.sp)
                                            if(!mapLoading && browsingAllowed) Button({retry++}) { Text("지도 다시 연결") }
                                        }
                                    }
                                }
                            }
                            if(territory) {
                                InfoReveal(2, Modifier.fillMaxWidth()) {
                                    Row(verticalAlignment=Alignment.CenterVertically) {
                                        Text(PassportCityAssets.SOURCE_LABEL, Modifier.weight(1f), color = Secondary, fontSize = 12.sp)
                                        if(browsingAllowed) TextButton({sourceOpen=true}) { Text("출처",fontSize=12.sp) }
                                    }
                                }
                                if(selectedCity!=null) InfoReveal(3, Modifier.fillMaxWidth()) {
                                    SelectedCityStamp(selectedCity, selectedCityVisit, selectedTripId != null, cityImpression)
                                }
                                if(browsingAllowed && atlas!=null) InfoReveal(4, Modifier.fillMaxWidth()) {
                                    CityRecommendations(recommendations, placeResults, loadingPlaces,
                                        onSelect = { selectCity(it.id) }, onNavigate = onRecommendPlace,
                                        onRetry = { city -> placeRefresh[city.id] = (placeRefresh[city.id] ?: 0) + 1 },
                                        navigationAllowed = navigationAllowed)
                                }
                            } else {
                                if (selected != null) InfoReveal(3, Modifier.fillMaxWidth()) {
                                    SelectedStamp(selected, browsingAllowed, cellImpression) { selectCell(null) }
                                }
                                InfoReveal(4, Modifier.fillMaxWidth()) {
                                    Text(if (preview) "샘플 위치로 스탬프를 둘러보세요. 실제 기록에는 저장하지 않아요."
                                        else "번호 스탬프로 실제 위치 기록을 확인해요.",color=Secondary,fontSize=12.sp)
                                }
                            }
                        }
                    }
                }
                if (expanded) Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(DetailPaneLayout.Gap)) {
                    left(Modifier.weight(DetailPaneLayout.LeftWeight).fillMaxHeight().verticalScroll(rememberScrollState(), enabled = browsingAllowed))
                    right(Modifier.weight(DetailPaneLayout.RightWeight).fillMaxHeight().verticalScroll(rememberScrollState(), enabled = browsingAllowed))
                } else Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState(), enabled = browsingAllowed), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    left(Modifier.fillMaxWidth())
                    right(Modifier.fillMaxWidth())
                }
            }
            ServiceRail(railWidth, browsingAllowed, onHome, onService, HomeService.PASSPORT)
        }
    }
    if (sourceOpen && browsingAllowed) DimmedDialog(onDismissRequest={sourceOpen=false}) {
        val close = rememberDialogAction { sourceOpen = false }
        Surface(Modifier.widthIn(max=580.dp).fillMaxWidth(.92f),shape=RoundedCornerShape(24.dp),color=SurfaceColor) {
            Column(Modifier.padding(24.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(14.dp)) {
                Text("지도 데이터 출처",color=Ink,fontSize=22.sp,fontWeight=FontWeight.SemiBold)
                Text("행정경계: 국가공간정보포털(V-World), 2023년 7월 29일 자료. 좌표 변환·단순화: KnellBalm/kr-admin-geojson. 출처 표시 조건으로 자유 이용 가능한 자료예요.",color=Secondary,fontSize=15.sp,lineHeight=23.sp)
                Text("서울·광역시·세종은 소속 구·군을 포함한 도시 전체로, 일반 시는 구를 합친 도시 단위로 표시하고, 그 밖의 군은 별도로 표시해요. 기존 약 1km 격자의 중심점이 경계 안에 있을 때 방문으로 판정하므로 경계 부근에는 오차가 있을 수 있어요. 판정되지 않은 기록은 그대로 보관해요.",color=Secondary,fontSize=14.sp,lineHeight=22.sp)
                Text(PassportCityAssets.SOURCE_URL,color=Blue,fontSize=12.sp)
                Button(close,Modifier.align(Alignment.End)) { Text("확인") }
            }
        }
    }
    if (tripMenuOpen && browsingAllowed) {
        TripFilterDialog(
            trips = tripChoices,
            selectedTripId = selectedTripId,
            onDismiss = { tripMenuOpen = false },
            onSelect = { id ->
                if (browsingAllowed) {
                    selectedTripId = id
                    selectedCellId = null
                    selectedCityId = null
                    tripMenuOpen = false
                }
            },
        )
    }
}

@Composable
private fun TripFilterDialog(
    trips: List<TripRecord>,
    selectedTripId: String?,
    onDismiss: () -> Unit,
    onSelect: (String?) -> Unit,
) {
    DimmedDialog(onDismissRequest = onDismiss) {
        val close = rememberDialogAction(onDismiss)
        val selectAll = rememberDialogAction { onSelect(null) }
        Surface(
            modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth(.92f).heightIn(max = 620.dp),
            shape = RoundedCornerShape(24.dp),
            color = SurfaceColor,
            border = BorderStroke(.5.dp, Line),
        ) {
            Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("주행 선택", Modifier.weight(1f), color = Ink, fontSize = 22.sp, fontWeight = FontWeight.Medium)
                    TextButton(close, Modifier.heightIn(min = 48.dp)) { Text("닫기", fontSize = 16.sp) }
                }
                Text("지도에서 확인할 주행을 선택하세요.", color = Secondary, fontSize = 15.sp, lineHeight = 22.sp)
                LazyColumn(
                    Modifier.weight(1f, fill = false).fillMaxWidth().selectableGroup(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item(key = "all-trips") {
                        TripFilterOption("모든 주행", selectedTripId == null, onSelect = selectAll)
                    }
                    items(trips, key = { "trip-${it.id}" }) { trip ->
                        val selectTrip = rememberDialogAction { onSelect(trip.id) }
                        TripFilterOption(tripLabel(trip), selectedTripId == trip.id, onSelect = selectTrip)
                    }
                }
            }
        }
    }
}

@Composable
private fun TripFilterOption(label: String, selected: Boolean, onSelect: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect),
        shape = RoundedCornerShape(14.dp),
        color = if (selected) PaleBlue else SurfaceColor,
        border = if (selected) BorderStroke(1.dp, Blue) else null,
    ) {
        Row(Modifier.heightIn(min = 60.dp).padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            RadioButton(selected = selected, onClick = null,
                colors = RadioButtonDefaults.colors(selectedColor = Blue, unselectedColor = Secondary))
            Text(label, Modifier.weight(1f), color = if (selected) Blue else Ink, fontSize = 16.sp,
                lineHeight = 24.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
        }
    }
}

@Composable
private fun PassportHero(areas: Int, linkedTrips: Int, artHeight: androidx.compose.ui.unit.Dp, areaLabel: String) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("드라이브 패스포트", color = Ink, fontSize = 30.sp, lineHeight = 38.sp, fontWeight = FontWeight.SemiBold)
        JourneyArtwork(JourneyArt.PASSPORT, Modifier.fillMaxWidth().height(artHeight))
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(areaLabel, color = Secondary, fontSize = 15.sp)
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(areas.toString(), color = Ink, fontSize = 40.sp, lineHeight = 46.sp, fontWeight = FontWeight.SemiBold)
                Text("곳", Modifier.padding(bottom = 4.dp), color = Ink, fontSize = 18.sp)
            }
            if (linkedTrips > 0) Text("연결된 주행 ${linkedTrips}회", color = Secondary, fontSize = 13.sp)
        }
    }
}

@Composable
private fun PassportRecordingStatus(
    recordingEnabled: Boolean,
    locationActive: Boolean,
    statusMessage: String,
    currentAreas: Int?,
    browsingAllowed: Boolean,
    chosenTrip: TripRecord?,
    onFilter: () -> Unit,
    onEnableLocation: () -> Unit,
    preview: Boolean = false,
) {
    val recording = !preview && locationActive && recordingEnabled
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val availableWidth = maxWidth
            val indicator: @Composable () -> Unit = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    Box(Modifier.width(12.dp).height(6.dp).background(if (recording) AccentGreen else Secondary, CircleShape))
                    Text(when {
                        preview -> "샘플 방문 기록"
                        !recordingEnabled -> "자동 기록 꺼짐"
                        locationActive -> "기록 중"
                        else -> "위치 연결 대기"
                    }, color = Ink, fontSize = 14.sp)
                }
            }
            val filter: @Composable (Modifier) -> Unit = { modifier ->
                TextButton(onFilter, modifier.heightIn(min = 48.dp)
                    .semantics { contentDescription = "주행별 방문 필터" },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)) {
                    Text(chosenTrip?.let(::tripLabel) ?: "모든 주행", Modifier.weight(1f, fill = false),
                        color = if (chosenTrip == null) Ink else Blue,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 14.sp)
                    Text("⌄", Modifier.padding(start = 6.dp), color = Secondary, fontSize = 15.sp)
                }
            }
            if (availableWidth >= 280.dp && LocalDensity.current.fontScale <= 1.2f) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween) {
                    indicator()
                    if (browsingAllowed) filter(Modifier.widthIn(max = availableWidth * .59f))
                }
            } else Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                indicator()
                if (browsingAllowed) filter(Modifier.fillMaxWidth())
            }
        }
        currentAreas?.let { Text("이번 주행에서 ${it}개 구역을 기록했어요", color = Ink, fontSize = 13.sp) }
        if (statusMessage.isNotBlank()) Text(statusMessage, color = Secondary, fontSize = 13.sp, lineHeight = 19.sp)
        if (browsingAllowed && !preview && !locationActive && recordingEnabled) {
            OutlinedButton(onEnableLocation, Modifier.fillMaxWidth().heightIn(min = 48.dp), shape = CircleShape) {
                Text("위치 기록 연결", fontSize = 15.sp)
            }
        }
        if (!browsingAllowed) Text("방문 기록 조회는 주차 후 이용하세요.", color = Secondary, fontSize = 14.sp, lineHeight = 21.sp)
    }
}

@Composable
private fun CityCollection(cities: List<PassportCityVisit>, selectedId: String?, impression: Float, onSelect: (String) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val wide = maxWidth >= 276.dp && LocalDensity.current.fontScale <= 1.2f
        val inline = maxWidth >= 395.dp
        if (wide) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            cities.forEach { found ->
                val selected = found.city.id == selectedId
                val ink = passportStampInk(found.city.id)
                val seal: @Composable () -> Unit = {
                    PassportStampSeal(found.city.id, found.city.name, found.firstAt, ink,
                        impression = if (selected) impression else 1f, compact = true)
                }
                val name: @Composable () -> Unit = {
                    Text(found.city.name, color = if (selected) Blue else Ink, fontSize = 14.sp,
                        lineHeight = 19.sp, fontWeight = FontWeight.Medium)
                }
                Surface(
                    Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).selectable(
                        selected = selected, role = Role.Button, onClick = { onSelect(found.city.id) }),
                    color = if (selected) PaleBlue else CardColor,
                    shape = RoundedCornerShape(12.dp),
                ) {
                    if (inline) Row(Modifier.padding(vertical = 8.dp, horizontal = 3.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        seal()
                        name()
                    } else Column(Modifier.padding(vertical = 8.dp, horizontal = 3.dp),
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        seal()
                        name()
                    }
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                cities.forEach { found ->
                    CityVisitRow(found, selectedId == found.city.id, impression) { onSelect(found.city.id) }
                }
            }
        }
    }
}

@Composable private fun PassportCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(shape = RoundedCornerShape(20.dp), color = CardColor, shadowElevation = CardShadowElevation) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
    }
}

@Composable private fun StampRow(
    stamp: Stamp,
    selected: Boolean,
    modifier: Modifier = Modifier,
    embedded: Boolean = false,
    impression: Float = 1f,
    onSelect: () -> Unit,
) {
    val ink = passportStampInk(stamp.cell.id)
    Surface(modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).clickable(role = Role.Button, onClick = onSelect),
        shape = RoundedCornerShape(18.dp), color = if (selected) PaleBlue else SurfaceColor,
        border = if (selected) BorderStroke(1.dp, Blue) else null) {
        val seal: @Composable () -> Unit = {
            PassportStampSeal(stamp.cell.id, stamp.number.toString().padStart(2, '0'), stamp.firstAt,
                ink, impression = if (selected) impression else 1f, numbered = true)
        }
        val details: @Composable (Modifier) -> Unit = { detailsModifier ->
            Column(detailsModifier, verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(stamp.name, color = Ink, fontSize = if (embedded) 14.sp else 16.sp,
                    fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text("스탬프 ${stamp.number} · ${stamp.count}회 방문", color = ink, fontSize = 12.sp)
                Text("최근 ${date(stamp.lastAt)}", color = Secondary, fontSize = 12.sp)
            }
        }
        if (embedded) Column(Modifier.passportPerforation(ink).padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            seal()
            details(Modifier.fillMaxWidth())
        } else Row(Modifier.passportPerforation(ink).padding(12.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            seal()
            details(Modifier.weight(1f))
        }
    }
}

@Composable private fun ModePill(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(modifier.heightIn(min = 48.dp).clip(RoundedCornerShape(50))
        .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
        .semantics { contentDescription = "$label 지도" }, color = if (selected) Blue else Color.Transparent, shape = RoundedCornerShape(50)) {
        Box(Modifier.padding(horizontal = 12.dp, vertical = 12.dp), contentAlignment = Alignment.Center) {
            Text(label, color = if (selected) OnBlue else Ink, fontSize = 16.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable private fun SelectedStamp(stamp: Stamp, browsingAllowed: Boolean, impression: Float, onClear: () -> Unit) {
    val ink = passportStampInk(stamp.cell.id)
    SelectedStampCard(ink, seal = {
        PassportStampSeal(stamp.cell.id, stamp.number.toString().padStart(2, '0'), stamp.firstAt,
            ink, large = true, numbered = true, impression = impression)
    }) {
        Text("스탬프 ${stamp.number}", color = ink, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        Text(stamp.name, color = Ink, fontSize = 18.sp, fontWeight = FontWeight.Medium)
        Text("첫 방문 ${fullDate(stamp.firstAt)}", color = Secondary, fontSize = 13.sp)
        Text("최근 ${fullDate(stamp.lastAt)} · ${stamp.count}회 방문", color = Secondary, fontSize = 13.sp)
        Text(String.format(Locale.KOREAN, "탐험 구역 중심 %.3f, %.3f", stamp.cell.centerLatitude, stamp.cell.centerLongitude), color = Secondary, fontSize = 12.sp)
        if (browsingAllowed) TextButton(onClear, Modifier.heightIn(min = 48.dp)) { Text("전체 보기", fontSize = 14.sp) }
    }
}

@Composable private fun SelectedCityStamp(city: PassportCity, found: PassportCityVisit?, filtered: Boolean, impression: Float) {
    if (found == null) {
        PassportCard {
            Text(city.title, color = Ink, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Text(if (filtered) "이 주행에서는 방문하지 않은 지역이에요" else "아직 방문하지 않은 지역이에요", color = Secondary, fontSize = 13.sp)
        }
        return
    }
    val ink = passportStampInk(city.id)
    SelectedStampCard(ink, seal = {
        PassportStampSeal(city.id, city.name, found.firstAt, ink, large = true, impression = impression)
    }) {
        Text("도시 컬렉션 · 방문 기록", color = ink, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        Text(city.title, color = Ink, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        Text("첫 방문 ${fullDate(found.firstAt)}", color = Secondary, fontSize = 13.sp)
        Text("최근 ${fullDate(found.lastAt)} · ${found.tripCount}개 주행", color = Secondary, fontSize = 13.sp)
    }
}

@Composable private fun SelectedStampCard(ink: Color, seal: @Composable () -> Unit, details: @Composable ColumnScope.() -> Unit) {
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), color = SurfaceColor, shadowElevation = CardShadowElevation) {
        BoxWithConstraints(Modifier.passportPerforation(ink).padding(18.dp)) {
            if (maxWidth >= 390.dp && LocalDensity.current.fontScale <= 1.3f) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    seal()
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp), content = details)
                }
            } else Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                seal()
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(7.dp), content = details)
            }
        }
    }
}

private fun date(value: Long): String = Instant.ofEpochMilli(value).atZone(ZoneId.systemDefault()).format(PassportDate)
private fun fullDate(value: Long): String = Instant.ofEpochMilli(value).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("yyyy.M.d", Locale.KOREAN))
private fun tripLabel(trip: TripRecord): String {
    val zone = runCatching { ZoneId.of(trip.zoneId) }.getOrDefault(ZoneId.systemDefault())
    return Instant.ofEpochMilli(trip.startedAtMs).atZone(zone).format(PassportTripDate) +
        if (trip.isActive) " · 주행 중" else " · ${String.format(Locale.KOREAN, "%.1f", trip.distanceMeters / 1000.0)} km"
}

@Composable
private fun CityVisitRow(found: PassportCityVisit, selected: Boolean, impression: Float, onSelect: () -> Unit) {
    val ink = passportStampInk(found.city.id)
    Surface(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).clickable(role=Role.Button,onClick=onSelect),
        shape=RoundedCornerShape(18.dp),color=if(selected) PaleBlue else SurfaceColor,
        border = if (selected) BorderStroke(1.dp, Blue) else null) {
        Row(Modifier.passportPerforation(ink).padding(12.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            PassportStampSeal(found.city.id, found.city.name, found.firstAt, ink, impression = if (selected) impression else 1f)
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                Text(found.city.name,color=Ink,fontSize=15.sp,fontWeight=FontWeight.SemiBold,maxLines=2,overflow=TextOverflow.Ellipsis)
                Text("${found.tripCount}개 주행의 방문 기록",color=ink,fontSize=12.sp)
                Text("최근 ${date(found.lastAt)}",color=Secondary,fontSize=12.sp)
            }
        }
    }
}

@Composable
private fun CityRecommendations(
    cities: List<PassportCity>,
    results: Map<String, PassportCityPlaces>,
    loading: Map<String, Boolean>,
    onSelect: (PassportCity) -> Unit,
    onNavigate: (NavigationDestination) -> Unit,
    onRetry: (PassportCity) -> Unit,
    navigationAllowed: Boolean,
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("다음에는 이 도시로", color = Ink, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
        Text("아직 방문하지 않은 도시에서 둘러볼 곳과 먹거리를 찾아요.",
            color = Secondary, fontSize = 15.sp, lineHeight = 22.sp)
        if (!navigationAllowed) Text("길찾기는 주차 후 이용해 주세요.", color = Secondary, fontSize = 14.sp)
        if (cities.isEmpty()) Text("새로운 도시 추천을 준비하고 있어요.", color = Secondary, fontSize = 15.sp)
        cities.forEach { city ->
            val result = results[city.id]
            val isLoading = loading[city.id] ?: (result == null)
            Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp), color = CardColor,
                shadowElevation = CardShadowElevation) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("미방문 도시 · ${city.province}", color = AccentGreen, fontSize = 13.sp)
                            Text(city.name, color = Ink, fontSize = 23.sp, fontWeight = FontWeight.SemiBold)
                        }
                        TextButton({ onSelect(city) }, Modifier.heightIn(min = 48.dp)) {
                            Text("지도 보기", fontSize = 15.sp)
                        }
                    }
                    if (isLoading && result?.places.isNullOrEmpty()) {
                        Row(Modifier.fillMaxWidth().padding(vertical = 18.dp), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            CircularProgressIndicator(Modifier.size(22.dp), color = Blue, strokeWidth = 2.dp)
                            Text("관광지와 음식점을 찾고 있어요", color = Secondary, fontSize = 15.sp)
                        }
                    }
                    result?.places.orEmpty().forEach { place ->
                        RecommendedPlaceRow(city, place, navigationAllowed, onNavigate)
                    }
                    result?.message?.let { Text(it, color = Secondary, fontSize = 14.sp, lineHeight = 21.sp) }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("카카오 장소 검색 · 도시 안의 장소", Modifier.weight(1f),
                            color = Secondary, fontSize = 12.sp, lineHeight = 18.sp)
                        TextButton({ onRetry(city) }, Modifier.heightIn(min = 48.dp), enabled = !isLoading) {
                            Text(if (isLoading) "찾는 중" else "다시 찾기", fontSize = 14.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RecommendedPlaceRow(
    city: PassportCity,
    place: PassportPlaceSuggestion,
    navigationAllowed: Boolean,
    onNavigate: (NavigationDestination) -> Unit,
) {
    val destination = place.destination
    val accent = when (place.kind) {
        PassportPlaceKind.ATTRACTION -> Blue
        PassportPlaceKind.RESTAURANT -> AccentOrange
        PassportPlaceKind.PARK -> AccentGreen
    }
    Surface(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
        .clickable(enabled = navigationAllowed, role = Role.Button) { onNavigate(destination) }
        .semantics { contentDescription = "${city.name} ${destination.name} 길찾기" },
        shape = RoundedCornerShape(16.dp), color = BackgroundColor) {
        Row(Modifier.fillMaxWidth().heightIn(min = 80.dp).padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(7.dp).background(accent, CircleShape))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(place.kind.label, color = accent, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                Text(destination.name, color = Ink, fontSize = 18.sp, fontWeight = FontWeight.SemiBold,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (destination.address.isNotBlank()) Text(destination.address, color = Secondary,
                    fontSize = 13.sp, lineHeight = 19.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                MoaIcon(MoaIconKind.DRIVE, Modifier.size(22.dp), if (navigationAllowed) Blue else Secondary, outline = true)
                Text("길찾기", color = if (navigationAllowed) Blue else Secondary, fontSize = 12.sp)
            }
        }
    }
}
