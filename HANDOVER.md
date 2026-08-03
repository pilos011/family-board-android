# 가족보드 (Family Board) — HANDOVER

가족 4명이 일정/할일/장보기를 공유하는 안드로이드 앱. 이 문서는 개발·빌드·배포·미완료 항목을 통합 정리한다.

## 1. 기술 스택
| 항목 | 값 |
|------|-----|
| 빌드 | AGP 8.3.2, Kotlin 1.9.23, Gradle 8.6 |
| minSdk / targetSdk / compileSdk | 24 / 34 / 34 |
| UI | Jetpack Compose + Material 3 (Compose BOM 2024.02.02, compiler ext 1.5.11) |
| 네비게이션 | navigation-compose 2.7.7 |
| 저장/동기화 | Firebase Firestore(예정) / 현재는 인메모리 |
| 로컬 저장 | DataStore Preferences (본인 멤버 id) |
| 날짜 | java.time + core library desugaring |
| 공휴일 | 공공데이터포털 특일정보 API |

`applicationId` = `com.familyboard.app`.

## 2. 앱 구조 (기능)
- **온보딩**: 최초 실행 시 4명(선일/은선/준영/준호) 중 본인 선택 → DataStore 저장. (안드로이드가 자기 번호를 못 읽는 문제 때문에 번호 자동인식 대신 이름 선택 방식)
- **캘린더 탭**: 월간 그리드, 한국 공휴일 빨강 표시, 날짜별 구성원 색상 점, 선택일 일정 목록, + 로 일정 추가(제목/하루종일/시간/담당 구성원).
- **리스트 탭**: 장보기(하늘색)·할 일(초록) 보드 카드 → 상세에서 체크박스 항목 추가/완료/삭제, 항목별 작성자 표시.

## 3. 소스 구조
```
com.familyboard.app/
├── FamilyBoardApp.kt          # Application + AppContainer 보관
├── MainActivity.kt            # setContent, edge-to-edge
├── data/
│   ├── Member.kt              # 가족 4명 정의 + 색상/이름 헬퍼
│   ├── CurrentUserStore.kt    # DataStore (본인 멤버 id)
│   ├── AppContainer.kt        # 수동 DI (저장소 구현 교체 지점)
│   ├── model/Models.kt        # CalendarEvent, ListItem, BoardType, Holiday
│   ├── repo/
│   │   ├── BoardRepository.kt        # 인터페이스
│   │   ├── InMemoryBoardRepository.kt# 현재 활성 (데모/오프라인)
│   │   └── HolidayRepository.kt      # 공휴일 API (키 없으면 빈 목록)
│   └── firebase/FirestoreBoardRepository.kt  # 실시간 공유 구현 (대기)
└── ui/
    ├── AppNav.kt              # 온보딩 게이트 + 하단탭 + NavHost
    ├── AppViewModel.kt        # 전역 상태/동작 허브
    ├── theme/                 # Color/Theme/Type (Material 3)
    ├── onboarding/OnboardingScreen.kt
    ├── calendar/CalendarScreen.kt, AddEventScreen.kt
    └── lists/ListsScreen.kt, ListDetailScreen.kt
```

## 4. 빌드 / 설치
```bash
cd "D:/Personal/family-board-android"
./gradlew.bat assembleDebug
```
APK: `app/build/outputs/apk/debug/app-debug.apk`

설치(Git Bash 경로 함정 회피 — push 후 pm install):
```bash
export MSYS_NO_PATHCONV=1
export MSYS2_ARG_CONV_EXCL="*"
ADB="/d/Personal/Dev-Project/Android/SDK/platform-tools/adb.exe"
APK="D:/Personal/family-board-android/app/build/outputs/apk/debug/app-debug.apk"
"$ADB" push "$APK" /data/local/tmp/app.apk
"$ADB" shell pm install -r /data/local/tmp/app.apk
"$ADB" shell rm /data/local/tmp/app.apk
```

## 5. 미완료 / 활성화 필요 (중요)

### 5-1. Firebase 실시간 공유 — 연결 완료 (2026-08-02)
- Firebase 프로젝트 `junfamily-board`, 앱 닉네임 "가족 게시판". `app/google-services.json` 배치됨(gitignore 제외).
- **applicationId = `com.jun.family_board`** (Firebase 등록 패키지와 일치). 내부 코드 namespace = `com.familyboard.app` 유지 → 둘이 다름.
  - 실행 컴포넌트: `com.jun.family_board/com.familyboard.app.MainActivity`
- google-services 플러그인 활성화됨(루트/app build.gradle), 저장소 = `FirestoreBoardRepository`.
- Firestore 컬렉션: `events`, `items`. 현재 **테스트 모드(30일)** → 만료 전 보안 규칙 설정 필요.
  - 인증(Firebase Auth) 미사용(이름 선택 방식)이라, 엄격한 규칙은 후속 과제. 가족 전용 사설 배포이므로 단기적으로 테스트 모드/단순 규칙 허용.
- 오프라인 데모로 되돌리려면 `AppContainer` 의 boardRepository 를 `InMemoryBoardRepository()` 로 교체.

### 5-2. 공휴일 API 키
1. 공공데이터포털에서 "특일 정보"(한국천문연구원) 활용신청 → 서비스키 발급
2. `local.properties` 에 `holiday.api.key=발급받은키` 입력 (URL 인코딩된 키 사용 권장)
3. 재빌드하면 `BuildConfig.HOLIDAY_API_KEY` 로 주입되어 캘린더에 공휴일 표시. (키 없으면 조용히 빈 목록)

### 5-3. 향후 개선 후보
- 일정 시간 선택을 텍스트 입력 → TimePicker 다이얼로그로
- 일정 반복/알림, 리스트 항목 정렬·드래그
- 온보딩에서 SMS 인증으로 번호 실소유 확인(선택)

## 6. 배포
- 가족 사이드로드는 디버그 APK로 충분. GitHub Release asset 으로 `FamilyBoard-vX.Y.Z.apk` 업로드.
- 레포: https://github.com/pilos011/family-board-android (GitHub CLI 없음 → REST API).
- 버전관리: main=안정 / 신기능=feature 브랜치. 문서 UTF-8.
- ⚠️ `google-services.json` 과 `.claude/memory/`(토큰 포함)는 `.gitignore` 로 커밋 제외됨.
