package com.moagauge.pleos.ui.care

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.net.http.SslError
import android.os.Message
import android.view.View
import android.view.ViewGroup
import android.webkit.PermissionRequest
import android.webkit.RenderProcessGoneDetail
import android.webkit.SslErrorHandler
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import com.moagauge.pleos.ui.common.SoftSurface as Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.moagauge.pleos.care.news.CareNewsWebPolicy
import com.moagauge.pleos.care.news.resolveChildNewsPreviewUrl
import com.moagauge.pleos.ui.*
import com.moagauge.pleos.ui.common.LocalDetailExpanded
import com.moagauge.pleos.ui.common.DimmedDialog
import com.moagauge.pleos.ui.common.rememberDialogAction
import java.io.ByteArrayInputStream

/** A publisher original, kept inside the same end-panel layout as temporary-driver insurance. */
@Composable
fun CareNewsWebDialog(
    title: String,
    source: String,
    publishedDate: String,
    url: String,
    preferDocumentPreview: Boolean = false,
    onDismiss: () -> Unit,
) {
    val currentDismiss by rememberUpdatedState(onDismiss)
    val windowDismiss = remember { mutableStateOf<(() -> Unit)?>(null) }
    val session = remember(url, preferDocumentPreview) { CareNewsWebSession(url, preferDocumentPreview) {
        windowDismiss.value?.invoke() ?: currentDismiss()
    } }
    LaunchedEffect(session) {
        if (preferDocumentPreview) session.openResolvedStart(resolveChildNewsPreviewUrl(url))
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val close = { session.close(); currentDismiss() }
    DisposableEffect(lifecycle, session) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> session.pause()
                Lifecycle.Event.ON_RESUME -> session.resume()
                Lifecycle.Event.ON_STOP -> close()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) session.resume() else session.pause()
        onDispose { lifecycle.removeObserver(observer); session.close() }
    }

    // Read the service window decision, not the much narrower news-card constraints.
    val expanded = LocalDetailExpanded.current
    DimmedDialog(onDismissRequest = close) {
        val dismiss = rememberDialogAction { currentDismiss() }
        val closeWithMotion = { session.close(); dismiss() }
        DisposableEffect(dismiss) {
            windowDismiss.value = dismiss
            onDispose { windowDismiss.value = null }
        }
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val panelModifier = if (expanded) {
                Modifier.align(Alignment.CenterEnd).padding(end = 16.dp)
                    .width((maxWidth * .60f).coerceAtMost(640.dp))
            } else Modifier.align(Alignment.Center).fillMaxWidth(.95f)
            Surface(panelModifier.fillMaxHeight(.94f),
                shape = RoundedCornerShape(22.dp), color = SurfaceColor) {
                BoxWithConstraints(Modifier.fillMaxSize()) {
                    val narrow = maxWidth < 440.dp
                    Column(Modifier.fillMaxSize()) {
                        val heading: @Composable () -> Unit = {
                            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(title, color = Ink,
                                    fontSize = if (narrow) 17.sp else 20.sp, fontWeight = FontWeight.SemiBold,
                                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Text(listOf(source, publishedDate).filter { it.isNotBlank() }.joinToString(" · "),
                                    color = Blue, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(session.host.ifBlank { "공식 소식 원문" }, color = Secondary,
                                    fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                        val controls: @Composable () -> Unit = {
                            if (preferDocumentPreview) TextButton(onClick = session::openOriginal,
                                modifier = Modifier.heightIn(min = 48.dp)
                                    .semantics { contentDescription = "뉴스 원문 페이지 열기" },
                                contentPadding = PaddingValues(horizontal = 8.dp)) {
                                Text("원문", color = Blue, fontSize = 13.sp)
                            }
                            CareNewsWebButton(NewsWebButton.BACK, session.canGoBack, session::goBack)
                            CareNewsWebButton(NewsWebButton.RELOAD, true, session::reload)
                            CareNewsWebButton(NewsWebButton.CLOSE, true, closeWithMotion)
                        }
                        if (narrow) Column(Modifier.fillMaxWidth().padding(start = 14.dp, end = 8.dp, top = 12.dp)) {
                            heading()
                            Row(Modifier.align(Alignment.End), verticalAlignment = Alignment.CenterVertically) { controls() }
                        } else Row(Modifier.fillMaxWidth().padding(start = 18.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Box(Modifier.weight(1f)) { heading() }
                            Row(verticalAlignment = Alignment.CenterVertically) { controls() }
                        }
                        Box(Modifier.fillMaxWidth().height(3.dp)) {
                            if (session.loading) LinearProgressIndicator(progress = session.progress / 100f,
                                modifier = Modifier.fillMaxSize(), color = Blue, trackColor = PaleBlue)
                        }
                        session.feedback?.let { message ->
                            Surface(color = PaleBlue) {
                                Row(Modifier.fillMaxWidth().padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(message, Modifier.weight(1f).padding(vertical = 8.dp), color = Ink,
                                        fontSize = 13.sp, lineHeight = 19.sp)
                                    IconButton(onClick = session::clearFeedback, modifier = Modifier.size(48.dp)
                                        .semantics { contentDescription = "뉴스 웹 안내 닫기" }) {
                                        MoaIcon(MoaIconKind.CLOSE, Modifier.size(18.dp), Secondary, outline = true)
                                    }
                                }
                            }
                        }
                        Box(Modifier.weight(1f).fillMaxWidth()) {
                            key(session) {
                                AndroidView(factory = { session.attach(it) }, modifier = Modifier.fillMaxSize())
                            }
                            if (session.resolvingPreview) Surface(Modifier.fillMaxSize(), color = SurfaceColor) {
                                Column(Modifier.fillMaxSize().padding(20.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically)) {
                                    CircularProgressIndicator(Modifier.size(28.dp), color = Blue, strokeWidth = 2.dp)
                                    Text("미리보기를 준비하고 있어요", color = Secondary, fontSize = 15.sp)
                                }
                            }
                            session.error?.let { message ->
                                Surface(Modifier.fillMaxSize(), color = SurfaceColor) {
                                    Column(Modifier.fillMaxSize().padding(20.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically)) {
                                        Text(message, color = Ink, fontSize = 16.sp, lineHeight = 24.sp)
                                        Button(onClick = session::reload, modifier = Modifier.heightIn(min = 48.dp)) {
                                            Text("다시 시도")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private enum class NewsWebButton(val description: String) {
    BACK("뉴스 웹 뒤로"), RELOAD("뉴스 웹 새로고침"), CLOSE("뉴스 웹 닫기")
}

@Composable
private fun CareNewsWebButton(kind: NewsWebButton, enabled: Boolean, onClick: () -> Unit) {
    val tint = if (enabled) Ink else Secondary.copy(alpha = .45f)
    IconButton(onClick = onClick, enabled = enabled,
        modifier = Modifier.size(48.dp).semantics { contentDescription = kind.description }) {
        if (kind == NewsWebButton.CLOSE) MoaIcon(MoaIconKind.CLOSE, Modifier.size(22.dp), tint, outline = true)
        else Canvas(Modifier.size(24.dp)) {
            val unit = size.minDimension / 24f
            val stroke = 1.9f * unit
            if (kind == NewsWebButton.BACK) {
                drawLine(tint, Offset(19f * unit, 12f * unit), Offset(5f * unit, 12f * unit), stroke, StrokeCap.Round)
                drawLine(tint, Offset(5f * unit, 12f * unit), Offset(11f * unit, 6f * unit), stroke, StrokeCap.Round)
                drawLine(tint, Offset(5f * unit, 12f * unit), Offset(11f * unit, 18f * unit), stroke, StrokeCap.Round)
            } else {
                drawArc(tint, 35f, 285f, false, Offset(4f * unit, 4f * unit), Size(16f * unit, 16f * unit),
                    style = Stroke(stroke, cap = StrokeCap.Round))
                drawLine(tint, Offset(20f * unit, 4f * unit), Offset(20f * unit, 10f * unit), stroke, StrokeCap.Round)
                drawLine(tint, Offset(20f * unit, 10f * unit), Offset(14f * unit, 10f * unit), stroke, StrokeCap.Round)
            }
        }
    }
}

/** Preserve article and popup history while the service window resizes. */
private class CareNewsWebSession(
    private val originalUrl: String,
    preferDocumentPreview: Boolean,
    private val onWindowClose: () -> Unit,
) {
    var host by mutableStateOf(CareNewsWebPolicy.displayHost(originalUrl).orEmpty())
        private set
    var resolvingPreview by mutableStateOf(preferDocumentPreview)
        private set
    var progress by mutableStateOf(0)
        private set
    var loading by mutableStateOf(true)
        private set
    var canGoBack by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var feedback by mutableStateOf<String?>(null)
        private set
    private var container: FrameLayout? = null
    private val pages = mutableListOf<WebPage>()
    private var closed = false
    private var paused = false
    private var startUrl: String? = if (preferDocumentPreview) null else originalUrl
    private var initialNavigationStarted = false

    private class WebPage(val web: WebView) {
        var url: String? = null
        var progress = 0
        var loading = true
        var error: String? = null
        var destroyed = false
    }

    fun attach(context: Context): FrameLayout {
        // AndroidView's factory can run again when responsive content moves to a new slot.
        // Move the existing native container; creating another page would reload the article
        // and incorrectly turn a window resize into a new popup/history entry.
        container?.let { existing ->
            (existing.parent as? ViewGroup)?.removeView(existing)
            active()?.let(::showPage)
            return existing
        }
        val hostView = FrameLayout(context)
        container = hostView
        if (!closed && pages.isEmpty()) {
            val page = newPage(context)
            pages += page
            showPage(page)
            loadStart(page)
        }
        return hostView
    }

    /** Resolution runs once per selection; Back, reload and resize never auto-forward again. */
    fun openResolvedStart(url: String) {
        if (closed || initialNavigationStarted || !resolvingPreview) return
        startUrl = url
        resolvingPreview = false
        pages.firstOrNull()?.let(::loadStart)
    }

    private fun loadStart(page: WebPage) {
        val url = startUrl ?: return
        if (initialNavigationStarted || !live(page)) return
        initialNavigationStarted = true
        if (CareNewsWebPolicy.isAllowedWebUrl(url)) page.web.loadUrl(url)
        else fail(page, "원문의 주소를 확인할 수 없어요.")
    }

    fun openOriginal() {
        if (closed) return
        // Also lets the user bypass a slow preview lookup. Its late result is ignored.
        initialNavigationStarted = false
        resolvingPreview = false
        startUrl = originalUrl
        val page = active() ?: return
        initialNavigationStarted = true
        if (page.destroyed) {
            page.url = originalUrl
            reload()
        } else if (CareNewsWebPolicy.isAllowedWebUrl(originalUrl)) page.web.loadUrl(originalUrl)
    }

    private fun active() = pages.lastOrNull()
    private fun live(page: WebPage) = !closed && !page.destroyed && page in pages

    private fun updateUi(page: WebPage) {
        if (closed || active() !== page) return
        host = page.url?.let(CareNewsWebPolicy::displayHost).orEmpty()
        progress = page.progress.coerceIn(0, 100)
        loading = page.loading && page.error == null
        error = page.error
        canGoBack = pages.size > 1 || (!page.destroyed && page.web.canGoBack())
    }

    private fun showPage(page: WebPage) {
        val hostView = container ?: return
        pages.forEach { it.web.visibility = if (it === page) View.VISIBLE else View.GONE }
        if (page.web.parent == null && !page.destroyed) hostView.addView(page.web,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        updateUi(page)
    }

    private fun fail(page: WebPage, message: String) {
        if (!live(page)) return
        page.loading = false
        page.error = message
        updateUi(page)
    }

    private fun allow(url: String): Boolean {
        if (CareNewsWebPolicy.isAllowedWebUrl(url)) return true
        feedback = "외부 앱이나 안전하지 않은 주소는 이 화면에서 열 수 없어요. 웹페이지의 다른 링크를 이용해 주세요."
        return false
    }

    @SuppressLint("SetJavaScriptEnabled")
    @Suppress("DEPRECATION")
    private fun newPage(context: Context): WebPage {
        val page = WebPage(WebView(context))
        page.web.apply {
            importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                allowFileAccess = false
                allowContentAccess = false
                allowFileAccessFromFileURLs = false
                allowUniversalAccessFromFileURLs = false
                mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                safeBrowsingEnabled = true
                saveFormData = false
                setGeolocationEnabled(false)
                setSupportMultipleWindows(true)
                javaScriptCanOpenWindowsAutomatically = false
                useWideViewPort = true
                loadWithOverviewMode = true
                setSupportZoom(true)
                builtInZoomControls = true
                displayZoomControls = false
                textZoom = 100
                mediaPlaybackRequiresUserGesture = true
            }
            setDownloadListener { _, _, _, _, _ ->
                if (live(page)) feedback = "첨부 파일은 이 화면에서 열 수 없어요."
            }
            webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                    // Form POST navigations can bypass shouldOverrideUrlLoading.
                    // Reject a non-HTTPS main frame before it reaches the network.
                    val url = request.url.toString()
                    if (request.isForMainFrame && url != "about:blank" && !CareNewsWebPolicy.isAllowedWebUrl(url)) {
                        view.post { if (live(page)) fail(page, "이 주소는 차량 화면에서 열 수 없어요.") }
                        return WebResourceResponse("text/plain", "utf-8", 403, "Blocked", emptyMap(),
                            ByteArrayInputStream(ByteArray(0)))
                    }
                    return null
                }

                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                    !live(page) || !allow(request.url.toString())

                override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean =
                    !live(page) || !allow(url)

                override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                    if (!live(page)) return
                    // A user-opened publisher window can be blank before its HTTPS navigation.
                    if (url == "about:blank" && page.url == null && pages.firstOrNull() !== page) return
                    if (url == null || !allow(url)) {
                        view.stopLoading()
                        fail(page, "이 주소는 차량 화면에서 열 수 없어요.")
                        return
                    }
                    page.url = url
                    page.error = null
                    page.loading = true
                    page.progress = 0
                    feedback = null
                    updateUi(page)
                }

                override fun onPageFinished(view: WebView, url: String?) {
                    if (!live(page)) return
                    page.loading = false
                    page.progress = 100
                    // A failed main frame also calls onPageFinished; retain its retry state.
                    updateUi(page)
                }

                override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) {
                    if (live(page)) updateUi(page)
                }

                override fun onReceivedError(view: WebView, request: WebResourceRequest, failure: WebResourceError) {
                    if (request.isForMainFrame) fail(page, "페이지에 연결하지 못했어요. 인터넷 연결을 확인한 뒤 다시 시도해 주세요.")
                }

                override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, response: WebResourceResponse) {
                    if (request.isForMainFrame) fail(page, "원문을 불러오지 못했어요. 잠시 후 다시 시도해 주세요.")
                }

                override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, sslError: SslError) {
                    handler.cancel()
                    val failingUrl = sslError.url?.substringBefore('#')
                    if (failingUrl == page.url?.substringBefore('#') || failingUrl == view.url?.substringBefore('#')) {
                        fail(page, "안전한 연결을 확인할 수 없어요. 잠시 후 다시 시도해 주세요.")
                    } else if (live(page)) feedback = "일부 콘텐츠의 안전한 연결을 확인하지 못했어요."
                }

                override fun onFormResubmission(view: WebView, dontResend: Message, resend: Message) {
                    dontResend.sendToTarget()
                    if (live(page)) feedback = "이전 입력을 다시 전송하지 않았어요. 웹페이지에서 직접 진행해 주세요."
                }

                override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                    fail(page, "웹 화면을 다시 준비해야 해요. 다시 시도해 주세요.")
                    disposePage(page)
                    updateUi(page)
                    return true
                }
            }
            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView, newProgress: Int) {
                    if (!live(page)) return
                    page.progress = newProgress
                    updateUi(page)
                }

                override fun onCreateWindow(view: WebView, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message): Boolean {
                    if (!live(page) || !isUserGesture || pages.size >= 4) {
                        if (live(page)) feedback = "추가 창을 열지 않았어요. 열린 창을 닫고 링크를 다시 눌러 주세요."
                        return false
                    }
                    val transport = resultMsg.obj as? WebView.WebViewTransport ?: return false
                    val child = newPage(view.context)
                    pages += child
                    showPage(child)
                    transport.webView = child.web
                    resultMsg.sendToTarget()
                    return true
                }

                override fun onCloseWindow(window: WebView) {
                    val closing = pages.find { it.web === window } ?: return
                    if (pages.firstOrNull() === closing) { close(); onWindowClose() }
                    else closeChild(closing)
                }

                override fun onPermissionRequest(request: PermissionRequest) { request.deny() }

                override fun onShowFileChooser(view: WebView, callback: ValueCallback<Array<android.net.Uri>>, params: FileChooserParams): Boolean {
                    callback.onReceiveValue(null)
                    if (live(page)) feedback = "파일 선택은 이 화면에서 지원하지 않아요."
                    return true
                }
            }
            if (paused) onPause() else onResume()
        }
        return page
    }

    fun clearFeedback() { feedback = null }

    fun pause() {
        if (closed) return
        paused = true
        pages.filterNot { it.destroyed }.forEach { it.web.onPause() }
    }

    fun resume() {
        if (closed) return
        paused = false
        pages.filterNot { it.destroyed }.forEach { it.web.onResume() }
    }

    fun goBack() {
        val page = active() ?: return
        if (closed) return
        feedback = null
        if (!page.destroyed && page.web.canGoBack()) page.web.goBack()
        else if (pages.size > 1) closeChild(page)
    }

    fun reload() {
        val page = active() ?: return
        if (closed) return
        feedback = null
        val url = page.url ?: startUrl ?: return
        if (!CareNewsWebPolicy.isAllowedWebUrl(url)) return
        if (page.destroyed) {
            val context = container?.context ?: return
            pages.remove(page)
            val replacement = newPage(context)
            pages += replacement
            showPage(replacement)
            replacement.web.loadUrl(url)
        } else {
            page.web.stopLoading()
            if (page.url == null) page.web.loadUrl(url) else page.web.reload()
        }
    }

    private fun closeChild(page: WebPage) {
        disposePage(page)
        pages.remove(page)
        feedback = null
        active()?.let(::showPage)
    }

    private fun disposePage(page: WebPage) {
        if (page.destroyed) return
        page.destroyed = true
        page.web.stopLoading()
        page.web.onPause()
        (page.web.parent as? ViewGroup)?.removeView(page.web)
        page.web.webChromeClient = null
        page.web.webViewClient = WebViewClient()
        page.web.removeAllViews()
        page.web.destroy()
    }

    fun close() {
        if (closed) return
        closed = true
        pages.toList().forEach(::disposePage)
        pages.clear()
        container = null
        canGoBack = false
        loading = false
    }
}
