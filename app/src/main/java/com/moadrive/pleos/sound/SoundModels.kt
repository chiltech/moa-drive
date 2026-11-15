package com.moadrive.pleos.sound

import com.moadrive.pleos.vehicle.VehicleTelemetry

enum class SoundKind { GENERATED, ORIGINAL, RECORDING }

/** User-provided Suno sounds, electronic synthesis and accurately attributed field recordings. */
enum class SoundProfile(
    val title: String,
    val character: String,
    val kind: SoundKind,
    val credit: String? = null,
    val sourceUrl: String? = null,
    val licenseUrl: String? = null,
    internal val recordingAsset: String? = null,
) {
    ELECTRIC_PROPULSION("일렉트릭 드라이브", "미래적인 울림의 전자 추진 사운드", SoundKind.GENERATED,
        credit = "MOA DRIVE 컬렉션",
        recordingAsset = "sound/suno/electric_propulsion.wav"),
    MOTOR_GROWL("모터 그로울", "깊고 묵직한 모터의 울림", SoundKind.GENERATED,
        credit = "MOA DRIVE 컬렉션",
        recordingAsset = "sound/suno/motor_growl.wav"),
    SILENT_DRIVE("사일런트 드라이브", "차분하게 이어지는 은은한 주행 사운드", SoundKind.GENERATED,
        credit = "MOA DRIVE 컬렉션",
        recordingAsset = "sound/suno/silent_drive.wav"),
    SILENT_DRIVE_ALT("나이트 크루즈", "고요한 밤길에 어울리는 부드러운 울림", SoundKind.GENERATED,
        credit = "MOA DRIVE 컬렉션",
        recordingAsset = "sound/suno/silent_drive_alt.wav"),
    SMOOTH_MOTOR("스무스 모터", "편안하게 이어지는 매끄러운 모터 사운드", SoundKind.GENERATED,
        credit = "MOA DRIVE 컬렉션",
        recordingAsset = "sound/suno/smooth_motor.wav"),
    AURORA("오로라", "부드럽게 펼쳐지는 화음과 유려한 가속", SoundKind.ORIGINAL),
    PULSE("펄스", "묵직한 저음과 속도에 반응하는 맥동", SoundKind.ORIGINAL),
    PRISM("프리즘", "맑은 전자음과 반짝이는 고음의 질감", SoundKind.ORIGINAL),
    STUDEBAKER("클래식 배기", "1953 Studebaker · 깊고 풍부한 배기음", SoundKind.RECORDING,
        "1953 Studebaker-Starts, Rev and Idle — lonemonk / Freesound · CC BY 4.0",
        "https://freesound.org/people/lonemonk/sounds/201988/", "https://creativecommons.org/licenses/by/4.0/", "sound/studebaker.wav"),
    RENAULT("소형 가솔린", "Renault 19 · 경쾌한 엔진 회전음", SoundKind.RECORDING,
        "Renault 19 underhood acceleration — lovretta / Freesound · CC0 1.0",
        "https://freesound.org/people/lovretta/sounds/141240/", "https://creativecommons.org/publicdomain/zero/1.0/", "sound/renault.wav"),
    DIESEL("디젤 트럭", "차종 미상 · 두터운 저음과 기계적인 울림", SoundKind.RECORDING,
        "Diesel idle 1.wav — C-V / Freesound · CC0 1.0",
        "https://freesound.org/people/C-V/sounds/565598/", "https://creativecommons.org/publicdomain/zero/1.0/", "sound/diesel.wav");

    companion object {
        // Retired synthetic Hyundai profiles cannot be relabelled as recordings of those cars.
        fun fromStored(name: String?): SoundProfile = entries.firstOrNull { it.name == name } ?: ELECTRIC_PROPULSION
    }
}

enum class SoundMode { OFF, LIVE, PREVIEW }

data class SoundUiState(
    val profile: SoundProfile = SoundProfile.ELECTRIC_PROPULSION,
    val volume: Float = .35f,
    val mode: SoundMode = SoundMode.OFF,
    val pausedForFocus: Boolean = false,
    val ducked: Boolean = false,
    val fundamentalHz: Int? = null,
    val previewSpeedKmh: Int? = null,
    val syntheticRpm: Int? = null,
    val syntheticGear: Int? = null,
    val isShifting: Boolean = false,
    val message: String = "사운드를 골라 미리듣기 또는 주행 재생을 켜 보세요.",
) {
    val isActive get() = mode != SoundMode.OFF
    val isPlaying get() = isActive && !pausedForFocus
}

internal fun String?.isSoundGear() = this in setOf("P", "R", "N", "D", "1", "2", "3", "4", "5", "6", "7", "8", "9")

fun VehicleTelemetry.supportsSound(): Boolean = active && speedKmh != null && speedKmh in 0..360 &&
    gear.isSoundGear() && ignitionOn != false

/** Media controls remain available in motion, but live playback still needs valid vehicle signals. */
fun VehicleTelemetry.canStartSound(): Boolean = supportsSound()

fun VehicleTelemetry.canPreviewSound(): Boolean = !active || supportsSound()

internal fun VehicleTelemetry.shouldStopSoundPreview(): Boolean = !canPreviewSound()
