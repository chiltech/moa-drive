package com.moadrive.pleos.ui.cctv

import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import com.moadrive.pleos.ui.common.SoftSurface as Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.moadrive.pleos.ui.common.DimmedDialog
import com.moadrive.pleos.ui.common.rememberDialogAction
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.AudioAttributes
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.moadrive.pleos.BuildConfig
import com.moadrive.pleos.cctv.CctvCamera
import com.moadrive.pleos.cctv.CctvState
import com.moadrive.pleos.cctv.CctvStatus
import com.moadrive.pleos.ui.BackgroundColor
import com.moadrive.pleos.ui.SurfaceColor
import com.moadrive.pleos.ui.Blue
import com.moadrive.pleos.ui.Ink
import com.moadrive.pleos.ui.Line
import com.moadrive.pleos.ui.MoaIcon
import com.moadrive.pleos.ui.MoaIconKind
import com.moadrive.pleos.ui.PaleBlue
import com.moadrive.pleos.ui.Secondary
import com.moadrive.pleos.ui.common.ExternalActions
import kotlinx.coroutines.delay

/** The map owns the panel's width/animation; every action remains usable in its narrow column. */
@Composable
fun CctvSidePanel(
    state: CctvState,
    browsingAllowed: Boolean,
    onSelect: (String) -> Unit,
    onClose: () -> Unit,
    onRefresh: () -> Unit,
    onDemo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var webUnavailable by remember { mutableStateOf(false) }
    Surface(modifier, color = SurfaceColor, border = BorderStroke(1.dp, Line),
        shape = RectangleShape) {
        Column(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("CCTV", Modifier.weight(1f).padding(start = 4.dp), color = Ink,
                    fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                IconButton(onClick = onClose, modifier = Modifier.size(48.dp)
                    .semantics { contentDescription = "CCTV 패널 닫기" }) {
                    MoaIcon(MoaIconKind.CLOSE, Modifier.size(20.dp), Secondary, outline = true)
                }
            }
            HorizontalDivider(color = Line)
            LazyColumn(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 12.dp), userScrollEnabled = browsingAllowed) {
                if (state.demo) item {
                    Text("예시", color = Blue, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.background(PaleBlue, RoundedCornerShape(8.dp)).padding(horizontal = 8.dp, vertical = 4.dp))
                }
                if (state.status == CctvStatus.LOADING) {
                    item { Box(Modifier.fillMaxWidth().padding(vertical = 14.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(Modifier.size(25.dp), color = Blue, strokeWidth = 2.dp)
                    } }
                }
                if (state.status == CctvStatus.ERROR && state.cameras.isNotEmpty()) item {
                    Text("영상에 연결하지 못했어요. 새로고침 후 다시 선택해 주세요.", color = Secondary,
                        fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp))
                }
                if (state.status == CctvStatus.OFFLINE && state.cameras.isNotEmpty()) item {
                    Text("인터넷 연결을 기다리고 있어요. 연결되면 자동으로 다시 불러와요.", color = Secondary,
                        fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp))
                }
                if (state.cameras.isEmpty()) {
                    item {
                        Text(when (state.status) {
                            CctvStatus.NEEDS_CONFIGURATION -> "연결 준비 중"
                            CctvStatus.NO_LOCATION -> "위치 확인 중"
                            CctvStatus.OFFLINE -> "인터넷 연결을 기다리고 있어요"
                            CctvStatus.ERROR -> "불러오기 실패"
                            CctvStatus.QUOTA_EXCEEDED -> "이번 달 CCTV 조회 한도를 초과했어요"
                            CctvStatus.LOADING -> "주변 CCTV를 찾고 있어요"
                            CctvStatus.READY -> "주변 CCTV 정보가 없어요"
                            CctvStatus.IDLE -> "주변 CCTV"
                        }, color = Secondary, fontSize = 13.sp, lineHeight = 20.sp,
                            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                    }
                }
                if (state.status == CctvStatus.QUOTA_EXCEEDED) item {
                    Text("국가교통정보센터에서 인증키의 월 호출 한도를 늘린 뒤 다시 확인해 주세요.",
                        color = Secondary, fontSize = 13.sp, lineHeight = 20.sp,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp))
                }
                items(state.cameras, key = { it.id }) { camera ->
                    val selected = state.selected?.id == camera.id
                    Surface(onClick = { if (browsingAllowed) onSelect(camera.id) }, enabled = browsingAllowed,
                        color = if (selected) PaleBlue else BackgroundColor,
                        border = BorderStroke(1.dp, if (selected) Blue else Line), shape = RectangleShape,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp)) {
                        Row(Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                            Box(Modifier.size(32.dp).background(PaleBlue),
                                contentAlignment = Alignment.Center) {
                                CctvGlyph(Modifier.size(20.dp), if (browsingAllowed) Blue else Secondary)
                            }
                            Column(Modifier.weight(1f)) {
                                Text(camera.name, color = Ink, fontSize = 13.sp, lineHeight = 18.sp,
                                    maxLines = 3, overflow = TextOverflow.Ellipsis)
                                if (camera.isDemo) Text("예시", color = Blue, fontSize = 10.sp)
                            }
                        }
                    }
                }
                item {
                    TextButton(onClick = { if (browsingAllowed) onRefresh() },
                        enabled = browsingAllowed && state.status != CctvStatus.LOADING,
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                        Text(if (state.status == CctvStatus.QUOTA_EXCEEDED) "한도 변경 후 다시 확인" else "새로고침",
                            fontSize = 12.sp, textAlign = TextAlign.Center)
                    }
                }
                if (state.status in listOf(CctvStatus.NEEDS_CONFIGURATION, CctvStatus.ERROR, CctvStatus.QUOTA_EXCEEDED)) {
                    item {
                        TextButton(onClick = {
                            if (browsingAllowed) webUnavailable = !ExternalActions.web(context, "https://www.its.go.kr/")
                        }, enabled = browsingAllowed, contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                            Text("공식 교통정보", fontSize = 12.sp, textAlign = TextAlign.Center)
                        }
                    }
                    if (webUnavailable) item {
                        Text("웹을 열 수 없어요", color = Secondary, fontSize = 12.sp, lineHeight = 18.sp,
                            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                    }
                }
                if (BuildConfig.DEBUG) item {
                    OutlinedButton(onClick = { if (browsingAllowed) onDemo() }, enabled = browsingAllowed,
                        contentPadding = PaddingValues(horizontal = 5.dp, vertical = 9.dp),
                        shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                        Text("지도 표시 체험", fontSize = 12.sp, lineHeight = 18.sp, textAlign = TextAlign.Center)
                    }
                }
            }
            if (!browsingAllowed) Text("주차 후 이용", Modifier.fillMaxWidth(), color = Secondary,
                fontSize = 12.sp, textAlign = TextAlign.Center)
        }
    }
}

/** Muted foreground-only playback. Demo cameras never enter the media pipeline. */
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun CctvVideoDialog(
    camera: CctvCamera,
    browsingAllowed: Boolean,
    onDismiss: () -> Unit,
    onStreamError: (String, Long) -> Unit,
    networkOnline: Boolean = true,
    reconnecting: Boolean = false,
    playbackGeneration: Long = 0L,
) {
    val density = LocalDensity.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val currentDismiss by rememberUpdatedState(onDismiss)
    val currentError by rememberUpdatedState(onStreamError)
    val currentAllowed by rememberUpdatedState(browsingAllowed)
    val currentOnline by rememberUpdatedState(networkOnline)
    val currentReconnecting by rememberUpdatedState(reconnecting)
    val waitingForNetwork = !camera.isDemo && (!networkOnline || reconnecting)
    val session = remember(camera.id, camera.streamUrl, playbackGeneration, waitingForNetwork) { VideoSession() }
    val uri = remember(camera.streamUrl, camera.isDemo) {
        if (camera.isDemo) null else runCatching {
            camera.streamUrl?.takeIf { it.isNotBlank() }?.let(Uri::parse)
                ?.takeIf { it.scheme.equals("https", ignoreCase = true) && !it.host.isNullOrBlank() }
        }.getOrNull()
    }
    var phase by remember(session) {
        mutableStateOf(when {
            waitingForNetwork -> PlaybackPhase.RECONNECTING
            uri != null -> PlaybackPhase.LOADING
            else -> PlaybackPhase.UNAVAILABLE
        })
    }
    var errorMessage by remember(session) { mutableStateOf("영상을 재생할 수 없어요.") }

    fun fail(message: String) {
        if (session.closed) return
        if (!currentOnline || currentReconnecting) { session.close(); return }
        errorMessage = message
        phase = PlaybackPhase.ERROR
        session.close()
        currentError(camera.id, playbackGeneration)
    }

    DisposableEffect(lifecycle, session) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) {
                session.close()
                currentDismiss()
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer); session.close() }
    }
    LaunchedEffect(browsingAllowed, session) {
        if (!browsingAllowed) { session.close(); currentDismiss() }
    }
    LaunchedEffect(phase, session) {
        if (phase == PlaybackPhase.LOADING) {
            delay(12_000)
            if (phase == PlaybackPhase.LOADING && !session.closed) fail("영상 연결이 지연되고 있어요.")
        }
    }
    if (!browsingAllowed) return

    val close = { session.close(); currentDismiss() }
    DimmedDialog(onDismissRequest = close, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val dismiss = rememberDialogAction { currentDismiss() }
        val closeWithMotion = { session.close(); dismiss() }
        CompositionLocalProvider(LocalDensity provides density) {
            Surface(Modifier.widthIn(max = 760.dp).fillMaxWidth(.94f).heightIn(max = 680.dp),
                shape = RoundedCornerShape(22.dp), color = SurfaceColor, tonalElevation = 0.dp) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Text(if (camera.isDemo) "CCTV · 예시" else "CCTV", color = Blue, fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold)
                            Text(camera.name, color = Ink, fontSize = 23.sp, lineHeight = 31.sp,
                                fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                        TextButton(onClick = closeWithMotion, modifier = Modifier.heightIn(min = 48.dp)) {
                            Text("닫기", fontSize = 16.sp)
                        }
                    }
                    Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(Color.Black),
                            contentAlignment = Alignment.Center) {
                            if (uri != null && !waitingForNetwork && phase != PlaybackPhase.ERROR) {
                                // AndroidView factories do not rerun when only the signed URL changes.
                                key(session) { AndroidView(
                                    modifier = Modifier.fillMaxSize(),
                                    factory = { context ->
                                        PlayerView(context).apply {
                                            session.view = this
                                            useController = false
                                            if (!session.closed && currentAllowed && currentOnline && !currentReconnecting &&
                                                lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                                                try {
                                                    val media = ExoPlayer.Builder(context).build()
                                                    session.player = media
                                                    // CCTV stays silent and must not take focus from navigation.
                                                    media.setAudioAttributes(AudioAttributes.DEFAULT, false)
                                                    media.volume = 0f
                                                    media.repeatMode = Player.REPEAT_MODE_OFF
                                                    val listener = object : Player.Listener {
                                                        override fun onPlaybackStateChanged(playbackState: Int) {
                                                            if (session.closed) return
                                                            if (!currentAllowed || !currentOnline || currentReconnecting ||
                                                                !lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                                                                session.close()
                                                                return
                                                            }
                                                            when (playbackState) {
                                                                Player.STATE_BUFFERING -> phase = PlaybackPhase.LOADING
                                                                Player.STATE_READY -> {
                                                                    media.play()
                                                                    phase = PlaybackPhase.PLAYING
                                                                }
                                                                Player.STATE_ENDED -> fail("영상이 종료됐어요.")
                                                            }
                                                        }

                                                        override fun onPlayerError(error: PlaybackException) {
                                                            fail("영상을 재생할 수 없어요.")
                                                        }
                                                    }
                                                    session.listener = listener
                                                    media.addListener(listener)
                                                    player = media
                                                    // ITS signs extensionless HLS URLs, so content type must be explicit.
                                                    media.setMediaItem(MediaItem.Builder().setUri(uri)
                                                        .setMimeType(MimeTypes.APPLICATION_M3U8).build())
                                                    media.prepare()
                                                } catch (_: RuntimeException) {
                                                    fail("영상에 연결할 수 없어요.")
                                                }
                                            } else session.close()
                                        }
                                    },
                                ) }
                            }
                            when (phase) {
                                PlaybackPhase.RECONNECTING -> Column(horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    CircularProgressIndicator(Modifier.size(28.dp), color = Color.White, strokeWidth = 2.dp)
                                    Text(if (networkOnline) "CCTV 다시 연결 중" else "인터넷 연결을 기다리고 있어요",
                                        color = Color.White, fontSize = 17.sp)
                                    Text("연결되면 이 카메라의 영상을 자동으로 다시 재생해요.",
                                        color = Color(0xFFB8C3D2), fontSize = 14.sp, textAlign = TextAlign.Center)
                                }
                                PlaybackPhase.LOADING -> Column(horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    CircularProgressIndicator(Modifier.size(28.dp), color = Color.White, strokeWidth = 2.dp)
                                    Text("영상 연결 중", color = Color.White, fontSize = 17.sp)
                                }
                                PlaybackPhase.UNAVAILABLE -> VideoPlaceholder(
                                    if (camera.isDemo) "예시 카메라 · 실제 영상 미연결" else "연결 가능한 영상이 없어요",
                                    if (camera.isDemo) "지도 위치와 카메라 선택 과정을 체험할 수 있어요."
                                    else "창을 닫고 CCTV 목록을 새로고침해 주세요.",
                                )
                                PlaybackPhase.ERROR -> VideoPlaceholder(errorMessage, "창을 닫고 CCTV 목록을 새로고침해 주세요.")
                                PlaybackPhase.PLAYING -> Unit
                            }
                        }
                        Text(if (camera.isDemo) "이 카메라는 지도 표시용 예시이며 실제 도로 영상을 제공하지 않아요."
                            else "영상은 무음으로 재생돼요. 전송 상황에 따라 지연되거나 연결이 종료될 수 있어요.",
                            color = Secondary, fontSize = 14.sp, lineHeight = 22.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun VideoPlaceholder(title: String, description: String) {
    Column(Modifier.padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        CctvGlyph(Modifier.size(37.dp), Color(0xFFB8C3D2))
        Text(title, color = Color.White, fontSize = 19.sp, lineHeight = 27.sp, fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center)
        Text(description, color = Color(0xFFB8C3D2), fontSize = 14.sp, lineHeight = 22.sp, textAlign = TextAlign.Center)
    }
}

@Composable
internal fun CctvGlyph(modifier: Modifier, tint: Color) {
    Canvas(modifier) {
        val unit = size.minDimension / 24f
        val stroke = Stroke(1.8f * unit)
        drawRoundRect(tint, Offset(2f * unit, 6f * unit), Size(13f * unit, 12f * unit),
            CornerRadius(2f * unit), style = stroke)
        val lens = Path().apply {
            moveTo(15f * unit, 10f * unit); lineTo(22f * unit, 6f * unit)
            lineTo(22f * unit, 18f * unit); lineTo(15f * unit, 14f * unit); close()
        }
        drawPath(lens, tint, style = stroke)
    }
}

private enum class PlaybackPhase { LOADING, PLAYING, RECONNECTING, ERROR, UNAVAILABLE }

@androidx.annotation.OptIn(UnstableApi::class)
private class VideoSession {
    var view: PlayerView? = null
    var player: ExoPlayer? = null
    var listener: Player.Listener? = null
    var closed = false
        private set

    fun close() {
        if (closed) return
        closed = true
        player?.let { media ->
            listener?.let(media::removeListener)
            view?.player = null
            runCatching { media.release() }
        }
        view = null
        player = null
        listener = null
    }
}
