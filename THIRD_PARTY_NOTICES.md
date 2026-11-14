# Third-party notices · MOA DRIVE

이 문서는 포함 자산의 출처와 제작 경위를 기록합니다. MOA DRIVE 전체, 기존 MOAGAUGE 원본 또는 제3자 자산에 새로운 오픈소스 라이선스를 부여하지 않습니다. 아래 음원 출처와 행정구역 데이터의 upstream 안내는 2026-11-15에 확인했습니다.

## 차량 녹음 3개

파일 경로는 `app/src/main/assets/` 기준입니다.

| 포함 파일 | 원본 · 제작자 | 라이선스 |
| --- | --- | --- |
| `sound/studebaker.wav` | [1953 Studebaker-Starts, Rev and Idle — lonemonk / Freesound](https://freesound.org/people/lonemonk/sounds/201988/) | [CC BY 4.0](https://creativecommons.org/licenses/by/4.0/) |
| `sound/renault.wav` | [Renault 19 underhood acceleration — lovretta / Freesound](https://freesound.org/people/lovretta/sounds/141240/) | [CC0 1.0](https://creativecommons.org/publicdomain/zero/1.0/) |
| `sound/diesel.wav` | [Diesel idle 1.wav — C-V / Freesound](https://freesound.org/people/C-V/sounds/565598/) | [CC0 1.0](https://creativecommons.org/publicdomain/zero/1.0/) |

각 파일은 공개 HQ MP3 미리듣기에서 6초를 발췌한 파생본입니다. 원본 WAV/FLAC 마스터를 사용하지 않았습니다. 모노·48 kHz PCM16 변환, 28 Hz 고역통과/6 kHz 저역통과 필터, DC 제거, RMS 음량 정규화 및 높은 피크의 완만한 압축을 적용했습니다. 앱에서는 60 ms 교차 페이드와 차량 속도에 따른 음량 변화를 적용하며 원래 재생속도를 유지합니다. 특정 현대 N 차종의 실녹음으로 표시하지 않습니다.

## 사용자가 제공한 Suno 생성 음원 5개

앱 소유자가 Suno로 제작해 MOA DRIVE 통합용으로 제공한 음원입니다. 이름이 있는 실제 차량의 녹음이 아닙니다.

| 포함 파일 | 제공된 원본 파일 |
| --- | --- |
| `sound/suno/electric_propulsion.wav` | `MOA DRIVE — Electric Propulsion Loop.mp3` |
| `sound/suno/motor_growl.wav` | `Motor Growl.mp3` |
| `sound/suno/silent_drive_alt.wav` | `Silent Drive (1).mp3` |
| `sound/suno/silent_drive.wav` | `Silent Drive.mp3` |
| `sound/suno/smooth_motor.wav` | `Smooth Motor Hum.mp3` |

원본에서 루프 구간을 발췌하고 모노 PCM16 변환, DC 제거, 음량 정규화 및 피크 제한을 적용했습니다. 이 문서는 해당 음원에 CC 또는 다른 독립적인 재배포 라이선스를 부여하지 않습니다.

8개 음원의 원본·가공본 SHA-256, 발췌 구간 및 처리 상세는 [credits.json](app/src/main/assets/sound/credits.json), 텍스트 크레딧은 [CREDITS.txt](app/src/main/assets/sound/CREDITS.txt)에 있습니다.

## Inter 글꼴

Copyright 2020 The Inter Project Authors ([Inter 프로젝트](https://github.com/rsms/inter)). 기존 MOAGAUGE에서 사용한 `inter_variable.ttf`를 재사용했습니다. **SIL Open Font License 1.1** 전문은 [OFL-Inter.txt](app/src/main/assets/licenses/OFL-Inter.txt)에 포함되어 있습니다.

## 행정구역 데이터

`app/src/main/assets/passport/korea-municipalities-2023.json`은 국가공간정보포털(V-World)의 2023-07-29 행정구역 경계 자료를 바탕으로 한 [KnellBalm/kr-admin-geojson의 고정 커밋](https://github.com/KnellBalm/kr-admin-geojson/tree/3a859f2a45be8acf8237d9eefd9565f99cc603da)에서 가져온 자료를 앱 형식으로 구성한 것입니다.

출처: **국가공간정보포털(V-World), 공공데이터포털, KnellBalm/kr-admin-geojson**. Upstream README는 원본과 가공 결과물을 출처 표시 조건으로 자유롭게 사용할 수 있다고 안내합니다. 이를 별도의 표준 오픈소스 라이선스로 바꾸어 표기하지 않습니다. 포함 JSON에 기준일, 출처 링크와 원본 해시가 보존되어 있습니다.

## 생성형 그래픽과 MOAGAUGE 연속성

앱 그래픽에는 Higgsfield의 Seedream으로 제작한 메뉴 아이콘, 서비스별 일러스트, 스플래시 이미지와 화면 시안을 바탕으로 한 자산이 포함됩니다. 이 고지는 생성형 이미지에 별도의 CC/오픈소스 라이선스를 부여하지 않습니다.

Horizon 계기판은 기존 **MOAGAUGE** 구현에서 이식했습니다. 주행기록·패스포트 등의 화면 구성은 기존 MOAGAUGE의 레이아웃·카드·타이포그래피를 참고했으며, 기존 프로젝트에서 이어 받은 자산도 포함합니다. 이 문서는 해당 원본 코드·디자인에 새로운 라이선스를 부여하지 않습니다.

## 라이브러리와 SDK

Gradle wrapper, Kotlin, AndroidX/Jetpack Compose, Media3, kotlinx.coroutines, Kakao Mobility KNSDK 및 Pleos SDK 등은 각 제공자의 라이선스·저작권 고지·이용 조건을 유지합니다. 의존성 목록과 버전은 Gradle 설정에 기록되어 있으며, 이 문서는 각 의존성의 라이선스 전문이나 SDK 이용 조건을 대체하지 않습니다.
