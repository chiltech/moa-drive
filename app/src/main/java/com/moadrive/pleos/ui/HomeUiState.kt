package com.moadrive.pleos.ui

import com.moadrive.pleos.vehicle.VehicleTelemetry

/** Display state uses the same nullable readings as the active vehicle session. */
data class HomeUiState(
    val gear: String? = null,
    val speedKmh: Int? = null,
    val batteryPercent: Int? = null,
    val rangeKm: Int? = null,
    val efficiencyKmPerKwh: Double? = null,
    val efficiencyLabel: String = "운행 전비",
    val connectionLabel: String = "차량 연결 대기",
) {
    companion object {
        fun fromVehicle(telemetry: VehicleTelemetry) = HomeUiState(
            gear = telemetry.gear,
            speedKmh = telemetry.speedKmh,
            batteryPercent = telemetry.batteryPercent,
            rangeKm = telemetry.rangeKm,
            efficiencyKmPerKwh = telemetry.efficiencyKmPerKwh,
            efficiencyLabel = telemetry.efficiencyLabel,
            connectionLabel = telemetry.connectionLabel,
        )
    }
}

enum class HomeService(val label: String, val icon: MoaIconKind, val description: String) {
    DRIVE("주행", MoaIconKind.DRIVE, "카카오내비 길 안내와 실제 차량 정보를 한곳에서 확인해요."),
    TRIPS("주행기록", MoaIconKind.TRIPS, "자동으로 저장한 주행 거리·시간·전비·배터리 사용량을 날짜별로 확인해요."),
    CONDITION_NOTE("컨디션 노트", MoaIconKind.CONDITION_NOTE, "비슷한 주행 기록과 비교해 내 차의 평소와 오늘의 차이를 살펴봐요."),
    PASSPORT("패스포트", MoaIconKind.PASSPORT, "주행 중 방문한 구역을 지도와 스탬프로 모으고, 주행별로 살펴봐요."),
    BATTERY_BUDGET("배터리 예산 드라이브", MoaIconKind.BATTERY, "돌아올 배터리를 남기고 시간과 잔량에 맞는 드라이브를 계획해요."),
    SAFETY("안전", MoaIconKind.SAFETY, "현재 위치·목적지 주변의 날씨 예보와 출발 안내를 확인해요."),
    CHILD_CARE("자녀 케어", MoaIconKind.CHILD_CARE, "학교생활 대화부터 건강 일정까지, 아이의 하루를 함께 살펴요."),
    PET_CARE("반려동물 케어", MoaIconKind.PET_CARE, "강아지와 고양이의 활동·식사 기록을 살피고, 함께할 장소를 찾아요."),
    SOUND("사운드", MoaIconKind.SOUND, "차량 속도에 반응하는 실내 합성 사운드를 골라 보세요."),
    INSURANCE("보험", MoaIconKind.INSURANCE, "내 보험사의 공식 도움 요청 채널과 주행 마일리지를 확인해요."),
    SETTINGS("설정", MoaIconKind.SETTINGS, "모아 드라이브 · 0.1.0\n차량에서 제공하는 정보를 표시합니다. 제공되지 않는 정보는 —로 표시됩니다."),
}
