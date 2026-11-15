package com.moadrive.pleos.ui.child

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moadrive.pleos.care.child.ChildProfile
import com.moadrive.pleos.care.child.ChildSchoolDay
import com.moadrive.pleos.care.school.*
import com.moadrive.pleos.ui.*
import com.moadrive.pleos.ui.care.CareSectionSymbol
import com.moadrive.pleos.ui.common.DetailCard
import com.moadrive.pleos.ui.common.SoftSurface as Surface
import kotlinx.coroutines.CancellationException
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
internal fun ChildSchoolCard(
    profile: ChildProfile?,
    day: ChildSchoolDay?,
    today: LocalDate,
    editable: Boolean,
    browsingAllowed: Boolean,
    placeSearchAllowed: Boolean,
    repository: NeisSchoolRepository,
    onEdit: () -> Unit,
    onSelectSchool: () -> Unit,
    onFindPlace: (String) -> Unit,
    onTodayMeals: (SchoolMealResult?) -> Unit,
    preview: Boolean = false,
    sampleMeals: SchoolMealResult? = null,
) {
    val school = profile?.linkedSchool
    val schoolKey = school?.let { "${it.officeCode}:${it.schoolCode}" }.orEmpty()
    var dateString by rememberSaveable(schoolKey) { mutableStateOf(today.toString()) }
    val selectedDate = if (preview) sampleMeals?.date ?: today else LocalDate.parse(dateString)
    val dateBrowsingAllowed = browsingAllowed && !preview
    var refresh by remember { mutableStateOf(0) }
    var result by remember(schoolKey, dateString) { mutableStateOf<SchoolMealResult?>(null) }
    var loading by remember(schoolKey, dateString) { mutableStateOf(school != null) }
    var error by remember(schoolKey, dateString) { mutableStateOf<String?>(null) }
    var mealDetails by rememberSaveable(schoolKey, dateString) { mutableStateOf(false) }
    val currentTodayMeals by rememberUpdatedState(onTodayMeals)
    val displayedMeals = if (preview) sampleMeals else result
    val activityDate = if (preview) day?.date ?: today else today

    LaunchedEffect(schoolKey, dateString, today, refresh, browsingAllowed, preview) {
        if (preview || school == null || !browsingAllowed) { loading = false; return@LaunchedEffect }
        loading = true; error = null
        try {
            val loaded = repository.fetchSchoolMeals(school, selectedDate)
            result = loaded
            if (loaded.date == today) currentTodayMeals(loaded)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: SchoolApiException) {
            error = failure.userMessage
            if (selectedDate == today) currentTodayMeals(null)
        } catch (_: Exception) {
            error = "급식 정보를 불러오지 못했어요. 연결을 확인하고 다시 시도해 주세요."
            if (selectedDate == today) currentTodayMeals(null)
        } finally { loading = false }
    }

    DetailCard {
        ChildCardHeader("오늘의 학교 생활", if (preview) "실제 공개 식단과 샘플 수업으로 살펴보는 하루"
            else "학교에서 제공하는 급식과 직접 적은 하루", Blue, CareSectionSymbol.SCHOOL)
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val identity: @Composable () -> Unit = {
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(school?.name ?: profile?.school?.takeIf { it.isNotBlank() } ?: "학교를 선택해 주세요",
                        color = Ink, fontSize = 19.sp, lineHeight = 27.sp, fontWeight = FontWeight.SemiBold)
                    Text(if (preview) "나이스 공개 급식으로 미리 보는 샘플 학교예요."
                        else if (school != null) listOf(school.region, school.address).filter { it.isNotBlank() }.distinct().joinToString(" · ")
                        else "학교를 연결하면 날짜별 실제 급식을 볼 수 있어요.", color = Secondary, fontSize = 12.sp, lineHeight = 19.sp)
                }
            }
            val selectSchool: @Composable () -> Unit = {
                OutlinedButton(onClick = onSelectSchool, enabled = editable, modifier = Modifier.heightIn(min = 48.dp),
                    shape = RoundedCornerShape(12.dp), contentPadding = PaddingValues(horizontal = 12.dp)) {
                    MoaIcon(MoaIconKind.SEARCH, Modifier.size(18.dp), Blue, outline = true)
                    Spacer(Modifier.width(6.dp))
                    Text(if (profile == null) "프로필 등록" else if (school == null) "학교 선택" else "학교 변경", fontSize = 13.sp)
                }
            }
            if (maxWidth >= 480.dp && LocalDensity.current.fontScale <= 1.3f) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) { identity() }
                    if (!preview) selectSchool()
                }
            } else Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                identity()
                if (!preview) Box(Modifier.align(Alignment.End)) { selectSchool() }
            }
        }
        if (preview && displayedMeals != null) {
            Text("나이스 실제 공개 식단 · ${selectedDate.format(DateTimeFormatter.ofPattern("yyyy.MM.dd (E)", Locale.KOREAN))}",
                color = AccentGreen, fontSize = 13.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium)
        } else if (school != null) {
            HorizontalDivider(color = Line)
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val wide = maxWidth >= 520.dp
                Column {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        TextButton(onClick = { dateString = selectedDate.minusDays(1).toString() }, enabled = dateBrowsingAllowed && selectedDate > LocalDate.of(2000, 1, 1),
                            modifier = Modifier.heightIn(min = 48.dp), contentPadding = PaddingValues(horizontal = 4.dp)) { Text("‹  이전 날", fontSize = 12.sp) }
                        Text(selectedDate.format(DateTimeFormatter.ofPattern("yyyy.MM.dd (E)", Locale.KOREAN)), Modifier.weight(1f),
                            color = Ink, fontSize = 14.sp, fontWeight = FontWeight.Medium, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        TextButton(onClick = { dateString = selectedDate.plusDays(1).toString() }, enabled = dateBrowsingAllowed && selectedDate < LocalDate.of(2100, 12, 31),
                            modifier = Modifier.heightIn(min = 48.dp), contentPadding = PaddingValues(horizontal = 4.dp)) { Text("다음 날  ›", fontSize = 12.sp) }
                        if (wide) TextButton(onClick = { refresh++ }, enabled = dateBrowsingAllowed && !loading,
                            modifier = Modifier.heightIn(min = 48.dp), contentPadding = PaddingValues(horizontal = 6.dp)) { Text("↻ 새로고침", fontSize = 12.sp) }
                    }
                    if (!wide || selectedDate != today) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        if (selectedDate != today) TextButton(onClick = { dateString = today.toString() }, enabled = dateBrowsingAllowed,
                            modifier = Modifier.heightIn(min = 48.dp)) { Text("오늘", fontSize = 12.sp) }
                        if (!wide) TextButton(onClick = { refresh++ }, enabled = dateBrowsingAllowed && !loading,
                            modifier = Modifier.heightIn(min = 48.dp)) { Text("↻ 새로고침", fontSize = 12.sp) }
                    }
                }
            }
        }
        Surface(shape = RoundedCornerShape(14.dp), color = CardColor) {
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val split = maxWidth >= 480.dp && LocalDensity.current.fontScale <= 1.3f
                val activities: @Composable () -> Unit = {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        ChildSchoolPanelTitle("수업 · 활동", Blue, meal = false)
                        Text("${activityDate.monthValue}월 ${activityDate.dayOfMonth}일 · ${if (preview) "샘플 수업" else "직접 입력"}", color = Secondary, fontSize = 11.sp, lineHeight = 17.sp)
                        Text(day?.classes?.takeIf { it.isNotBlank() } ?: "알림장에서 확인한 오늘의 활동을 적어 주세요.",
                            color = Ink, fontSize = 14.sp, lineHeight = 22.sp)
                        if (!preview && !day?.meal.isNullOrBlank()) {
                            Text("식사 메모", color = Secondary, fontSize = 11.sp)
                            Text(day!!.meal, color = Ink, fontSize = 13.sp, lineHeight = 20.sp)
                        }
                        FilledTonalButton(onClick = onEdit, enabled = editable, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                            shape = RoundedCornerShape(10.dp), contentPadding = PaddingValues(horizontal = 10.dp)) {
                            Text("수업·메모 입력", Modifier.weight(1f), fontSize = 13.sp)
                            Text("›", fontSize = 19.sp)
                        }
                    }
                }
                val meals: @Composable () -> Unit = {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        ChildSchoolPanelTitle(when {
                            preview -> "공개 급식 미리보기"
                            selectedDate == today -> "오늘의 급식"
                            else -> "이날의 급식"
                        }, AccentGreen, meal = true)
                        when {
                            preview && displayedMeals?.meals.isNullOrEmpty() ->
                                Text("공개된 샘플 급식이 아직 없어요.", color = Secondary, fontSize = 14.sp, lineHeight = 22.sp)
                            !preview && school == null -> Text("학교를 선택하면 공개된 급식이 여기에 표시돼요.", color = Secondary, fontSize = 14.sp, lineHeight = 22.sp)
                            !preview && loading -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                                Text("급식을 불러오고 있어요", color = Secondary, fontSize = 13.sp)
                            }
                            !preview && error != null -> Text(error!!, color = OrangeInk, fontSize = 13.sp, lineHeight = 20.sp)
                            displayedMeals?.meals?.isEmpty() == true -> Text("이 날짜에 공개된 급식 정보가 없어요.", color = Secondary, fontSize = 14.sp, lineHeight = 22.sp)
                            displayedMeals != null -> displayedMeals.meals.forEachIndexed { index, meal ->
                                if (index > 0) HorizontalDivider(color = Line.copy(alpha = .6f))
                                ChildPublishedMeal(meal, mealDetails)
                            }
                            !browsingAllowed -> Text("급식 조회는 주차 후 이용해 주세요.", color = Secondary, fontSize = 13.sp)
                        }
                        if (school != null || displayedMeals != null)
                            Text(displayedMeals?.source ?: SCHOOL_SOURCE_NAME, color = Secondary, fontSize = 11.sp, lineHeight = 17.sp)
                        if (displayedMeals?.meals?.isNotEmpty() == true) TextButton(onClick = { mealDetails = !mealDetails }, enabled = browsingAllowed,
                            modifier = Modifier.heightIn(min = 48.dp), contentPadding = PaddingValues(0.dp)) {
                            Text(if (mealDetails) "상세 정보 접기  ‹" else "영양 · 알레르기 · 원산지 상세  ›", fontSize = 11.sp)
                        }
                        if (mealDetails) Text("메뉴의 숫자와 영양정보는 학교에서 제공한 표기예요.", color = Secondary, fontSize = 11.sp, lineHeight = 17.sp)
                    }
                }
                if (split) Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                    Box(Modifier.weight(1f)) { activities() }
                    VerticalDivider(color = Line)
                    Box(Modifier.weight(1f)) { meals() }
                } else Column {
                    activities()
                    HorizontalDivider(color = Line)
                    meals()
                }
            }
        }
        if (school != null && !preview) TextButton(onClick = {
            if (placeSearchAllowed) onFindPlace("${school.name} ${school.address}")
        }, enabled = placeSearchAllowed, modifier = Modifier.heightIn(min = 48.dp).align(Alignment.End), contentPadding = PaddingValues(horizontal = 4.dp)) {
            Text(if (placeSearchAllowed) "학교 위치 찾기  ›" else "학교 위치 검색은 주차 후 이용해 주세요", fontSize = 12.sp)
        }
    }
}

@Composable
private fun ChildPublishedMeal(meal: SchoolMeal, details: Boolean) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Surface(color = AccentGreen.copy(alpha = .09f), shape = RoundedCornerShape(8.dp)) {
                Text(meal.mealType.ifBlank { "학교 급식" }, Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    color = AccentGreen, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            }
            if (meal.calories.isNotBlank()) Text(meal.calories, Modifier.weight(1f), color = Ink,
                fontSize = 15.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold,
                textAlign = androidx.compose.ui.text.style.TextAlign.End)
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            meal.menu.lineSequence().filter { it.isNotBlank() }.forEach { dish ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                    Box(Modifier.padding(top = 9.dp).size(4.dp).background(AccentGreen.copy(alpha = .7f), CircleShape))
                    Text(dish, Modifier.weight(1f), color = Ink, fontSize = 14.sp, lineHeight = 22.sp)
                }
            }
        }
        if (meal.nutrition.isNotBlank()) {
            Surface(color = PaleBlue.copy(alpha = if (IsDarkTheme) .35f else .55f), shape = RoundedCornerShape(10.dp)) {
                Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text("영양 정보", color = Blue, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    Text(if (details) meal.nutrition else meal.nutrition.lineSequence().filter { it.isNotBlank() }.take(3).joinToString("\n"),
                        color = Secondary, fontSize = 12.sp, lineHeight = 19.sp)
                }
            }
        }
        if (meal.allergyText.isNotBlank()) {
            Text("알레르기 표기", color = OrangeInk, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            Text(if (details) meal.allergyText else meal.allergyText.lineSequence().first(),
                color = Secondary, fontSize = 11.sp, lineHeight = 18.sp)
        }
        if (details && meal.origin.isNotBlank()) {
            Text("원산지", color = Blue, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            Text(meal.origin, color = Secondary, fontSize = 11.sp, lineHeight = 18.sp)
        }
    }
}

@Composable
private fun ChildSchoolPanelTitle(title: String, tint: Color, meal: Boolean) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(20.dp)) {
            val u = size.minDimension / 24f
            fun line(a: Float, b: Float, c: Float, d: Float) = drawLine(tint, Offset(a*u,b*u), Offset(c*u,d*u), 1.7f*u, StrokeCap.Round)
            if (meal) {
                line(5f, 3f, 5f, 11f); line(8f,3f,8f,21f); line(11f,3f,11f,11f)
                line(5f,11f,11f,11f); line(18f,3f,18f,21f); line(16f,3f,16f,13f); line(16f,13f,18f,13f)
            } else {
                drawRoundRect(tint, Offset(4f*u,4f*u), Size(16f*u,18f*u), CornerRadius(2f*u), style = Stroke(1.7f*u))
                line(9f,2f,9f,6f); line(15f,2f,15f,6f)
                line(8f,10f,16f,10f); line(8f,14f,16f,14f); line(8f,18f,13f,18f)
            }
        }
        Text(title, color = Ink, fontSize = 16.sp, lineHeight = 23.sp, fontWeight = FontWeight.Medium)
    }
}
