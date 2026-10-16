# 7-2 검증: 배포 파일과 README (code-verifier 보고, 메인 에이전트가 대신 저장)

판정: 심각도 높음·중간 FAIL 없음, 낮음 항목만. 제품 소스·README·bat 수정 없음. 시험 파일은 `C:\Users\park\AppData\Local\Temp\claude\v7\v72`. 환경: Windows 11, JDK 17.0.20.

## A. dist 무결성: PASS
`dist\converter.jar`와 `target\converter.jar`의 SHA256 동일(d7723001…97f7), `dist\README.md`와 루트 `README.md` 동일(5229f9f3…), `tools\dist-files\*.bat` 5개는 `dist\`와 바이트 단위로 동일. bat 5개 모두 비ASCII 바이트·BOM 없음, 모든 줄 CRLF, 첫 줄 `@echo off`, 경로는 `"%~dp0..."` 따옴표. `_checkjava.bat`는 `setlocal`이 없어 `JV`·`JMAJOR`·`JNUM` 변수가 호출한 쪽 환경에 남음(무해).

## B. 실행 시험: PASS (창 제목 `Converter - 모자이크`, `GetWindowDisplayAffinity` 0x11)
1. `cmd /c dist\run.bat`: javaw가 뜨고 제목·0x11 일치, ESC로 종료. 종료 코드 0은 `System.exit(0)` 코드와 `run-console.bat`이 ESC 후 `Converter exited with code 0.`을 출력한 것으로 확인(javaw는 `start`로 띄워 코드를 직접 읽지 못함).
2. 작업 디렉터리 `C:\Windows`에서 전체 경로로 실행: PASS.
3. `...\v72\테스트 폴더\dist` 복사본에서 `run.bat`·`run-console.bat` 모두 jar를 올바르게 찾음. `a&b (x) 100` 폴더도 PASS. **폴더 이름에 `%`가 있는 경우는 시험하지 못함.**
4. `run.bat -Dconverter.fps=45 -Dconverter.metrics=true`, `run-console.bat -Dconverter.fps=45`: javaw 명령줄에 `-D` 옵션이 그대로 들어가고 `[metrics]` fps=45.0~45.2. 쉼표가 든 `-Dconverter.bounds=100,100,800,600`도 창 rect 100,100,800x600으로 적용.
5. `run-console.bat`: `[metrics]` 줄이 5초 간격으로 출력, `< nul`이면 pause가 즉시 반환되고 `Converter exited with code 0.` 출력, cmd 종료 코드 0.
6. `run-60fps.bat`·`run-max.bat`: 명령줄에 각각 `-Dconverter.fps=60`, `-Dconverter.fps=max`. 같은 속성으로 직접 실행하면 max는 fps≈143(grab 한계), `fps=0`도 143, `500`은 143(240으로 맞춘 뒤 grab 한계), `abc`는 30.2. bat이 띄운 javaw의 fps는 콘솔이 없어 직접 측정하지 못함.
7. `"-Dconverter.saveDir=C:\path with space" -Dconverter.fps=20`이 깨지지 않고 전달(`run-console` fps 20.1). `converter.jar`가 없을 때는 안내 후 pause.
- 시험 중 ESC가 안 먹은 경우가 많았으나 원인은 하네스(`SetForegroundWindow`만으로는 Swing 키 포커스가 안 잡힘, 창을 한 번 클릭한 뒤에는 6/6 종료) — README 문제 해결 표의 "창을 한 번 클릭" 안내와 일치, 제품 결함 아님. 더블클릭으로 처음 떴을 때 키보드 포커스를 바로 받는지는 확인하지 못함.

## C. `_checkjava.bat` 오류 경로: PASS
PATH에서 java를 제거하면 `Java was not found in PATH.`와 영어 안내 후 `pause`, errorlevel 1(`run.bat`·`run-console.bat` 모두). 가짜 java 스텁으로 버전 형식별 판정: `11.0.2`·`1.8.0_301`·`9.0.4`·`16.0.2`는 안내·errorlevel 1, `openjdk version "17.0.20" 2026-07-21 LTS`(이 PC의 실제 형식)·`java version "17.0.1"`·`"17"`·`"21"`·`"21-ea"`·`"25.0.1"`은 통과. `Picked up JAVA_TOOL_OPTIONS` 줄이 먼저 나오고 그 뒤에 `11.0.2`가 와도 11로 판정. javaw만 있고 java가 없으면 `where java`가 실패해 `Java was not found in PATH.`(JDK·JRE에는 항상 java.exe가 있어 문제 없음). pause 문구는 OS 언어를 따름(README의 "영어 안내"는 안내문 본문에 대한 말).

## D. jar: PASS
`com/sun/jna/win32-x86-64/jnidispatch.dll` 있음, `Main-Class: com.converter.Main`, `Build-Jdk: 17.0.20`, `META-INF`에 `.SF`·`.RSA`·`.DSA` 없음. JRE에서 `--add-modules` 없이 실행되는지는 확인하지 못함(이 PC에는 JDK만 있음, `java.desktop`은 기본 모듈이며 일반 JRE 17 배포본에는 대체로 있으나 jlink로 줄인 런타임에는 없을 수 있음 — 코드·문서로만 판단).

## E. README 사실 대조: PASS (하네스 `h\com\converter\ui\Harness.java`로 실제 앱을 띄우고 Robot으로 키 입력)
- 효과 1~9 이름·옵션 범위·기본값이 코드와 같음(모자이크 2~64 단위 2 기본 10, 흑백 2색 0~255 단위 8 기본 128, 도트 5·6번 2~64 기본 8, 7·8번 4~64 기본 8, ASCII 칸 8x16/6x12/4x8 기본 8x16 + 줄 끝 공백 제거 기본 꺼짐).
- 키 `1`~`9`·숫자패드 `1`~`9`: 제목·옵션 라벨 변경. `+`/`-` 계열: `=`, `Shift+=`, 숫자패드 `+`, `-`, 숫자패드 `-`가 모자이크 10→12→14→16→14→12, 4번 128→136, 9번 `+`로 8x16→6x12, 1번은 반응 없음. `Space`: 제목에 `[일시정지]`, 다시 누르면 해제되고 상태 줄에 "재개". `Ctrl+C`: 9번에서 35줄(2694자) 복사·"복사됨 (76x35자)", 2번에서 "ASCII 아트(9번)에서 사용". `ESC` 종료 코드 0. 이미지 저장·항상 위 버튼에는 단축키 없음(InputMap은 숫자, 숫자패드, Ctrl+C, Space, `+`/`-` 계열, ESC뿐). 항상 위 기본 켜짐(`isAlwaysOnTop()` true, 버튼 토글은 6단계 검증을 신뢰).
- `converter.fps` 기본 30·1~240 보정·`max`/`0` 무제한·숫자가 아니면 30(B-6 실측과 같음), `converter.metrics`/`CONVERTER_METRICS=true`(환경변수만으로도 5초 간격 `[metrics]` 출력, 속성을 `false`로 주면 환경변수보다 우선하는데 README에 없었음 — 사소), `converter.bounds`(800x600 적용, 값이 3개면 무시, 숫자가 아니면 `NumberFormatException`), `converter.saveDir`(지정 폴더에 `converter-20261016-211528.png` 저장·상태 줄 "저장됨 (v72\save)", 같은 이름의 `-1`·`-2`는 코드로만 확인), 기본 저장 폴더·폴백(`Pictures\Converter` → `<home>\Converter`, 코드 확인만), `swing.bufferPerWindow`(Main이 null일 때만 true).
- 최소 창 518x427, 시작 크기 640x360(최소보다 작으면 자동으로 늘어남), 상태·버튼·제목 문구(`[일시정지]`, `(제외 실패)`, `창 변경: 일시정지 해제`, `항상 위: 켜짐/꺼짐`, `일시정지 (Space)`, `텍스트 복사 (Ctrl+C)`, `이미지 저장`, `저장 중...`, `저장 실패 (…)`, 툴팁 `마지막 저장:`)가 코드와 같고 상태 문구는 2.5초 뒤 사라짐, 메뉴 버튼 순서(효과 목록, 옵션, 일시정지, 복사, 저장, 항상 위, 상태 줄)·폭 180px 일치(오른쪽 클릭 "닫기"는 `installPopup` 코드 확인), 시작 효과 2번, ASCII 글꼴(Consolas → Courier New)·흰 배경 검은 글자.
- CPU 수치(640x360 약 13%, 1280x720 ASCII 약 29%)는 5-5 요약과 같고 5-6의 13.1%와도 일치("정적 화면" 표현은 640x360 쪽만 5-6에서 확인). 한계 항목(DPI·다중 모니터·원격 데스크톱·DRM)은 모두 "검증하지 않음"으로 표기되고 사실처럼 단정하지 않음, 자기 창 제외 0x11·항상 위·메뉴 갱신 수정은 올바르게 서술. 개발자 안내의 패키지·경로가 모두 존재, `mvn -B package`는 F에서 실제로 동작, 마크다운 표 안 깨짐·상대 링크 없음.
- **미검증(README의 해당 주장)**: 슬라이더 트랙 클릭·끌기가 가장 가까운 단위 값으로 맞춰진다는 설명, 창을 옮기면 변환 대상이 따라 움직인다는 설명(5-6 기록만 확인), `PrintScreen`이나 캡처 도구로 찍으면 변환 결과 대신 원본이 찍힌다는 설명(0x11에서 예상되는 동작이지만 이번에 시험하지 않음), 슬라이더로 임계값 최대 255에 도달하는지(단위 8이라 슬라이더는 최대 248에서 멈출 수 있음, README의 "0~255"와 미세하게 어긋날 수 있음 — 낮음).

## F. `make-dist.bat`: PASS
이 환경에는 PATH에 mvn이 없어 `.m2\wrapper\dists\apache-maven-3.8.7-bin` 캐시에서 찾아 빌드(4.8초, BUILD SUCCESS, rc 0, `dist is ready`). 재빌드 후 `dist\converter.jar`와 `target\converter.jar` 해시 동일(d7723001… 그대로). `dist\run.bat`로 앱을 띄운 상태에서 실행해도 복사가 실패하지 않음(JVM이 jar를 쓰기 공유로 열어 둔 것으로 보이며 해시가 같아 실제로 덮어썼는지는 구분하지 못함). `USERPROFILE`과 PATH를 비운 환경에서는 `Maven was not found.`와 `MVN` 환경변수 안내·rc 1. `Build failed. dist was not updated.`와 복사 실패 분기는 코드로만 확인.

## G. 낮은 항목
- `dist\README.md`가 `ROADMAP.md`·`agent-summaries/`·`tools\make-dist.bat`를 언급하지만 `dist\` 안에는 없음("소스 폴더 기준" 표기 제안).
- `dist\`의 bat 이름을 바꾸거나 삭제해도 `make-dist.bat`는 `dist\`의 오래된 파일을 지우지 않음.
- 폴더 이름에 `%`가 들어가면 `call "%~dp0_checkjava.bat"`가 퍼센트를 다시 해석해 실패할 가능성(이 환경에서는 호출 방법 때문에 확정하지 못함).
- `converter.bounds`가 숫자가 아니면 EDT 예외 스택트레이스만 찍고 창은 뜨지 않음(프로세스가 곧바로 끝나는지는 확인하지 않음).
- 흑백 2색 임계값이 단위 8이라 슬라이더로는 최대 248(README는 "0~255, 8 단위"로만 기재).

## 정리
시험 중 띄운 java/javaw 프로세스는 모두 종료(0개). 임시 폴더 밖에서 바뀐 것은 `make-dist.bat` 재실행으로 재생성된 `dist\converter.jar`(해시 동일), `target\` 빌드 산출물, `target\surefire-reports\`뿐.
