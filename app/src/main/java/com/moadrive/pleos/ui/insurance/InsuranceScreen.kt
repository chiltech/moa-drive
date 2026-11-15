package com.moadrive.pleos.ui.insurance

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import com.moadrive.pleos.ui.common.SoftSurface as Surface
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moadrive.pleos.insurance.*
import com.moadrive.pleos.safety.score.DrivingScoreState
import com.moadrive.pleos.safety.score.DrivingScorePreview
import com.moadrive.pleos.trips.TripRecord
import com.moadrive.pleos.ui.*
import com.moadrive.pleos.ui.common.*
import com.moadrive.pleos.ui.safety.DrivingScoreInsuranceCard
import java.time.format.DateTimeFormatter
import java.util.Locale

private enum class InsuranceDetail { POLICY, TEMPORARY_DRIVER }

@Composable
fun InsuranceScreen(
    model: InsuranceViewModel,
    records: List<TripRecord>,
    browsingAllowed: Boolean,
    onHome: () -> Unit,
    onService: (HomeService) -> Unit,
    formsAllowed: Boolean = browsingAllowed,
    drivingScoreState: DrivingScoreState = DrivingScoreState(),
) {
    val actualState by model.state.collectAsState()
    var preview by rememberSaveable { mutableStateOf(false) }
    val sampleProvider = actualState.provider ?: InsuranceProvider.SAMSUNG
    // The interactive preview has its own cancellable scope and no persistence callbacks.
    val sampleSession = if (preview) rememberSampleInsuranceSession(sampleProvider) else null
    val state = if (sampleSession != null) {
        val sampleState by sampleSession.state.collectAsState()
        sampleState
    } else actualState
    val connectProvider = sampleSession?.let { it::connect } ?: model::connect
    val showProviderPicker = sampleSession?.let { it::showProviderPicker } ?: model::showProviderPicker
    val cancelProviderPicker = sampleSession?.let { it::cancelProviderPicker } ?: model::cancelProviderPicker
    val retryConnection = sampleSession?.let { it::retryConnection } ?: model::retryConnection
    val openRequest = sampleSession?.let { it::openRequest } ?: model::openRequest
    val submitRequest = sampleSession?.let { it::submitRequest } ?: model::submitRequest
    val dismissRequest = sampleSession?.let { it::dismissRequest } ?: model::dismissRequest
    val provider = state.provider
    val actualMileage = remember(records) { InsuranceMileage.from(records) }
    val mileage = if (preview) InsuranceMileage(totalKm = 7_420.0, monthKm = 620.0, tripCount = 124, incompleteCount = 0) else actualMileage
    val choosingProvider = provider == null
    val actionsEnabled = browsingAllowed
    DisposableEffect(model) {
        onDispose {
            model.dismissRequest()
            model.cancelProviderPicker()
        }
    }
    LaunchedEffect(browsingAllowed) {
        if (!browsingAllowed) {
            preview = false
            model.dismissRequest()
            model.cancelProviderPicker()
        }
    }

    val revealState = rememberInfoRevealState()
    // Changing the insurer or page phase starts its independent scroll panes at the top.
    key(provider, choosingProvider, preview) {
        var detail by remember { mutableStateOf<InsuranceDetail?>(null) }
        LaunchedEffect(browsingAllowed, formsAllowed) {
            if (!browsingAllowed || (!formsAllowed && detail == InsuranceDetail.TEMPORARY_DRIVER)) detail = null
        }
        val contract = state.contract?.takeIf {
            it.provider == provider && state.connectionStatus == InsuranceConnectionStatus.CONNECTED
        }
        DetailPage(HomeService.INSURANCE, browsingAllowed, onHome, onService, left = {
            DetailHeading("보험", "내 차를 위한 안심, 지금 여기예요")
            DetailPageArtwork(JourneyArt.INSURANCE, .73f, 285.dp)
            if (provider == null) {
                DetailSummary {
                    Text("내 보험과 연결해 볼까요?", color = Ink, fontSize = 20.sp, fontWeight = FontWeight.Medium)
                    IntroStep("01", "가입한 보험사 선택")
                    IntroStep("02", "보험기간 · 특약 한눈에 확인")
                    IntroStep("03", "필요한 서비스 요청")
                    HorizontalDivider(color = Line)
                    Text("연동 준비", color = Blue, fontSize = 13.sp, lineHeight = 18.sp)
                    Text("보험사 제휴 연결 전 · 계약과 서비스 요청 흐름을 확인해요.", color = Secondary, fontSize = 14.sp, lineHeight = 21.sp)
                }
            } else {
                InsurerIdentitySummary(provider, contract, state.connectionStatus, actionsEnabled,
                    choosingProvider, onChange = showProviderPicker)
            }
        }, right = {
            InfoReveal(0) {
                SampleDataHeader(
                    title = if (choosingProvider) "보험과 서비스 살펴보기" else "내 자동차보험",
                    description = if (browsingAllowed) "계약부터 도움 요청까지 간편하게."
                        else "보험 변경과 도움 요청은 주차 후 이용해 주세요.",
                    preview = preview,
                    enabled = browsingAllowed,
                    onToggle = {
                        dismissRequest()
                        cancelProviderPicker()
                        preview = !preview
                    },
                    previewDescription = "샘플 데이터 · 보험 연결과 실제 기록은 그대로 유지돼요",
                )
            }
            if (choosingProvider) {
                InfoReveal(1) {
                    DrivingScoreInsuranceCard(drivingScoreState, browsingAllowed) { onService(HomeService.SAFETY) }
                }
                ProviderPicker(provider, browsingAllowed,
                    onSelect = { if (browsingAllowed) connectProvider(it) },
                    onCancel = cancelProviderPicker)
            } else {
                InfoReveal(1) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { detail = InsuranceDetail.POLICY }, enabled = browsingAllowed,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            modifier = Modifier.heightIn(min = 48.dp)) {
                            Text("보험 상세", fontSize = 14.sp)
                        }
                    }
                }
                InfoReveal(2) {
                    PolicySummaryCard(state, actionsEnabled, retryConnection)
                }
                InfoReveal(3) {
                    InsuranceServiceBanners(
                        enabled = actionsEnabled && state.connectionStatus == InsuranceConnectionStatus.CONNECTED,
                        usage = contract?.roadsideUsage,
                        onTemporaryDriver = { if (actionsEnabled && formsAllowed) detail = InsuranceDetail.TEMPORARY_DRIVER },
                        temporaryDriverEnabled = actionsEnabled && formsAllowed,
                        onSelect = { if (actionsEnabled) openRequest(it) },
                    )
                }
                InfoReveal(4) {
                    if (preview) SampleDrivingScoreCard()
                    else DrivingScoreInsuranceCard(drivingScoreState, browsingAllowed) { onService(HomeService.SAFETY) }
                }
                contract?.let {
                    InfoReveal(5) { AnnualMileageCard(it.annualMileageKm) }
                }
                InfoReveal(6) {
                    MileageCard(mileage, actionsEnabled, preview = preview) { onService(HomeService.TRIPS) }
                }
            }
        }, revealState = revealState)
        if (provider != null && !choosingProvider && !state.selectingProvider && browsingAllowed) {
            when (detail) {
                InsuranceDetail.POLICY -> InsuranceDetailsDialog("보험 상세", onDismiss = { detail = null }) {
                    PolicySummaryCard(state, actionsEnabled, retryConnection)
                    contract?.let { AnnualMileageCard(it.annualMileageKm) }
                    MileageCard(mileage, actionsEnabled, preview = preview) {
                        detail = null
                        onService(HomeService.TRIPS)
                    }
                }
                InsuranceDetail.TEMPORARY_DRIVER -> if (formsAllowed) InsuranceWebDialog(provider, onDismiss = { detail = null })
                null -> Unit
            }
        }
    }
    if (actionsEnabled && state.selectingProvider && state.provider != null) {
        InsuranceProviderDialog(
            selected = state.provider,
            onSelect = { if (actionsEnabled) connectProvider(it) },
            onDismiss = cancelProviderPicker,
        )
    }
    state.request?.takeIf { browsingAllowed && !choosingProvider && !state.selectingProvider && it.provider == provider }?.let { request ->
        InsuranceRequestDialog(request, browsingAllowed,
            onSubmit = { if (browsingAllowed) submitRequest() },
            onDismiss = dismissRequest,
            usage = state.contract?.roadsideUsage)
    }
}

@Composable
private fun rememberSampleInsuranceSession(provider: InsuranceProvider): InsuranceSession {
    val scope = rememberCoroutineScope()
    val session = remember { InsuranceSession(DemoInsuranceContractSource(), scope) }
    LaunchedEffect(session, provider) { session.connect(provider) }
    DisposableEffect(session) { onDispose { session.dismissRequest() } }
    return session
}

@Composable
private fun IntroStep(number: String, title: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(number, Modifier.widthIn(min = 24.dp),
            color = Blue, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Text(title, color = Ink, fontSize = 16.sp)
    }
}

@Composable
private fun ProviderPicker(
    selected: InsuranceProvider?,
    enabled: Boolean,
    onSelect: (InsuranceProvider) -> Unit,
    onCancel: (() -> Unit)? = null,
    showHeading: Boolean = true,
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (showHeading) {
            InfoReveal(0) {
                DetailHeading("가입한 보험사를 선택해 주세요", "선택한 보험사의 계약 화면으로 이동해요.")
            }
        }
        InsuranceProvider.entries.forEachIndexed { index, provider ->
            InfoReveal(index + 1) {
                Surface(
                    onClick = { if (enabled) onSelect(provider) }, enabled = enabled,
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
                    color = if (selected == provider) PaleBlue else CardColor,
                    border = if (selected == provider) BorderStroke(1.5.dp, Blue) else null,
                    shadowElevation = CardShadowElevation,
                ) {
                    Row(Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(42.dp).background(PaleBlue, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                            MoaIcon(MoaIconKind.INSURANCE, Modifier.size(26.dp), Blue)
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(provider.title, color = Ink, fontSize = 20.sp, fontWeight = FontWeight.Medium)
                            Text(provider.phone, color = Secondary, fontSize = 14.sp)
                        }
                        Text(if (selected == provider) "다시 연결 ›" else "연결 ›", color = Blue, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
        InfoReveal(InsuranceProvider.entries.size + 1) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                DemoBadge("연동 준비")
                Text("보험사 제휴 연결 전", Modifier.weight(1f), color = Secondary, fontSize = 13.sp)
            }
        }
        if (selected != null && onCancel != null) {
            InfoReveal(InsuranceProvider.entries.size + 2) {
                OutlinedButton(onClick = onCancel, enabled = enabled, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text("내 보험으로 돌아가기", fontSize = 16.sp)
                }
            }
        }
    }
}

@Composable
private fun InsuranceProviderDialog(
    selected: InsuranceProvider?,
    onSelect: (InsuranceProvider) -> Unit,
    onDismiss: () -> Unit,
) {
    // Use the source window, before the dialog excludes the service menu rail.
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val expanded = DetailPaneLayout.isExpanded(maxWidth, LocalDensity.current.fontScale)
        DimmedDialog(onDismissRequest = onDismiss) {
            val dismiss = rememberDialogAction(onDismiss)
            val motion = LocalDialogMotion.current
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val panelModifier = if (expanded) {
                    Modifier.align(Alignment.CenterEnd).padding(end = 16.dp)
                        .width((maxWidth * .60f).coerceAtMost(640.dp))
                } else Modifier.align(Alignment.Center).fillMaxWidth(.95f)
                Surface(panelModifier.fillMaxHeight(.94f),
                    shape = RoundedCornerShape(22.dp), color = SurfaceColor) {
                    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("보험사 변경", color = Ink, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
                                Text("가입한 보험사를 선택해 주세요.", color = Secondary, fontSize = 14.sp, lineHeight = 21.sp)
                            }
                            TextButton(onClick = dismiss, modifier = Modifier.heightIn(min = 48.dp)) {
                                Text("닫기", fontSize = 16.sp)
                            }
                        }
                        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                            ProviderPicker(selected = selected, enabled = true, showHeading = false,
                                onSelect = { provider -> motion.close { onSelect(provider) } })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InsurerIdentitySummary(
    provider: InsuranceProvider,
    contract: InsuranceContract?,
    status: InsuranceConnectionStatus,
    enabled: Boolean,
    choosingProvider: Boolean,
    onChange: () -> Unit,
) {
    DetailSummary {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("선택한 보험사", color = Secondary, fontSize = 14.sp, lineHeight = 19.sp)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(provider.title, color = Ink, fontSize = 25.sp, lineHeight = 31.sp, fontWeight = FontWeight.SemiBold)
                    Text(provider.phone, color = Secondary, fontSize = 16.sp, lineHeight = 21.sp)
                    contract?.let {
                        Text(it.productName.ifBlank { "자동차보험" }, color = Secondary, fontSize = 14.sp, lineHeight = 19.sp)
                    }
                }
                if (!choosingProvider) {
                    OutlinedButton(onClick = { if (enabled) onChange() }, enabled = enabled,
                        modifier = Modifier.heightIn(min = 48.dp), shape = RoundedCornerShape(24.dp)) {
                        Text("보험사 변경", fontSize = 14.sp)
                    }
                }
            }
            if (contract != null) {
                HorizontalDivider(color = Line)
                PolicyPeriod(contract)
                HorizontalDivider(color = Line)
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text("가입 특약", color = Ink, fontSize = 14.sp, lineHeight = 19.sp, fontWeight = FontWeight.Medium)
                    Text(contract.endorsements.takeIf { it.isNotEmpty() }?.joinToString(" · ")
                        ?: "등록된 특약 정보가 없어요.", color = Secondary, fontSize = 13.sp, lineHeight = 20.sp)
                }
            } else {
                val statusLabel = when (status) {
                    InsuranceConnectionStatus.CONNECTING -> "계약 준비 중"
                    InsuranceConnectionStatus.FAILED -> "연결 확인 필요"
                    else -> "연결 대기"
                }
                Text(statusLabel, color = Blue, fontSize = 13.sp, lineHeight = 18.sp)
            }
            val sourceLabel = if (contract?.source == ContractSource.DEMO) "데모 계약 정보" else "보험사 대표 연락처"
            Text("$sourceLabel · 보험사 제휴 연결 전", color = Secondary, fontSize = 13.sp, lineHeight = 18.sp)
        }
    }
}

@Composable
private fun PolicySummaryCard(state: InsuranceUiState, enabled: Boolean, onRetry: () -> Unit) {
    val contract = state.contract?.takeIf { it.provider == state.provider }
    DetailCard {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("보험 기간 · 특약", color = Ink, fontSize = 18.sp, lineHeight = 23.sp, fontWeight = FontWeight.Medium)
            when {
                state.connectionStatus == InsuranceConnectionStatus.CONNECTING -> {
                    LinearProgressIndicator(Modifier.fillMaxWidth().clip(RoundedCornerShape(50)),
                        color = Blue, trackColor = PaleBlue, strokeCap = StrokeCap.Round)
                    Text("${state.provider?.title.orEmpty()} 계약 화면을 준비하고 있어요.", color = Secondary, fontSize = 16.sp, lineHeight = 24.sp)
                }
                contract != null && state.connectionStatus == InsuranceConnectionStatus.CONNECTED -> {
                    BoxWithConstraints(Modifier.fillMaxWidth()) {
                        val useRow = maxWidth >= 450.dp && LocalDensity.current.fontScale <= 1.2f
                        if (useRow) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.weight(.42f)) { PolicyPeriod(contract) }
                                VerticalDivider(Modifier.height(42.dp), color = Line)
                                Box(Modifier.weight(.58f)) { EndorsementPills(contract.endorsements) }
                            }
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                PolicyPeriod(contract)
                                EndorsementPills(contract.endorsements)
                            }
                        }
                    }
                }
                else -> {
                    Text(state.message ?: "계약 연결을 다시 확인해 주세요.", color = Secondary, fontSize = 16.sp, lineHeight = 24.sp)
                    OutlinedButton(onClick = { if (enabled) onRetry() }, enabled = enabled,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("연결 다시 확인", fontSize = 16.sp) }
                }
            }
        }
    }
}

@Composable
private fun PolicyPeriod(contract: InsuranceContract) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text("보험 기간", color = Secondary, fontSize = 13.sp, lineHeight = 18.sp)
        Text("${contract.startDate.format(PolicyDate)} – ${contract.endDate.format(PolicyDate)}",
            color = Ink, fontSize = 16.sp, lineHeight = 21.sp)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EndorsementPills(endorsements: List<String>) {
    if (endorsements.isEmpty()) {
        Text("등록된 특약 정보가 없어요.", color = Secondary, fontSize = 13.sp, lineHeight = 18.sp)
    } else {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            endorsements.forEach { item ->
                Text(item, Modifier.background(BackgroundColor, RoundedCornerShape(20.dp))
                    .padding(horizontal = 9.dp, vertical = 3.dp), color = Ink, fontSize = 13.sp, lineHeight = 17.sp)
            }
        }
    }
}

@Composable
private fun InsuranceDetailsDialog(title: String, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    DimmedDialog(onDismissRequest = onDismiss) {
        val dismiss = rememberDialogAction(onDismiss)
        Surface(
            modifier = Modifier.widthIn(max = 620.dp).fillMaxWidth(.92f).heightIn(max = 680.dp),
            shape = RoundedCornerShape(24.dp), color = SurfaceColor, shadowElevation = CardShadowElevation,
        ) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(title, color = Ink, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
                Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(14.dp), content = content)
                Button(onClick = dismiss, modifier = Modifier.align(Alignment.End).heightIn(min = 48.dp)) {
                    Text("닫기", fontSize = 16.sp)
                }
            }
        }
    }
}

@Composable
private fun MileageCard(mileage: InsuranceMileage, enabled: Boolean, preview: Boolean = false, onTrips: () -> Unit) {
    DetailCard {
        Text(if (preview) "주행 거리 미리보기" else "앱에 기록된 거리", color = Ink, fontSize = 20.sp, fontWeight = FontWeight.Medium)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            MileageValue("이번 달", mileage.monthKm, Modifier.weight(1f))
            MileageValue("전체 기록", mileage.totalKm, Modifier.weight(1f))
        }
        Text("종료한 주행 ${mileage.tripCount}회 · 불완전한 기록 ${mileage.incompleteCount}회", color = Secondary, fontSize = 13.sp, lineHeight = 20.sp)
        Text(if (preview) "샘플 주행 거리예요. 실제 주행기록과 보험 제출 자료에는 반영되지 않아요."
            else "앱에 저장한 추정 거리예요. 보험 할인용 인증·제출과는 별개예요.", color = Secondary, fontSize = 13.sp, lineHeight = 20.sp)
        TextButton(onClick = { if (enabled) onTrips() }, enabled = enabled) { Text("주행기록 보기 ›", fontSize = 15.sp) }
    }
}

@Composable
private fun SampleDrivingScoreCard() {
    val sample = DrivingScorePreview.READY
    DetailCard {
        Text("안전운전 점수", color = Ink, fontSize = 20.sp, fontWeight = FontWeight.Medium)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("안전운전 점수", color = Secondary, fontSize = 13.sp)
                Text("${sample.score}점", color = Blue, fontSize = 28.sp, fontWeight = FontWeight.SemiBold)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("관측 거리", color = Secondary, fontSize = 13.sp)
                Text("${sample.distanceKm.toInt()} km", color = Ink, fontSize = 26.sp)
            }
        }
        Text("앱 참고 조건 충족 · 보험사 확인 필요", color = Blue, fontSize = 15.sp, fontWeight = FontWeight.Medium)
        Text("샘플 점수예요. 80점 · 500 km는 앱의 참고 기준이며 실제 할인 여부는 보험사가 확인해요.",
            color = Secondary, fontSize = 13.sp, lineHeight = 20.sp)
    }
}

@Composable
private fun MileageValue(label: String, km: Double, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, color = Secondary, fontSize = 13.sp)
        Text(String.format(Locale.KOREAN, "%,.1f", km), color = Ink, fontSize = 26.sp, fontWeight = FontWeight.Normal)
        Text("km", color = Secondary, fontSize = 13.sp)
    }
}

@Composable
private fun DemoBadge(label: String) {
    Text(label, Modifier.background(PaleBlue, RoundedCornerShape(8.dp)).padding(horizontal = 9.dp, vertical = 5.dp),
        color = Blue, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
}

private val PolicyDate = DateTimeFormatter.ofPattern("yyyy.MM.dd")
