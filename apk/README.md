# 제출 APK

**팀 케어메이트 · 모아 드라이브 · release 1.0.1**

다운로드·최종 제출 파일명: `CareMate_MOADRIVE_release.apk`

APK가 GitHub 일반 저장소의 파일 크기 제한을 초과하므로 공모전 안내에 따라 GitHub Releases에 등록합니다.

- [Release 페이지 — APK 다운로드](https://github.com/chiltech/moa-drive/releases/tag/v1.0.1)
- [APK 직접 다운로드](https://github.com/chiltech/moa-drive/releases/download/v1.0.1/CareMate_MOADRIVE_release.apk)
- [SHA-256 체크섬](SHA256SUMS)

다운로드한 `CareMate_MOADRIVE_release.apk`를 이름 변경 없이 그대로 제출합니다.

접수 페이지의 APK 경로 항목에는 위 **Release 페이지 주소**를 입력하세요. 접수 메일에는 저장소 주소와 최종 commit hash를 함께 기재합니다.

```bash
# APK와 SHA256SUMS를 같은 폴더에 내려받은 뒤 검증
shasum -a 256 -c SHA256SUMS
adb -s emulator-5554 install -r 'CareMate_MOADRIVE_release.apk'
adb -s emulator-5554 shell am start --user current -n com.moadrive.pleos/.MainActivity
```

이번 버전의 패키지는 `com.moadrive.pleos`입니다. 이전 패키지의 앱과 별도로 설치되며 기존 앱의 저장 데이터는 자동으로 옮겨지지 않습니다. 같은 패키지의 개발용 앱과 서명이 다르면 덮어쓰기 설치가 거부되므로 별도 AVD를 사용하세요.
