package com.moadrive.pleos.ui.trips

import com.moadrive.pleos.ui.CardShadowElevation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import com.moadrive.pleos.ui.common.SoftSurface as Surface
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moadrive.pleos.ui.common.DimmedDialog
import com.moadrive.pleos.ui.common.rememberDialogAction
import com.moadrive.pleos.ui.common.JourneyArt
import com.moadrive.pleos.ui.common.DetailPaneLayout
import com.moadrive.pleos.ui.common.InfoReveal
import com.moadrive.pleos.ui.common.LocalInfoRevealState
import com.moadrive.pleos.ui.common.rememberDetailPane
import com.moadrive.pleos.ui.common.rememberInfoRevealState
import com.moadrive.pleos.ui.common.JourneyArtwork
import androidx.compose.ui.window.DialogProperties
import com.moadrive.pleos.trips.TripCostCalculator
import com.moadrive.pleos.trips.TripCostPreferences
import com.moadrive.pleos.trips.TripCostSummary
import com.moadrive.pleos.trips.TripRecord
import com.moadrive.pleos.trips.TripStatus
import com.moadrive.pleos.trips.tripSampleRecords
import com.moadrive.pleos.ui.BackgroundColor
import com.moadrive.pleos.ui.CardColor
import com.moadrive.pleos.ui.AccentGreen
import com.moadrive.pleos.ui.AccentOrange
import com.moadrive.pleos.ui.OrangeInk
import com.moadrive.pleos.ui.common.RoundedMetricBar
import com.moadrive.pleos.ui.SurfaceColor
import com.moadrive.pleos.ui.Blue
import com.moadrive.pleos.ui.IsDarkTheme
import com.moadrive.pleos.ui.OnBlue
import com.moadrive.pleos.ui.HomeService
import com.moadrive.pleos.ui.Ink
import com.moadrive.pleos.ui.Line
import com.moadrive.pleos.ui.MoaIcon
import com.moadrive.pleos.ui.MoaIconKind
import com.moadrive.pleos.ui.PaleBlue
import com.moadrive.pleos.ui.Secondary
import com.moadrive.pleos.ui.drive.ServiceRail
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val DateLabel = DateTimeFormatter.ofPattern("yyyy년 M월 d일 (E)", Locale.KOREAN)
private val ClockLabel = DateTimeFormatter.ofPattern("HH:mm", Locale.KOREAN)
private val OtherDayClockLabel = DateTimeFormatter.ofPattern("M/d HH:mm", Locale.KOREAN)
private val ReceiptSnapshotSaver = listSaver<TripReceiptSnapshot?, String>(
    save = { it?.savedState() ?: emptyList() },
    restore = { restoreReceiptSnapshot(it) },
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TripsScreen(
    records: List<TripRecord>,
    current: TripRecord?,
    recordingEnabled: Boolean,
    statusMessage: String,
    browsingAllowed: Boolean,
    onRecordingEnabled: (Boolean) -> Unit,
    onHome: () -> Unit,
    onService: (HomeService) -> Unit,
    textInputAllowed: Boolean = browsingAllowed,
) {
    val revealState = rememberInfoRevealState()
    val context = LocalContext.current
    var preview by rememberSaveable { mutableStateOf(false) }
    val sampleAnchorText = rememberSaveable { LocalDate.now().toString() }
    val sampleRecords = remember(sampleAnchorText) { tripSampleRecords(LocalDate.parse(sampleAnchorText)) }
    var liveDateBeforePreview by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    val costPreferences = remember(context) { TripCostPreferences(context) }
    var costBasis by remember { mutableStateOf(costPreferences.load()) }
    var basisOpen by rememberSaveable { mutableStateOf(false) }
    val basisEditingAllowed = browsingAllowed && textInputAllowed && !preview
    var receipt by rememberSaveable(stateSaver = ReceiptSnapshotSaver) { mutableStateOf<TripReceiptSnapshot?>(null) }
    var selectedDateText by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    var selectedTripId by rememberSaveable { mutableStateOf<String?>(null) }
    var detailRequestSequence by remember { mutableIntStateOf(0) }
    val detailRequester = remember { BringIntoViewRequester() }
    fun selectTrip(id: String?) { selectedTripId = id; detailRequestSequence++ }
    var calendarOpen by rememberSaveable { mutableStateOf(false) }
    val selectedDate = remember(selectedDateText) { LocalDate.parse(selectedDateText) }
    val sourceRecords = if (preview) sampleRecords else records
    val completed = remember(sourceRecords) {
        sourceRecords.filter { !it.isActive }
            .sortedByDescending { it.endedAtMs ?: it.startedAtMs }
            .distinctBy { it.id }
            .sortedByDescending { it.startedAtMs }
    }
    // Keep trips on the date captured at departure, including trips across midnight.
    val dayRecords = remember(completed, selectedDateText) {
        completed.filter { it.startDate == selectedDateText }
    }
    var showAllTrips by rememberSaveable(selectedDateText, preview) { mutableStateOf(false) }
    var showTripList by rememberSaveable(selectedDateText, preview) { mutableStateOf(true) }
    val dayDistance = remember(dayRecords) { TripCostCalculator.calculate(dayRecords).totalDistanceKm }
    val dayDuration = remember(dayRecords) { dayRecords.sumOf { it.durationMs.coerceAtLeast(0) } }
    val active = current?.takeIf { it.isActive && !preview }
    val selectedTrip = dayRecords.firstOrNull { it.id == selectedTripId }
    val selectedActive = active?.takeIf { it.id == selectedTripId }
    val comparisonRecords = selectedTrip?.let { listOf(it) } ?: dayRecords
    val comparison = remember(comparisonRecords, costBasis) { TripCostCalculator.calculate(comparisonRecords, costBasis) }
    val previousTrip = remember(completed, selectedTrip) {
        selectedTrip?.let { selected -> completed.firstOrNull {
            it.startedAtMs < selected.startedAtMs && TripCostCalculator.isMeasured(it)
        } }
    }
    val previousDay = remember(completed, selectedDateText) {
        completed.groupBy { it.startDate }.entries.filter { it.key < selectedDateText }
            .sortedByDescending { it.key }.firstOrNull { entry -> entry.value.any(TripCostCalculator::isMeasured) }
    }
    val previousComparison = remember(previousTrip, previousDay, selectedTrip, costBasis) {
        if (selectedTrip != null) previousTrip?.let { TripCostCalculator.calculate(listOf(it), costBasis) }
        else previousDay?.value?.let { TripCostCalculator.calculate(it, costBasis) }
    }
    val previousLabel = if (selectedTrip != null) "이전 측정 주행" else "이전 측정일 ${previousDay?.key.orEmpty()}"
    LaunchedEffect(selectedDateText) { selectedTripId = null }
    LaunchedEffect(browsingAllowed) {
        if (!browsingAllowed) {
            if (preview) { preview = false; selectedDateText = liveDateBeforePreview }
            calendarOpen = false; basisOpen = false; receipt = null
        }
    }
    LaunchedEffect(basisEditingAllowed) { if (!basisEditingAllowed) basisOpen = false }
    BackHandler {
        when {
            receipt != null -> receipt = null
            basisOpen -> basisOpen = false
            calendarOpen -> calendarOpen = false
            else -> onHome()
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(BackgroundColor).windowInsetsPadding(WindowInsets.safeDrawing)) {
        val railWidth = (maxWidth * .06f).coerceIn(60.dp, 72.dp)
        val expanded = DetailPaneLayout.isExpanded(maxWidth, LocalDensity.current.fontScale)
        val pagePadding = DetailPaneLayout.pagePadding(expanded)
        val density = LocalDensity.current
        LaunchedEffect(detailRequestSequence) {
            if (detailRequestSequence > 0 && !expanded && browsingAllowed) {
                withFrameNanos { }
                detailRequester.bringIntoView(Rect(0f, 0f, 1f, with(density) { 240.dp.toPx() }))
            }
        }
        Row(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f).fillMaxHeight()) {
                if (browsingAllowed) {
                    BoxWithConstraints(Modifier.fillMaxSize().padding(pagePadding)) {
                        val leftPaneWidth = (maxWidth - DetailPaneLayout.Gap) * DetailPaneLayout.LeftWeight
                        val artHeight = if (expanded) minOf((maxHeight * .44f).coerceIn(185.dp, 320.dp), leftPaneWidth * .87f)
                            else (maxWidth * .32f).coerceIn(120.dp, 160.dp)
                        val openReceipt: () -> Unit = {
                            if (browsingAllowed && comparisonRecords.isNotEmpty()) {
                                val snapshot = tripReceipt(comparisonRecords.toList(), costBasis,
                                    selectedDate.format(DateLabel) +
                                        if (selectedTrip != null) " · ${timeWindow(selectedTrip)}" else " · 일별 요약")
                                receipt = if (preview) snapshot.copy(
                                    title = "샘플 ${snapshot.title}",
                                    selectionLabel = "샘플 데이터 · ${snapshot.selectionLabel}",
                                    fileName = "moa-sample-trip-$selectedDateText.pdf",
                                    notes = listOf("샘플 데이터로 만든 미리보기이며 실제 주행 기록이 아닙니다.") + snapshot.notes,
                                ) else snapshot
                            }
                        }
                        val list = rememberDetailPane { modifier ->
                            Column(modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                JourneyArtwork(JourneyArt.TRIPS, Modifier.fillMaxWidth().height(artHeight))
                                DayTotals(dayDistance, dayDuration)
                                HorizontalDivider(color = Line)
                                if (preview) {
                                    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                        Text("샘플 데이터 · 실제 주행 기록이 아니에요", color = OrangeInk,
                                            fontSize = 15.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold)
                                        Text("7일간의 주행 14회를 살펴보세요. 실제 기록과 자동 기록 설정은 그대로 유지돼요.",
                                            color = Secondary, fontSize = 14.sp, lineHeight = 21.sp)
                                    }
                                } else RecordingStatus(recordingEnabled, statusMessage, onRecordingEnabled)
                            }
                        }
                        val detail = rememberDetailPane { modifier ->
                            CompositionLocalProvider(LocalInfoRevealState provides revealState) {
                                Column(modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                    InfoReveal(0, Modifier.fillMaxWidth()) {
                                        TripDashboardHeader(
                                            title = when {
                                                selectedActive != null -> "기록 중인 드라이브"
                                                selectedTrip != null -> "선택한 드라이브"
                                                selectedDate == LocalDate.now() -> "오늘의 드라이브"
                                                else -> "${selectedDate.monthValue}월 ${selectedDate.dayOfMonth}일 드라이브"
                                            },
                                            receiptEnabled = comparisonRecords.isNotEmpty() && selectedActive == null,
                                            onReceipt = openReceipt,
                                        )
                                    }
                                    InfoReveal(1, Modifier.fillMaxWidth()) {
                                        Surface(Modifier.fillMaxWidth().bringIntoViewRequester(detailRequester),
                                            shape = RoundedCornerShape(26.dp), color = CardColor,
                                            shadowElevation = CardShadowElevation) {
                                            Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                                                TripDashboardMetrics(comparisonRecords, comparison, selectedActive ?: selectedTrip)
                                                HorizontalDivider(color = Line)
                                                if (selectedActive == null) {
                                                    TripCostInsight(comparison, previousComparison, previousLabel)
                                                } else {
                                                    Text("주행을 마치면 비용을 비교해 드려요", color = Ink,
                                                        fontSize = 21.sp, lineHeight = 29.sp, fontWeight = FontWeight.Medium)
                                                }
                                                HorizontalDivider(color = Line)
                                                if (showTripList) {
                                                    TripListContent(dayRecords, active, selectedTripId, recordingEnabled,
                                                        active?.startDate == selectedDateText, showAllTrips,
                                                        { showAllTrips = !showAllTrips }, ::selectTrip, preview)
                                                } else {
                                                    DailyTripSummary(dayRecords, dayDistance, dayDuration, preview)
                                                }
                                                val focused = selectedActive ?: selectedTrip
                                                if (focused != null) {
                                                    HorizontalDivider(color = Line)
                                                    FocusedTripSummary(focused)
                                                } else if (!showTripList) {
                                                    dayRecords.firstOrNull()?.let { latest ->
                                                        CompactBatterySummary(latest, "가장 최근 주행의 배터리")
                                                    }
                                                }
                                                TripViewSwitch(showTripList,
                                                    onList = { showTripList = true },
                                                    onSummary = { showTripList = false; selectTrip(null) })
                                            }
                                        }
                                    }
                                    InfoReveal(2, Modifier.fillMaxWidth()) {
                                        TripCostBasisFooter(comparison, costBasis,
                                            onBasis = { if (basisEditingAllowed) basisOpen = true },
                                            basisEnabled = basisEditingAllowed)
                                    }
                                    InfoReveal(3, Modifier.fillMaxWidth()) {
                                        Text(if (preview) "샘플 기간 · ${LocalDate.parse(sampleAnchorText).minusDays(6)} ~ $sampleAnchorText"
                                            else "출발 날짜 기준 · 거리와 전비는 차량 신호로 계산한 추정값이에요.",
                                            color = Secondary, fontSize = 12.sp, lineHeight = 18.sp)
                                    }
                                }
                            }
                        }
                        val header: @Composable () -> Unit = {
                            TripsHeader(selectedDate, expanded, preview, onTogglePreview = {
                                if (!preview) {
                                    liveDateBeforePreview = selectedDateText
                                    selectedDateText = sampleAnchorText
                                } else selectedDateText = liveDateBeforePreview
                                preview = !preview
                                selectedTripId = null
                                calendarOpen = false
                                basisOpen = false
                                receipt = null
                            },
                                onPrevious = { selectedDateText = selectedDate.minusDays(1).toString() },
                                onNext = { selectedDateText = selectedDate.plusDays(1).toString() },
                                onToday = { selectedDateText = if (preview) sampleAnchorText else LocalDate.now().toString() },
                                onCalendar = { calendarOpen = true })
                        }
                        if (expanded) Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            header()
                            Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(DetailPaneLayout.Gap)) {
                                list(Modifier.weight(DetailPaneLayout.LeftWeight).fillMaxHeight().verticalScroll(rememberScrollState()))
                                detail(Modifier.weight(DetailPaneLayout.RightWeight).fillMaxHeight().verticalScroll(rememberScrollState()))
                            }
                        } else Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                            header()
                            list(Modifier.fillMaxWidth())
                            detail(Modifier.fillMaxWidth())
                        }
                    }
                } else {
                    DrivingStatus(current?.takeIf { it.isActive }, recordingEnabled, statusMessage, Modifier.padding(pagePadding))
                }
            }
            ServiceRail(railWidth, browsingAllowed, onHome, onService, selectedService = HomeService.TRIPS)
        }
    }
    if (basisOpen && basisEditingAllowed) TripCostBasisDialog(costBasis, onSave = {
        if (basisEditingAllowed) { costPreferences.save(it); costBasis = it; basisOpen = false }
    }, onDismiss = { basisOpen = false })
    receipt?.takeIf { browsingAllowed }?.let { selectedReceipt ->
        TripReceiptDialog(selectedReceipt, onDismiss = { receipt = null })
    }
    if (calendarOpen && browsingAllowed) {
        TripDateDialog(selectedDate, onSelect = {
            selectedDateText = it.toString()
            calendarOpen = false
        }, onDismiss = { calendarOpen = false })
    }
}

@Composable
private fun TripsHeader(date: LocalDate, expanded: Boolean, preview: Boolean, onTogglePreview: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit, onToday: () -> Unit, onCalendar: () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val wide = expanded && maxWidth >= 820.dp && LocalDensity.current.fontScale <= 1.2f
        val stackControls = maxWidth < 340.dp || LocalDensity.current.fontScale > 1.2f
        val inlineTitle = maxWidth >= 430.dp && LocalDensity.current.fontScale <= 1.2f
        val title: @Composable () -> Unit = {
            if (wide) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                Text("주행기록", color = Ink, fontSize = 30.sp, fontWeight = FontWeight.SemiBold)
                Text(date.format(DateTimeFormatter.ofPattern("yyyy.MM.dd")), color = Secondary, fontSize = 16.sp)
            } else Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text("주행기록", color = Ink, fontSize = 30.sp, fontWeight = FontWeight.Medium)
                Text(date.format(DateLabel), color = Secondary, fontSize = 14.sp)
            }
        }
        val previewButton: @Composable () -> Unit = {
            Button(onClick = onTogglePreview, shape = CircleShape,
                modifier = Modifier.heightIn(min = 48.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)) {
                Text(if (preview) "내 기록 보기" else "샘플 데이터 보기", fontSize = 15.sp)
            }
        }
        if (wide) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(Modifier.weight(1f)) { title() }
            previewButton()
            DateNavigation(date, onPrevious, onNext, onToday, onCalendar)
        } else Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (inlineTitle) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.weight(1f)) { title() }
                    previewButton()
                }
            } else {
                title()
                previewButton()
            }
            DateNavigation(date, onPrevious, onNext, onToday, onCalendar, stacked = stackControls)
        }
    }
}

@Composable
private fun TripListContent(records: List<TripRecord>, active: TripRecord?, selectedId: String?,
    recordingEnabled: Boolean, hasCurrent: Boolean, showAll: Boolean, onToggleAll: () -> Unit,
    onSelect: (String) -> Unit, preview: Boolean = false) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (records.size > 1) Text("주행 목록 · ${records.size}회", color = Secondary, fontSize = 13.sp)
        if (active != null) TripSelectionRow(active, active.id == selectedId) { onSelect(active.id) }
        if (records.isEmpty()) EmptyDate(recordingEnabled, hasCurrent, preview)
        val displayed = if (showAll) records else records.take(5)
        displayed.forEach { record -> TripSelectionRow(record, record.id == selectedId) { onSelect(record.id) } }
        if (records.size > 5) {
            TextButton(onToggleAll, Modifier.heightIn(min = 48.dp)) {
                Text(if (showAll) "주행 목록 접기" else "외 ${records.size - 5}회 · 모든 주행 보기", fontSize = 14.sp)
            }
        }
    }
}

@Composable
private fun TripSelectionRow(record: TripRecord, selected: Boolean, onSelect: () -> Unit) {
    Surface(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(role = Role.Button, onClick = onSelect)
        .semantics {
            contentDescription = "주행 상세 ${clockText(record.startedAtMs, record.zoneId)}"
            this.selected = selected
        },
        shape = RoundedCornerShape(12.dp), color = if (selected) PaleBlue else CardColor) {
        Row(Modifier.heightIn(min = 48.dp).padding(horizontal = 6.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            Box(Modifier.size(7.dp).background(if (record.isActive) AccentGreen else Blue, CircleShape))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(if (record.isActive) "${clockText(record.startedAtMs, record.zoneId)} · ${activeStatus(record)}" else timeWindow(record),
                    color = Ink, fontSize = 19.sp, lineHeight = 27.sp, fontWeight = FontWeight.Medium)
                Text("${decimal(record.distanceMeters / 1000.0)} km · ${durationText(record.durationMs)}" +
                    when {
                        record.status == TripStatus.INTERRUPTED -> " · 중단된 기록"
                        record.hasDataGap -> " · 일부 구간 누락"
                        record.isActive -> ""
                        else -> " · 주행 완료"
                    }, color = if (record.status == TripStatus.INTERRUPTED || record.hasDataGap) OrangeInk else Secondary,
                    fontSize = 13.sp, lineHeight = 20.sp)
            }
            val chevronColor = if (selected) Blue else Secondary
            Canvas(Modifier.size(14.dp)) {
                drawLine(chevronColor, Offset(size.width * .35f, size.height * .2f), Offset(size.width * .65f, size.height * .5f), 1.5.dp.toPx(), StrokeCap.Round)
                drawLine(chevronColor, Offset(size.width * .65f, size.height * .5f), Offset(size.width * .35f, size.height * .8f), 1.5.dp.toPx(), StrokeCap.Round)
            }
        }
    }
}

@Composable
private fun RecordingStatus(enabled: Boolean, message: String, onEnabled: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                Box(Modifier.size(7.dp).background(if (enabled) AccentGreen else Secondary, CircleShape))
                Text(if (enabled) "자동 기록 켜짐" else "자동 기록 꺼짐", color = Ink, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            }
            Text(message.ifBlank { if (enabled) "출발하면 기록하고, 주차하면 저장해요." else "다음 주행부터 자동으로 기록해요." },
                color = Secondary, fontSize = 12.sp, lineHeight = 18.sp)
        }
        Switch(enabled, onEnabled, modifier = Modifier.semantics { contentDescription = "자동 주행 기록" },
            colors = SwitchDefaults.colors(checkedTrackColor = AccentGreen, checkedThumbColor = CardColor))
    }
}

@Composable
private fun DrivingStatus(current: TripRecord?, enabled: Boolean, message: String, modifier: Modifier) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        val short = maxHeight < 440.dp
        val fontScale = LocalDensity.current.fontScale
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(if (short) 12.dp else 22.dp)) {
            Text("주행기록", color = Ink, fontSize = if (short) 28.sp else 32.sp, fontWeight = FontWeight.Medium, maxLines = 1)
            Surface(Modifier.weight(1f).fillMaxWidth(), shape = RoundedCornerShape(24.dp), color = PaleBlue) {
                BoxWithConstraints(Modifier.padding(if (short) 18.dp else 28.dp)) {
                    val showMetrics = maxHeight >= (125 * fontScale).dp
                    val showMessage = maxHeight >= ((if (current == null) 100 else 205) * fontScale).dp
                    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically)) {
                        StatusHeading(current?.let { activeStatus(it) } ?: if (enabled) "출발 감지 대기" else "자동 기록 꺼짐", enabled)
                        if (current != null && showMetrics) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                                MetricValue(TripMetric("주행 거리 · 추정", "${decimal(current.distanceMeters / 1000.0)} km"), Modifier.weight(1f))
                                MetricValue(TripMetric("주행 시간", durationText(current.durationMs)), Modifier.weight(1f))
                            }
                        }
                        if (showMessage && message.isNotBlank()) {
                            Text(message, color = Secondary, fontSize = 17.sp, lineHeight = 24.sp,
                                maxLines = if (short) 2 else 3, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
            Text("기록 조회와 설정 변경은 주차 후 이용하세요.", color = Secondary, fontSize = 17.sp, lineHeight = 25.sp,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun StatusHeading(text: String, active: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.size(10.dp).background(if (active) Blue else Secondary, CircleShape))
        Text(text, color = if (active) Blue else Ink, fontSize = 22.sp, fontWeight = FontWeight.Medium,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun DateNavigation(date: LocalDate, onPrevious: () -> Unit, onNext: () -> Unit,
    onToday: () -> Unit, onCalendar: () -> Unit, stacked: Boolean = false) {
    val arrows: @Composable () -> Unit = {
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
            DateArrow("이전 날짜", false, onPrevious)
            DateArrow("다음 날짜", true, onNext)
            TextButton(onToday, Modifier.heightIn(min = 48.dp), enabled = date != LocalDate.now()) { Text("오늘", fontSize = 14.sp) }
        }
    }
    val calendar: @Composable () -> Unit = {
        OutlinedButton(onCalendar, Modifier.heightIn(min = 48.dp), shape = RoundedCornerShape(50), border = BorderStroke(.5.dp, Line)) {
            Text("날짜 선택", color = Ink, fontSize = 14.sp)
        }
    }
    if (stacked) Column(verticalArrangement = Arrangement.spacedBy(4.dp)) { arrows(); calendar() }
    else Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) { arrows(); calendar() }
}

@Composable
private fun DateArrow(label: String, forward: Boolean, onClick: () -> Unit) {
    val arrowColor = Blue
    IconButton(onClick, Modifier.size(48.dp).semantics { contentDescription = label }) {
        Canvas(Modifier.size(22.dp)) {
            val outer = if (forward) .35f else .65f
            val middle = if (forward) .65f else .35f
            val stroke = 2.dp.toPx()
            drawLine(arrowColor, Offset(size.width * outer, size.height * .2f), Offset(size.width * middle, size.height * .5f), stroke, StrokeCap.Round)
            drawLine(arrowColor, Offset(size.width * middle, size.height * .5f), Offset(size.width * outer, size.height * .8f), stroke, StrokeCap.Round)
        }
    }
}

@Composable
private fun DayTotals(distance: Double, durationMs: Long) {
    BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
        val distanceMetric = TripMetric("주행 거리 · 추정", decimal(distance), "km")
        val durationMetric = TripMetric("주행 시간", durationText(durationMs))
        val valueStyle = MaterialTheme.typography.bodyLarge.copy(fontSize = 36.sp, lineHeight = 42.sp,
            fontWeight = FontWeight.SemiBold, platformStyle = PlatformTextStyle(includeFontPadding = false))
        val distanceValue = buildAnnotatedString {
            append(distanceMetric.value)
            withStyle(SpanStyle(fontSize = 16.sp, fontWeight = FontWeight.Normal)) { append(" km") }
        }
        val durationValue = buildAnnotatedString {
            // Keep the numbers as large as the distance, with units on the same baseline.
            var cursor = 0
            Regex("시간|분|초").findAll(durationMetric.value).forEach { unit ->
                append(durationMetric.value.substring(cursor, unit.range.first))
                withStyle(SpanStyle(fontSize = 16.sp, fontWeight = FontWeight.Normal)) { append(unit.value) }
                cursor = unit.range.last + 1
            }
            append(durationMetric.value.substring(cursor))
        }
        val density = LocalDensity.current
        val measurer = rememberTextMeasurer()
        val availableValueWidth = with(density) { ((maxWidth - 25.dp) / 2).toPx() }
        val widestValue = maxOf(
            measurer.measure(distanceValue, valueStyle, softWrap = false).size.width,
            measurer.measure(durationValue, valueStyle, softWrap = false).size.width,
        )
        if (maxWidth < 230.dp || density.fontScale > 1.3f || widestValue > availableValueWidth) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                DayTotal(distanceMetric.label, distanceValue, valueStyle)
                DayTotal(durationMetric.label, durationValue, valueStyle)
            }
        } else Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            DayTotal(distanceMetric.label, distanceValue, valueStyle, Modifier.weight(1f))
            VerticalDivider(Modifier.fillMaxHeight(), color = Line)
            DayTotal(durationMetric.label, durationValue, valueStyle, Modifier.weight(1f))
        }
    }
}

@Composable
private fun DayTotal(label: String, value: AnnotatedString, valueStyle: TextStyle, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(value, Modifier.height(with(LocalDensity.current) { 42.sp.toDp() }), color = Ink,
            style = valueStyle, maxLines = 1, softWrap = false)
        Text(label, color = Secondary, fontSize = 14.sp)
    }
}

@Composable
private fun TripDashboardHeader(title: String, receiptEnabled: Boolean, onReceipt: () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val narrow = maxWidth < 380.dp || LocalDensity.current.fontScale > 1.2f
        val action: @Composable () -> Unit = {
            Surface(onClick = onReceipt, enabled = receiptEnabled,
                modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = "주행 영수증 열기" },
                shape = CircleShape, color = CardColor,
                shadowElevation = CardShadowElevation) {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    ReceiptIcon(if (receiptEnabled) Ink else Secondary)
                    Text("주행 영수증", color = if (receiptEnabled) Ink else Secondary,
                        fontSize = 15.sp, fontWeight = FontWeight.Medium)
                }
            }
        }
        if (narrow) Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, color = Ink, fontSize = 25.sp, lineHeight = 33.sp, fontWeight = FontWeight.SemiBold)
            action()
        } else Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Text(title, Modifier.weight(1f), color = Ink, fontSize = 25.sp,
                lineHeight = 33.sp, fontWeight = FontWeight.SemiBold)
            action()
        }
    }
}

@Composable
private fun TripDashboardMetrics(records: List<TripRecord>, summary: TripCostSummary, focused: TripRecord?) {
    val energyRecords = records.filter { it.consumedWh?.let { wh -> wh.isFinite() && wh >= 0.0 } == true }
    val efficiency = if (focused != null) focused.averageEfficiencyKmPerKwh else summary.efficiencyKmPerKwh
    val energy = if (focused != null) focused.consumedWh?.takeIf { it.isFinite() && it >= 0.0 }?.div(1_000.0)
        else energyRecords.takeIf { it.isNotEmpty() }?.sumOf { it.consumedWh!! }?.div(1_000.0)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val metrics = listOf(TripMetric("평균 전비 · 추정", decimal(efficiency), "km/kWh"),
                TripMetric("배터리 사용", decimal(energy, 2), "kWh"))
            if (maxWidth < 280.dp || LocalDensity.current.fontScale > 1.3f) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    metrics.forEach { DashboardMetric(it) }
                }
            } else Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                DashboardMetric(metrics[0], Modifier.weight(1f))
                VerticalDivider(Modifier.fillMaxHeight(), color = Line)
                DashboardMetric(metrics[1], Modifier.weight(1f))
            }
        }
        if (focused == null && records.isNotEmpty() &&
            (summary.measuredCount < records.size || energyRecords.size < records.size)) {
            Text("측정값 기준 · 전비 ${summary.measuredCount}/${records.size}회 · 배터리 ${energyRecords.size}/${records.size}회",
                color = Secondary, fontSize = 12.sp, lineHeight = 18.sp)
        }
    }
}

@Composable
private fun DashboardMetric(metric: TripMetric, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(metric.label, color = Secondary, fontSize = 14.sp, lineHeight = 21.sp)
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(metric.value, Modifier.weight(1f, fill = false), color = Ink, fontSize = 31.sp,
                lineHeight = 38.sp, fontWeight = FontWeight.Medium)
            Text(metric.unit, color = Secondary, fontSize = 13.sp, modifier = Modifier.padding(bottom = 4.dp))
        }
    }
}

@Composable
private fun TripViewSwitch(showList: Boolean, onList: () -> Unit, onSummary: () -> Unit) {
    Surface(Modifier.fillMaxWidth(), color = BackgroundColor, shape = CircleShape) {
        Row(Modifier.padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf("주행 목록" to onList, "일별 요약" to onSummary).forEachIndexed { index, (label, action) ->
                val active = (index == 0) == showList
                Surface(onClick = action, modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                    .semantics { contentDescription = "$label 보기"; selected = active; role = Role.Tab },
                    color = if (active) CardColor else Color.Transparent, shape = CircleShape,
                    shadowElevation = if (active) CardShadowElevation else 0.dp) {
                    Box(Modifier.padding(horizontal = 8.dp, vertical = 12.dp), contentAlignment = Alignment.Center) {
                        Text(label, color = if (active) Blue else Secondary, fontSize = 15.sp,
                            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal)
                    }
                }
            }
        }
    }
}

@Composable
private fun DailyTripSummary(records: List<TripRecord>, distanceKm: Double, durationMs: Long, preview: Boolean = false) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("일별 요약 · ${records.size}회", color = Ink, fontSize = 18.sp, fontWeight = FontWeight.Medium)
        if (records.isEmpty()) {
            Text(if (preview) "샘플 기간 안의 다른 날짜를 선택해 보세요." else "주행을 마치면 하루의 이동을 모아 보여드려요.", color = Secondary, fontSize = 14.sp, lineHeight = 21.sp)
        } else {
            Text("${decimal(distanceKm)} km · ${durationText(durationMs)}", color = Ink, fontSize = 22.sp)
            Text("위 전비와 비용은 이 날짜의 비교 가능한 기록을 모은 결과예요.",
                color = Secondary, fontSize = 12.sp, lineHeight = 18.sp)
        }
    }
}

@Composable
private fun FocusedTripSummary(record: TripRecord) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(if (record.isActive) "지금 기록하고 있어요" else "선택한 주행 상세", color = Ink,
            fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        Text("${decimal(record.distanceMeters / 1_000.0)} km · ${durationText(record.durationMs)}", color = Ink, fontSize = 22.sp)
        Text(if (record.isActive) "${clockText(record.startedAtMs, record.zoneId)} 출발 · ${activeStatus(record)}" else timeWindow(record),
            color = Secondary, fontSize = 14.sp)
        CompactBatterySummary(record)
        if (record.status == TripStatus.INTERRUPTED || record.hasDataGap) {
            Text("${if (record.status == TripStatus.INTERRUPTED) "중단된 기록" else "일부 구간 누락"} · 마지막으로 확인한 신호까지 표시해요.",
                color = OrangeInk, fontSize = 12.sp, lineHeight = 18.sp)
        }
    }
}

@Composable
private fun CompactBatterySummary(record: TripRecord, title: String = "배터리 변화") {
    val start = record.batteryStartPercent?.takeIf { it in 0..100 }
    val end = record.batteryEndPercent?.takeIf { it in 0..100 }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, color = Secondary, fontSize = 13.sp)
        Text("출발 ${start?.let { "$it%" } ?: "—"}   →   ${if (record.isActive) "현재" else "종료"} ${end?.let { "$it%" } ?: "—"}",
            color = Ink, fontSize = 16.sp)
        Box(Modifier.fillMaxWidth().height(8.dp)) {
            RoundedMetricBar(if (start != null && end != null) maxOf(start, end) / 100f else 0f,
                Modifier.fillMaxSize(), color = AccentGreen.copy(alpha = .25f), trackColor = Line)
            if (start != null && end != null) RoundedMetricBar(minOf(start, end) / 100f,
                Modifier.fillMaxSize(), color = AccentGreen, trackColor = Color.Transparent)
        }
        Text(socText(record), color = Secondary, fontSize = 12.sp, lineHeight = 18.sp)
    }
}

@Composable
private fun ReceiptIcon(tint: Color) {
    Canvas(Modifier.size(32.dp)) {
        fun point(x: Float, y: Float) = Offset(size.width * x, size.height * y)
        val receipt = Path().apply {
            val points = listOf(point(.15f, .12f), point(.85f, .12f), point(.85f, .89f),
                point(.73f, .82f), point(.62f, .89f), point(.50f, .82f), point(.38f, .89f),
                point(.27f, .82f), point(.15f, .89f))
            points.forEachIndexed { index, p -> if (index == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y) }
            close()
        }
        val stroke = 1.7.dp.toPx()
        drawPath(receipt, tint, style = Stroke(stroke, join = StrokeJoin.Round))
        for (y in listOf(.32f, .46f, .60f)) {
            drawLine(tint, point(.31f, y), point(if (y == .60f) .56f else .69f, y), stroke, StrokeCap.Round)
        }
    }
}

@Composable
private fun EmptyDate(recordingEnabled: Boolean, hasCurrent: Boolean, preview: Boolean = false) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        MoaIcon(MoaIconKind.TRIPS, Modifier.size(42.dp), Blue)
        Text(if (preview) "이 날짜에는 샘플 주행이 없어요" else "아직 저장된 주행이 없어요", color = Ink, fontSize = 18.sp, fontWeight = FontWeight.Medium)
        Text(when {
            preview -> "오늘부터 이전 7일 중 다른 날짜를 선택해 보세요."
            hasCurrent -> "현재 주행은 종료되면 이 날짜에 표시돼요."
            recordingEnabled -> "다른 날짜를 선택하거나, 다음 이동을 기록해 보세요."
            else -> "자동 기록을 켜면 다음 이동을 기록할 수 있어요."
        }, color = Secondary, fontSize = 14.sp, lineHeight = 21.sp)
    }
}

private data class TripMetric(val label: String, val value: String, val unit: String = "")

@Composable
private fun MetricValue(metric: TripMetric, modifier: Modifier = Modifier, accent: Color = Ink) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(metric.label, color = Secondary, fontSize = 15.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.Bottom) {
            Text(metric.value, Modifier.weight(1f, fill = false), color = accent, fontSize = 30.sp, fontWeight = FontWeight.Medium)
            if (metric.unit.isNotEmpty()) Text(metric.unit, Modifier.padding(bottom = 3.dp), color = Secondary, fontSize = 14.sp)
        }
    }
}

@Composable
private fun TripDateDialog(selected: LocalDate, onSelect: (LocalDate) -> Unit, onDismiss: () -> Unit) {
    var monthText by rememberSaveable { mutableStateOf(YearMonth.from(selected).toString()) }
    val month = remember(monthText) { YearMonth.parse(monthText) }
    val firstOffset = month.atDay(1).dayOfWeek.value % 7
    val cells = List(((firstOffset + month.lengthOfMonth() + 6) / 7) * 7) { index ->
        (index - firstOffset + 1).takeIf { it in 1..month.lengthOfMonth() }?.let { month.atDay(it) }
    }
    DimmedDialog(onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val close = rememberDialogAction(onDismiss)
        val selectToday = rememberDialogAction { onSelect(LocalDate.now()) }
        BoxWithConstraints(Modifier.padding(20.dp), contentAlignment = Alignment.Center) {
            Surface(Modifier.widthIn(max = 560.dp).fillMaxWidth().heightIn(max = maxHeight),
                shape = RoundedCornerShape(26.dp), color = SurfaceColor) {
                Column(Modifier.verticalScroll(rememberScrollState()).padding(22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("날짜 선택", Modifier.weight(1f), color = Ink, fontSize = 26.sp, fontWeight = FontWeight.Medium)
                        IconButton(close, Modifier.semantics { contentDescription = "날짜 선택 닫기" }) {
                            MoaIcon(MoaIconKind.CLOSE, Modifier.size(24.dp), Secondary)
                        }
                    }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        DateArrow("이전 달", false) { monthText = month.minusMonths(1).toString() }
                        Text("${month.year}년 ${month.monthValue}월", color = Ink, fontSize = 22.sp, fontWeight = FontWeight.Medium)
                        DateArrow("다음 달", true) { monthText = month.plusMonths(1).toString() }
                    }
                    Row(Modifier.fillMaxWidth()) {
                        listOf("일", "월", "화", "수", "목", "금", "토").forEach { day ->
                            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { Text(day, color = Secondary, fontSize = 16.sp) }
                        }
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        cells.chunked(7).forEach { week ->
                            Row(Modifier.fillMaxWidth()) {
                                week.forEach { date ->
                                    val isSelected = date == selected
                                    val today = date == LocalDate.now()
                                    val selectDate = rememberDialogAction { date?.let(onSelect) }
                                    Box(Modifier.weight(1f).heightIn(min = 48.dp).clip(RoundedCornerShape(12.dp))
                                        .background(if (isSelected) Blue else if (today) PaleBlue else Color.Transparent)
                                        .then(if (date != null) Modifier.semantics { contentDescription = date.format(DateLabel) }
                                            .clickable(role = Role.Button, onClick = selectDate) else Modifier),
                                        contentAlignment = Alignment.Center) {
                                        if (date != null) Text(date.dayOfMonth.toString(), color = if (isSelected) OnBlue else if (today) Blue else Ink,
                                            fontSize = 20.sp, fontWeight = if (isSelected || today) FontWeight.SemiBold else FontWeight.Normal)
                                    }
                                }
                            }
                        }
                    }
                    TextButton(selectToday, Modifier.align(Alignment.End).heightIn(min = 48.dp)) { Text("오늘로 이동", fontSize = 18.sp) }
                }
            }
        }
    }
}

private fun activeStatus(record: TripRecord) = if (record.status == TripStatus.PARKING) "주차 확인 중" else "주행 기록 중"

private fun decimal(value: Double?, places: Int = 1): String = value?.takeIf { it.isFinite() }
    ?.let { String.format(Locale.KOREAN, "%.${places}f", it) } ?: "—"

private fun durationText(durationMs: Long): String {
    val seconds = durationMs.coerceAtLeast(0) / 1000
    val minutes = seconds / 60
    return when {
        seconds == 0L -> "0분"
        minutes == 0L -> "${seconds}초"
        minutes < 60 -> "${minutes}분"
        else -> "${minutes / 60}시간 ${minutes % 60}분"
    }
}

private fun tripZone(zoneId: String): ZoneId = runCatching { ZoneId.of(zoneId) }.getOrDefault(ZoneId.systemDefault())

private fun clockText(atMs: Long, zoneId: String): String = Instant.ofEpochMilli(atMs).atZone(tripZone(zoneId)).format(ClockLabel)

private fun timeWindow(record: TripRecord): String {
    val zone = tripZone(record.zoneId)
    val start = Instant.ofEpochMilli(record.startedAtMs).atZone(zone)
    val end = record.endedAtMs?.let { Instant.ofEpochMilli(it).atZone(zone) }
    val endLabel = end?.format(if (start.toLocalDate() == end.toLocalDate()) ClockLabel else OtherDayClockLabel) ?: "—"
    return "${start.format(ClockLabel)} → $endLabel"
}

private fun socText(record: TripRecord): String {
    val start = record.batteryStartPercent?.takeIf { it in 0..100 }
    val end = record.batteryEndPercent?.takeIf { it in 0..100 }
    val change = if (start != null && end != null) {
        val used = start - end
        if (used >= 0) " · ${used}%p 사용" else " · ${-used}%p 증가"
    } else ""
    return "배터리 ${start?.let { "$it%" } ?: "—"} → ${end?.let { "$it%" } ?: "—"}$change"
}
