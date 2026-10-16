# 4-11 검증: ASCII 아트 (code-verifier 보고, 메인 에이전트가 대신 저장)

판정: 결함 없음. 동작 실패 0건, 낮음·정보 수준 한계만. 11개 항목 모두 수행. 소스 수정 없음(변경 실험은 scratchpad\mut 복사본에서만). 스크립트는 scratchpad\x (R9, A9, D9, D9c, D9d, P9, CPU9, R9j, V9, H9).

## 빌드·코드 리뷰
- `mvn -B clean package` BUILD SUCCESS, 테스트 65개(실패·에러·스킵 0).
- GlyphOrdering: 잉크 수 오름차순 후 문자 코드 오름차순, 같은 잉크는 앞선 것 하나만 유지, 공백이 첫 번째. 실제 세트 Consolas 24개·Courier New 22개·Monospaced 20개 모두 strictly 오름차순·첫 글리프 공백(코드 32).
- FontGlyphSet: `FONT_CANDIDATES`를 리플렉션으로 바꿔 3경로 확인(Consolas 채택, 없는 글꼴이면 Dialog 대체를 걸러내고 Courier New, 둘 다 없으면 `Monospaced.plain` 폴백). `lum < 128` 이진화, 안티앨리어싱·텍스트 AA·분수 메트릭 OFF 힌트, 칸 크기 BufferedImage 마스크. `-Djava.awt.headless=true`에서도 생성·apply 정상.
- AsciiArtEffect: 밝기식 `(299R+587G+114B+500)/1000`, k=`((255-L)*N)/256`(L=255 → 0, L=0 → N-1), 칸 평균 `(sum+count/2)/count`(부분 칸 실제 픽셀 수), 글리프 int[] 사전 펼침 + arraycopy/Arrays.fill, 부분 칸은 min으로 잘라 이미지 밖에 쓰지 않음, directPixels 판정은 PixelDotEffect와 같음, 출력 새 TYPE_INT_RGB·배경 0xFFFFFF·글자 0x000000, 입력 불변, 생성자 IAE.
- import 규칙: 금지 대상(`javax.swing`, `java.awt.Component` 계열, `com.sun.jna`, ui/win32)은 package-info 주석 외 소스 본문에 없음, `java.awt` import는 Color·Font·FontMetrics·Graphics2D·RenderingHints뿐. `EffectPackageImportsTest`는 디렉터리 없음/소스 비어 있음이면 실패하고 순수 검출기 단위 테스트도 따로 있음 — 복사본에 위반을 주입해 확인: `java.awt.Toolkit`·`com.sun.jna.Native`·`java.awt.*`·`com.converter.ui.CapturePanel`은 검출, **`java.awt.Button`(금지 목록에 없는 Component 하위)·import 없는 완전수식 `java.awt.Toolkit.getDefaultToolkit()`·한 줄에 import 두 개는 못 잡음**(낮음, 현재 소스에는 해당 없음, import 줄 스캔의 한계이며 빈 검사는 아님).
- EffectRegistry 9번 "ASCII 아트", 1~8 불변. `defaults()` Javadoc 문장에 문법 오타(정보). 미변경 대상 파일(win32 3개, OverlayWindow, Main, MosaicEffect, PixelDotEffect, GrayscaleEffect, IdentityEffect, MinimumWindowSize) 수정 시각 15:08~15:55 < 4-10 변경 시각 16:12:03.

## 단위 테스트 독립 재검토
N=4 손계산: L 255→0, 192→63×4=252→0, 191→256→1, 128→508→1, 127→512→2, 64→764→2, 63→768→3, 0→1020→3 (테스트 하드코딩 값과 일치). 부분 칸 5x3: 12픽셀 칸 (1530+6)/12=128 → k=1, 3픽셀 칸 (510+1)/3=170 → k=1. 구현을 재호출하지 않는 손계산 하드코딩이라 순환 아님(분모를 16으로 잘못 쓰면 k=2가 되어 잡힘).

## 독립 레퍼런스 전수 비교 (`x\R9.java`)
검증자가 GlyphSet 인터페이스만으로 따로 구현(getRGB 픽셀 단위). 글리프 세트 7종(FakeGlyphSet 유사 4x4, 실제 FontGlyphSet 8x16(N=24), 4x4, 3x5, 8x8, 16x32, 1x1(N=1)) × 이미지 타입 9종(INT_RGB, INT_ARGB, ARGB_PRE, INT_BGR, 3BYTE_BGR, 4BYTE_ABGR, USHORT_565, BYTE_GRAY, BYTE_INDEXED) × 크기 12종(1x1~1738x1007, 칸 배수 아님 포함) + 서브이미지 9종·scanline stride 37·오프셋 raster·임의 알파: 총 2896건 bad=0, 입력 불변, 출력 TYPE_INT_RGB, 알파 누출 없음. 정수 밝기식 16,777,216 전 조합 대조(N=256 글리프에 인덱스를 인코딩해 k=255-L에서 L 복원) mismatch 0, GrayscaleEffect 출력과도 mismatch 0. k 매핑 L 0..255 × N 1..64 = 16,384 조합 전수 bad=0, 항상 0..N-1. 칸 1x1은 허용·정상이나 `FontGlyphSet(1,1)`은 모든 글리프 잉크가 같아 N=1(공백뿐)로 퇴화(정보).

## 통합 (`x\D9.java`, 좌표 패턴 창 + Main 경로)
효과 1~9 × (6개 크기 + 최대화) = 63케이스를 captureRect 기준 독립 기대 이미지와 비교 failing=0(2회 반복; 홀수·518x221·부분 칸 크기 442x319, 703x514 포함). 9번 속성: 두 색뿐, 모든 칸이 문자 세트 글리프(부분 칸은 잘린 마스크)와 일치, 칸 평균 밝기와 선택 글리프의 단조성 위반 0(7케이스), 이미지 경계 처리 정상. captureExcluded=true, affinity 0x11, alwaysOnTop=true, 초기 제목 모자이크. 효과 1~8 회귀 모두 통과(7번 비격자 = 모자이크(8)). 음성 대조 모두 올바르게 FAIL: 뒤집힌 매핑(정확 비교 bad=30432 + 단조성 위반 — 두 색·글리프 일치만으로는 못 잡고 정확 비교·단조성으로 잡힘), 다른 문자 세트 ` .:#@`(bad=23992), 8x8 칸(bad=28641), 도트화를 9번으로 주입, 2색 흑백을 9번으로 주입, 기존 1~8 쪽 5종.

## 키 입력 실측
숫자 1~9와 숫자패드 NP1~NP9가 제목(네이티브 GetWindowText 포함)과 이미지를 올바르게 바꿈(이미지 bad=0), 0·NP0·A 무시, 9의 제목 "Converter - ASCII 아트", 전환 후 HWND·스타일·bounds 불변·affinity 0x11. 별도 `java -jar` 프로세스에서 1~9와 NP5~NP9 제목 정확, 0·NP0 무시. 전환 지연: 키 누름부터 첫 매칭 프레임까지 중앙값 2→9 약 23~40ms, 2→8 약 10~27ms(8회 × 2세트, 범위 약 7~48ms, 캡처 주기 33ms와 Robot 지연 포함) — 글리프 캐시가 생성자에서 이미 만들어져 첫 사용 지연 없음.

## 동적 검증
캡처 가동 중 키 1~9 무작위 전환(9 포함) + 창 이동·리사이즈, 5ms 주기 프레임 검사, 시드 7~16의 10회, 회당 약 590·총 약 5,900프레임: propBad=0, sizeBad=0, exactMiss=0, 예외·멈춤 없음, 최대 프레임 간격 52~65ms, stderr 0바이트. 첫 실행 `D9`의 propBad=1(`cur=8 prev=9`, 442x319)은 하네스 버그(타이밍 측정 코드가 끝에서 키 2를 눌러 실제 효과 2인데 하네스 변수는 9)였고 `D9c`에서 바로잡자 PASS — 제품 결함 아님.

## 성능 (1738x1007, 워밍업 100회 후 300프레임, 중앙값 / p95 ms)
1 원본 13.6/16.4, 2 모자이크 6.4/8.2, 3 흑백 6.5/8.0, 4 흑백 2색 7.4/9.1, 5 도트 16색 3.7/4.8, 6 도트 게임보이 3.1/4.3, 7 도트 격자 3.2/4.5, 8 도트 16색+격자 4.0/5.2, **9 ASCII 3.7/4.9** (33ms 주기 안). 레지스트리 생성(글리프 캐시 포함) 약 93ms. 실제 앱(640x360) CPU 단일 코어 대비: 모자이크 약 7%, ASCII 약 5~7%, 도트 격자+16색 약 7.6%.

## 회귀 (jar 프로세스, `x\R9j.java`)
기본 효과 모자이크, affinity 0x11, topmost, 제목줄 드래그 +200,+100 정확, 최소 크기 518x221·캡처 320x180 유지, 이동·리사이즈 후 키 동작 정상. 닫기: ESC(클릭 후)·X 버튼·우클릭 '닫기' 종료 코드 0·stderr 0바이트, ESC(클릭 없이)는 종료되지 않음 — 3-3 (c) 이래 알려진 포커스 이월 이슈(4-10 회귀 아님, 변경 전 상태와의 직접 대조는 못 하고 이전 보고서 기록과 일치함만 확인).

## 육안 (PNG는 scratchpad\x: `asc{0,1,2}_{synth,text,coord}.png`, 원본 `src_synth.png`, `src_text.png`, `src_coord.png`)
합성 그림·코드 텍스트 화면 형태·좌표 패턴을 Consolas/Courier New/Monospaced(FONT_CANDIDATES 리플렉션으로 구성)로 변환해 확인. 방향성 정상: 왼쪽 위 밝은 곳은 공백과 `-` `'` `:`, 오른쪽 아래 어두운 곳은 `8%$#&W@`, 빨간 원은 `j`·`x` 계열 덩어리로 윤곽, 노란 원은 옅게, 초록 사각형은 `x`·`7` 계열. "ASCII ART" 글자는 읽을 수 없는 진한 노이즈, 텍스트 화면은 얇은 글자가 평균되어 거의 공백이 되고 파란 버튼 사각형만 `+xx17o777` 막대로 보임. 칸 정렬은 8x16 격자로 가지런. **Consolas가 가장 선명**, Courier New는 글리프가 작고 중앙에 몰림, Monospaced는 더 성김, 세 글꼴 모두 방향성 동일. DWM 합성 화면 직접 확인은 불가.

## 발견 사항
- 높음/중간: 없음.
- 낮음: `EffectPackageImportsTest`가 import 줄 문자열 스캔뿐이라 금지 목록에 없는 Component 하위(`java.awt.Button` 등)·완전수식 사용·한 줄 다중 import를 놓침. `AsciiArtEffect`는 GlyphSet "잉크 오름차순" 계약을 코드로 검증하지 않음(뒤집힌 세트를 넣어도 예외 없이 동작, FontGlyphSet이 보장).
- 정보: 글꼴 후보를 지정하는 공개 API 없음(리플렉션으로만 검증 가능), `FontGlyphSet(1,1)` N=1 퇴화, EffectRegistry Javadoc 오타, ESC(클릭 없이) 포커스 이월.
- 하지 못한 것: DWM 합성 화면 직접 육안, DPI 125/150%, 다중 모니터·음수 좌표, 장시간 메모리·핸들, 텍스트가 많은 실제 브라우저·IDE 화면(합성 텍스트 그림으로 대체), 실제 화면 콘텐츠 변화(동영상 등). java 프로세스 0개.
