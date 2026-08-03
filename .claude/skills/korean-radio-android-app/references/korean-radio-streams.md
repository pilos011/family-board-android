# 한국 라디오 방송국 스트림 주소 (검증 완료)

2026-08-02 기준 실제 curl 테스트로 동작 확인. 유사 앱(com.freeradio.streaming, Flutter)의
`libapp.so`에서 추출 + 방송사 API 직접 검증.

모두 HLS(`#EXTM3U`) → ExoPlayer + `media3-exoplayer-hls`로 재생. 검증 시 모바일 User-Agent 권장:
`Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120 Mobile Safari/537.36`

## 소스 방식(SourceType)
- **DIRECT**: URL 자체가 최종 m3u8 (토큰 없음)
- **PLAIN**: GET 하면 응답 본문이 곧 m3u8 URL (만료 토큰 포함) — 재생 시점마다 호출
- **KBS_JSON**: GET 하면 JSON, `channel_item[0].service_url`이 m3u8 (CloudFront 서명)
- **PLS**: .pls 플레이리스트의 `File1=` 값 (serpent0 중계 서버)

---

## 방송사 공식 소스 (Primary로 사용)

| 방송국 | id | 방식 | URL |
|--------|-----|------|-----|
| MBC 표준 FM | mbcsfm | PLAIN | `https://sminiplay.imbc.com/aacplay.ashx?agent=webapp&channel=sfm` |
| MBC FM4U | mbcfm | PLAIN | `https://sminiplay.imbc.com/aacplay.ashx?agent=webapp&channel=mfm` |
| CBS Music FM (93.9) | cbsfm | DIRECT | `https://m-aac.cbs.co.kr/mweb_cbs939/_definst_/cbs939.stream/playlist.m3u8` |
| CBS 표준 FM (98.1) | cbssfm | DIRECT | `https://m-aac.cbs.co.kr/mweb_cbs981/_definst_/cbs981.stream/playlist.m3u8` |
| SBS 파워 FM | sbsfm | PLAIN | `https://apis.sbs.co.kr/play-api/1.0/livestream/powerpc/powerfm?protocol=hls&ssl=Y` |
| SBS 러브 FM | sbs2fm | PLAIN | `https://apis.sbs.co.kr/play-api/1.0/livestream/lovepc/lovefm?protocol=hls&ssl=Y` |
| KBS 제1라디오 | kbs1radio | KBS_JSON | `https://cfpwwwapi.kbs.co.kr/api/v1/landing/live/channel_code/21` |
| KBS 해피FM (2라디오) | kbs2radio | KBS_JSON | `https://cfpwwwapi.kbs.co.kr/api/v1/landing/live/channel_code/22` |
| KBS 클래식FM (1FM) | kbsfm | KBS_JSON | `https://cfpwwwapi.kbs.co.kr/api/v1/landing/live/channel_code/24` |
| KBS 쿨FM (2FM) | kbs2fm | KBS_JSON | `https://cfpwwwapi.kbs.co.kr/api/v1/landing/live/channel_code/25` |

### KBS channel_code 매핑
- 21 = 1radio(제1라디오), 22 = 2radio(해피FM), 24 = 1fm(클래식FM), 25 = 2fm(쿨FM)
- (26 = 한민족방송, 23 = 3라디오/사랑의소리 — 참고)

### 주의
- CBS는 `chunklist.m3u8`이 아니라 `playlist.m3u8` 경로여야 200 (chunklist는 404).
- MBC/SBS 응답 본문은 순수 URL 한 줄. PLAIN 파싱은 "http로 시작하는 첫 줄" 취함.
- cbscmc(`mweb_cbscmc`)는 CBS JOY4U로 추정 — 위 10개엔 미포함.

---

## serpent0 중계 서버 (백업으로 사용, .pls)

패턴: `http://serpent0.duckdns.org:8088/<id>.pls` (HTTP=cleartext, .pls의 File1= 파싱)
- 위 표의 id 그대로: mbcsfm, mbcfm, cbsfm, cbssfm, sbsfm, sbs2fm, kbs1radio, kbs2fm, kbsfm, kbs2radio
- 개인 중계 서버라 가용성 변동 가능 → 백업/보조로만.

---

## 추가 채널 (v1.4.0~, 백업 없음)

방송사 공식 소스만 있고 serpent0 백업이 없는 채널. 모두 검증 완료(HTTP 200 / #EXTM3U).

| 방송국 | id | 방식 | URL |
|--------|-----|------|-----|
| TBS FM | tbsfm | DIRECT | `https://cdnfm.tbs.seoul.kr/tbs/_definst_/tbs_fm_web_360.smil/playlist.m3u8` |
| YTN 라디오 | ytnradio | DIRECT | `https://radiolive.ytn.co.kr/radio/_definst_/20211118_fmlive/playlist.m3u8` |
| OBS 라디오 | obsradio | DIRECT | `https://vod3.obs.co.kr:444/live/obsstream1/radio.stream/playlist.m3u8` |
| CBS JOY4U | cbsjoy | DIRECT | `https://m-aac.cbs.co.kr/mweb_cbscmc/_definst_/cbscmc.stream/playlist.m3u8` |
| KBS 3라디오 | kbs3radio | KBS_JSON | `https://cfpwwwapi.kbs.co.kr/api/v1/landing/live/channel_code/23` |

### 주의
- **TBS FM은 영상(360p) 스트림**(`tbs_fm_web_360.smil`, RESOLUTION 640x360 포함). ExoPlayer가
  오디오 트랙만 재생하므로 소리는 정상.
- CBS JOY4U = cbscmc(=CMC). CBS 계열은 `playlist.m3u8` 경로 사용.
- KBS 3라디오(사랑의소리) = channel_code 23, channel_id 3radio.
- 백업 없음 → 이 채널들은 `RadioStation.backup = null`. 재생 실패 시 바로 "연결 실패"
  (불필요한 "백업 주소로 재시도" 멘트 생략).

---

## 참고: 유사 앱에서 발견됐으나 미사용
- CORS 프록시(웹용): `https://proxy-mhfmes4lrq-uc.a.run.app/proxy?url=`
- freeradio 웹 프론트: `https://freeradio-stream.web.app/`
