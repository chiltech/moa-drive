package com.moagauge.pleos.ui.child

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moagauge.pleos.care.school.*
import com.moagauge.pleos.ui.*
import com.moagauge.pleos.ui.common.DimmedDialog
import com.moagauge.pleos.ui.common.LocalDialogMotion
import com.moagauge.pleos.ui.common.rememberDialogAction
import com.moagauge.pleos.ui.common.SoftSurface as Surface
import kotlinx.coroutines.CancellationException

internal data class ChildSchoolSelection(val school: SchoolInfo?)
internal val ChildSchoolInfoSaver = listSaver<ChildSchoolSelection, String>(
    save = { selection -> selection.school?.let { listOf(it.officeCode, it.schoolCode, it.name, it.address, it.region) }.orEmpty() },
    restore = { ChildSchoolSelection(if (it.size == 5) SchoolInfo(it[0], it[1], it[2], it[3], it[4]) else null) },
)

@Composable
internal fun ChildSchoolSelectionDialog(
    repository: NeisSchoolRepository,
    selected: SchoolInfo?,
    legacyName: String,
    onSelect: (SchoolInfo) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf((selected?.name ?: legacyName).take(60)) }
    var requestedQuery by rememberSaveable { mutableStateOf("") }
    var requestVersion by remember { mutableStateOf(0) }
    var page by remember { mutableStateOf(1) }
    var schools by remember { mutableStateOf<List<SchoolInfo>>(emptyList()) }
    var result by remember { mutableStateOf<SchoolSearchResult?>(null) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val keyboard = LocalSoftwareKeyboardController.current

    fun search() {
        if (query.trim().length < 2) return
        keyboard?.hide()
        schools = emptyList(); result = null; error = null; page = 1
        requestedQuery = query.trim(); requestVersion++
    }

    LaunchedEffect(requestedQuery, page, requestVersion) {
        if (requestedQuery.isEmpty()) return@LaunchedEffect
        loading = true; error = null
        try {
            val loaded = repository.searchSchools(requestedQuery, page)
            result = loaded
            schools = (if (page == 1) loaded.schools else schools + loaded.schools)
                .distinctBy { "${it.officeCode}:${it.schoolCode}" }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: SchoolApiException) {
            error = failure.userMessage
        } catch (_: Exception) {
            error = "학교 정보를 불러오지 못했어요. 연결을 확인하고 다시 검색해 주세요."
        } finally { loading = false }
    }

    DimmedDialog(onDismissRequest = onDismiss) {
        val dismiss = rememberDialogAction(onDismiss)
        val motion = LocalDialogMotion.current
        Surface(Modifier.padding(20.dp).widthIn(max = 650.dp).fillMaxWidth().fillMaxHeight(.9f),
            color = SurfaceColor, shape = RoundedCornerShape(24.dp), shadowElevation = CardShadowElevation) {
            Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("학교 검색·선택", color = Ink, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
                Text("학교 이름을 검색한 뒤 지역과 주소를 확인해 주세요.", color = Secondary, fontSize = 14.sp, lineHeight = 21.sp)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(value = query, onValueChange = { query = it.take(60) }, modifier = Modifier.weight(1f),
                        label = { Text("학교 이름", fontSize = 14.sp) }, singleLine = true, shape = RoundedCornerShape(14.dp),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { search() }))
                    Button(onClick = ::search, enabled = query.trim().length >= 2 && !loading,
                        modifier = Modifier.heightIn(min = 48.dp), shape = RoundedCornerShape(14.dp)) { Text("검색", fontSize = 14.sp) }
                }
                if (requestedQuery.isEmpty()) Text("두 글자 이상 입력해 주세요. 초·중·고등학교 등 나이스에서 제공하는 학교를 찾을 수 있어요.", color = Secondary, fontSize = 13.sp, lineHeight = 20.sp)
                if (requestedQuery.isNotEmpty()) Text("‘$requestedQuery’ 검색 결과", color = Blue, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(schools, key = { "${it.officeCode}:${it.schoolCode}" }) { school ->
                        Surface(onClick = { motion.close { onSelect(school) } }, modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp), color = CardColor, shadowElevation = CardShadowElevation) {
                            Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(school.name, Modifier.weight(1f), color = Ink, fontSize = 17.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold)
                                    if (selected?.officeCode == school.officeCode && selected?.schoolCode == school.schoolCode) Text("선택됨", color = Blue, fontSize = 12.sp)
                                }
                                if (school.region.isNotBlank()) Text(school.region, color = Blue, fontSize = 13.sp)
                                Text(school.address.ifEmpty { "주소 정보가 제공되지 않았어요" }, color = Secondary, fontSize = 13.sp, lineHeight = 20.sp)
                            }
                        }
                    }
                    if (loading) item {
                        Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                            Text("학교를 찾고 있어요", color = Secondary, fontSize = 14.sp)
                        }
                    }
                    if (error != null) item { Text(error!!, color = OrangeInk, fontSize = 14.sp, lineHeight = 21.sp) }
                    if (!loading && error == null && result != null && schools.isEmpty()) item {
                        Text("검색된 학교가 없어요. 학교 이름을 다시 확인해 주세요.", color = Secondary, fontSize = 14.sp, lineHeight = 21.sp)
                    }
                    if (result?.limitedWithoutKey == true) item {
                        Text("검색 결과가 일부만 표시됐어요. 학교 이름을 더 정확히 입력해 주세요.", color = OrangeInk, fontSize = 13.sp, lineHeight = 20.sp)
                    }
                    if (result?.hasMore == true && !loading && error == null) item {
                        OutlinedButton(onClick = { page++ }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("검색 결과 더 보기") }
                    }
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("출처 · ${result?.source ?: "나이스 교육정보 개방 포털"}", Modifier.weight(1f), color = Secondary, fontSize = 12.sp)
                    TextButton(onClick = dismiss, modifier = Modifier.heightIn(min = 48.dp)) { Text("닫기") }
                }
            }
        }
    }
}
