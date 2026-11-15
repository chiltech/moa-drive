package com.moadrive.pleos.assist

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.annotation.MainThread
import ai.pleos.playground.tts.TextToSpeech
import ai.pleos.playground.tts.constant.ErrorType
import ai.pleos.playground.tts.constant.Mode
import ai.pleos.playground.tts.listener.EventListener
import ai.pleos.playground.tts.listener.OnInitializeListener
import com.moadrive.pleos.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

/** Pleos on-device speech, without server credentials or queued briefing text. */
class AiGuideSpeech(context: Context) {
    private val handler = Handler(Looper.getMainLooper())
    private val audio = context.applicationContext.getSystemService(AudioManager::class.java)
    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANT)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()
    private val mutableAvailable = MutableStateFlow(false)
    val available = mutableAvailable.asStateFlow()
    private val mutableSpeaking = MutableStateFlow(false)
    val speaking = mutableSpeaking.asStateFlow()
    private val mutableLastFailureId = MutableStateFlow<String?>(null)
    val lastFailureId = mutableLastFailureId.asStateFlow()
    private val spokenIds = linkedSetOf<String>()
    private var engine: TextToSpeech? = null
    private var closed = false
    private var initialized = false
    private var stopping = false
    private var activeId: String? = null
    private var focusHeld = false
    private val timeout = Runnable {
        mutableLastFailureId.value = activeId
        if (BuildConfig.DEBUG) Log.d("PleosAiSpeech", "utterance timed out id=$activeId")
        stop()
    }
    private val stopTimeout = Runnable {
        // Callbacks carry no utterance ID. Do not let a late stop finish a newer request.
        initialized = false
        mutableAvailable.value = false
        debug("stop callback timed out; retaining visual guidance")
    }
    private val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
        .setAudioAttributes(attributes)
        .setAcceptsDelayedFocusGain(false)
        .setWillPauseWhenDucked(true)
        .setOnAudioFocusChangeListener({ change ->
            if (change != AudioManager.AUDIOFOCUS_GAIN) {
                debug("audio focus lost code=$change")
                stop()
            }
        }, handler)
        .build()

    private val listener = object : EventListener {
        override fun onReady() = Unit
        override fun onUpdatedRms(rms: Double) = Unit
        override fun onStart() {
            handler.post {
                if (!closed && !stopping && activeId != null) {
                    mutableSpeaking.value = true
                    debug("utterance started id=$activeId")
                }
            }
        }
        override fun onDone() = finish("done")
        override fun onStop() = finish("stopped")
        override fun onError(errorType: ErrorType) = finish("error", errorType)
    }

    init {
        runCatching {
            // Pleos exposes its own binder contract; Android's default TTS engine does not bind it.
            val tts = TextToSpeech(context.applicationContext, Mode.ON_DEVICE)
            engine = tts
            tts.initialize(object : OnInitializeListener {
                override fun onSuccess() {
                    handler.post {
                        if (closed) return@post
                        initialized = runCatching {
                            tts.addEventListener(listener)
                            tts.setLocale(Locale.KOREA)
                            tts.setAudioAttributes(attributes)
                        }.isSuccess
                        mutableAvailable.value = initialized
                        debug("Pleos ON_DEVICE Korean voice ready=$initialized")
                    }
                }
                override fun onFailure(errorType: ErrorType) {
                    handler.post {
                        initialized = false
                        mutableAvailable.value = false
                        debug("Pleos ON_DEVICE initialization failed code=$errorType")
                    }
                }
            })
        }.onFailure { debug("Pleos ON_DEVICE initialization exception=${it.javaClass.simpleName}") }
    }

    /** Returns false without queuing when unavailable. Never replay an expired message after initialization. */
    @MainThread
    fun speak(id: String, text: String): Boolean {
        val tts = engine ?: return false
        if (closed || !available.value || id in spokenIds || text.isBlank()) return false
        if (activeId != null) {
            stop()
            return false
        }
        val granted = runCatching { audio?.requestAudioFocus(focusRequest) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED }
            .getOrDefault(false)
        if (!granted) return false
        focusHeld = true
        activeId = id
        val result = runCatching { tts.speak(text.take(500), isFinal = true) }
        if (result.isFailure) {
            mutableLastFailureId.value = id
            debug("utterance rejected id=$id exception=${result.exceptionOrNull()?.javaClass?.simpleName}")
            stop()
            return false
        }
        spokenIds.add(id)
        while (spokenIds.size > 64) spokenIds.remove(spokenIds.first())
        mutableLastFailureId.value = null
        handler.postDelayed(timeout, 45_000L)
        debug("utterance accepted id=$id")
        return true
    }

    private fun finish(event: String, error: ErrorType? = null) {
        handler.post {
            if (closed) return@post
            val id = activeId
            if (error != null && id != null) mutableLastFailureId.value = id
            if (error in setOf(ErrorType.ON_DEVICE_ENGINE_ERROR, ErrorType.UNAVAILABLE_LANGUAGE,
                    ErrorType.PERMISSION_DENIED, ErrorType.NOT_SUPPORTED_API)) initialized = false
            debug("utterance $event id=$id code=$error")
            activeId = null
            stopping = false
            mutableSpeaking.value = false
            mutableAvailable.value = initialized
            handler.removeCallbacks(timeout)
            handler.removeCallbacks(stopTimeout)
            abandonFocus()
        }
    }

    @MainThread
    fun stop() {
        val hadRequest = activeId != null
        activeId = null
        mutableSpeaking.value = false
        handler.removeCallbacks(timeout)
        if (hadRequest && !stopping) {
            stopping = true
            mutableAvailable.value = false
            runCatching { engine?.stop() }
            handler.postDelayed(stopTimeout, 5_000L)
        }
        abandonFocus()
    }

    private fun abandonFocus() {
        if (focusHeld) {
            focusHeld = false
            runCatching { audio?.abandonAudioFocusRequest(focusRequest) }
        }
    }

    @MainThread
    fun close() {
        if (closed) return
        closed = true
        stop()
        handler.removeCallbacks(stopTimeout)
        mutableAvailable.value = false
        runCatching { engine?.removeEventListener(listener) }
        runCatching { engine?.release() }
        engine = null
    }

    private fun debug(message: String) {
        if (BuildConfig.DEBUG) Log.d("PleosAiSpeech", message)
    }
}
