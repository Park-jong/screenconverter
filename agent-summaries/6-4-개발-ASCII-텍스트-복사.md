# 6-4 개발: ASCII 텍스트 복사 (code-writer 보고, 메인 에이전트가 대신 저장)

사용자 요청: "아스키아트 모드 같은 경우 복사가 가능했으면 좋겠어". `mvn -B package` BUILD SUCCESS, 테스트 124개 통과(기존 106 + 신규 18). 리뷰 판정 없음, java 프로세스 잔존 없음. `agent-summaries`·`ROADMAP.md`는 건드리지 않음(메인이 처리).

## 변경·추가 파일
- effect: `TextProducingEffect.java`(신규, `String lastText()`), `AsciiArtEffect.java`(`TextProducingEffect` 구현 추가).
- ui: 신규 `TextClipboard.java`, `SystemTextClipboard.java`, `AsciiCopyAction.java`; 수정 `MenuPanel.java`, `OverlayWindow.java`, `MinimumWindowSize.java`.
- 테스트: 신규 `AsciiCopyActionTest`(7), `AsciiArtTextTest`(5); 갱신 `MenuPanelTest`(+4), `MinimumWindowSizeTest`(+2).

## 구현 요약
- **텍스트 생성**: `apply()`가 칸별로 이미 고른 글리프 인덱스 k로 `glyphChars[k]`를 `char[cols*rows]`에 모으고 프레임 끝에서 불변 `Grid` 레코드를 volatile로 게시. 프레임당 추가 비용은 `char[]` 할당 1회 + 칸 수만큼의 char 쓰기, 밝기식·k 계산·출력 이미지는 불변. `lastText()`는 행을 `\n`으로 잇고(마지막 줄 뒤 개행 없음) 행 끝 공백·전부 공백인 행 보존, 프레임이 없거나 이미지 크기 0이면 빈 문자열. 줄 끝 공백 제거 옵션은 만들지 않음(루프 B로 미룸). effect 패키지에 금지 import 없고 `EffectPackageImportsTest` 통과.
- **클립보드**: `TextClipboard.setText(String)`이 성공 여부를 `boolean`으로 반환(요구의 `void`와 다른 점). `SystemTextClipboard`는 `StringSelection`으로 쓰고 `HeadlessException`·`IllegalStateException` 등 모든 `Exception`을 잡아 false.
- **복사 로직** `AsciiCopyAction(Supplier<Effect>, TextClipboard).copy()` → `Result(success, message)`: 성공 `복사됨 (열x행자)`, 비 텍스트 효과 `ASCII 아트(9번)에서 사용`, 빈 텍스트 `복사할 내용 없음`, 클립보드 실패/예외 `복사 실패 (클립보드 사용 불가)`.
- **UI**: 메뉴 하단에 "텍스트 복사 (Ctrl+C)" 버튼(직접 그린 어두운 평면 스타일, `setFocusable(false)`)과 상태 문구 한 줄. `MenuPanel.setSelected()`가 선택된 효과의 `TextProducingEffect` 여부로 버튼 활성 상태를 맞춰 숫자 키·목록 클릭·코드 호출 어느 경로든 즉시 동기화, 비활성 툴팁 "ASCII 아트(9번)에서 사용". 상태 문구는 `showStatus()`로 표시하고 `javax.swing.Timer`(2.5초, 비반복)가 지움. 비활성일 때의 안내는 툴팁뿐이고 상태줄에는 상시 표시 안 함. Ctrl+C는 `content`의 `WHEN_IN_FOCUSED_WINDOW` 바인딩(ESC·숫자 키와 같은 방식, 충돌 없음). 버튼과 Ctrl+C 모두 `OverlayWindow.copyText()` 호출 — 호출 시점의 `effect` 필드를 읽으므로 누른 순간의 가장 최근 프레임 텍스트가 복사됨.
- **메뉴 높이·최소 창 크기**: 메뉴 preferred 높이 = 헤더 18 + 목록 9행 162 + 여백 4 + 버튼 24 + 상태 18 = 226, 폭 180 그대로(목록 이름·버튼 문구 모두 들어가 폭 불변). `MinimumWindowSize.compute(frameInsets, menuWidth, captureInsets, targetCapture, menuHeight)`로 시그니처 확장, 높이는 `frameInsets + max(캡처 border 포함 180+2, menuHeight)`. 6-3 피드백 (a)항 처리. **새 최소 창 518x265**(이전 518x221), 콘텐츠 높이 226은 메뉴가 결정하고 캡처 영역은 320x180보다 세로로 약 44px 더 큼, 가로 518 불변.

## 테스트 내용
- `AsciiArtTextTest`: FakeGlyphSet 4x4로 10x7 이미지를 3열 2행으로 나눈 기대 문자열 `"# .\n-.#"`(부분 칸 포함) 하드코딩, 전부 흰색이면 `"   \n   "`(공백 행·행 끝 공백 보존), 23x18 랜덤 이미지에서 텍스트의 각 문자가 출력 이미지의 해당 칸 픽셀과 `FakeGlyphSet.MASKS` 마스크 기준으로 일치, 여러 프레임 후 최신 프레임 반영, 프레임 없음 → 빈 문자열.
- `AsciiCopyActionTest`: 성공, 218x63 대형 격자 문구, 모자이크·원본 거부, 빈/null 텍스트, 클립보드 false 반환·예외, 줄·열 계산.
- `MenuPanelTest`: 버튼 활성/비활성 동기화, 클릭 시 리스너 호출과 비포커스, 상태 문구, preferred 높이와 목록 9행 확보(JFrame 없이 헤드리스 통과). `MinimumWindowSizeTest`: 기존 3개를 `menuHeight` 인자에 맞게 갱신, 메뉴가 더 클 때·캡처가 더 클 때 추가.

## 실제 창 검증 (개발자 하네스 `C:\Users\park\AppData\Local\Temp\claude\v6\h\Harness.java`, `Main` 실행 + Robot 조작, 창 900x500, 캡처 영역 내부 704x464, JNA jar를 classpath에 넣어야 실행)
| 단계 | 결과 |
|---|---|
| 모자이크(2번)에서 Ctrl+C | 상태 "ASCII 아트(9번)에서 사용", 클립보드는 센티널 그대로, 버튼 비활성 |
| 9번 선택 후 Ctrl+C | 클립보드에 29줄·88열(2580자) 텍스트, 상태 "복사됨 (88x29자)", 이미지 격자 ceil(704/8)=88·ceil(464/16)=29와 일치 |
| 버튼 클릭 | 클립보드 29줄이 다시 복사됨(센티널 사전 설정 후 확인) |
| 버튼 클릭 후 숫자 2 | 제목이 모자이크로 바뀌고 버튼 비활성, 포커스가 막히지 않음 |
| ESC | 정상 종료 |
최소 창 518x265는 `getMinimumSize()`로 확인. 메뉴 스크린샷(`menu0.png` 모자이크 선택, `menu1.png` ASCII 선택)을 눈으로 확인 — 헤더·9행 목록·하단 버튼·상태 문구가 잘리지 않고, 비활성 버튼은 회색·활성 버튼은 밝은 회색 배경에 흰 글씨, 한글 정상, 폭 180 안에 들어감.

## 참고·미확인
`menu0.png`에는 앞 단계에서 남은 상태 문구가 보임. 비활성 툴팁·2.5초 자동 지움은 코드와 단위 테스트로만 확인하고 화면에서 직접 보지 않음. 최대화 크기(약 14k자)는 하네스에서 돌려 보지 않고 단위 테스트에서 218x63 문구와 길이만 확인.
