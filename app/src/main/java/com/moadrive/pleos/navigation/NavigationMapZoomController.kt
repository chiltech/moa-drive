package com.moadrive.pleos.navigation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** The active map owns the action; a detached map must never receive button presses. */
class NavigationMapZoomController {
    var available by mutableStateOf(false)
        private set

    private var action: ((Boolean) -> Unit)? = null

    fun zoomIn() { action?.invoke(true) }
    fun zoomOut() { action?.invoke(false) }

    internal fun bind(onZoom: (Boolean) -> Unit): () -> Unit {
        action = onZoom
        available = true
        return {
            if (action === onZoom) {
                action = null
                available = false
            }
        }
    }
}
