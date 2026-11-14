package com.moagauge.pleos.cctv

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.moagauge.pleos.BuildConfig
import com.moagauge.pleos.navigation.GeoPoint
import com.moagauge.pleos.network.NetworkMonitor
import kotlinx.coroutines.launch

class CctvViewModel(application: Application) : AndroidViewModel(application) {
    private val source = BuildConfig.ITS_CCTV_API_KEY.trim().takeIf { it.isNotEmpty() && it != "test" }?.let(::ItsCctvSource)
    private val networkMonitor = NetworkMonitor(application)
    private val controller = CctvController(source, viewModelScope, demoAllowed = BuildConfig.DEBUG,
        initiallyOnline = networkMonitor.isOnline())
    val state = controller.state

    init {
        viewModelScope.launch { networkMonitor.online.collect(controller::setNetworkOnline) }
    }

    fun setVisible(active: Boolean) = controller.setVisible(active)
    fun updateLocation(point: GeoPoint?, browsingAllowed: Boolean) = controller.updateLocation(point, browsingAllowed)
    fun openPanel(cameraId: String? = null) = controller.openPanel(cameraId)
    fun closePanel() = controller.closePanel()
    fun selectCamera(id: String) = controller.selectCamera(id)
    fun dismissVideo() = controller.dismissVideo()
    fun refresh() = controller.refresh()
    fun showDemoCameras() = controller.showDemoCameras()
    fun onStreamError(cameraId: String, playbackGeneration: Long) {
        // Player failure can precede delivery of the default-network callback.
        controller.setNetworkOnline(networkMonitor.isOnline())
        controller.onStreamError(cameraId, playbackGeneration)
    }

    override fun onCleared() { controller.setVisible(false) }
}
