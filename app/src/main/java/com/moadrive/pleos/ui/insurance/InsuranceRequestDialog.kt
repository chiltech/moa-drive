package com.moadrive.pleos.ui.insurance

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import com.moadrive.pleos.ui.common.SoftSurface as Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moadrive.pleos.ui.common.DimmedDialog
import com.moadrive.pleos.ui.common.rememberDialogAction
import androidx.compose.ui.window.DialogProperties
import com.moadrive.pleos.insurance.InsuranceRequestPhase
import com.moadrive.pleos.insurance.InsuranceRequestState
import com.moadrive.pleos.insurance.InsuranceRoadsideUsage
import com.moadrive.pleos.insurance.RoadsideService
import com.moadrive.pleos.ui.CardShadowElevation
import com.moadrive.pleos.ui.BackgroundColor
import com.moadrive.pleos.ui.SurfaceColor
import com.moadrive.pleos.ui.Blue
import com.moadrive.pleos.ui.Ink
import com.moadrive.pleos.ui.Line
import com.moadrive.pleos.ui.PaleBlue
import com.moadrive.pleos.ui.Secondary
import com.moadrive.pleos.ui.common.ExternalActions
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Demo receipt flow with a separate, explicit handoff for real assistance. */
@Composable
fun InsuranceRequestDialog(
    request: InsuranceRequestState,
    browsingAllowed: Boolean,
    onSubmit: () -> Unit,
    onDismiss: () -> Unit,
    usage: InsuranceRoadsideUsage? = null,
) {
    val context = LocalContext.current
    val contentDensity = LocalDensity.current
    val provider = request.provider
    val service = request.service
    var feedback by remember(provider, service) { mutableStateOf<String?>(null) }
    var showOfficialChannels by remember(provider, service) { mutableStateOf(false) }
    val currentDismiss by rememberUpdatedState(onDismiss)
    LaunchedEffect(browsingAllowed) {
        if (!browsingAllowed) currentDismiss()
    }
    if (!browsingAllowed) return

    DimmedDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
      val dismiss = rememberDialogAction(onDismiss)
      CompositionLocalProvider(LocalDensity provides contentDensity) {
        BoxWithConstraints(contentAlignment = Alignment.Center) {
        val scrollWholeRequest = maxHeight < 520.dp || LocalDensity.current.fontScale > 1.3f
        Surface(
            modifier = Modifier.widthIn(max = 620.dp).fillMaxWidth(0.92f).heightIn(max = minOf(680.dp, maxHeight)),
            color = SurfaceColor,
            shape = RoundedCornerShape(24.dp),
            tonalElevation = 0.dp,
        ) {
            Column(Modifier.then(if (scrollWholeRequest) Modifier.verticalScroll(rememberScrollState()) else Modifier)
                .padding(if (maxHeight < 520.dp) 16.dp else 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Surface(color = PaleBlue, shape = RoundedCornerShape(6.dp)) {
                            Text("요청 흐름 확인", Modifier.padding(horizontal = 9.dp, vertical = 4.dp), color = Blue,
                                fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }
                        Text("${service.title} 요청", color = Ink, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                    }
                    TextButton(onClick = dismiss, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text("닫기", color = Secondary, fontSize = 16.sp)
                    }
                }
                Column(
                    if (scrollWholeRequest) Modifier else Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Surface(color = PaleBlue, shape = RoundedCornerShape(16.dp)) {
                        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(provider.title, color = Secondary, fontSize = 16.sp)
                            when (request.phase) {
                                InsuranceRequestPhase.READY -> {
                                    Text("요청 내용을 확인해 주세요", color = Ink, fontSize = 21.sp, fontWeight = FontWeight.SemiBold)
                                    Text(service.description, color = Blue, fontSize = 17.sp)
                                }
                                InsuranceRequestPhase.SUBMITTING -> {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                        CircularProgressIndicator(Modifier.size(24.dp), color = Blue, strokeWidth = 2.dp)
                                        Text("모의 요청을 처리하고 있어요", color = Ink, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                                    }
                                    Text("요청 내용을 확인하고 있어요.", color = Secondary, fontSize = 15.sp)
                                }
                                InsuranceRequestPhase.COMPLETED -> {
                                    Text("모의 접수 완료", color = Blue, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                                    Text("실제 출동은 요청되지 않았어요.", color = Ink, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                                    request.result?.let { receipt ->
                                        Spacer(Modifier.height(4.dp))
                                        Text("모의 접수번호", color = Secondary, fontSize = 13.sp)
                                        Text(receipt.requestId, color = Ink, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
                                        Text(
                                            Instant.ofEpochMilli(receipt.requestedAtMs).atZone(ZoneId.systemDefault())
                                                .format(DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm")),
                                            color = Secondary, fontSize = 14.sp,
                                        )
                                    }
                                }
                                InsuranceRequestPhase.FAILED -> {
                                    Text("모의 요청을 완료하지 못했어요", color = Ink, fontSize = 21.sp, fontWeight = FontWeight.SemiBold)
                                    Text(request.message ?: "잠시 후 다시 시도해 주세요.", color = Secondary, fontSize = 15.sp, lineHeight = 22.sp)
                                }
                            }
                        }
                    }
                    usage?.let {
                        Text(if (request.phase == InsuranceRequestPhase.COMPLETED)
                            "${service.title} 사용 ${it.usedFor(service)}회 · 공통 잔여 ${it.remaining}회"
                            else "공통 잔여 ${it.remaining}회 · 접수 완료 시 1회 사용", color = Blue,
                            fontSize = 16.sp, fontWeight = FontWeight.Medium)
                    }
                    if (request.phase != InsuranceRequestPhase.COMPLETED) {
                        Text("요청 흐름을 확인하는 단계예요. 실제 보험사 접수나 출동은 발생하지 않아요.",
                            color = Secondary, fontSize = 15.sp, lineHeight = 23.sp)
                        Surface(color = SurfaceColor, shape = RoundedCornerShape(14.dp), shadowElevation = CardShadowElevation) {
                            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("${service.title} · 서비스 안내", color = Ink, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                                Text(
                                    when (service) {
                                        RoadsideService.BATTERY -> "배터리 방전·시동 도움을 요청하는 과정이에요. 전기차 구동 배터리 충전 지원은 별도 확인이 필요해요."
                                        RoadsideService.TIRE -> "타이어 펑크·교체 도움을 요청하는 과정이에요."
                                        RoadsideService.TOW -> "견인 도움을 요청하는 과정이에요. 가능 거리와 비용은 실제 가입 계약에 따라 달라요."
                                        RoadsideService.ROADSIDE -> "차량 고장이나 운행이 어려운 상황에 도움을 요청하는 과정이에요."
                                    },
                                    color = Secondary, fontSize = 15.sp, lineHeight = 23.sp,
                                )
                            }
                        }
                    }
                    TextButton(
                        onClick = { if (browsingAllowed) showOfficialChannels = !showOfficialChannels },
                        enabled = browsingAllowed,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    ) { Text(if (showOfficialChannels) "실제 도움 요청 접기" else "실제 도움이 필요하세요?", fontSize = 15.sp) }
                    if (showOfficialChannels) {
                        Surface(color = SurfaceColor, shape = RoundedCornerShape(14.dp), shadowElevation = CardShadowElevation) {
                            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text("실제 도움 요청", color = Ink, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                                Text("${provider.title}  ·  ${provider.phone}", color = Blue, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                                Text("현재 위치·차량번호·연락처를 준비해 주세요. 보장 범위와 비용은 보험사에서 확인해 주세요.",
                                    color = Secondary, fontSize = 14.sp, lineHeight = 21.sp)
                                OutlinedButton(
                                    onClick = {
                                        if (browsingAllowed) {
                                            feedback = if (ExternalActions.dial(context, provider.phone)) {
                                                "전화 앱에서 보험사에 연결해 ${service.title} 서비스를 요청해 주세요."
                                            } else {
                                                "전화 앱을 열 수 없어요. ${provider.phone} 번호로 휴대전화에서 연락하거나 번호를 복사해 주세요."
                                            }
                                        }
                                    }, enabled = browsingAllowed,
                                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                                ) { Text("전화 앱으로 요청", fontSize = 16.sp) }
                                OutlinedButton(
                                    onClick = {
                                        if (browsingAllowed) {
                                            feedback = if (ExternalActions.web(context, provider.mobileUrl)) {
                                                "보험사 공식 페이지에서 서비스 접수를 진행해 주세요."
                                            } else {
                                                "웹 페이지를 열 수 없어요. 주소를 복사해 휴대전화에서 열어 주세요."
                                            }
                                        }
                                    }, enabled = browsingAllowed,
                                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                                ) { Text("공식 접수 페이지", fontSize = 16.sp) }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    TextButton(onClick = {
                                        if (browsingAllowed) {
                                            ExternalActions.copy(context, "${provider.title} 연락처", provider.phone)
                                            feedback = "연락처 ${provider.phone}를 복사했어요."
                                        }
                                    }, enabled = browsingAllowed, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                                        Text("번호 복사", fontSize = 14.sp)
                                    }
                                    TextButton(onClick = {
                                        if (browsingAllowed) {
                                            ExternalActions.copy(context, "${provider.title} 공식 페이지", provider.mobileUrl)
                                            feedback = "공식 페이지 주소를 복사했어요."
                                        }
                                    }, enabled = browsingAllowed, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                                        Text("주소 복사", fontSize = 14.sp)
                                    }
                                }
                                feedback?.let { Text(it, color = Blue, fontSize = 14.sp, lineHeight = 21.sp) }
                            }
                        }
                    }
                }
                Button(
                    onClick = {
                        if (browsingAllowed) {
                            when (request.phase) {
                                InsuranceRequestPhase.READY, InsuranceRequestPhase.FAILED -> onSubmit()
                                InsuranceRequestPhase.COMPLETED -> dismiss()
                                InsuranceRequestPhase.SUBMITTING -> Unit
                            }
                        }
                    },
                    enabled = browsingAllowed && request.phase != InsuranceRequestPhase.SUBMITTING,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text(when (request.phase) {
                        InsuranceRequestPhase.READY -> "요청하기"
                        InsuranceRequestPhase.SUBMITTING -> "모의 요청 처리 중"
                        InsuranceRequestPhase.COMPLETED -> "확인"
                        InsuranceRequestPhase.FAILED -> "모의 요청 다시 시도"
                    }, fontSize = 17.sp)
                }
            }
        }
        }
      }
    }
}
