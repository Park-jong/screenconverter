# 4-10 개발: ASCII 아트 (code-writer 보고, 메인 에이전트가 대신 저장)

`mvn -B package` BUILD SUCCESS, 테스트 65개 통과(기존 46 + 신규 19 안팎). 창은 띄우지 않았고 java 프로세스 잔존 없음. 리뷰 판정 없음.

## 신규·변경 (`effect/`)
- `GlyphSet.java`: 인터페이스(cellWidth, cellHeight, size, charAt, mask(boolean[] 행 우선)). 잉크 오름차순 정렬 계약을 문서화.
- `GlyphOrdering.java`(패키지 내부): 잉크 밀도 오름차순 정렬 + 동점 중복 제거(문자 코드 앞선 것 유지).
- `FontGlyphSet.java`: Consolas → Courier New → MONOSPACED. `getFamily()`가 요청 이름과 같을 때만 채택해 Dialog 대체를 걸러냄. `fontName()` 조회 가능. 글꼴 크기는 ascent+descent가 칸 높이 안에 드는 최대 크기. 흰 배경 위 검정 글자, `INK_THRESHOLD=128` 이진화, 안티앨리어싱·텍스트 AA·분수 메트릭 OFF를 RenderingHints로 명시.
- `AsciiArtEffect.java`: `cellAverages` 별도 메서드, 밝기식 private 복제("4-5 전수 검증된 식과 동일" 주석), `k=((255-L)*N)/256`(반개구간 규칙 Javadoc), 글리프를 생성자에서 `int[]`로 펼쳐 arraycopy/Arrays.fill로 합성(drawString 없음), directPixels 판정 복제, 부분 칸은 실제 픽셀 수로 평균·마스크를 잘라 그림, 생성자 IAE 검증.
- `package-info.java`: import 허용·금지 규칙 문장 추가.
- `EffectRegistry.java`: `defaults()` 맨 끝에 9번 "ASCII 아트" 등록.
- 미수정: win32, OverlayWindow, Main, MosaicEffect, PixelDotEffect, GrayscaleEffect, IdentityEffect, MinimumWindowSize.

## 테스트
`FakeGlyphSet`(4x4: 공백/점/가로줄/꽉 찬 칸), `AsciiArtEffectTest`(11: N=4에서 L=255/192/191/128/127/64/63/0의 k 손계산 하드코딩, 칸별 독립 선택, 부분 칸 실제 분모(5x3: 12px 칸 128, 3px 칸 170), 10x7에서 마스크 잘림·경계 준수, 두 색만 출력·TYPE_INT_RGB·입력 불변, ARGB/3BYTE_BGR/INT_BGR/BYTE_GRAY/서브이미지 폴백 동일 결과, 생성자 IAE), `GlyphOrderingTest`(3), `FontGlyphSetTest`(3, 글꼴 비의존 속성), `EffectPackageImportsTest`(2, 소스 import 스캔, 디렉터리 없으면 실패), `EffectRegistryTest` 갱신(9개, 미등록 10·0·-1, register 새 번호 10, 9번 이름·타입 검증).

## 성능 (1738x1007, 기본 8x16, FontGlyphSet)
글꼴 Consolas, 중복 제거 후 글리프 24개(후보 약 60자), 문자 순서(잉크 오름차순) ` -':,!"+(/C1F7oxj8%$#&W@`. 실제 칸 수 218x63 = 13,734. 생성자(글리프 캐시 생성) 약 87ms(첫 호출이라 JIT·글꼴 로딩 포함). 1프레임(워밍업 100회 후 300프레임): 랜덤 중앙값 3.62ms / p95 5.06ms, 사진형 중앙값 3.59ms / p95 4.57ms. 벤치/PNG 스크립트는 scratchpad의 `b\Bench.java`.

## 육안 (개발자)
scratchpad의 `ascii.png`(전체), `ascii_crop.png`(640x400), `src.png`(원본: 그라디언트 + 빨간 원 + 초록 사각형 + 선 + "ASCII ART" 글자 + 노란 원). 왼쪽 위 흰 영역은 공백, 오른쪽 아래 어두운 파랑은 8 % $ # & 같은 빽빽한 문자, 밝은 쪽에서 어두운 쪽으로 방향이 맞음. 빨간 원은 x·o·7 계열로 윤곽이 뚜렷, 초록 사각형은 7·F 계열, 노란 원은 :·, 계열로 옅게. "ASCII ART" 글자는 @ W & 짙은 덩어리로 보이나 읽기는 어렵고 거침. 이진 마스크라 8x16에서 다소 거칠고, 사진형에서는 x·7·o 같은 중간 밝기 구간이 넓음.

참고: 개발자 환경에서 Bash 도구가 일부 heredoc을 파싱하지 못해 소스·테스트는 Write 도구로 작성.
