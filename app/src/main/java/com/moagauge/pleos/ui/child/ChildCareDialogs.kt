package com.moagauge.pleos.ui.child

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moagauge.pleos.care.child.*
import com.moagauge.pleos.care.school.NeisSchoolRepository
import com.moagauge.pleos.ui.*
import com.moagauge.pleos.ui.common.DimmedDialog
import com.moagauge.pleos.ui.common.rememberDialogAction
import com.moagauge.pleos.ui.common.SoftSurface as Surface
import java.time.LocalDate
import java.util.UUID

@Composable
internal fun ChildProfileDialog(profile: ChildProfile?, schoolRepository: NeisSchoolRepository, onSave: (ChildProfile) -> Unit, onDismiss: () -> Unit) {
    var name by rememberSaveable { mutableStateOf(profile?.name.orEmpty()) }
    var age by rememberSaveable { mutableStateOf(profile?.age?.toString().orEmpty()) }
    var gender by rememberSaveable { mutableStateOf(profile?.gender ?: ChildGender.UNSPECIFIED) }
    var legacySchool by rememberSaveable { mutableStateOf(profile?.school.orEmpty()) }
    var schoolSelection by rememberSaveable(stateSaver = ChildSchoolInfoSaver) { mutableStateOf(ChildSchoolSelection(profile?.linkedSchool)) }
    val selectedSchool = schoolSelection.school
    var selectingSchool by rememberSaveable { mutableStateOf(false) }
    var attempted by rememberSaveable { mutableStateOf(false) }
    val schoolName = selectedSchool?.name ?: legacySchool
    val error = validateChildProfile(name, age, schoolName)
    if (selectingSchool) ChildSchoolSelectionDialog(schoolRepository, selectedSchool, legacySchool,
        onSelect = { schoolSelection = ChildSchoolSelection(it); legacySchool = ""; selectingSchool = false },
        onDismiss = { selectingSchool = false })
    else ChildFormDialog(if (profile == null) "아이 프로필 등록" else "아이 프로필 수정", onDismiss, onSave = {
        attempted = true
        if (error == null) onSave(ChildProfile(name.trim(), age.toInt(), gender, schoolName.trim(), selectedSchool))
    }, animateSave = error == null) {
        Text("이름 대신 애칭을 사용해도 좋아요. 입력한 정보는 이 기기에 저장돼요.", color = Secondary, fontSize = 14.sp, lineHeight = 21.sp)
        ChildTextField(name, { name = it.take(24) }, "이름 또는 애칭", "아이를 부르는 이름")
        ChildTextField(age, { age = it.take(2) }, "나이", "0~18세", KeyboardType.Number)
        Text("성별 · 선택", color = Secondary, fontSize = 14.sp)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ChildGender.entries.forEach { option ->
                FilterChip(selected = gender == option, onClick = { gender = option },
                    label = { Text(option.label, fontSize = 13.sp) }, modifier = Modifier.weight(1f).heightIn(min = 48.dp))
            }
        }
        Text("학교 · 선택", color = Secondary, fontSize = 14.sp)
        if (selectedSchool != null) {
            Text(selectedSchool.name, color = Ink, fontSize = 17.sp, fontWeight = FontWeight.Medium)
            Text(selectedSchool.address, color = Secondary, fontSize = 13.sp, lineHeight = 20.sp)
        } else if (legacySchool.isNotBlank()) {
            Text("기존 입력 · $legacySchool", color = Ink, fontSize = 15.sp)
            Text("실제 급식을 보려면 검색 결과에서 학교를 다시 선택해 주세요.", color = OrangeInk, fontSize = 13.sp, lineHeight = 20.sp)
        } else Text("학교를 선택하면 실제 급식 정보를 가져와요.", color = Secondary, fontSize = 14.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = rememberDialogAction { selectingSchool = true }, modifier = Modifier.heightIn(min = 48.dp), shape = RoundedCornerShape(14.dp)) {
                Text(if (selectedSchool == null) "학교 검색·선택" else "학교 변경", fontSize = 14.sp)
            }
            if (selectedSchool != null || legacySchool.isNotBlank()) TextButton(onClick = { schoolSelection = ChildSchoolSelection(null); legacySchool = "" }, modifier = Modifier.heightIn(min = 48.dp)) {
                Text("선택 해제", fontSize = 14.sp)
            }
        }
        if (attempted && error != null) Text(error, color = MaterialTheme.colorScheme.error, fontSize = 14.sp)
    }
}

@Composable
internal fun ChildSchoolDayDialog(day: ChildSchoolDay?, editingDate: LocalDate, onSave: (ChildSchoolDay) -> Unit, onDismiss: () -> Unit) {
    var classes by rememberSaveable { mutableStateOf(day?.classes.orEmpty()) }
    var meal by rememberSaveable { mutableStateOf(day?.meal.orEmpty()) }
    ChildFormDialog("오늘의 학교 생활", onDismiss, onSave = {
        onSave(ChildSchoolDay(editingDate, classes.trim(), meal.trim()))
    }) {
        Text("${editingDate.monthValue}월 ${editingDate.dayOfMonth}일 · 직접 입력", color = Blue, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        Text("알림장에서 확인한 수업과 기억할 식사 메모를 적어 주세요. 대화 주제에 함께 반영해요.", color = Secondary, fontSize = 14.sp, lineHeight = 21.sp)
        ChildTextField(classes, { classes = it.take(100) }, "오늘의 수업·활동", "예: 미술, 과학 실험", singleLine = false)
        ChildTextField(meal, { meal = it.take(120) }, "식사 메모 · 직접 입력", "예: 오늘 먹은 간식", singleLine = false)
        Text("학교 급식은 선택한 학교에서 별도로 조회해요. 비워 두고 저장하면 직접 입력한 내용을 지워요.", color = Secondary, fontSize = 13.sp, lineHeight = 20.sp)
    }
}

@Composable
internal fun ChildReminderDialog(
    reminder: ChildVaccinationReminder?,
    today: LocalDate,
    notificationsAllowed: Boolean,
    onOpenNotificationSettings: () -> Unit,
    onSave: (ChildVaccinationReminder) -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    var title by rememberSaveable { mutableStateOf(reminder?.title.orEmpty()) }
    var date by rememberSaveable { mutableStateOf((reminder?.date ?: today).toString()) }
    var note by rememberSaveable { mutableStateOf(reminder?.note.orEmpty()) }
    var notificationsEnabled by rememberSaveable { mutableStateOf(reminder?.notificationsEnabled ?: true) }
    var attempted by rememberSaveable { mutableStateOf(false) }
    val parsedDate = parseChildReminderDate(date)
    val error = when {
        title.trim().isEmpty() -> "접종 이름이나 확인할 내용을 입력해 주세요."
        parsedDate == null -> "날짜를 YYYY-MM-DD 형식으로 입력해 주세요. (2000~2100년)"
        else -> null
    }
    ChildFormDialog(if (reminder == null) "예방접종 일정 추가" else "예방접종 일정 수정", onDismiss, onSave = {
        attempted = true
        if (error == null && parsedDate != null) onSave(ChildVaccinationReminder(
            reminder?.id ?: UUID.randomUUID().toString(), title.trim(), parsedDate, note.trim(), reminder?.completedOn, notificationsEnabled))
    }, extraAction = {
        if (onDelete != null) TextButton(onClick = rememberDialogAction(onDelete), modifier = Modifier.heightIn(min = 48.dp)) {
            Text("삭제", color = MaterialTheme.colorScheme.error)
        }
    }, animateSave = error == null) {
        Text("의료기관에서 안내받은 접종 날짜를 직접 기록해 주세요.", color = Secondary, fontSize = 14.sp, lineHeight = 21.sp)
        ChildTextField(title, { title = it.take(40) }, "접종 이름·확인할 내용", "일정 이름을 입력해 주세요")
        ChildTextField(date, { date = it.take(10) }, "예정일", "YYYY-MM-DD", KeyboardType.Ascii)
        ChildTextField(note, { note = it.take(120) }, "메모 · 선택", "방문할 병원이나 준비물", singleLine = false)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = notificationsEnabled, onCheckedChange = { notificationsEnabled = it },
                modifier = Modifier.semantics { contentDescription = "예방접종 일정 알림 받기" })
            Text("알림 받기", color = Ink, fontSize = 15.sp)
        }
        Text("하루 전 오전 9시에 한 번 알려드려요. 그 시간이 지났다면 당일 오전 9시에 알려드려요. 차량 상태에 따라 알림이 늦어질 수 있어요.",
            color = Secondary, fontSize = 13.sp, lineHeight = 20.sp)
        if (notificationsEnabled && !notificationsAllowed) {
            Text("알림을 받으려면 앱 알림 권한을 켜 주세요.", color = OrangeInk, fontSize = 13.sp)
            TextButton(onClick = onOpenNotificationSettings, modifier = Modifier.heightIn(min = 48.dp)) { Text("알림 설정 열기", fontSize = 14.sp) }
        }
        Text("당일 오전 9시 이후에 새로 등록한 일정은 앱에서 확인해 주세요. 접종 시기는 의료기관의 안내를 따라 주세요.", color = Secondary, fontSize = 13.sp, lineHeight = 20.sp)
        if (attempted && error != null) Text(error, color = MaterialTheme.colorScheme.error, fontSize = 14.sp)
    }
}

@Composable
private fun ChildTextField(
    value: String, onChange: (String) -> Unit, label: String, placeholder: String,
    keyboardType: KeyboardType = KeyboardType.Text, singleLine: Boolean = true,
) {
    OutlinedTextField(value = value, onValueChange = onChange, modifier = Modifier.fillMaxWidth(),
        label = { Text(label, fontSize = 14.sp) }, placeholder = { Text(placeholder, fontSize = 14.sp) },
        singleLine = singleLine, maxLines = if (singleLine) 1 else 3,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType), shape = RoundedCornerShape(14.dp))
}

@Composable
private fun ChildFormDialog(
    title: String,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
    extraAction: (@Composable () -> Unit)? = null,
    animateSave: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    DimmedDialog(onDismissRequest = onDismiss) {
        val dismiss = rememberDialogAction(onDismiss)
        val save = rememberDialogAction(onSave)
        Surface(Modifier.padding(20.dp).widthIn(max = 560.dp).fillMaxWidth().fillMaxHeight(.9f),
            color = SurfaceColor, shape = RoundedCornerShape(24.dp), shadowElevation = CardShadowElevation) {
            Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(title, color = Ink, fontSize = 24.sp, lineHeight = 32.sp, fontWeight = FontWeight.SemiBold)
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(13.dp), content = content)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    extraAction?.invoke()
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = dismiss, modifier = Modifier.heightIn(min = 48.dp)) { Text("취소") }
                    Button(onClick = if (animateSave) save else onSave, modifier = Modifier.heightIn(min = 48.dp), shape = RoundedCornerShape(14.dp)) { Text("저장") }
                }
            }
        }
    }
}
