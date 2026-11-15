# 제출 APK

**팀 케어메이트 · 모아 드라이브 · release 1.0.0**

다운로드·최종 제출 파일명: `CareMate_MOADRIVE_release.apk`

APK가 GitHub 일반 저장소의 파일 크기 제한을 초과하므로 공모전 안내에 따라 GitHub Releases에 등록합니다.

- [Release 페이지 — APK 다운로드](https://github.com/chiltech/moa-drive/releases/tag/v1.0.0)
- [APK 직접 다운로드](https://github.com/chiltech/moa-drive/releases/download/v1.0.0/CareMate_MOADRIVE_release.apk)
- [SHA-256 체크섬](SHA256SUMS)

다운로드한 `CareMate_MOADRIVE_release.apk`를 이름 변경 없이 그대로 제출합니다.

접수 페이지의 APK 경로 항목에는 위 **Release 페이지 주소**를 입력하세요. 접수 메일에는 저장소 주소와 최종 commit hash를 함께 기재합니다.

```bash
# APK와 SHA256SUMS를 같은 폴더에 내려받은 뒤 검증
shasum -a 256 -c SHA256SUMS
adb -s emulator-5554 install -r 'CareMate_MOADRIVE_release.apk'
adb -s emulator-5554 shell am start --user current -n com.moagauge.pleos/.MainActivity
```

개발용 서명 앱이 이미 설치된 장치에는 새 release 서명 APK를 덮어쓸 수 없습니다. 기존 데이터를 보존하려면 새 Pleos AVD를 이용하세요.
