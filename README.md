# 가족보드 (Family Board)

가족 4명(선일·은선·준영·준호)이 일정·리스트·용돈·추억을 함께 나누는 안드로이드 앱.
Jetpack Compose + Material 3, Firebase(Firestore 실시간 공유 + FCM 알림), 자체호스팅 Hermes 릴레이 서버 기반.

- 최초 실행 시 본인 선택으로 사용자 식별 · 현재 버전 **v0.9.95 (versionCode 104)**
- 자세한 개발/빌드/배포는 [HANDOVER.md](HANDOVER.md), 전체 이력은 [CHANGELOG.md](CHANGELOG.md) 참고.

## 주요 기능
- **가족 달력**: 월간 보기, 한국 공휴일(대체공휴일·명절), 음력+24절기, 구성원 색상 일정, 연속 다일 일정 막대. 반복 일정(매주·**격주**·매월·매년+음력), 미리 알림(AlarmManager), 드래그로 기간 선택.
- **리스트**: 장보기 / 할 일 / 가족 공지 + 사용자 커스텀 리스트. 담당자 태깅(복수), 작성자 표시.
- **맛집 · 가볼 곳**: 네이버 플레이스 공유 링크 파싱(상호·종목·영업시간·주소·좌표), 은선 별점, 거리순 정렬, 댓글.
- **재미진 곳 / 내 재미진 곳**: 유튜브·웹·이미지·영상 공유 게시판. 페이지 방식(이전/다음 + 이어보기), 유형/안 본 필터, 500px 썸네일로 빠른 로딩. "내 재미진 곳"은 본인만 조회.
- **용돈 정산**: 준영/준호 항목 정산·조르기(엄마에게 합계 알림).
- **인생 버킷 리스트**: 부부 공용, 진행 이력·사진.
- **D-Day**: 사용자 D-Day + 가족 생일(매년 자동, 만나이).
- **빠른 연락 요청**: 전체화면 긴급 알림(잠금화면·화면 켜짐·진동) + 위치 공유.
- **알림**: FCM data-only를 Hermes 서버가 릴레이 → 앱이 직접 알림 생성(여러 줄 펼침). 앱 내 업데이트(종 아이콘).
- **삭제 안전장치**: 모든 리스트·보드 항목은 삭제 전 확인 다이얼로그.

## 최근 변경 (v0.9.84 ~ v0.9.95)
- **격주(biweekly) 반복 일정** 추가.
- 리스트·맛집·가볼곳·용돈·D-Day·버킷·공지·재미진 곳 **전 보드 삭제 확인 다이얼로그**.
- 재미진 곳 **페이지네이션 → 페이지 번호 방식 + 이어보기**(정렬 방향별 마지막 페이지 기억), 리스트 카드에 **전체 개수** 표시(집계 count).
- 재미진 곳/맛집 **500px 서버 썸네일**(원본은 보존, 그리드만 경량화) → 빠른 로딩 + 로컬 캐시 유지.

## 기술 스택
- UI: Jetpack Compose + Material 3, 단일 Activity + Compose Navigation
- 상태: `AppViewModel`(activity 스코프) + StateFlow
- 백엔드: Firestore(익명 인증, 실시간) + FCM(data-only 푸시)
- 서버: 자체호스팅 Hermes(Node/Express + PostgreSQL, Docker) — 알림 릴레이 · 사진/APK 호스트 · 네이버/썸네일 처리
- 영상: media3(ExoPlayer), 이미지: Coil, 날짜: java.time + core library desugaring

## 빠른 시작
```bash
gradlew.bat assembleDebug
```
APK: `app/build/outputs/apk/debug/app-debug.apk`
