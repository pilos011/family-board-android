# Family Board (안드로이드 앱)

Windows 11 환경의 개인 안드로이드 앱 프로젝트. 루트: `D:\AI-Projects\Personal\family-board-android`.

## 시작 시 참고
- **작업 스킬(필수): 글로벌 스킬 `family-board-android`** — 빌드/설치/푸시 루틴, 아키텍처(Compose+Firestore+FCM data-only+자체 릴레이 서버), 기능 맵, 반복 함정이 모두 정리돼 있음. 작업 시작 전 먼저 읽을 것.
- 프로젝트 로컬 메모리: `.claude/memory/`(인덱스 `MEMORY.md`, `android_dev_common.md`·`project_family_board.md`·`project_github.md`·`user_prefs.md`).
- (라디오 앱 노하우가 필요하면 글로벌 스킬 `korean-radio-android-app` 참조 — 그 스킬의 대표 프로젝트는 `D:\AI-Projects\Personal\esp32s3-radio-android`(별도 세션)이며, 예전 이 레포에 있던 중복 사본은 제거함.)

## 핵심 규칙
- **빌드**: `cmd //c "gradlew.bat assembleDebug"` → `app/build/outputs/apk/debug/app-debug.apk`. (`cmd //c` 래퍼가 `EXIT=1`을 뱉어도 무시하고 로그의 `BUILD SUCCESSFUL`을 신뢰.)
- **설치**: **PowerShell에서 `adb install -r` 직접** 실행이 정상 동작한다.
  ```powershell
  $adb = "D:\Personal\Dev-Project\Android\SDK\platform-tools\adb.exe"
  & $adb -s <serial> install -r "D:\AI-Projects\Personal\family-board-android\app\build\outputs\apk\debug\app-debug.apk"
  ```
  - ⚠️ `push` + `pm install`은 쓰지 말 것(멈추는 사례 있었음). "push 후 pm install"은 **Git Bash에서 adb 를 직접 돌릴 때** 경로 변환 함정을 피하려던 옛 방식(`android_dev_common.md`) — PowerShell로 `adb install -r`를 쓰면 그 함정이 없다.
  - ⚠️ 설치가 "Success" 없이 멈추면 폰 개발자옵션 "USB로 앱 확인"(Verify apps over USB)을 끌 것. offline/unauthorized면 `adb kill-server; adb start-server` 후 폰에서 USB디버깅 재승인.
  - adb.exe: `D:\Personal\Dev-Project\Android\SDK\platform-tools\adb.exe` (AI-Projects 밖, 이동되지 않음).
- **버전 관리**: `main`=안정 / 신기능=`feature/<이름>` 브랜치. 문서 UTF-8(README·CHANGELOG·HANDOVER). 버전마다 GitHub Release(APK `family-board-<ver>.apk` + 소스 zip).
- **배포(GitHub)**: **`gh` CLI 사용**(설치돼 있음). 한글 릴리즈 노트는 파일 경유(`gh api --input` / `gh release upload`). push 자격증명이 막히면 `gh auth setup-git` 후 `git -c credential.helper='!gh auth git-credential' push`.
- **배포(가족 폰 인앱 업데이트)**: APK + `version.json`을 **신 NAS(NANAS, 192.168.10.165) `/volume1/docker/family-board/apk/`** 에 올린다(공개주소 `http://y.iptime.org:8390/apk/`). 접속·배포 절차·자격증명은 글로벌 스킬 `family-board-android` 참조(SSH 자격증명은 스킬이 아니라 `My-Home-Assistant/INFO.md`). **명시적으로 "배포"라고 할 때만** 수행.
