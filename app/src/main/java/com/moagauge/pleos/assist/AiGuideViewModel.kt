package com.moagauge.pleos.assist

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Activity-owned state survives window reconfiguration and menu navigation. */
class AiGuideViewModel : ViewModel() {
    private val session = AiGuideSession(nowElapsedMs = SystemClock::elapsedRealtime)
    val state = session.state
    private var ticker: Job? = null

    fun setVisible(active: Boolean) {
        session.setVisible(active)
        if (active && ticker?.isActive != true) {
            ticker = viewModelScope.launch {
                while (isActive) { session.tick(); delay(500) }
            }
        } else if (!active) {
            ticker?.cancel()
            ticker = null
        }
    }

    fun updateContext(context: AiGuideContext) = session.updateContext(context)
    fun requestBriefing() = session.requestBriefing()
    fun dismiss() = session.dismiss()
    override fun onCleared() { ticker?.cancel(); session.setVisible(false) }
}
