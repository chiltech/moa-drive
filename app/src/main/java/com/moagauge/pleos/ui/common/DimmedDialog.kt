package com.moagauge.pleos.ui.common

import android.animation.ValueAnimator
import android.view.View
import android.view.WindowManager
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import com.moagauge.pleos.ui.common.SoftSurface as Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import com.moagauge.pleos.ui.drive.serviceRailWidth
import kotlinx.coroutines.launch

/** Commits one user dismissal after the popup's exit, while keeping its content alive. */
class DialogMotion internal constructor(private val requestClose: (() -> Unit) -> Unit) {
    fun close(action: () -> Unit) = requestClose(action)
}

val LocalDialogMotion = staticCompositionLocalOf { DialogMotion { action -> action() } }

/** Call from popup content, including alert button slots. Non-closing controls stay immediate. */
@Composable
fun rememberDialogAction(action: () -> Unit): () -> Unit {
    val motion = LocalDialogMotion.current
    val latest by rememberUpdatedState(action)
    return remember(motion) { { motion.close(latest) } }
}

/** Draw the scrim inside the popup window, above native map/video surfaces. */
@Composable
fun DimmedDialog(
    onDismissRequest: () -> Unit,
    properties: DialogProperties = DialogProperties(usePlatformDefaultWidth = false),
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val latestDismiss by rememberUpdatedState(onDismissRequest)
    val progress = remember { Animatable(if (ValueAnimator.areAnimatorsEnabled()) 0f else 1f) }
    val scope = rememberCoroutineScope()
    var exiting by remember { mutableStateOf(false) }
    var placed by remember { mutableStateOf(false) }
    val motion = remember {
        DialogMotion { action ->
            if (!exiting) {
                exiting = true
                scope.launch {
                    if (ValueAnimator.areAnimatorsEnabled()) {
                        progress.animateTo(0f, tween(220, easing = FastOutLinearInEasing))
                    } else progress.snapTo(0f)
                    action()
                    // A validation callback may intentionally keep the form open.
                    // If its caller did remove the Dialog, this scope is cancelled.
                    withFrameNanos { }
                    withFrameNanos { }
                    exiting = false
                    if (ValueAnimator.areAnimatorsEnabled()) {
                        progress.animateTo(1f, tween(280, easing = FastOutSlowInEasing))
                    } else progress.snapTo(1f)
                }
            }
        }
    }
    val dismiss = { motion.close { latestDismiss() } }
    Dialog(onDismissRequest = dismiss, properties = properties) {
        DimmedDialogWindow()
        LaunchedEffect(placed) {
            if (placed) {
                withFrameNanos { }
                // Closing during the first frame must win over the entry job.
                if (!exiting) {
                    if (ValueAnimator.areAnimatorsEnabled()) {
                        progress.animateTo(1f, tween(280, easing = FastOutSlowInEasing))
                    } else progress.snapTo(1f)
                }
            }
        }
        // Dialog has its own Compose root; keep the vehicle display's content scale.
        CompositionLocalProvider(LocalDensity provides density, LocalDialogMotion provides motion) {
            BoxWithConstraints(Modifier.fillMaxSize().onGloballyPositioned {
                if (it.size.width > 0 && it.size.height > 0) placed = true
            }) {
                val railWidth = serviceRailWidth(maxWidth)
                Row(Modifier.fillMaxSize()) {
                    // Match the page's service area. Keep the menu visible and prevent popup
                    // content or shadows from spilling into its transparent strip.
                    Box(Modifier.weight(1f).fillMaxHeight().clipToBounds(), contentAlignment = Alignment.Center) {
                        // The scrim stays anchored to the service area while only
                        // the popup rises/falls. Native map/video surfaces remain covered.
                        Box(Modifier.matchParentSize().graphicsLayer { alpha = progress.value }
                            .background(Color.Black.copy(alpha = .60f)))
                        Box(Modifier.matchParentSize().then(
                            if (properties.dismissOnClickOutside) Modifier.clickable(
                                interactionSource = remember { MutableInteractionSource() }, indication = null,
                                onClickLabel = "팝업 닫기", onClick = dismiss,
                            ).semantics { contentDescription = "팝업 바깥 영역" } else Modifier
                        ))
                        Box(Modifier.graphicsLayer {
                            translationY = 56.dp.toPx() * (1f - progress.value)
                            alpha = progress.value
                        }.then(if (exiting) Modifier.clearAndSetSemantics { } else Modifier),
                            contentAlignment = Alignment.Center) {
                            content()
                        }
                        if (exiting) Box(Modifier.matchParentSize().pointerInput(Unit) {
                            awaitPointerEventScope {
                                while (true) awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
                            }
                        })
                    }
                    Spacer(Modifier.width(railWidth).fillMaxHeight())
                }
            }
        }
    }
}

/**
 * A modal end panel inside the service area, leaving the right menu visible. The shared scrim
 * covers SurfaceView maps. The transparent outer hit target closes the panel; Surface consumes
 * touches in panel gaps so they cannot dismiss it or reach the map underneath.
 */
@Composable
fun DimmedSideSheet(
    onDismissRequest: () -> Unit,
    width: Dp,
    endInset: Dp = 0.dp,
    content: @Composable () -> Unit,
) {
    DimmedDialog(onDismissRequest = onDismissRequest) {
        val close = rememberDialogAction(onDismissRequest)
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val inset = endInset.coerceIn(0.dp, maxWidth)
            val panelWidth = width.coerceIn(1.dp, (maxWidth - inset).coerceAtLeast(1.dp))
            Box(Modifier.fillMaxSize()
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null,
                    onClickLabel = "패널 닫기", onClick = close)
                .semantics { contentDescription = "패널 바깥 영역" })
            Surface(
                modifier = Modifier.align(Alignment.CenterEnd).padding(end = inset)
                    .width(panelWidth).fillMaxHeight(),
                color = Color.Transparent,
                content = content,
            )
        }
    }
}

/** Alert content uses the same drawn scrim as every other popup. */
@Composable
fun DimmedAlertDialog(
    onDismissRequest: () -> Unit,
    title: @Composable () -> Unit,
    text: @Composable () -> Unit,
    confirmButton: @Composable () -> Unit,
    dismissButton: (@Composable () -> Unit)? = null,
) {
    DimmedDialog(onDismissRequest = onDismissRequest) {
        Surface(Modifier.padding(24.dp).widthIn(max = 560.dp).fillMaxWidth(),
            shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp) {
            Column(Modifier.padding(24.dp)) {
                CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
                    ProvideTextStyle(MaterialTheme.typography.headlineSmall, content = title)
                }
                Spacer(Modifier.height(16.dp))
                Box(Modifier.weight(1f, fill = false)) {
                    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurfaceVariant) {
                        ProvideTextStyle(MaterialTheme.typography.bodyMedium, content = text)
                    }
                }
                Spacer(Modifier.height(24.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                    dismissButton?.invoke()
                    confirmButton()
                }
            }
        }
    }
}

@Composable
private fun DimmedDialogWindow() {
    var ancestor: View? = LocalView.current
    while (ancestor != null && ancestor !is DialogWindowProvider) ancestor = ancestor.parent as? View
    val window = (ancestor as? DialogWindowProvider)?.window
    DisposableEffect(window) {
        // Keep the dim pixels in the popup's own buffer so display and capture use
        // the same layer. Disable the native effect to avoid double darkening.
        window?.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        window?.setDimAmount(0f)
        // Pleos skips native window animations. Animate the content above and
        // suppress default window effects so there is never a second transition.
        window?.setWindowAnimations(0)
        // This window belongs to the Dialog and is discarded on dismissal. Keep
        // its attributes through the exit so native dimming cannot cover the rail.
        onDispose { }
    }
}
