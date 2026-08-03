# Family Board (안드로이드 앱)

Windows 11 + Git Bash 환경의 개인 안드로이드 앱 프로젝트. 루트: `D:\Personal\family-board-android`.

## 시작 시 참고
- 프로젝트/공통 메모리: `.claude/memory/` (인덱스: `.claude/memory/MEMORY.md`)
- 안드로이드 노하우 스킬: `.claude/skills/korean-radio-android-app` (media3/빌드/adb/릴리즈 범용 참고)

## 핵심 규칙 (메모리 요약)
- 빌드: `./gradlew.bat assembleDebug` → `app/build/outputs/apk/debug/app-debug.apk`
- 설치: `adb install` 직접 금지 — push 후 `pm install` (Git Bash 경로 함정, `android_dev_common.md` 참조)
- adb.exe: `D:\Personal\Dev-Project\Android\SDK\platform-tools\adb.exe`
- 버전 관리: main=안정 / 신기능=feature 브랜치, 문서 UTF-8, README·CHANGELOG·HANDOVER 3종
- 배포: GitHub REST API (CLI 없음), 디버그 APK를 릴리즈 asset으로 업로드
