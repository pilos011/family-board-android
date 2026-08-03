---
name: korean-radio-android-app
description: >
  한국 라디오 스트리밍 안드로이드 앱(특히 저시력/시각장애·고령 사용자용 초대형 버튼
  라디오)을 만들거나 유지보수할 때 사용하는 스킬. media3(ExoPlayer) 기반 HLS 스트리밍,
  MediaLibraryService + Android Auto 지원, TTS 음성 안내, Primary/백업 이중화와 재생 중
  자동 재연결을 다룬다. 다음 중 하나라도 해당하면 사용한다: 라디오/방송 스트리밍 앤드로이드
  앱 제작, MBC/SBS/KBS/CBS 등 방송사 스트림 URL 확보, .pls/.m3u8/HLS 재생, ExoPlayer
  "No suitable media source factory" 오류, HTTP(cleartext) 스트림 차단, Android Auto
  미디어 앱, Git Bash에서 adb 설치 실패, "라디오 앱", "연신내 라디오" 언급, 방송국 버튼 UI.
  대표 프로젝트: pilos011/Radio-Mom-in-law (D:\Personal\esp32s3-radio-android).
---

# 한국 라디오 스트리밍 안드로이드 앱

저시력/시각장애·고령 사용자를 위한 초대형 버튼 라디오 앱을 media3로 만드는 방법과,
반복해서 부딪혔던 함정들을 정리한다. 각 항목은 실제로 한 번씩 빌드·설치·테스트를
낭비하게 만든 실수들이다. **관련 섹션을 먼저 읽고 시작할 것.**

대표 구현: `pilos011/Radio-Mom-in-law` (로컬 `D:\Personal\esp32s3-radio-android`).
방송국 스트림 주소 전체는 [references/korean-radio-streams.md](references/korean-radio-streams.md) 참조.

---

## 1. 기술 스택 (검증된 조합)

| 항목 | 값 |
|------|-----|
| 빌드 | AGP 8.3.2, Kotlin 1.9.23, Gradle 8.6 |
| minSdk / targetSdk | 24 / 34 |
| 미디어 | androidx.media3 1.3.1 (`exoplayer`, `exoplayer-hls`, `session`, `ui`) |
| 언어 | Kotlin, Groovy DSL(build.gradle), Version Catalog(libs.versions.toml) |

`gradle.properties`에 반드시:
```
android.useAndroidX=true
android.enableJetifier=true
```
(없으면 "AndroidX dependencies but android.useAndroidX not enabled" 빌드 실패)

---

## 2. 반복된 함정 (Trial-and-error) — 먼저 읽어라

### 2-1. HLS 스트림이 "No suitable media source factory for content type: 2"
- 원인: `.m3u8`(HLS) 재생에는 HLS 확장 모듈이 필요.
- 해결: `implementation libs.media3.exoplayer.hls` 추가. (content type 2 = HLS)

### 2-2. HTTP(평문) 스트림이 재생 안 됨 (Android 9+)
- 방송사/중계 서버가 `http://`면 기본 차단됨.
- 해결(둘 다 적용 권장):
  - Manifest `application`에 `android:usesCleartextTraffic="true"`
  - `res/xml/network_security_config.xml`로 cleartext 허용 후 `android:networkSecurityConfig` 지정.

### 2-3. 버튼 눌러도 크래시 / 재생 안 됨 (Foreground Service)
- Android 10+는 `startForeground()`에 서비스 타입 필수.
  `startForeground(id, noti, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)`
- Manifest: `android:foregroundServiceType="mediaPlayback"` +
  `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK` 권한.

### 2-4. 코루틴에서 `runOnUiThread` 중첩 → 크래시
- `lifecycleScope.launch{}`는 이미 Main 디스패처. 내부에서 또 `runOnUiThread`로
  UI 만지면 IllegalState 크래시. 네트워크만 `withContext(Dispatchers.IO)`로 감싸고
  UI는 코루틴 본문(Main)에서 직접 갱신.

### 2-5. Git Bash(Windows)에서 `adb install` 실패
- 증상: `adb install`이 `/data/local/tmp`를 Windows 경로(`C:/Program Files/Git/...`)로
  잘못 변환하거나 스트리밍 중 device offline로 실패.
- 해결: 경로 변환을 끄고 push 후 pm install.
```bash
export MSYS_NO_PATHCONV=1
export MSYS2_ARG_CONV_EXCL="*"
ADB="/d/Personal/Dev-Project/Android/SDK/platform-tools/adb.exe"   # 프로젝트별 경로 확인
APK="<프로젝트>/app/build/outputs/apk/debug/app-debug.apk"
"$ADB" push "$APK" /data/local/tmp/radio.apk
"$ADB" shell pm install -r /data/local/tmp/radio.apk
"$ADB" shell rm /data/local/tmp/radio.apk
```
- unauthorized: 폰에서 USB 디버깅 허용 팝업 승인("항상 허용" 체크). 화면 꺼지면
  offline 됨 → 설치 전 폰 깨어있게. `adb reconnect offline` 후 재승인.

### 2-6. 방송사 스트림 토큰 만료
- MBC(`_lsu_sa_`)/SBS(JWT)/KBS(CloudFront 서명)는 **재생 시점마다 resolve** 해야 신선한
  토큰을 받는다. 앱이 토큰을 저장/갱신하지 않는다 — 매번 endpoint 호출로 새 URL 획득.
- CBS는 토큰 없는 고정 m3u8.

### 2-7. "resolve 성공 ≠ 재생 성공" — 폴백은 재생 실패에도 걸어라
- 중계 .pls는 파일 자체는 늘 열려서 resolve는 성공하지만, 그 안의 URL(만료 토큰 등)이
  죽어 실제 재생이 실패할 수 있다. 따라서 백업 폴백은 **resolve 실패(null)뿐 아니라
  `Player.Listener.onPlayerError`(재생 실패)** 에도 걸어야 실효성이 있다.

### 2-8. 앱 아이콘 색상 리소스 누락
- `mipmap-anydpi-v26/ic_launcher.xml`이 `@color/ic_launcher_background`를 참조하는데
  colors.xml에 없으면 AAPT 오류. 색상 정의 추가.

---

## 3. 저시력/시각장애 사용자 UI 원칙 (황반변성 말기 기준)

- 순수 검정 배경 `#000000` + 각 방송국 **고유 형광색** 버튼(검정 텍스트). 일반적 명암비
  이론 무시, "매우 밝은 형광색"만 인식 가능한 말기 기준으로.
- 버튼 초대형(높이 110dp), 굵은 24sp.
- **상단 전체(110dp)를 정지 버튼으로.** 재생 중 `#FF00FF`, 정지 중 `#FFFF00`.
- **하단 70dp는 빈 안전 여백** — 안드로이드 내비게이션 바 오조작 방지.
- **TTS 음성 안내 필수**(시각장애): 버튼 클릭 시 방송국명을 읽고, TTS가 끝난 뒤 재생
  시작(동시 재생 금지). `speakAndWait` = `suspendCancellableCoroutine` +
  `UtteranceProgressListener`. 다른 방송국 누르면 기존 즉시 정지 → TTS → 새 재생.

---

## 4. 아키텍처 (media3, 폰 + Android Auto 공용)

```
MainActivity  ── MediaController ──▶  RadioLibraryService (MediaLibraryService)
   UI/TTS                                 ExoPlayer + MediaLibrarySession
```
- `RadioLibraryService`(MediaLibraryService): 미디어 트리(onGetLibraryRoot/onGetChildren)로
  방송국 목록 제공 → Android Auto가 표준 템플릿으로 표시. `onAddMediaItems`에서
  mediaId로 방송국을 찾아 스트림 URL을 resolve.
- MainActivity는 커스텀 바인더가 아니라 `MediaController`로 서비스 제어(폰/Auto 통일).
- Android Auto 선언: manifest `meta-data`(com.google.android.gms.car.application) +
  `res/xml/automotive_app_desc.xml`(`<uses name="media"/>`) + 서비스 intent-filter
  (`androidx.media3.session.MediaLibraryService`, `android.media.browse.MediaBrowserService`),
  서비스 `android:exported="true"`.

### 재생 흐름 + 이중화
- 버튼 클릭 → 방송국명 TTS → **Primary resolve→재생**. 실패(resolve null 또는
  onPlayerError) 시 **"연결 실패. 백업 주소로 재시도합니다." 안내 후 백업 재생**.
- Primary/백업은 각각 `StreamSource(type, url)`. `SourceType`: `PLS`(.pls File1=),
  `DIRECT`(그대로 m3u8), `PLAIN`(응답 본문이 URL: MBC/SBS), `KBS_JSON`(channel_item[0].service_url).
- 현재 대표 프로젝트 설정(v1.3.0): **Primary=방송사 공식, 백업=serpent0 중계(.pls)**.

### 하루 종일 연속 재생 견고성 (서비스 측, 무음 자동 재연결)
- **재연결은 반드시 서비스(백그라운드)에** 둔다. Activity에 두면 화면 꺼지거나 UI 종료 시
  안 돈다. 첫 재생 성공(STATE_READY) 이후부터 서비스가 재연결 담당.
- `Player.Listener`에서 `onPlayerError`/`STATE_ENDED` → 재-resolve 후 재생.
  지수 백오프(1s→2→4→…→30s cap). `STATE_BUFFERING` 25초 지속 시 watchdog로 강제 재연결.
- `ExoPlayer.setWakeMode(C.WAKE_MODE_NETWORK)` + `WAKE_LOCK` 권한 → 화면 꺼짐 시 WiFi
  슬립으로 인한 끊김 방지.
- 사용자 정지는 커스텀 SessionCommand로 서비스에 전달 → 재연결 완전 중단.
- 초기 연결(사용자 앞·화면 on)은 Activity가 TTS 안내와 함께 처리, 첫 재생 후 무음
  재연결은 서비스가 처리 → 역할이 STATE_READY 시점에 교대(충돌 없음).

---

## 5. 새 방송국 스트림 주소 알아내기 (유사 앱 리버스 엔지니어링)

Flutter 라디오 앱(APK)에서 하드코딩된 스트림 URL 추출한 방법:
- Flutter 릴리스는 Dart가 `lib/<abi>/libapp.so`로 컴파일되지만 **URL 문자열 리터럴은 남는다.**
- split APK면 base APK엔 `lib/`가 없다 → arch split(예: `config.arm64_v8a.apk` /
  `config.armeabi_v7a.apk`)에 `libapp.so`가 있다.
```bash
unzip -o config.<abi>.apk -d out
strings -n 8 out/lib/<abi>/libapp.so | grep -iE "https?://" | sort -u
```
- 얻은 endpoint는 **curl로 실제 응답 형식·동작을 반드시 검증**(직접 m3u8인지, 본문이
  URL인지, JSON인지, 토큰 유무). User-Agent 모바일 헤더 넣을 것.
- 검증된 한국 방송국 주소 전체는 [references/korean-radio-streams.md](references/korean-radio-streams.md).

---

## 6. 빌드·설치·배포 워크플로

- 빌드: `cd <프로젝트> && ./gradlew.bat assembleDebug`
- 설치: 위 2-5의 push + pm install 방식.
- 버전관리(사용자 선호): 안정 버전은 main + Git 태그(vX.Y.Z), 신기능은 `feature/xxx`
  브랜치 → 완성·테스트 후 `--no-ff` merge. GitHub CLI 없음 → git + REST API 직접.
  릴리즈마다 Source ZIP 자동 첨부(별도 업로드 불필요). 문서 UTF-8.
- 문서 3종: README.md(한 줄 + HANDOVER 링크) / HANDOVER.md(통합 상세) / CHANGELOG.md
  (Keep a Changelog).
- GitHub Release 생성(REST):
```python
import urllib.request, json
body={'tag_name':'vX.Y.Z','target_commitish':'main','name':'...','body':'...','draft':False}
req=urllib.request.Request('https://api.github.com/repos/<owner>/<repo>/releases',
  data=json.dumps(body).encode('utf-8'),
  headers={'Authorization':f'token <PAT>','Content-Type':'application/json'})
rel=json.load(urllib.request.urlopen(req))  # rel['id'] = release_id
```

### APK를 릴리즈에서 다운로드 가능하게 첨부 (중요)
- Release의 자동 "Source code (zip)"에는 **빌드된 APK가 없다.** 폰에서 바로 설치하려면
  APK를 릴리즈 asset으로 직접 업로드해야 한다.
- 가족 배포/사이드로드는 **디버그 서명 APK**(`assembleDebug` 결과)로 충분하다(디버그
  키로 서명돼 설치됨). 정식 스토어 배포가 아니면 release 서명·키스토어 불필요.
- asset 업로드는 `uploads.github.com` 사용, Content-Type은 APK MIME:
```python
def upload_apk(release_id, path, name, TOKEN, REPO):
    data=open(path,'rb').read()
    url=f'https://uploads.github.com/repos/{REPO}/releases/{release_id}/assets?name={name}'
    req=urllib.request.Request(url,data=data,method='POST',
      headers={'Authorization':f'token {TOKEN}',
               'Content-Type':'application/vnd.android.package-archive'})
    urllib.request.urlopen(req)
# 파일명은 앱·버전 식별되게: Yeonsinnae-Radio-v1.5.0.apk
```
- 여러 앱(다른 applicationId)을 한 레포에서 배포할 때: 앱별로 태그를 분리한다
  (예: main 앱은 `vX.Y.Z`, 변형 앱은 `hongcheon-v1.0.0`). 변형 앱 릴리즈는
  `target_commitish`를 해당 feature 브랜치로 지정.
- 폰 설치 안내: 릴리즈 페이지 Assets에서 APK 탭 → 다운로드 → "출처를 알 수 없는 앱
  설치 허용" 후 설치. 서로 다른 applicationId면 한 폰에 공존 가능.

---

## 7. 표준 파일 구성

```
app/src/main/java/com/radio/app/
├── MainActivity.kt         # UI, TTS, MediaController 제어, 초기 연결+백업 폴백
├── RadioLibraryService.kt  # MediaLibraryService, ExoPlayer, 무음 자동 재연결
├── RadioStation.kt         # 방송국 목록 + StreamSource(primary/backup) + toMediaItem()
└── StreamResolver.kt       # SourceType별 resolve (PLS/DIRECT/PLAIN/KBS_JSON), suspend+blocking
app/src/main/res/
├── layout/activity_main.xml        # 상단 정지버튼 + 스크롤 방송국 목록 + 하단 안전여백
├── xml/network_security_config.xml # cleartext 허용
├── xml/automotive_app_desc.xml     # Android Auto <uses name="media"/>
└── values/{strings,colors,themes}.xml
```
