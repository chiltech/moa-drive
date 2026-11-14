package com.moagauge.pleos.navigation.kakao

import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import com.kakaomobility.knsdk.R as SdkR
import com.kakaomobility.knsdk.ui.view.KNNaviView

/** All navigation actions belong to the Pleos controls and their AAOS restrictions. */
internal class NativeControlsPolicy(private val view: KNNaviView, private val showSectionInfo: () -> Boolean = { true }) : ViewTreeObserver.OnPreDrawListener {
    private var observed: ViewTreeObserver? = null
    private val preparedControls = java.util.Collections.newSetFromMap(java.util.IdentityHashMap<View, Boolean>())
    private val consumeTouch = View.OnTouchListener { _, _ -> true }
    private val sectionInfo = view.findViewById<View>(SdkR.id.component_section_info_container)
    private val sectionAccessibility = sectionInfo?.importantForAccessibility
    private val suppressedSectionNodes = java.util.IdentityHashMap<View, Pair<Boolean, Boolean>>()
    private val hiddenControls = listOfNotNull(
        // These readonly summaries are rendered by the MoaGauge-style Compose layers.
        // Lane/sign/junction views remain native because they carry additional road detail.
        view.curDirComponent,
        view.nextDirComponent,
        view.speedComponent,
        view.findViewById<View>(SdkR.id.component_cur_direction_popup),
        view.findViewById<View>(SdkR.id.component_over_speed),
        view.findViewById<View>(SdkR.id.component_highway_info),
        view.findViewById<View>(SdkR.id.component_traffic),
        // Camera perspective belongs to the persisted Pleos 2D/3D setting.
        view.findViewById<View>(SdkR.id.component_mapmode),
        view.bottomComponent,
        view.bottomMenuComponent,
        view.findViewById<View>(SdkR.id.component_around_menu),
        view.findViewById<View>(SdkR.id.component_route_info_container),
        view.findViewById<View>(SdkR.id.component_emergency),
        view.findViewById<View>(SdkR.id.component_accident),
        view.findViewById<View>(SdkR.id.component_yugo),
    )

    fun attach() {
        observed?.takeIf { it.isAlive }?.removeOnPreDrawListener(this)
        observed = view.viewTreeObserver.also { it.addOnPreDrawListener(this) }
        // Configure the native cancel behavior once when attaching the policy.
        if (view.bottomMenuComponent.useGuideCancel) view.bottomMenuComponent.useGuideCancel = false
        apply()
    }
    fun release() {
        observed?.takeIf { it.isAlive }?.removeOnPreDrawListener(this)
        observed = null
    }
    fun apply() {
        hiddenControls.forEach { control ->
            // The SDK updates component visibility from layoutSubViews/checkVisibility.
            // Suppress pixels without fighting that layout state or requesting another layout.
            // Touch, focus and accessibility remain blocked below, including alpha-zero children.
            if (control.alpha != 0f) control.alpha = 0f
            if (control.importantForAccessibility != View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS) {
                control.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
            }
            disableActions(control)
        }
        // Keep the SDK's extra section-distance detail, following the same visual toggle.
        // Preserve listeners and original enabled/focus states so toggling back is reversible.
        sectionInfo?.let { section ->
            if (showSectionInfo()) {
                if (suppressedSectionNodes.isNotEmpty()) {
                    section.alpha = 1f
                    section.importantForAccessibility = sectionAccessibility ?: View.IMPORTANT_FOR_ACCESSIBILITY_AUTO
                    suppressedSectionNodes.forEach { (node, state) -> node.isEnabled = state.first; node.isFocusable = state.second }
                    suppressedSectionNodes.clear()
                }
            } else {
                if (section.alpha != 0f) section.alpha = 0f
                section.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
                suppressSectionActions(section)
            }
        }
    }
    private fun suppressSectionActions(node: View) {
        if (!suppressedSectionNodes.containsKey(node)) suppressedSectionNodes[node] = node.isEnabled to node.isFocusable
        if (node.isEnabled) node.isEnabled = false
        if (node.isFocusable) node.isFocusable = false
        if (node is ViewGroup) for (index in 0 until node.childCount) suppressSectionActions(node.getChildAt(index))
    }
    private fun disableActions(control: View) {
        val firstVisit = preparedControls.add(control)
        if (firstVisit || control.isClickable || control.isLongClickable) {
            // Android listener setters can enable clickable flags. Clear listeners first.
            control.setOnClickListener(null)
            control.setOnLongClickListener(null)
        }
        if (firstVisit) control.setOnTouchListener(consumeTouch)
        if (control.isEnabled) control.isEnabled = false
        if (control.isClickable) control.isClickable = false
        if (control.isLongClickable) control.isLongClickable = false
        if (control.isFocusable) control.isFocusable = false
        if (control is ViewGroup) for (index in 0 until control.childCount) disableActions(control.getChildAt(index))
    }
    override fun onPreDraw(): Boolean { apply(); return true }
}
