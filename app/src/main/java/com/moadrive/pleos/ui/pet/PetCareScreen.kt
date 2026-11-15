package com.moadrive.pleos.ui.pet

import androidx.compose.foundation.background
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moadrive.pleos.care.pet.*
import com.moadrive.pleos.ui.*
import com.moadrive.pleos.ui.care.CareArt
import com.moadrive.pleos.ui.care.CareArtwork
import com.moadrive.pleos.ui.care.CareNewsSection
import com.moadrive.pleos.ui.care.CareNewsTopic
import com.moadrive.pleos.ui.care.CareSectionIcon
import com.moadrive.pleos.ui.care.CareSectionSymbol
import com.moadrive.pleos.ui.common.*
import com.moadrive.pleos.ui.assist.AiGuideAvatar
import com.moadrive.pleos.ui.common.SoftSurface as Surface
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.delay

private enum class PetDialog { PROFILE, RECORD, HISTORY }

@Composable
fun PetCareScreen(
    browsingAllowed: Boolean,
    formsAllowed: Boolean,
    onHome: () -> Unit,
    onService: (HomeService) -> Unit,
    onFindPlace: (String) -> Unit,
    navigationAllowed: Boolean = true,
) {
    val context = LocalContext.current
    val store = remember(context) { PetCareStore(context) }
    var saved by remember(store) { mutableStateOf(store.load()) }
    var sampleMode by rememberSaveable { mutableStateOf(false) }
    var sampleSpeciesName by rememberSaveable { mutableStateOf(saved.profile.species.name) }
    var today by remember { mutableStateOf(LocalDate.now()) }
    var recordDate by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    var dialog by remember { mutableStateOf<PetDialog?>(null) }
    val pinnedRecordDate = remember(recordDate) { LocalDate.parse(recordDate) }
    val sampleSpecies = PetSpecies.fromStored(sampleSpeciesName)
    val displayed = remember(saved, sampleMode, sampleSpecies, today) { saved.displayData(sampleMode, today, sampleSpecies) }
    val record = displayed.recordFor(today)
    val suggestions = remember(displayed, today) { PetCareGuidance.forDay(displayed, today) }
    val canEdit = browsingAllowed && formsAllowed && !sampleMode
    val canSearch = browsingAllowed && navigationAllowed

    LaunchedEffect(Unit) {
        while (true) {
            today = LocalDate.now()
            delay(60_000)
        }
    }
    LaunchedEffect(browsingAllowed, formsAllowed) {
        if (!browsingAllowed || (!formsAllowed && dialog != PetDialog.HISTORY)) dialog = null
        if (!browsingAllowed) sampleMode = false
    }

    DetailPage(HomeService.PET_CARE, browsingAllowed, onHome, onService, left = {
        DetailHeading("반려동물 케어", "강아지와 고양이, 함께하는 하루를 살펴봐요")
        CareArtwork(CareArt.PET, Modifier.fillMaxWidth().height(if (LocalDetailExpanded.current) 280.dp else 160.dp))
        PetSpeciesSelector(displayed.profile.species, if (sampleMode) browsingAllowed else canEdit) { species ->
            if (sampleMode) sampleSpeciesName = species.name
            else if (canEdit && species != saved.profile.species) {
                saved = saved.withProfile(saved.profileFor(species))
                store.save(saved)
            }
            dialog = null
        }
        PetProfileSummary(displayed.profile, record, canEdit, sampleMode,
            onProfile = { if (canEdit) dialog = PetDialog.PROFILE },
            onRecord = {
                if (canEdit) {
                    recordDate = LocalDate.now().toString()
                    dialog = PetDialog.RECORD
                }
            },
            onHistory = { if (browsingAllowed) dialog = PetDialog.HISTORY },
            browsingAllowed = browsingAllowed,
        )
        if (!formsAllowed) Text("프로필과 하루 기록은 주차 후 입력해 주세요.", color = Secondary, fontSize = 13.sp, lineHeight = 20.sp)
    }, right = {
        InfoReveal(0) {
            SampleDataHeader("반려동물의 하루 살펴보기", "기록에 맞춘 케어와 함께 갈 장소를 확인해요",
                sampleMode, browsingAllowed, onToggle = {
                    if (browsingAllowed) {
                        if (!sampleMode) sampleSpeciesName = saved.profile.species.name
                        sampleMode = !sampleMode
                        dialog = null
                    }
                })
        }
        InfoReveal(1) { PetGuidanceCard(suggestions, displayed.profile.species, sampleMode, browsingAllowed) }
        if (!canSearch) Text("장소 검색과 길 안내는 주차 후 이용해 주세요.",
            color = Secondary, fontSize = 13.sp, lineHeight = 20.sp)
        InfoReveal(2) { PetNearbyCard(displayed.profile.species, canSearch, onFindPlace) }
        InfoReveal(3) { PetOutingsCard(displayed.profile.species, canSearch, onFindPlace) }
        InfoReveal(4) { CareNewsSection(CareNewsTopic.PET, enabled = browsingAllowed) }
    })

    if (browsingAllowed) when (dialog) {
        PetDialog.PROFILE -> if (canEdit) PetProfileDialog(saved, onDismiss = { dialog = null }) { profile ->
            saved = saved.withProfile(profile)
            store.save(saved)
            dialog = null
        }
        PetDialog.RECORD -> if (canEdit) PetRecordDialog(saved.recordFor(pinnedRecordDate), pinnedRecordDate, saved.profile,
            onDismiss = { dialog = null }) { entry ->
            saved = saved.withRecord(entry)
            store.save(saved)
            dialog = null
        }
        PetDialog.HISTORY -> PetHistoryDialog(displayed, sampleMode, onDismiss = { dialog = null })
        null -> Unit
    }
}

@Composable
private fun PetProfileSummary(
    profile: PetProfile,
    record: PetDailyRecord?,
    canEdit: Boolean,
    sampleMode: Boolean,
    onProfile: () -> Unit,
    onRecord: () -> Unit,
    onHistory: () -> Unit,
    browsingAllowed: Boolean,
) {
    DetailSummary {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(profile.displayName, color = Ink, fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
                val details = listOfNotNull(profile.species.label, profile.ageYears?.let { "${it.petNumber()}세" }, profile.weightKg?.let { "${it.petNumber()}kg" })
                Text(details.joinToString(" · "),
                    color = Secondary, fontSize = 15.sp, lineHeight = 22.sp)
            }
            if (sampleMode) PetTag("예시", AccentOrange)
            else TextButton(onClick = onProfile, enabled = canEdit, modifier = Modifier.heightIn(min = 48.dp),
                contentPadding = PaddingValues(horizontal = 6.dp)) { Text("프로필 편집", fontSize = 14.sp) }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("오늘의 기록", Modifier.weight(1f), color = Ink, fontSize = 17.sp, fontWeight = FontWeight.Medium)
            TextButton(onClick = onHistory, enabled = browsingAllowed, modifier = Modifier.heightIn(min = 48.dp),
                contentPadding = PaddingValues(horizontal = 4.dp)) { Text("기록 보기 ›", fontSize = 13.sp) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PetMetric(profile.species.activityLabel, record?.walkMinutes?.let { "${it}분" } ?: "—", AccentGreen,
                progress = record?.walkMinutes?.let { minutes -> profile.usualWalkMinutes?.takeIf { it > 0 }?.let { minutes.toFloat() / it } },
                modifier = Modifier.weight(1f))
            PetMetric("식사", record?.mealGrams?.let { "${it.petNumber()}g" } ?: "—", AccentOrange,
                progress = record?.mealGrams?.let { grams -> profile.usualMealGrams?.takeIf { it > 0 }?.let { (grams / it).toFloat() } },
                modifier = Modifier.weight(1f))
            PetMetric("상태", if (record?.conditionNote.isNullOrBlank()) "—" else "메모", Blue,
                progress = if (record?.conditionNote.isNullOrBlank()) 0f else 1f, modifier = Modifier.weight(1f))
        }
        Text(if (sampleMode) "${profile.displayName}의 하루를 가정한 예시예요."
            else "직접 입력한 기록 · 막대는 평소량 대비와 메모 여부를 표시해요",
            color = Secondary, fontSize = 12.sp, lineHeight = 18.sp)
        Button(onClick = onRecord, enabled = canEdit,
            modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp), shape = RoundedCornerShape(14.dp)) {
            Text(if (sampleMode) "예시 모드에서는 저장하지 않아요" else if (record == null) "오늘 기록하기" else "오늘 기록 수정", fontSize = 15.sp)
        }
    }
}

@Composable
private fun PetMetric(label: String, value: String, accent: Color, progress: Float?, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Text(label, color = Secondary, fontSize = 13.sp)
        Text(value, color = accent, fontSize = 25.sp, fontWeight = FontWeight.Medium)
        RoundedMetricBar(progress ?: 0f, color = accent,
            trackColor = accent.copy(alpha = if (IsDarkTheme) .17f else .10f),
            modifier = Modifier.semantics {
                contentDescription = when {
                    progress == null -> "$label 평소 비교 기준 또는 오늘 기록 없음"
                    label == "상태" -> if (progress > 0f) "상태 메모 기록됨" else "상태 메모 없음"
                    else -> "$label 보호자가 정한 평소량 대비"
                }
            })
    }
}

@Composable
private fun PetGuidanceCard(suggestions: List<PetSuggestion>, species: PetSpecies, sampleMode: Boolean, enabled: Boolean) {
    DetailCard {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            AiGuideAvatar(Modifier.size(44.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text("오늘의 맞춤 케어", color = Ink, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                Text("기록에서 찾은 하루 힌트", color = Secondary, fontSize = 13.sp)
            }
            PetTag(if (sampleMode) "예시 기록" else "내 기록 기반", Blue)
        }
        suggestions.forEachIndexed { index, suggestion ->
            val accent = when (suggestion.kind) {
                PetSuggestionKind.ACTIVITY -> AccentGreen
                PetSuggestionKind.MEAL -> AccentOrange
                PetSuggestionKind.CONDITION -> Blue
            }
            if (index > 0) HorizontalDivider(color = Line.copy(alpha = .7f))
            PetGuidanceRow(suggestion, species, accent, sampleMode, enabled)
        }
        Text("보호자 기록 요약 · 건강 진단이나 권장 운동·급여량은 아니에요.",
            color = Secondary, fontSize = 12.sp, lineHeight = 18.sp)
    }
}

@Composable
private fun PetGuidanceRow(suggestion: PetSuggestion, species: PetSpecies, accent: Color,
    sampleMode: Boolean, enabled: Boolean) {
    var expanded by rememberSaveable(suggestion.kind, species, sampleMode) { mutableStateOf(false) }
    val label = when (suggestion.kind) {
        PetSuggestionKind.ACTIVITY -> if (species == PetSpecies.CAT) "놀이·활동" else "산책·활동"
        PetSuggestionKind.MEAL -> "식사량"
        PetSuggestionKind.CONDITION -> "상태 메모"
    }
    val icon = when (suggestion.kind) {
        PetSuggestionKind.ACTIVITY -> MoaIconKind.TRIPS
        PetSuggestionKind.MEAL -> MoaIconKind.PET_CARE
        PetSuggestionKind.CONDITION -> MoaIconKind.CONDITION_NOTE
    }
    Surface(onClick = { expanded = !expanded }, enabled = enabled,
        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
            .semantics { stateDescription = if (expanded) "상세 펼침" else "상세 접힘" }, color = Color.Transparent,
        shape = RoundedCornerShape(12.dp)) {
        Column(Modifier.padding(vertical = 5.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val inline = maxWidth >= 460.dp && LocalDensity.current.fontScale <= 1.15f
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    MoaIcon(icon, Modifier.size(23.dp), accent)
                    if (inline) {
                        Text(label, Modifier.width(90.dp), color = Ink, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                        Text(suggestion.title, Modifier.weight(1f), color = Secondary, fontSize = 15.sp, lineHeight = 23.sp)
                    } else {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(label, color = Ink, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                            Text(suggestion.title, color = Secondary, fontSize = 14.sp, lineHeight = 21.sp)
                        }
                    }
                    Text(if (expanded) "⌃" else "›", color = Secondary, fontSize = 23.sp)
                }
            }
            if (expanded) Column(Modifier.padding(start = 35.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(suggestion.description, color = Secondary, fontSize = 14.sp, lineHeight = 22.sp)
                Text(if (suggestion.source == PetSuggestionSource.USER_RECORD)
                    if (sampleMode) "출처 · 예시로 만든 보호자 기록" else "출처 · 보호자가 입력한 오늘 기록·프로필"
                    else "아직 오늘 기록이 없어요", color = accent, fontSize = 11.sp, lineHeight = 16.sp)
            }
        }
    }
}

@Composable
private fun PetNearbyCard(species: PetSpecies, enabled: Boolean, onFindPlace: (String) -> Unit) {
    DetailCard {
        PetSectionTitle("가까운 케어 공간", "필요한 도움을 찾고, 방문 전 준비를 확인해요", AccentGreen, CareSectionSymbol.NEARBY)
        val isCat = species == PetSpecies.CAT
        val categories = listOf(
            PetPlaceCategory("동물병원", if (isCat) "고양이 진료" else "진료·상담", species.searchQuery(PetPlaceSearch.CLINIC), MoaIconKind.INSURANCE),
            PetPlaceCategory("동물약국", "취급·상담", species.searchQuery(PetPlaceSearch.PHARMACY), MoaIconKind.CONDITION_NOTE),
            PetPlaceCategory("호텔링", "돌봄·위탁", species.searchQuery(PetPlaceSearch.HOTEL), MoaIconKind.HOME),
            PetPlaceCategory("미용", "이용·상담", species.searchQuery(PetPlaceSearch.GROOMING), MoaIconKind.PET_CARE),
        )
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val fontScale = LocalDensity.current.fontScale
            val columns = when {
                maxWidth >= 480.dp && fontScale <= 1.15f -> 4
                maxWidth >= 260.dp && fontScale <= 1.5f -> 2
                else -> 1
            }
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                categories.chunked(columns).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        row.forEach { category ->
                            Surface(onClick = { if (enabled) onFindPlace(category.query) }, enabled = enabled,
                                modifier = Modifier.weight(1f).heightIn(min = 112.dp), shape = RoundedCornerShape(14.dp),
                                color = if (IsDarkTheme) Blue.copy(alpha = .07f) else BackgroundColor) {
                                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    MoaIcon(category.icon, Modifier.size(26.dp), Blue)
                                    Text(category.label, color = Ink, fontSize = 15.sp, lineHeight = 21.sp, fontWeight = FontWeight.Medium)
                                    Text(category.subtitle, color = Secondary, fontSize = 12.sp, lineHeight = 18.sp)
                                    Text("주변 검색 ›", color = Blue, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
        Text("거리·연락처는 검색에서, 영업·이용 조건은 방문 전 업체에 확인해 주세요.",
            color = Secondary, fontSize = 12.sp, lineHeight = 18.sp)
    }
}

private data class PetPlaceCategory(val label: String, val subtitle: String, val query: String, val icon: MoaIconKind)

@Composable
private fun PetOutingsCard(species: PetSpecies, enabled: Boolean, onFindPlace: (String) -> Unit) {
    DetailCard {
        val isCat = species == PetSpecies.CAT
        PetSectionTitle(if (isCat) "필요한 외출, 편안하게" else "함께 가는 곳",
            if (isCat) "이동이 필요할 때, 성향과 편안함을 먼저 살펴봐요" else "반려동물과 더 특별한 하루를 보내세요",
            AccentGreen, CareSectionSymbol.OUTING)
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val threeColumns = maxWidth >= 480.dp && LocalDensity.current.fontScale <= 1.15f
            val tiles: @Composable (Modifier) -> Unit = { modifier ->
                PetOutingTile(if (isCat) "동반 카페" else "공원", if (isCat) "편안한 휴식" else "산책과 휴식",
                    AccentGreen, MoaIconKind.TRIPS, modifier, enabled) { onFindPlace(species.searchQuery(PetPlaceSearch.OUTING)) }
                PetOutingTile("식당", "맛있는 한 끼", AccentOrange, MoaIconKind.PET_CARE, modifier, enabled) {
                    onFindPlace(species.searchQuery(PetPlaceSearch.RESTAURANT))
                }
                PetOutingTile("숙소", "편안한 밤", Blue, MoaIconKind.HOME, modifier, enabled) {
                    onFindPlace(species.searchQuery(PetPlaceSearch.STAY))
                }
            }
            if (threeColumns) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                tiles(Modifier.weight(1f))
            } else Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                tiles(Modifier.fillMaxWidth())
            }
        }
        Text(if (isCat) "고양이 동반 여부·이동장·분리 공간과 객실 안전 규정은 방문 전 확인해 주세요."
            else "장소 검색으로 연결돼요. 동반 가능 구역·크기·이동장 조건과 추가 요금은 방문 전 확인해 주세요.",
            color = Secondary, fontSize = 12.sp, lineHeight = 18.sp)
    }
}

@Composable
private fun PetOutingTile(title: String, description: String, accent: Color,
    icon: MoaIconKind, modifier: Modifier, enabled: Boolean, onClick: () -> Unit) {
    Surface(onClick = { if (enabled) onClick() }, enabled = enabled,
        modifier = modifier.heightIn(min = 72.dp).semantics { contentDescription = "$title 주변 검색, $description" },
        shape = RoundedCornerShape(14.dp), color = if (IsDarkTheme) Blue.copy(alpha = .07f) else BackgroundColor) {
        Row(Modifier.padding(11.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            Box(Modifier.size(38.dp).background(accent.copy(alpha = .10f), RoundedCornerShape(11.dp)), contentAlignment = Alignment.Center) {
                MoaIcon(icon, Modifier.size(24.dp), accent)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("$title ›", color = Ink, fontSize = 15.sp, lineHeight = 21.sp, fontWeight = FontWeight.Medium)
                Text(description, color = Secondary, fontSize = 12.sp, lineHeight = 18.sp)
            }
        }
    }
}

@Composable
private fun PetSectionTitle(title: String, description: String, accent: Color, symbol: CareSectionSymbol) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        CareSectionIcon(symbol, accent)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, color = Ink, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            Text(description, color = Secondary, fontSize = 13.sp, lineHeight = 20.sp)
        }
    }
}

@Composable
private fun PetTag(label: String, accent: Color) {
    Text(label, Modifier.background(accent.copy(alpha = .10f), RoundedCornerShape(8.dp))
        .padding(horizontal = 8.dp, vertical = 5.dp), color = accent, fontSize = 11.sp, fontWeight = FontWeight.Medium)
}

@Composable
private fun PetSpeciesSelector(selected: PetSpecies, enabled: Boolean = true, onSelect: (PetSpecies) -> Unit) {
    Row(Modifier.fillMaxWidth().selectableGroup().background(Line.copy(alpha = if (IsDarkTheme) .55f else .40f), RoundedCornerShape(17.dp))
        .padding(5.dp), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        PetSpecies.entries.forEach { species ->
            val active = species == selected
            Surface(modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                .selectable(selected = active, enabled = enabled, role = Role.Tab, onClick = { onSelect(species) }),
                color = if (active) SurfaceColor else Color.Transparent, shape = RoundedCornerShape(13.dp),
                shadowElevation = if (active) 1.dp else 0.dp) {
                Box(Modifier.padding(vertical = 12.dp, horizontal = 8.dp), contentAlignment = Alignment.Center) {
                    Text(species.label, color = if (active) Ink else Secondary, fontSize = 16.sp,
                        fontWeight = if (active) FontWeight.Medium else FontWeight.Normal)
                }
            }
        }
    }
}

@Composable
private fun PetProfileDialog(data: PetCareData, onDismiss: () -> Unit, onSave: (PetProfile) -> Unit) {
    var input by remember(data) { mutableStateOf(PetProfileInput.from(data.profile)) }
    var drafts by remember(data) { mutableStateOf(emptyMap<PetSpecies, PetProfileInput>()) }
    var error by remember { mutableStateOf<String?>(null) }
    PetEditorDialog("우리 반려동물 프로필", "강아지와 고양이 중 함께하는 반려동물을 골라 주세요.", onDismiss, error,
        onSave = { val result = input.validate(); error = result.error; result.value?.let(onSave) },
        animateSave = input.validate().value != null) {
        PetSpeciesSelector(input.species) { species ->
            drafts = drafts + (input.species to input)
            input = drafts[species] ?: PetProfileInput.from(data.profileFor(species))
            error = null
        }
        PetField("이름 (선택)", input.name, { input = input.copy(name = it); error = null })
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PetField("나이 · 세 (선택)", input.ageYears, { input = input.copy(ageYears = it); error = null },
                Modifier.weight(1f), KeyboardType.Decimal)
            PetField("몸무게 · kg (선택)", input.weightKg, { input = input.copy(weightKg = it); error = null },
                Modifier.weight(1f), KeyboardType.Decimal)
        }
        PetField("평소 하루 식사량 · g (선택)", input.usualMealGrams,
            { input = input.copy(usualMealGrams = it); error = null }, keyboardType = KeyboardType.Decimal)
        PetField("평소 하루 ${input.species.activityLabel} · 분 (선택)", input.usualWalkMinutes,
            { input = input.copy(usualWalkMinutes = it); error = null }, keyboardType = KeyboardType.Number)
        Text("평소 양과 시간은 보호자가 정한 비교 기준이에요. 권장 급여량이나 운동량으로 사용하지 않아요.",
            color = Secondary, fontSize = 13.sp, lineHeight = 20.sp)
        if (input.species != data.profile.species) Text("강아지와 고양이의 프로필·평소 양·하루 기록은 각각 보관해요.",
            color = Blue, fontSize = 13.sp, lineHeight = 20.sp)
    }
}

@Composable
private fun PetRecordDialog(record: PetDailyRecord?, date: LocalDate, profile: PetProfile,
    onDismiss: () -> Unit, onSave: (PetDailyRecord) -> Unit) {
    var input by remember(record, date) { mutableStateOf(PetRecordInput.from(record)) }
    var error by remember { mutableStateOf<String?>(null) }
    PetEditorDialog("오늘의 하루 기록", "${date.format(PetDate)} · ${profile.displayName}", onDismiss, error,
        onSave = { val result = input.validate(date, profile.species); error = result.error; result.value?.let(onSave) },
        animateSave = input.validate(date, profile.species).value != null) {
        PetField("오늘 ${profile.species.activityLabel} 시간 · 분", input.walkMinutes,
            { input = input.copy(walkMinutes = it); error = null }, keyboardType = KeyboardType.Number)
        PetField("오늘 먹은 식사량 · g", input.mealGrams,
            { input = input.copy(mealGrams = it); error = null }, keyboardType = KeyboardType.Decimal)
        profile.usualMealGrams?.let {
            Text("프로필에 기록한 평소 하루 식사량 ${it.petNumber()}g", color = AccentOrange, fontSize = 13.sp)
        }
        OutlinedTextField(value = input.conditionNote,
            onValueChange = { input = input.copy(conditionNote = it); error = null },
            label = { Text("활동·식욕·배변 등 상태 메모") }, placeholder = { Text("직접 살핀 작은 변화를 적어 주세요") },
            minLines = 3, maxLines = 5, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
        Text("아직 확인하지 않은 항목은 비워 두세요. ${profile.species.activityLabel}·식사가 없었다면 0으로 기록할 수 있어요.",
            color = Secondary, fontSize = 13.sp, lineHeight = 20.sp)
    }
}

@Composable
private fun PetField(label: String, value: String, onChange: (String) -> Unit,
    modifier: Modifier = Modifier, keyboardType: KeyboardType = KeyboardType.Text) {
    OutlinedTextField(value = value, onValueChange = onChange, modifier = modifier.fillMaxWidth(),
        label = { Text(label, fontSize = 14.sp) }, singleLine = true, shape = RoundedCornerShape(12.dp),
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType))
}

@Composable
private fun PetEditorDialog(title: String, subtitle: String, onDismiss: () -> Unit,
    error: String?, onSave: () -> Unit, animateSave: Boolean, content: @Composable ColumnScope.() -> Unit) {
    DimmedDialog(onDismissRequest = onDismiss) {
        val dismiss = rememberDialogAction(onDismiss)
        val save = rememberDialogAction(onSave)
        Surface(Modifier.padding(18.dp).widthIn(max = 580.dp).fillMaxWidth().heightIn(max = 720.dp),
            shape = RoundedCornerShape(24.dp), color = SurfaceColor, shadowElevation = CardShadowElevation) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(title, color = Ink, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
                Text(subtitle, color = Secondary, fontSize = 14.sp, lineHeight = 21.sp)
                Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(14.dp), content = content)
                if (error != null) Text(error, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End)) {
                    TextButton(onClick = dismiss, modifier = Modifier.heightIn(min = 48.dp)) { Text("취소") }
                    Button(onClick = if (animateSave) save else onSave, modifier = Modifier.heightIn(min = 48.dp)) { Text("저장") }
                }
            }
        }
    }
}

@Composable
private fun PetHistoryDialog(data: PetCareData, sampleMode: Boolean, onDismiss: () -> Unit) {
    val records = data.records.filter { it.species == data.profile.species }
    DimmedDialog(onDismissRequest = onDismiss) {
        val dismiss = rememberDialogAction(onDismiss)
        Surface(Modifier.padding(18.dp).widthIn(max = 580.dp).fillMaxWidth().heightIn(max = 680.dp),
            shape = RoundedCornerShape(24.dp), color = SurfaceColor, shadowElevation = CardShadowElevation) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(if (sampleMode) "예시 하루 기록" else "함께 쌓은 하루 기록", color = Ink, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
                Text(if (sampleMode) "${data.profile.displayName}의 하루를 가정한 예시예요." else "${data.profile.species.label} 기록 · 최근 90개의 하루 기록 중에서 확인해요.",
                    color = Secondary, fontSize = 14.sp, lineHeight = 21.sp)
                Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    if (records.isEmpty()) Text("아직 저장한 기록이 없어요. 오늘의 첫 기록을 남겨 주세요.",
                        color = Secondary, fontSize = 15.sp, lineHeight = 23.sp)
                    records.forEach { record ->
                        Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                            Text(record.date.format(PetDate), color = Ink, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                            val walk = record.walkMinutes?.let { "${it}분" } ?: "미기록"
                            val meal = record.mealGrams?.let { "${it.petNumber()}g" } ?: "미기록"
                            Text("${record.species.activityLabel} $walk · 식사 $meal", color = Secondary, fontSize = 14.sp)
                            if (record.conditionNote.isNotBlank()) Text(record.conditionNote, color = Secondary, fontSize = 14.sp, lineHeight = 21.sp)
                            HorizontalDivider(color = Line)
                        }
                    }
                }
                Button(onClick = dismiss, modifier = Modifier.align(Alignment.End).heightIn(min = 48.dp)) { Text("닫기") }
            }
        }
    }
}

private val PetDate = DateTimeFormatter.ofPattern("yyyy.MM.dd")
