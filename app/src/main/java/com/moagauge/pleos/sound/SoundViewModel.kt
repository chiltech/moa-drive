package com.moagauge.pleos.sound

import android.app.Application
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import com.moagauge.pleos.BuildConfig
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.moagauge.pleos.vehicle.VehicleTelemetry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/** Foreground-only cabin audio. A saved profile never restores playback. */
class SoundViewModel(application: Application) : AndroidViewModel(application) {
    private val preferences = application.getSharedPreferences("moa_sound", 0)
    private val audioManager = application.getSystemService(AudioManager::class.java)
    private val main = Handler(Looper.getMainLooper())
    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
        .build()
    private val mutableState = MutableStateFlow(SoundUiState(
        profile = SoundProfile.fromStored(preferences.getString("profile", null)),
        volume = preferences.getFloat("volume", .35f).takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: .35f,
    ))
    val state = mutableState.asStateFlow()

    private var visible = false
    private var telemetry = VehicleTelemetry()
    private var session: Session? = null

    fun setVisible(value: Boolean) {
        visible = value
        if (!value && session != null) stopWithMessage("앱을 벗어나 사운드를 껐어요.")
    }

    fun updateTelemetry(value: VehicleTelemetry) {
        telemetry = value
        val playing = session ?: return
        if (playing.mode == SoundMode.LIVE && !value.supportsSound()) {
            stopWithMessage("차량 속도·기어 정보를 확인할 수 없어 사운드를 껐어요.")
        } else if (playing.mode == SoundMode.PREVIEW && value.shouldStopSoundPreview()) {
            stopWithMessage("차량 속도·기어 정보를 확인할 수 없어 미리듣기를 마쳤어요.")
        } else updateParameters()
    }

    fun selectProfile(profile: SoundProfile) {
        mutableState.value = state.value.copy(profile = profile)
        preferences.edit().putString("profile", profile.name).apply()
        updateParameters()
    }

    fun setVolume(volume: Float) {
        if (!volume.isFinite()) return
        val safeVolume = volume.coerceIn(0f, 1f)
        mutableState.value = state.value.copy(volume = safeVolume)
        preferences.edit().putFloat("volume", safeVolume).apply()
        updateParameters()
    }

    fun start(browsingAllowed: Boolean) {
        if (!visible || !browsingAllowed || !telemetry.canStartSound()) {
            mutableState.value = state.value.copy(message = "차량 속도·기어 정보를 확인한 뒤 켤 수 있어요.")
            return
        }
        begin(SoundMode.LIVE)
    }

    fun preview(browsingAllowed: Boolean) {
        if (!visible || !browsingAllowed || !telemetry.canPreviewSound()) return
        begin(SoundMode.PREVIEW)
    }

    fun stop() = stopWithMessage("사운드를 껐어요.")

    private fun begin(mode: SoundMode) {
        stopWithMessage("사운드를 준비하고 있어요.")
        if (!visible) return
        val candidate = Session(mode)
        candidate.focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(attributes)
            .setAcceptsDelayedFocusGain(false)
            .setWillPauseWhenDucked(false)
            .setOnAudioFocusChangeListener({ change -> onFocus(candidate, change) }, main)
            .build()
        session = candidate
        val granted = runCatching { audioManager.requestAudioFocus(candidate.focus) }.getOrDefault(AudioManager.AUDIOFOCUS_REQUEST_FAILED)
        if (granted != AudioManager.AUDIOFOCUS_REQUEST_GRANTED || session !== candidate || !visible) {
            stopWithMessage("지금은 오디오를 사용할 수 없어요. 다른 안내가 끝난 뒤 다시 켜 주세요.")
            return
        }
        mutableState.value = state.value.copy(mode = mode, pausedForFocus = false, ducked = false,
            previewSpeedKmh = if (mode == SoundMode.PREVIEW) 0 else null,
            message = if (mode == SoundMode.PREVIEW) "출발·가속·감속을 5초 동안 들어보세요." else "정차하면 무음, 출발하면 속도에 따라 소리가 커져요.")
        updateParameters()
        candidate.worker = viewModelScope.launch(Dispatchers.IO) { play(candidate) }
    }

    private fun onFocus(owner: Session, change: Int) {
        // Late focus grants cannot resurrect a stopped/backgrounded session.
        if (session !== owner || !owner.running || !visible) return
        when (change) {
            AudioManager.AUDIOFOCUS_LOSS -> stopWithMessage("다른 오디오가 시작되어 사운드를 껐어요.")
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                runCatching { owner.outputGate.pauseForFocus() }
                mutableState.value = state.value.copy(pausedForFocus = true, ducked = false,
                    message = "다른 음성 안내가 끝날 때까지 잠시 멈췄어요.")
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                mutableState.value = state.value.copy(ducked = true,
                    message = "다른 안내를 들을 수 있도록 소리를 낮췄어요.")
                updateParameters()
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                owner.outputGate.resumeFocus()
                mutableState.value = state.value.copy(pausedForFocus = false, ducked = false,
                    message = if (owner.mode == SoundMode.PREVIEW) "출발·가속·감속을 5초 동안 들어보세요." else "정차하면 무음, 출발하면 속도에 따라 소리가 커져요.")
                updateParameters()
            }
        }
    }

    private fun updateParameters() {
        val owner = session ?: return
        val current = state.value
        val speed = if (owner.mode == SoundMode.PREVIEW) 0.0 else telemetry.speedKmh?.toDouble() ?: 0.0
        val gear = if (owner.mode == SoundMode.PREVIEW) "D" else telemetry.gear ?: "?"
        val p = SoundParameters(current.profile, speed, gear, current.volume.toDouble(), if (current.ducked) .2 else 1.0)
        owner.parameters = p
        val receivedAt = SystemClock.elapsedRealtime()
        val transition = runCatching { owner.outputGate.updateSpeed(speed) }.getOrElse {
            stopWithMessage("오디오 출력을 제어할 수 없어 사운드를 껐어요.")
            return
        }
        when (transition) {
            SoundOutputGate.MotionChange.STOPPED -> logOutput(owner, "live_zero_flushed", receivedAt)
            SoundOutputGate.MotionChange.STARTED -> logOutput(owner, "live_motion", receivedAt)
            SoundOutputGate.MotionChange.NONE -> Unit
        }
    }

    private fun logOutput(owner: Session, event: String, receivedAt: Long = SystemClock.elapsedRealtime()) {
        if (!BuildConfig.DEBUG || owner.mode != SoundMode.LIVE) return
        val frames = runCatching { owner.track?.bufferSizeInFrames }.getOrNull()
        Log.d("SoundOutput", "event=$event elapsedMs=${SystemClock.elapsedRealtime()} " +
            "receivedElapsedMs=$receivedAt wallMs=${System.currentTimeMillis()} " +
            "speedObservedElapsedMs=${telemetry.speedObservedElapsedMs} speedKmh=${telemetry.speedKmh} " +
            "epoch=${owner.outputGate.epoch} bufferFrames=$frames performanceMode=${runCatching { owner.track?.performanceMode }.getOrNull()}")
    }

    private suspend fun play(owner: Session) {
        var track: AudioTrack? = null
        var sink: SoundOutputGate.Sink? = null
        try {
            if (!owner.running) return
            val minBuffer = AudioTrack.getMinBufferSize(SoundSynthesizer.SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
            check(minBuffer > 0) { "Unsupported audio format" }
            val output = AudioTrack.Builder()
                .setAudioAttributes(attributes)
                .setAudioFormat(AudioFormat.Builder().setSampleRate(SoundSynthesizer.SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO).setEncoding(AudioFormat.ENCODING_PCM_16BIT).build())
                .setTransferMode(AudioTrack.MODE_STREAM)
                .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
                .setBufferSizeInBytes(maxOf(minBuffer, 960 * 2 * 4))
                .build()
            track = output
            check(output.state == AudioTrack.STATE_INITIALIZED) { "Audio output unavailable" }
            owner.track = output
            val deviceOutput = TrackOutput(output)
            sink = deviceOutput
            owner.outputGate.attach(deviceOutput)
            logOutput(owner, "track_attached")
            val recordings = SoundProfile.entries.filter { it.recordingAsset != null }.associateWith { profile ->
                getApplication<Application>().assets.open(checkNotNull(profile.recordingAsset)).use(RecordedLoop::readWav)
            }
            var renderer = SoundSynthesizer(recordings = recordings)
            var rendererEpoch = owner.outputGate.epoch
            val buffer = ShortArray(960)
            var submittedFrames = 0L
            var lastUiFrame = -4_800L
            var frameOffset = 0L
            val engineHistory = ArrayDeque<Pair<Long, EngineSoundFrame>>()
            engineHistory.addLast(0L to renderer.engineState)
            val preview = owner.mode == SoundMode.PREVIEW
            suspend fun updateEngineProgress() {
                val frame = output.playbackHeadPosition.toLong() and 0xffff_ffffL
                if (frame < lastUiFrame) {
                    // Live focus/standstill flushes reset the device playback head to zero.
                    frameOffset = submittedFrames - frame
                    engineHistory.clear()
                    engineHistory.addLast(frame to renderer.engineState)
                }
                if (frame >= lastUiFrame && frame - lastUiFrame < 2_400L) return
                lastUiFrame = frame
                val previewSpeed = if (preview) SoundPreview.parametersAt(frame, owner.parameters).speedKmh.roundToInt() else null
                while (engineHistory.size > 1 && engineHistory[1].first <= frame) engineHistory.removeFirst()
                val engine = engineHistory.first().second
                withContext(Dispatchers.Main.immediate) {
                    if (session === owner && owner.running) {
                        mutableState.value = state.value.copy(previewSpeedKmh = previewSpeed,
                            fundamentalHz = (engine.rpm / 30.0).roundToInt(), syntheticRpm = engine.rpm.roundToInt(),
                            syntheticGear = engine.gear, isShifting = engine.shiftCut > .2)
                    }
                }
            }
            while (owner.running && viewModelScope.isActive &&
                (!preview || submittedFrames < SoundPreview.TOTAL_FRAMES)) {
                val block = owner.outputGate.beginBlock()
                if (block == null) {
                    delay(10)
                    continue
                }
                if (!preview && rendererEpoch != block.epoch) {
                    // Reuse the source loop positions, but start a new volume fade after a flushed stop.
                    renderer = SoundSynthesizer(recordings = recordings)
                    rendererEpoch = block.epoch
                }
                renderer.parameters = if (preview) SoundPreview.parametersAt(submittedFrames, owner.parameters)
                    else owner.parameters
                renderer.render(buffer)
                val frameCount = if (preview) minOf(buffer.size.toLong(), SoundPreview.TOTAL_FRAMES - submittedFrames).toInt()
                    else buffer.size
                var offset = 0
                while (offset < frameCount && owner.running) {
                    // A stop invalidates even a partly written block. It must never refill a flushed queue.
                    val written = owner.outputGate.write(block, buffer, offset, frameCount - offset)
                    if (written == null) {
                        if (preview && owner.running) {
                            // Focus interruptions keep the exact remainder of a five-second preview.
                            delay(20)
                            continue
                        }
                        break
                    }
                    check(written >= 0) { "Audio output disconnected" }
                    if (written == 0) delay(10) else {
                        offset += written
                        submittedFrames += written
                    }
                }
                engineHistory.addLast((submittedFrames - frameOffset) to renderer.engineState)
                if (engineHistory.size > 32) engineHistory.removeFirst()
                updateEngineProgress()
            }
            if (preview) {
                // Finish after five seconds of consumed PCM, not wall time spent waiting for audio focus.
                while (owner.running && viewModelScope.isActive &&
                    (output.playbackHeadPosition.toLong() and 0xffff_ffffL) < SoundPreview.TOTAL_FRAMES) {
                    owner.outputGate.resumePreview()
                    updateEngineProgress()
                    delay(20)
                }
                withContext(Dispatchers.Main.immediate) {
                    if (session === owner && owner.running) stopWithMessage("5초 미리듣기를 마쳤어요.")
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            withContext(Dispatchers.Main.immediate) {
                if (session === owner) stopWithMessage("음원이나 오디오 출력을 준비할 수 없어 사운드를 껐어요.")
            }
        } finally {
            sink?.let(owner.outputGate::detach)
            owner.track = null
            runCatching { track?.pause() }
            runCatching { track?.flush() }
            runCatching { track?.release() }
        }
    }

    private fun stopWithMessage(message: String) {
        val previous = session
        session = null
        if (previous != null) {
            previous.running = false
            runCatching { previous.outputGate.stop() }
            runCatching { audioManager.abandonAudioFocusRequest(previous.focus) }
            previous.worker?.cancel()
        }
        mutableState.value = state.value.copy(mode = SoundMode.OFF, pausedForFocus = false, ducked = false,
            fundamentalHz = null, previewSpeedKmh = null, syntheticRpm = null, syntheticGear = null,
            isShifting = false, message = message)
    }

    override fun onCleared() {
        visible = false
        stopWithMessage("사운드를 껐어요.")
        super.onCleared()
    }

    private class TrackOutput(private val track: AudioTrack) : SoundOutputGate.Sink {
        override fun volume(value: Float) { track.setVolume(value) }
        override fun pause() { track.pause() }
        override fun flush() { track.flush() }
        override fun play() { track.play() }
        override fun write(samples: ShortArray, offset: Int, count: Int): Int =
            track.write(samples, offset, count, AudioTrack.WRITE_NON_BLOCKING)
    }

    private class Session(val mode: SoundMode) {
        val outputGate = SoundOutputGate(preview = mode == SoundMode.PREVIEW)
        lateinit var focus: AudioFocusRequest
        var worker: Job? = null
        @Volatile var running = true
        @Volatile var track: AudioTrack? = null
        @Volatile var parameters = SoundParameters(SoundProfile.AURORA, 0.0, "P", 0.0)
    }
}
