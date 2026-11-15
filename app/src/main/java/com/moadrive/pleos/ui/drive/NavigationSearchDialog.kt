package com.moadrive.pleos.ui.drive

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import com.moadrive.pleos.ui.common.SoftSurface as Surface
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.moadrive.pleos.navigation.DrivePhase
import com.moadrive.pleos.navigation.DriveState
import com.moadrive.pleos.navigation.NavigationDestination
import com.moadrive.pleos.navigation.NavigationPlacesState
import com.moadrive.pleos.navigation.SavedPlaceSlot
import com.moadrive.pleos.ui.*
import com.moadrive.pleos.ui.common.DimmedDialog
import com.moadrive.pleos.ui.common.LocalDialogMotion
import com.moadrive.pleos.ui.common.rememberDialogAction
import kotlinx.coroutines.launch

/** Native search, saved places and recent queries share one bounded, keyboard-aware modal. */
@Composable
fun NavigationSearchDialog(
    state: DriveState,
    places: NavigationPlacesState,
    enabled: Boolean,
    limit: Int,
    onDismiss: () -> Unit,
    onQueryChange: (String) -> Unit,
    onSearch: (String) -> Unit,
    onSelect: (NavigationDestination) -> Unit,
    onSavePlace: (SavedPlaceSlot, NavigationDestination) -> Unit,
    onRemovePlace: (SavedPlaceSlot) -> Unit,
    onRemoveRecentQuery: (String) -> Unit,
    onClearRecentQueries: () -> Unit,
) {
    var registrationName by rememberSaveable { mutableStateOf<String?>(null) }
    val registration = SavedPlaceSlot.entries.firstOrNull { it.name == registrationName }
    val input = remember { SearchInputDismissal() }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val currentDismiss by rememberUpdatedState(onDismiss)
    val searching = state.phase == DrivePhase.SEARCHING
    val actionsAllowed = enabled && state.phase !in listOf(DrivePhase.ROUTING, DrivePhase.STARTING, DrivePhase.GUIDING)
    val dismissSearch = { input.dismiss(); currentDismiss() }
    LaunchedEffect(enabled) {
        if (!enabled) { input.dismiss(); currentDismiss() }
    }
    if (!enabled) return

    fun submit(query: String = state.searchQuery) {
        if (!actionsAllowed || query.isBlank()) return
        input.dismiss()
        onSearch(query)
    }
    fun register(slot: SavedPlaceSlot) {
        if (!actionsAllowed) return
        registrationName = slot.name
        onQueryChange("")
        scope.launch { listState.animateScrollToItem(0) }
    }
    DimmedDialog(onDismissRequest = dismissSearch,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        val close = rememberDialogAction(dismissSearch)
        val motion = LocalDialogMotion.current
        fun choose(destination: NavigationDestination) {
            if (!actionsAllowed) return
            input.dismiss()
            if (registration != null) {
                onSavePlace(registration, destination)
                registrationName = null
            } else motion.close { onSelect(destination) }
        }
        fun openSaved(destination: NavigationDestination) {
            if (!actionsAllowed) return
            input.dismiss()
            motion.close { onSelect(destination) }
        }
        // These owners belong to the dialog's Compose root, not the map behind its window.
        val keyboard = LocalSoftwareKeyboardController.current
        val focus = LocalFocusManager.current
        DisposableEffect(keyboard, focus) {
            input.dismiss = { keyboard?.hide(); focus.clearFocus() }
            onDispose { input.dismiss(); input.dismiss = {} }
        }
        Box(Modifier.fillMaxSize()) {
            // A full-window dialog needs its own outside hit target; native dim remains behind it.
            Box(Modifier.matchParentSize().clickable(
                interactionSource = remember { MutableInteractionSource() }, indication = null,
                onClickLabel = "목적지 검색 닫기", onClick = close,
            ).semantics { contentDescription = "검색창 바깥 영역" })
            BoxWithConstraints(Modifier.fillMaxSize().imePadding(), contentAlignment = Alignment.Center) {
                val compact = maxWidth < 600.dp || maxHeight < 480.dp
                val showSubtitle = maxHeight >= 400.dp
                val inset = if (compact) 12.dp else 20.dp
                val dialogHeight = minOf((maxHeight - inset * 2).coerceAtLeast(1.dp), 720.dp)
                Surface(Modifier.padding(inset).widthIn(max = 840.dp).fillMaxWidth().heightIn(max = dialogHeight),
                    shape = RoundedCornerShape(26.dp), color = SurfaceColor) {
                    Column(Modifier.padding(if (compact) 16.dp else 24.dp), verticalArrangement = Arrangement.spacedBy(if (compact) 12.dp else 16.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("목적지 검색", color = Ink, fontSize = if (compact) 22.sp else 26.sp, fontWeight = FontWeight.Medium)
                                if (showSubtitle) Text("자주 가는 곳과 최근 검색을 빠르게 찾아요", color = Secondary, fontSize = 14.sp)
                            }
                            IconButton(onClick = close, modifier = Modifier.size(48.dp)
                                .semantics { contentDescription = "목적지 검색 닫기" }) {
                                MoaIcon(MoaIconKind.CLOSE, Modifier.size(22.dp), Secondary)
                            }
                        }
                        LazyColumn(Modifier.fillMaxWidth().weight(1f, fill = false),
                            state = listState,
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                            contentPadding = PaddingValues(bottom = 4.dp)) {
                            item(key = "query") {
                                BoxWithConstraints(Modifier.fillMaxWidth()) {
                                    val stacked = maxWidth < 520.dp || LocalDensity.current.fontScale > 1.3f
                                    val field: @Composable (Modifier) -> Unit = { modifier ->
                                        OutlinedTextField(value = state.searchQuery,
                                            onValueChange = { if (actionsAllowed) onQueryChange(it.take(100)) },
                                            modifier = modifier,
                                            enabled = actionsAllowed, singleLine = true,
                                            label = { Text(if (registration == null) "목적지를 검색하세요" else "${registration.label} 위치 검색") },
                                            shape = RoundedCornerShape(22.dp),
                                            colors = OutlinedTextFieldDefaults.colors(
                                                unfocusedBorderColor = Line, focusedBorderColor = Blue,
                                                unfocusedContainerColor = BackgroundColor, focusedContainerColor = BackgroundColor,
                                            ),
                                            leadingIcon = { MoaIcon(MoaIconKind.SEARCH, Modifier.size(20.dp), Secondary, outline = true) },
                                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                            keyboardActions = KeyboardActions(onSearch = { submit() }),
                                            trailingIcon = if (state.searchQuery.isNotEmpty()) ({
                                                IconButton(onClick = { onQueryChange("") }, enabled = actionsAllowed,
                                                    modifier = Modifier.semantics { contentDescription = "검색어 지우기" }) {
                                                    MoaIcon(MoaIconKind.CLOSE, Modifier.size(18.dp), Secondary)
                                                }
                                            }) else null,
                                        )
                                    }
                                    val button: @Composable (Modifier) -> Unit = { modifier ->
                                        Button(onClick = { submit() }, enabled = actionsAllowed && !searching && state.searchQuery.isNotBlank(),
                                            modifier = modifier.heightIn(min = 56.dp), shape = RoundedCornerShape(16.dp)) {
                                            Text("검색", fontSize = 17.sp)
                                        }
                                    }
                                    if (stacked) Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        field(Modifier.fillMaxWidth()); button(Modifier.fillMaxWidth())
                                    } else Row(verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                        field(Modifier.weight(1f)); button(Modifier.width(92.dp).padding(top = 7.dp))
                                    }
                                }
                            }
                            if (registration != null) item(key = "registration") {
                                Surface(shape = RoundedCornerShape(14.dp), color = PaleBlue) {
                                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically) {
                                        Text("검색 결과를 선택하면 ${registration.label} 위치로 저장해요.", Modifier.weight(1f),
                                            color = Ink, fontSize = 14.sp)
                                        TextButton(onClick = { registrationName = null }, enabled = actionsAllowed) { Text("취소") }
                                    }
                                }
                            }
                            item(key = "saved") {
                                BoxWithConstraints(Modifier.fillMaxWidth()) {
                                    val stacked = maxWidth < 500.dp || LocalDensity.current.fontScale > 1.3f
                                    if (stacked) Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                        SavedPlaceSlot.entries.forEach { slot ->
                                            SavedPlaceTile(slot, places.savedPlaces[slot], actionsAllowed,
                                                onOpen = { destination -> openSaved(destination) }, onRegister = { register(slot) },
                                                onRemove = { onRemovePlace(slot) }, modifier = Modifier.fillMaxWidth())
                                        }
                                    } else Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                        SavedPlaceSlot.entries.forEach { slot ->
                                            SavedPlaceTile(slot, places.savedPlaces[slot], actionsAllowed,
                                                onOpen = { destination -> openSaved(destination) }, onRegister = { register(slot) },
                                                onRemove = { onRemovePlace(slot) }, modifier = Modifier.weight(1f))
                                        }
                                    }
                                }
                            }
                            if (searching) item(key = "progress") {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    LinearProgressIndicator(Modifier.fillMaxWidth(), color = Blue, trackColor = PaleBlue, strokeCap = androidx.compose.ui.graphics.StrokeCap.Round)
                                    Text("목적지를 찾고 있어요", color = Secondary, fontSize = 14.sp)
                                }
                            }
                            state.message?.let { message -> item(key = "message") {
                                Text(message, color = Secondary, fontSize = 14.sp, lineHeight = 21.sp)
                            } }
                            if (state.results.isNotEmpty()) {
                                item(key = "result-heading") {
                                    Text(if (registration == null) "검색 결과" else "${registration.label} 위치로 저장할 장소를 선택하세요",
                                        color = Ink, fontSize = 18.sp, fontWeight = FontWeight.Medium)
                                }
                                items(state.results.take(limit.coerceAtLeast(0))) { destination ->
                                    SearchResultCard(destination, places, registration, actionsAllowed,
                                        onChoose = { choose(destination) }, onSave = { slot ->
                                            if (actionsAllowed) onSavePlace(slot, destination)
                                        })
                                }
                            } else {
                                item(key = "recent-heading") {
                                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                        Text("최근 검색어", Modifier.weight(1f), color = Ink, fontSize = 18.sp,
                                            fontWeight = FontWeight.Medium)
                                        if (places.recentQueries.isNotEmpty()) TextButton(onClick = onClearRecentQueries,
                                            enabled = actionsAllowed, modifier = Modifier.heightIn(min = 48.dp)) {
                                            Text("전체 삭제", fontSize = 14.sp)
                                        }
                                    }
                                }
                                if (places.recentQueries.isEmpty()) item(key = "recent-empty") {
                                    Text("아직 검색한 장소가 없어요. 장소 이름이나 주소를 입력해 보세요.",
                                        color = Secondary, fontSize = 14.sp, lineHeight = 22.sp,
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp))
                                }
                                items(places.recentQueries.take(limit.coerceAtLeast(0)), key = { "recent:$it" }) { query ->
                                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                        Row(Modifier.weight(1f).clickable(enabled = actionsAllowed, role = Role.Button) {
                                            onQueryChange(query); submit(query)
                                        }.padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                            MoaIcon(MoaIconKind.SEARCH, Modifier.size(20.dp), Secondary)
                                            Text(query, color = Ink, fontSize = 17.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                        }
                                        IconButton(onClick = { onRemoveRecentQuery(query) }, enabled = actionsAllowed,
                                            modifier = Modifier.size(48.dp).semantics { contentDescription = "최근 검색어 $query 삭제" }) {
                                            MoaIcon(MoaIconKind.CLOSE, Modifier.size(18.dp), Secondary)
                                        }
                                    }
                                    HorizontalDivider(color = Line)
                                }
                            }
                            item(key = "storage-note") {
                                Text("검색어와 집·회사 위치는 이 차량에 저장돼요.", color = Secondary, fontSize = 12.sp,
                                    modifier = Modifier.padding(top = 4.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

private class SearchInputDismissal(var dismiss: () -> Unit = {})

@Composable
private fun SavedPlaceTile(slot: SavedPlaceSlot, destination: NavigationDestination?, enabled: Boolean,
    onOpen: (NavigationDestination) -> Unit, onRegister: () -> Unit, onRemove: () -> Unit, modifier: Modifier) {
    Surface(modifier, shape = RoundedCornerShape(18.dp), color = CardColor, shadowElevation = CardShadowElevation) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth().heightIn(min = 68.dp)
                .clickable(enabled = enabled, role = Role.Button,
                    onClickLabel = if (destination == null) "${slot.label} 위치 등록" else "${slot.label} 경로 보기") {
                    if (destination == null) onRegister() else onOpen(destination)
                }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(44.dp).background(PaleBlue, CircleShape), contentAlignment = Alignment.Center) {
                    Text(slot.label.take(1), color = Blue, fontSize = 18.sp, fontWeight = FontWeight.Medium)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(slot.label, color = Secondary, fontSize = 13.sp)
                    Text(destination?.name ?: "위치 등록", color = Ink, fontSize = 18.sp,
                        fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (destination != null) Text(destination.address, color = Secondary, fontSize = 12.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            if (destination != null) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onRegister, enabled = enabled, modifier = Modifier.heightIn(min = 48.dp)
                    .semantics { contentDescription = "${slot.label} 위치 변경" }) { Text("변경", fontSize = 14.sp) }
                TextButton(onClick = onRemove, enabled = enabled, modifier = Modifier.heightIn(min = 48.dp)
                    .semantics { contentDescription = "${slot.label} 위치 삭제" }) { Text("삭제", fontSize = 14.sp) }
            }
        }
    }
}

@Composable
private fun SearchResultCard(destination: NavigationDestination, places: NavigationPlacesState,
    registration: SavedPlaceSlot?, enabled: Boolean, onChoose: () -> Unit, onSave: (SavedPlaceSlot) -> Unit) {
    Surface(shape = RoundedCornerShape(16.dp), color = CardColor, shadowElevation = CardShadowElevation) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).clickable(enabled = enabled, role = Role.Button,
                onClickLabel = if (registration == null) "경로 보기" else "${registration.label} 위치 저장", onClick = onChoose),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(destination.name, color = Ink, fontSize = 20.sp, fontWeight = FontWeight.Medium,
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(destination.address, color = Secondary, fontSize = 14.sp, maxLines = 2,
                        lineHeight = 20.sp, overflow = TextOverflow.Ellipsis)
                }
                Text("›", color = Blue, fontSize = 28.sp)
            }
            if (registration == null) Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SavedPlaceSlot.entries.forEach { slot ->
                    val saved = places.savedPlaces[slot]
                    val same = saved != null && saved.point == destination.point && saved.name == destination.name
                    TextButton(onClick = { onSave(slot) }, enabled = enabled && !same,
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                        Text(when { same -> "${slot.label} 저장됨"; saved != null -> "${slot.label} 위치 변경"; else -> "${slot.label} 저장" },
                            fontSize = 14.sp)
                    }
                }
            }
        }
    }
}
