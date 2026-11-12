# 기준 이미지 변경 기록

규칙: `docs/architecture.md` 4절 15항. 기준 이미지를 바꾸는 커밋에는 바뀐 이유를 한 줄씩 적는다.

| 날짜 | 스냅숏 | 이유 |
|---|---|---|
| 2026-11-12 | `feature/trip` `d1-speech-unavailable-*`, `d1-navi-unavailable-*` (1280·1600, 라이트·다크) | D1에 차량 문제 배너와 "다시 시도"가 생겼다(음성 엔진 없음, 내비 사용 불가). 문제가 있으면 장식용 인사말 줄 대신 그 배너를 그린다. 기존 `d1-waiting*` 기준 이미지는 바뀌지 않았다. |
| 2026-11-12 | 새로 만든 `feature/trip` `d3-end-dialog-*`, `d3-in-trip-*`, `d6-requests-connected-*`, `d6-requests-card-sent-*`, `d6-requests-arrived-*` (1280·1600, 라이트·다크) | 리뷰 B3·M2·M3. 기사님이 D3에서도 운행을 끝낼 수 있어 확인창을 추가했고(`d3-end-dialog`), IN_TRIP의 D3는 가장 넓은 레일(내릴 곳·다시 듣기·경로 다시 시작·도착)이 1280dp에 들어가는지 본다(`d3-in-trip`). 질문이 있으면 CONNECTED·CARD_SENT·ARRIVED에서도 D6가 열린다. |
| 2026-11-12 | `feature/trip` `d3-*`(card, card-waiting, card-four, card-long, route-started, route-failed, route-stale), `d6-requests*`(기본, many, pin, replying, notice-only) (1280·1600, 라이트·다크) | B3·M3. D3에 "운행 끝내기"가 생겼다(내용 열, 레일 폭은 그대로). 릴레이가 IN_TRIP에서 `card.recheck`를 받지 않으므로 IN_TRIP의 D3에서 "다시 확인 요청"을 뺐다. D6 레일에 "운행 끝내기"가 생기고 "도착했어요"는 IN_TRIP에서만 보인다. 나머지 화면(D1·D2·D4·D5·D7)의 기준 이미지는 바뀌지 않았다. |
| 2026-11-12 | 새로 만든 `feature/trip` `d3-route-requested-*`, `d3-route-confirm-*`, `d3-route-not-ready-*`, `d3-route-confirm-timeout-*` (1280·1600, 라이트·다크) | 경로 시작이 명시적 상태가 됐다(에뮬레이터 실측: 내비가 미리보기를 띄우고 사람이 "안내 시작"을 눌러야 함). 확인 대기 화면은 "내비에서 안내 시작을 눌러 주세요"와 취소, 내비 미준비는 "내비 열기"+"다시 시도", 3분 초과는 별도 문구를 보인다. 경로 버튼은 대기 중 스피너다. |
| 2026-11-12 | `feature/trip` 시계가 있는 모든 스냅숏 | 시계 글자를 시스템 시간대로 쓰도록 바꿨다. 스냅숏은 `LocalClockZone`으로 Asia/Seoul을 고정하므로 시계 글자는 그대로다. `d3-route-failed-*`는 `NAVI_ERROR` 상태로 만들어져 배너 문구는 같고 "다시 시도" 배치만 `d3-route-failed`의 이전 이미지와 같은지 확인했다. |
| 2026-11-12 | 새로 만든 `core/designsystem` `banner-long-title-*` (1280·1600, 라이트·다크), 바뀐 `parts_banners-*` | `HeyBanner`가 `actions: List<HeyBannerAction>`를 직접 그린다(주 행동 Large 96dp, 나머지 Regular 76dp, 한 줄). 자유 슬롯이 없어 작은 칩 버튼이 불가능하다. 긴 제목 "내비가 아직 준비되지 않았어요"가 "…않았어 / 요"로 끊겨서 새 스냅숏으로 고정했다. |
| 2026-11-12 | 한국어 줄이 긴 모든 스냅숏(`core/designsystem` parts·screens, `feature/trip` d1~d7 일부) | `InkText`가 한글 음절 사이에 WORD JOINER(U+2060)를 넣어 한국어가 단어(공백) 경계에서만 줄바꿈한다. 글자 모양은 같고 줄 나뉨만 바뀐다. |
| 2026-11-12 | `feature/trip` `d3-route-*`(requested, confirm, not-ready, confirm-timeout, failed), `d1-navi-unavailable-*`, `d1-speech-unavailable-*` | D3 상태 알약이 경로 상태에서 나온다(내비 연결 중 / 내비 확인 대기 / 경로 시작 실패). 배너 행동 버튼은 배너 안으로 들어갔다. D3 오른쪽 열은 스크롤 열이다. |
