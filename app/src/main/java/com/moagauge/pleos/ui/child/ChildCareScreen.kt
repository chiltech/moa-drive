package com.moagauge.pleos.ui.child

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moagauge.pleos.care.child.*
import com.moagauge.pleos.care.school.NeisSchoolRepository
import com.moagauge.pleos.care.school.SchoolMealResult
import com.moagauge.pleos.BuildConfig
import com.moagauge.pleos.ui.*
import com.moagauge.pleos.ui.care.CareArt
import com.moagauge.pleos.ui.care.CareArtwork
import com.moagauge.pleos.ui.care.CareNewsSection
import com.moagauge.pleos.ui.care.CareNewsTopic
import com.moagauge.pleos.ui.care.CareSectionIcon
import com.moagauge.pleos.ui.care.CareSectionSymbol
import com.moagauge.pleos.ui.common.*
import com.moagauge.pleos.ui.assist.AiGuideAvatar
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

@Composable
fun ChildCareScreen(
    browsingAllowed: Boolean,
    formsAllowed: Boolean,
    onHome: () -> Unit,
    onService: (HomeService) -> Unit,
    onFindPlace: (String) -> Unit,
    navigationAllowed: Boolean = true,
) {
    val context = LocalContext.current
    val store = remember(context) { ChildCareStore(context) }
    val schoolRepository = remember { NeisSchoolRepository(BuildConfig.NEIS_API_KEY) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var data by remember(store) { mutableStateOf(store.load()) }
    var notificationsAllowed by remember(context) { mutableStateOf(ChildReminderNotifications.notificationsAllowed(context)) }
    var notificationSettingsFailed by remember { mutableStateOf(false) }
    var today by remember { mutableStateOf(childLocalDate()) }
    var todayMeals by remember(data.profile?.linkedSchool, today) { mutableStateOf<SchoolMealResult?>(null) }
    var profileOpen by rememberSaveable { mutableStateOf(false) }
    var schoolSelectionOpen by rememberSaveable { mutableStateOf(false) }
    var schoolOpen by rememberSaveable { mutableStateOf(false) }
    var editingSchoolDate by rememberSaveable { mutableStateOf(today.toString()) }
    var reminderOpen by rememberSaveable { mutableStateOf(false) }
    var reminderId by rememberSaveable { mutableStateOf<String?>(null) }
    var deleteReminderId by rememberSaveable { mutableStateOf<String?>(null) }
    var preview by rememberSaveable { mutableStateOf(false) }
    val editable = browsingAllowed && formsAllowed && !preview
    val sampleData = remember(today) {
        ChildCareData(
            profile = ChildProfile("하루", 8, ChildGender.UNSPECIFIED,
                ChildSchoolMealSample.school.name, ChildSchoolMealSample.school),
            schoolDay = ChildSchoolMealSample.day,
            reminders = listOf(
                ChildVaccinationReminder("sample-upcoming", "예방접종 일정 확인", today.plusDays(7),
                    note = "보호자가 입력한 일정의 샘플이에요.", notificationsEnabled = false),
                ChildVaccinationReminder("sample-complete", "예방접종 기록 확인", today.minusDays(14),
                    note = "완료한 접종을 기록한 샘플이에요.", completedOn = today.minusDays(14), notificationsEnabled = false),
            ),
        )
    }
    val displayedData = if (preview) sampleData else data
    val currentDay = data.schoolDayFor(today)
    val conversationDay = childConversationSchoolDay(today, currentDay, todayMeals)
    val displayedDay = if (preview) sampleData.schoolDay else conversationDay
    val prompts = childConversationPrompts(displayedData.profile, displayedDay)
    val reminders = displayedData.reminders.sortedChildReminders()
    fun save(updated: ChildCareData) { store.save(updated); data = updated }
    fun openNotificationSettings() {
        if (editable) notificationSettingsFailed = !ChildReminderNotifications.openNotificationSettings(context)
    }

    DisposableEffect(context, lifecycle) {
        fun refreshNotifications() {
            notificationsAllowed = ChildReminderNotifications.notificationsAllowed(context)
            ChildReminderNotifications.reschedule(context)
        }
        refreshNotifications()
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) refreshNotifications() }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(Unit) {
        while (isActive) { today = childLocalDate(); delay(30_000) }
    }
    LaunchedEffect(editable) {
        if (!editable) { profileOpen = false; schoolSelectionOpen = false; schoolOpen = false; reminderOpen = false; deleteReminderId = null }
    }
    LaunchedEffect(browsingAllowed) { if (!browsingAllowed) preview = false }

    DetailPage(HomeService.CHILD_CARE, browsingAllowed, onHome, onService, left = {
        DetailHeading("자녀 케어", "아이의 하루에 한 걸음 더 가까이")
        CareArtwork(CareArt.CHILD, Modifier.fillMaxWidth().height(if (LocalDetailExpanded.current) 280.dp else 160.dp))
        ChildProfileSummary(displayedData.profile, editable) { profileOpen = true }
        HorizontalDivider(color = Line)
        Text(if (preview) "샘플 프로필과 일정은 저장하거나 알림을 보내지 않아요." else "프로필과 일정은 이 기기에 저장돼요.",
            color = if (preview) OrangeInk else Secondary, fontSize = 12.sp, lineHeight = 18.sp)
        if (!browsingAllowed || !formsAllowed) Text("정보 입력과 일정 관리는 주차 후 이용해 주세요.", color = Secondary, fontSize = 14.sp, lineHeight = 21.sp)
    }, right = {
        InfoReveal(0) {
            SampleDataHeader("아이의 하루 살펴보기", "대화와 학교 생활, 챙길 일정을 함께 확인해요",
                preview, browsingAllowed, onToggle = { if (browsingAllowed) preview = !preview })
        }
        InfoReveal(1) {
            ChildConversationCard(prompts, preview, browsingAllowed, displayedData.profile != null,
                displayedDay?.let { it.classes.isNotBlank() || it.meal.isNotBlank() } == true,
                !preview && todayMeals?.takeIf { it.date == today }?.meals?.isNotEmpty() == true)
        }
        InfoReveal(2) {
            ChildSchoolCard(displayedData.profile, if (preview) displayedDay else currentDay, today,
                editable, browsingAllowed, browsingAllowed && navigationAllowed && !preview,
                repository = schoolRepository,
                onEdit = { editingSchoolDate = childLocalDate().toString(); schoolOpen = true },
                onSelectSchool = { if (data.profile == null) profileOpen = true else schoolSelectionOpen = true },
                onFindPlace = onFindPlace, onTodayMeals = { todayMeals = it }, preview = preview,
                sampleMeals = if (preview) ChildSchoolMealSample.meals else null)
        }
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val sideBySide = maxWidth >= 600.dp && LocalDensity.current.fontScale <= 1.2f
            val reminderCard: @Composable () -> Unit = {
                InfoReveal(3) {
                    ChildRemindersCard(reminders, today, editable, browsingAllowed, notificationsAllowed, !preview && notificationSettingsFailed,
                        onNotificationSettings = ::openNotificationSettings,
                        onAdd = { reminderId = null; reminderOpen = true },
                        onEdit = { reminderId = it.id; reminderOpen = true },
                        onComplete = { reminder ->
                            if (editable) save(data.copy(reminders = data.reminders.map {
                                if (it.id == reminder.id) it.copy(completedOn = if (it.completedOn == null) today else null) else it
                            }))
                        })
                }
            }
            val newsPane = rememberDetailPane { modifier ->
                Box(modifier) { InfoReveal(4) { CareNewsSection(topic = CareNewsTopic.CHILD, enabled = browsingAllowed) } }
            }
            if (sideBySide) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.Top) {
                    Box(Modifier.weight(1f)) { reminderCard() }
                    newsPane(Modifier.weight(1f))
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    reminderCard()
                    newsPane(Modifier.fillMaxWidth())
                }
            }
        }
    })

    if (profileOpen && editable) ChildProfileDialog(data.profile, schoolRepository,
        onSave = { if (editable) { save(data.copy(profile = it)); profileOpen = false } },
        onDismiss = { profileOpen = false })
    if (schoolSelectionOpen && editable) ChildSchoolSelectionDialog(schoolRepository, data.profile?.linkedSchool, data.profile?.school.orEmpty(),
        onSelect = { school ->
            if (editable) data.profile?.let { profile -> save(data.copy(profile = profile.copy(school = school.name, schoolInfo = school))) }
            schoolSelectionOpen = false
        }, onDismiss = { schoolSelectionOpen = false })
    if (schoolOpen && editable) {
        // Keep the edited calendar day stable while the page's current day advances.
        val editDate = LocalDate.parse(editingSchoolDate)
        ChildSchoolDayDialog(data.schoolDayFor(editDate), editDate,
            onSave = { if (editable) { save(data.copy(schoolDay = it)); preview = false; schoolOpen = false } },
            onDismiss = { schoolOpen = false })
    }
    if (reminderOpen && editable) {
        val editing = data.reminders.firstOrNull { it.id == reminderId }
        ChildReminderDialog(editing, today, notificationsAllowed, ::openNotificationSettings,
            onSave = { reminder ->
                if (editable) {
                    val updated = data.reminders.filterNot { it.id == reminder.id } + reminder
                    if (updated.size <= 50) save(data.copy(reminders = updated))
                    reminderOpen = false
                }
            },
            onDelete = editing?.let { reminder -> { reminderOpen = false; deleteReminderId = reminder.id } },
            onDismiss = { reminderOpen = false })
    }
    val deleting = data.reminders.firstOrNull { it.id == deleteReminderId }
    if (deleting != null && editable) DimmedAlertDialog(
        onDismissRequest = { deleteReminderId = null },
        title = { Text("접종 일정을 삭제할까요?") },
        text = { Text("‘${deleting.title}’ 일정과 메모가 이 기기에서 삭제돼요.") },
        confirmButton = { TextButton(onClick = rememberDialogAction {
            if (editable) save(data.copy(reminders = data.reminders.filterNot { it.id == deleting.id }))
            deleteReminderId = null
        }, modifier = Modifier.heightIn(min = 48.dp)) { Text("삭제", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = rememberDialogAction { deleteReminderId = null }, modifier = Modifier.heightIn(min = 48.dp)) { Text("취소") } })
}

@Composable
private fun ChildProfileSummary(profile: ChildProfile?, editable: Boolean, onEdit: () -> Unit) {
    DetailSummary {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text("우리 아이 프로필", color = Blue, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                Text(profile?.name ?: "아이를 소개해 주세요", color = Ink, fontSize = 24.sp, lineHeight = 32.sp, fontWeight = FontWeight.SemiBold)
            }
            MoaIcon(MoaIconKind.CHILD_CARE, Modifier.size(30.dp), tint = Blue, outline = true)
        }
        if (profile != null) {
            val gender = if (profile.gender == ChildGender.UNSPECIFIED) "성별 미입력" else profile.gender.label
            Text("${profile.age}세 · $gender", color = Secondary, fontSize = 15.sp)
            Text(profile.school.ifEmpty { "학교·유치원 미입력" }, color = Ink, fontSize = 18.sp, lineHeight = 26.sp, fontWeight = FontWeight.Medium)
            profile.linkedSchool?.let { Text(it.address, color = Secondary, fontSize = 13.sp, lineHeight = 20.sp) }
        } else Text("이름·나이·학교를 입력하면 아이에게 맞는 대화 주제를 함께 준비해요.", color = Secondary, fontSize = 15.sp, lineHeight = 23.sp)
        OutlinedButton(onClick = onEdit, enabled = editable, shape = CircleShape, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            Text(if (profile == null) "아이 프로필 등록" else "프로필 수정", Modifier.weight(1f), fontSize = 15.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            Text("›", fontSize = 24.sp)
        }
    }
}

@Composable
private fun ChildConversationCard(
    prompts: List<ChildConversationPrompt>, preview: Boolean, enabled: Boolean,
    hasProfile: Boolean, hasDailyInput: Boolean, hasOfficialMeal: Boolean,
) {
    var moreOpen by rememberSaveable { mutableStateOf(false) }
    val source = when {
        preview -> "샘플 수업과 실제 공개 식단으로 만든 예시 대화예요."
        hasOfficialMeal -> "학교가 공개한 오늘 급식을 반영했어요."
        hasDailyInput -> "직접 적은 오늘의 수업·식사 메모를 반영했어요."
        hasProfile -> "아이의 이름과 나이에 맞춘 대화 주제예요."
        else -> "프로필 없이도 나눌 수 있는 대화 주제예요."
    }
    LaunchedEffect(enabled) { if (!enabled) moreOpen = false }
    DetailCard {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            ChildConversationSymbol(Blue, Modifier.size(32.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("오늘의 대화 한 조각", color = Ink, fontSize = 21.sp, lineHeight = 29.sp, fontWeight = FontWeight.SemiBold)
                Text("모아가 준비한 우리 아이와의 이야기", color = OrangeInk, fontSize = 13.sp, lineHeight = 20.sp)
            }
            AiGuideAvatar(Modifier.size(46.dp))
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            prompts.take(2).forEach { prompt -> ChildConversationBubble(prompt.question, compact = true) }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(source, Modifier.weight(1f), color = if (preview) OrangeInk else Secondary, fontSize = 11.sp, lineHeight = 17.sp)
            TextButton(onClick = { moreOpen = true }, enabled = enabled, modifier = Modifier.heightIn(min = 48.dp),
                contentPadding = PaddingValues(horizontal = 4.dp)) { Text("대화 주제 더 보기  ›", fontSize = 13.sp) }
        }
    }
    if (moreOpen && enabled) DimmedAlertDialog(
        onDismissRequest = { moreOpen = false },
        title = { Text("오늘의 대화 한 조각") },
        text = {
            Column(Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                prompts.forEach { prompt ->
                    Text(prompt.label, color = Blue, fontSize = 12.sp)
                    ChildConversationBubble(prompt.question, compact = false)
                }
                Text(source, color = Secondary, fontSize = 12.sp, lineHeight = 18.sp)
                if (preview) Text("예시 데이터이며 실제 학교 정보가 아니에요.", color = OrangeInk, fontSize = 12.sp)
            }
        },
        confirmButton = { TextButton(onClick = rememberDialogAction { moreOpen = false }, modifier = Modifier.heightIn(min = 48.dp)) { Text("닫기") } },
    )
}

@Composable
private fun ChildConversationBubble(question: String, compact: Boolean) {
    Row(Modifier.fillMaxWidth().background(Secondary.copy(alpha = if (IsDarkTheme) .09f else .055f), RoundedCornerShape(12.dp))
        .padding(horizontal = 12.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        ChildConversationSymbol(Secondary, Modifier.size(19.dp))
        Text(question, Modifier.weight(1f), color = Ink, fontSize = 15.sp, lineHeight = 23.sp,
            maxLines = if (compact) 2 else Int.MAX_VALUE, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun ChildConversationSymbol(tint: Color, modifier: Modifier) {
    Canvas(modifier) {
        val u = size.minDimension / 24f
        val bubble = Path().apply {
            moveTo(7f*u, 19f*u); lineTo(3f*u, 22f*u); lineTo(4f*u, 16f*u)
            cubicTo(-1f*u, 9f*u, 3f*u, 2f*u, 12f*u, 2f*u)
            cubicTo(25f*u, 2f*u, 25f*u, 19f*u, 14f*u, 19f*u)
            close()
        }
        drawPath(bubble, tint, style = Stroke(1.4f*u, cap = StrokeCap.Round))
        listOf(7f,12f,17f).forEach { drawCircle(tint, .8f*u, Offset(it*u, 10f*u)) }
    }
}

@Composable
internal fun ChildSchoolLine(label: String, value: String, color: Color) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
        Box(Modifier.padding(top = 7.dp).size(7.dp).background(color, CircleShape))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, color = Secondary, fontSize = 13.sp)
            Text(value, color = Ink, fontSize = 15.sp, lineHeight = 22.sp)
        }
    }
}

@Composable
private fun ChildRemindersCard(
    reminders: List<ChildVaccinationReminder>, today: LocalDate, editable: Boolean, browsingAllowed: Boolean,
    notificationsAllowed: Boolean, notificationSettingsFailed: Boolean, onNotificationSettings: () -> Unit,
    onAdd: () -> Unit, onEdit: (ChildVaccinationReminder) -> Unit, onComplete: (ChildVaccinationReminder) -> Unit,
) {
    var detailsOpen by rememberSaveable { mutableStateOf(false) }
    val next = reminders.firstOrNull { it.completedOn == null }
    LaunchedEffect(browsingAllowed) { if (!browsingAllowed) detailsOpen = false }
    DetailCard {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            CareSectionIcon(CareSectionSymbol.VACCINATION, AccentOrange)
            Text("예방접종 알림", color = Ink, fontSize = 18.sp, lineHeight = 25.sp, fontWeight = FontWeight.SemiBold)
        }
        if (!detailsOpen) {
            if (next == null) {
                Text(if (reminders.isEmpty()) "등록한 일정이 없어요" else "등록한 접종을 모두 확인했어요", color = Secondary, fontSize = 14.sp, lineHeight = 21.sp)
            } else {
                Text(next.title, color = Secondary, fontSize = 14.sp, lineHeight = 21.sp)
                Text("${next.date.year}.${next.date.monthValue.toString().padStart(2, '0')}.${next.date.dayOfMonth.toString().padStart(2, '0')}",
                    color = Ink, fontSize = 22.sp, fontWeight = FontWeight.Medium)
                Text(reminderLabel(next, today), color = if (next.date <= today) OrangeInk else Blue, fontSize = 12.sp)
            }
            HorizontalDivider(color = Line)
            TextButton(onClick = { if (reminders.isEmpty()) onAdd() else detailsOpen = true }, enabled = if (reminders.isEmpty()) editable else browsingAllowed,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), contentPadding = PaddingValues(0.dp)) {
                Text(if (reminders.isEmpty()) "접종 일정 추가" else "접종 일정 관리", Modifier.weight(1f), fontSize = 14.sp)
                Text("›", fontSize = 22.sp)
            }
        } else Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (reminders.isEmpty()) {
                Text("아직 등록한 일정이 없어요", color = Ink, fontSize = 18.sp, fontWeight = FontWeight.Medium)
                Text("접종 이름과 날짜를 적어 두고, 완료한 날 체크해 주세요.", color = Secondary, fontSize = 15.sp, lineHeight = 22.sp)
            } else reminders.forEachIndexed { index, reminder ->
                if (index > 0) HorizontalDivider(color = Line.copy(alpha = .65f))
                val status = reminder.status(today)
                val accent = when (status) {
                    ChildReminderStatus.COMPLETE -> AccentGreen
                    ChildReminderStatus.TODAY, ChildReminderStatus.OVERDUE -> OrangeInk
                    ChildReminderStatus.UPCOMING -> Blue
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = reminder.completedOn != null, onCheckedChange = { onComplete(reminder) }, enabled = editable,
                        modifier = Modifier.semantics { contentDescription = "${reminder.title} 완료 ${if (reminder.completedOn == null) "체크" else "취소"}" },
                        colors = CheckboxDefaults.colors(checkedColor = AccentGreen))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(reminder.title, color = Ink, fontSize = 16.sp, lineHeight = 23.sp, fontWeight = FontWeight.Medium)
                        Text("${reminder.date.year}.${reminder.date.monthValue.toString().padStart(2, '0')}.${reminder.date.dayOfMonth.toString().padStart(2, '0')} · ${reminderLabel(reminder, today)}",
                            color = accent, fontSize = 13.sp, lineHeight = 20.sp)
                        if (reminder.note.isNotBlank()) Text(reminder.note, color = Secondary, fontSize = 13.sp, lineHeight = 20.sp)
                        if (reminder.completedOn == null) Text(
                            if (!reminder.notificationsEnabled) "알림 꺼짐" else if (notificationsAllowed) "알림 켜짐" else "알림 권한 필요",
                            color = if (reminder.notificationsEnabled && !notificationsAllowed) OrangeInk else Secondary, fontSize = 12.sp)
                    }
                    TextButton(onClick = { onEdit(reminder) }, enabled = editable, modifier = Modifier.heightIn(min = 48.dp), contentPadding = PaddingValues(horizontal = 8.dp)) {
                        Text("수정", fontSize = 13.sp)
                    }
                }
            }
            OutlinedButton(onClick = onAdd, enabled = editable && reminders.size < 50, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), shape = RoundedCornerShape(14.dp)) {
                Text(if (reminders.size < 50) "+ 접종 일정 추가" else "일정은 50개까지 저장할 수 있어요", fontSize = 14.sp)
            }
            if (!notificationsAllowed && reminders.any { it.notificationsEnabled && it.completedOn == null }) {
                Text("알림을 받으려면 앱 알림 권한을 켜 주세요.", color = OrangeInk, fontSize = 13.sp, lineHeight = 20.sp)
                TextButton(onClick = onNotificationSettings, enabled = editable, modifier = Modifier.heightIn(min = 48.dp)) { Text("알림 설정 열기", fontSize = 14.sp) }
            }
            if (notificationSettingsFailed) Text("알림 설정을 열지 못했어요. 기기 설정에서 MOA DRIVE 알림을 확인해 주세요.", color = OrangeInk, fontSize = 13.sp, lineHeight = 20.sp)
            Text("알림은 하루 전 오전 9시, 늦게 등록하면 당일 오전 9시에 한 번 보내요. 당일 오전 9시 이후에 새로 등록한 일정은 앱에서 확인해 주세요.", color = Secondary, fontSize = 13.sp, lineHeight = 20.sp)
            Text("접종 시기와 필요 여부는 의료기관의 안내를 따라 주세요.", color = Secondary, fontSize = 13.sp, lineHeight = 20.sp)
            TextButton(onClick = { detailsOpen = false }, modifier = Modifier.heightIn(min = 48.dp).align(Alignment.End)) { Text("접기", fontSize = 13.sp) }
        }
    }
}

private fun reminderLabel(reminder: ChildVaccinationReminder, today: LocalDate): String = when (reminder.status(today)) {
    ChildReminderStatus.COMPLETE -> "완료 ${reminder.completedOn!!.monthValue}/${reminder.completedOn.dayOfMonth}"
    ChildReminderStatus.TODAY -> "오늘 확인"
    ChildReminderStatus.OVERDUE -> "지난 일정 · 확인 필요"
    ChildReminderStatus.UPCOMING -> "${ChronoUnit.DAYS.between(today, reminder.date)}일 남음"
}

@Composable
internal fun ChildCardHeader(title: String, subtitle: String, accent: Color, symbol: CareSectionSymbol? = null) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        if (symbol != null) CareSectionIcon(symbol, accent)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(title, color = Ink, fontSize = 21.sp, lineHeight = 29.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = accent, fontSize = 13.sp, lineHeight = 20.sp)
        }
    }
}
