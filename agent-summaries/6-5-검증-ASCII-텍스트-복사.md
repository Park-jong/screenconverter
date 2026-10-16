# 6-5 검증: ASCII 텍스트 복사 (code-verifier 보고, 메인 에이전트가 대신 저장)

판정: 확인된 결함 없음. 필수 항목 대부분 통과. 확인하지 못한 항목: (g) 메모장 붙여넣기, 줄 끝 공백 실물 확인, 사전 대비 CPU 비교. 소스 수정 없음, 시스템 클립보드는 시작 전 원문(2610자)으로 복원 확인, java 프로세스 0개. 임시 파일은 `C:\Users\park\AppData\Local\Temp\claude\v6\v65`(Inv.java, Win.java, Perf.java, Bench.java, win2.out, snap_*.png).

## 1. 빌드·파일 변경
`mvn -B clean package` 성공, 테스트 124개(실패·오류 0). 18:07 이후 수정 파일은 effect `AsciiArtEffect`·`TextProducingEffect`, ui `TextClipboard`·`SystemTextClipboard`·`AsciiCopyAction`·`MinimumWindowSize`·`MenuPanel`·`OverlayWindow`뿐. `win32/*`, `CapturePanel`, `FramePacer` 등 capture/*, `Main`, 그 외 effect/*는 불변.

## 2. 코드 리뷰
- 글리프 매핑: `k = glyphIndex(brightness(avg))`를 한 번 계산해 `glyphChars[k]` 기록과 `glyphPixels[k]` 합성에 같이 사용, 격자 `ceil(w/cellW) × ceil(h/cellH)`(부분 칸 포함). `Grid` record를 `volatile lastGrid`에 한 번에 게시, `lastText()`는 로컬 복사 후 읽어 스레드 안전. 프레임당 추가 할당은 `char[cols*rows]` 하나(최대화 시 약 27KB).
- `AsciiCopyAction` 3분기(비텍스트 효과 "ASCII 아트(9번)에서 사용", 텍스트 없음 "복사할 내용 없음", 클립보드 false/RuntimeException "복사 실패 (클립보드 사용 불가)"). `SystemTextClipboard`는 `Exception`을 모두 잡아 false 반환. Ctrl+C는 `WHEN_IN_FOCUSED_WINDOW`(ESC·숫자 키와 같은 방식), 버튼·목록 `setFocusable(false)`, 포커스 없을 때 키가 안 먹는 것은 설계상이며 별도 시험 안 함. 상태 문구 Timer는 Swing 비반복 타이머, `closeApp`이 `System.exit(0)` 호출 → 종료를 막지 않음(실측 확인). `MinimumWindowSize.compute` 호출부는 `OverlayWindow.applyMinimumSize` 한 곳.
- **경미(Low)**: `lastGrid`는 `apply` 안에서 `publish` 이전에 갱신되어 복사 텍스트가 화면에 보이는 이미지보다 최대 1프레임 앞설 수 있음. 9번이 아닌 효과에서 9번으로 돌아온 직후 첫 새 프레임 전에 복사하면 이전 세션의 오래된 텍스트(창 크기가 다를 수도 있음)가 복사될 수 있음 — **코드 리뷰로만 확인, 실행으로 재현하지 않음**.

## 3. ASCII 이미지 불변성(회귀): 통과
`v65\Inv.java`가 독립 구현(밝기식, `k=((255-L)*N)/256`, 칸 평균 `(sum+count/2)/count`, 부분 칸, `FontGlyphSet` 마스크를 `GlyphSet`으로 읽어 합성)과 대조 — 크기 12종(1x1, 7x15, 8x16, 9x17, 518x265, 640x360, 3x200, 200x3, 1001x517, 최대화급 1744x1008 등) × 패턴 4종 × 이미지 타입 3종(`INT_RGB`, `INT_ARGB`, `3BYTE_BGR`) = 144건 모든 픽셀 비트 단위 일치. `lastText()`의 각 문자가 독립 구현이 고른 글리프 문자와 전부 일치, 줄 수·열 수 맞음, 비ASCII 문자 0개, 출력 타입 `INT_RGB`, 어긋남 0건(N=24, 셀 8x16).

## 4. 실제 창 동작 (Main 경로, Robot)
- (a) 시작은 모자이크·버튼 비활성. 숫자 키 1~9와 목록 행 클릭 1~9 모두 9번에서만 활성이고 즉시 반영, 제목도 같이 바뀜, 행 클릭 뒤에도 키 입력 계속 동작.
- (b) 900x500에서 Ctrl+C·버튼 클릭 모두 시스템 클립보드에 텍스트가 들어감: 상태 문구 "복사됨 (88x29자)", 격자 88x29 = `ceil(702/8) × ceil(459/16)`, 길이 2580 = `88*29 + 28`, 마지막 줄 뒤 개행 없음, ASCII만. `CapturePanel` 이미지와 칸별 비교는 2552칸 전체에서 불일치 0(두 경로 동일). 줄 끝 공백 보존은 이 실행에서 공백으로 끝나는 줄이 없어 실물 확인은 못 함(코드상 `append`로 그대로 보존). 버튼 클릭 뒤에도 Ctrl+C 동작(포커스 유지).
- (c) 3번(비 ASCII)에서 Ctrl+C: 클립보드 그대로(SENT1)·"ASCII 아트(9번)에서 사용" 문구. 비활성 버튼 실제 클릭도 클립보드 불변(문구는 이전 문구가 남은 것이라 클릭이 문구를 새로 만든 것인지는 구별 못 함).
- (d) `MovingPatternWindow` 위 연속 20회 복사: 불완전 격자 0건, 서로 다른 복사본 20/20, 모든 줄 길이 동일 88x29, 예외·멈춤 없음, 반복 1회(복사 후 대기 300ms 포함) 최대 314ms.
- (e) 레이아웃: 요청 300x200 → 실제 518x265(최소), 캡처 320x224, 메뉴 180x226, 격자 40x14 / 640x360 → 캡처 442x319, 메뉴 180x321, 격자 56x20 / 900x500 → 702x459, 메뉴 180x461, 격자 88x29 / 최대화 1936x1048 → 캡처 1738x1007, 메뉴 180x1009, 격자 218x63. 최소 창에서 메뉴 높이 226 = `menuPref` 226, 목록 y=18..180, 버튼 y=184..208, 상태 y=208..226으로 잘림 없음, 상태 문구 폭 94px(168px 안). 스크린샷 `snap_min_status.png`에서 메뉴 9행·버튼·"복사됨 (40x14자)" 모두 보임(640x360·900x500·최대화 스크린샷도 `snap_*.png`). 최대화 복사: 218x63, 길이 13796(이론값 일치), 키 입력~클립보드 반영 약 9ms, EDT의 `copyText` 호출 2.1ms.
- (f) 다른 프로세스(PowerShell `OpenClipboard`)가 클립보드를 점유한 동안: `copyText` 1.0ms에 반환, 문구 "복사 실패 (클립보드 사용 불가)", EDT 응답 0.9ms로 멈춤 없음, 점유 해제 후 정상 복사(첫 시도는 PowerShell 실행 정책 오류로 점유가 안 돼 `-ExecutionPolicy Bypass`로 재실행).
- (g) 메모장 붙여넣기: 생략(클립보드 내용 검증으로 갈음).

## 5. 성능·회귀
- 최대화 크기(1738x1007) `apply` 중앙값 3.66ms·p95 5.3ms(이전 약 3.7ms와 같음), `lastText()` 0.05ms, 실제 앱(metrics) 안에서 effect 평균 4.0ms·p95 5.0ms. 계측 켠 간격 fps 30.0~30.2, p95 36.1~36.6ms, max 37.3ms, skipped 0·replaced 0·displayed = attempts. CPU: 거의 전체화면 1920x1040·9번·metrics 켠 상태 15초간 한 코어의 약 46%(비교할 이전 수치가 없어 절대값만, grab 평균 약 19ms가 대부분).
- `GetWindowDisplayAffinity` 0x11, 항상 위 true. 닫기 4가지 모두 종료 코드 0·stderr 0바이트(ESC 약 0.4초, X 약 0.5초, 우클릭 닫기 약 1.0초, 9번에서 Ctrl+C로 상태 문구 Timer가 남은 직후 ESC 약 0.4초).
