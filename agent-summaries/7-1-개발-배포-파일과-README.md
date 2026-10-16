# 7-1 개발: 배포 파일과 README (code-writer 보고, 메인 에이전트가 대신 저장)

검증 판정 없음. 소스 코드·`pom.xml` 수정 없음(스크립트·문서만).

## 만든 파일
- `README.md`(프로젝트 루트, 한국어, 요청한 11개 절).
- `tools\make-dist.bat`: Maven을 `MVN` 환경변수 → PATH의 `mvn.cmd` → `%USERPROFILE%\.m2\wrapper\dists\apache-maven-3.8.7-bin\*\...\mvn.cmd` 순으로 탐색, 못 찾으면 영어 안내 출력. `mvn -B package` 후 jar·bat·README를 `dist`로 복사.
- `tools\dist-files\`(bat 원본): `_checkjava.bat`, `run.bat`, `run-console.bat`, `run-60fps.bat`, `run-max.bat`.
- `dist\`: `converter.jar`, 위 bat 5개, `README.md` — `make-dist.bat` 실행으로 생성. `converter.jar`는 `target\converter.jar`와 바이트 단위로 동일(`cmp`).

## bat 구성
- 모두 ASCII, 첫 줄 `@echo off`, 경로는 `"%~dp0..."` 따옴표 처리. 공용 `_checkjava.bat`가 `where java`로 java.exe 존재를, `java -version`으로 17 이상 여부를 확인하고 없거나 낮으면 영어 안내 후 `pause`(**이 helper의 오류 경로(java 없음, 17 미만)는 실제로 시험하지 않음**).
- `run.bat`은 `start "" javaw %* -jar "%~dp0converter.jar"`. 요청 형태 `javaw -jar converter.jar %*` 로는 `-D` 옵션이 JVM이 아니라 main의 args로 가서 무시되므로 `%*`를 `-jar` 앞에 두었고 `run.bat -Dconverter.fps=45`처럼 사용 가능. `run-console.bat`은 `java -Dconverter.metrics=true %* -jar ...` 뒤 종료 코드를 보여 주고 `pause`. `run-60fps.bat`은 `-Dconverter.fps=60`, `run-max.bat`은 `-Dconverter.fps=max`(CPU 경고 주석).

## 빌드·테스트·jar 점검
- `tools\make-dist.bat` 실행: BUILD SUCCESS, surefire 리포트 합계 194개 중 실패 0·오류 0. shade의 "중복 클래스/파일" WARNING은 JNA와 jna-platform 쪽이며 빌드에 영향 없음.
- `target\converter.jar`: JNA 네이티브 포함(`com/sun/jna/win32-x86-64/jnidispatch.dll`, `win32-x86`, `win32-aarch64` 등 다른 OS용 네이티브도 함께), `Main-Class: com.converter.Main`, `META-INF`에 `*.SF`·`*.RSA`·`*.DSA` 없음(pom filter가 이미 제외, pom 수정 불필요).
- 스모크: `dist\run.bat` 실행 → `javaw.exe -jar "C:\Users\park\Desktop\converter\dist\converter.jar"` 프로세스(PID 17468)가 뜨고 창 제목 `Converter - 모자이크` 확인, 그 프로세스만 종료. 사용자 시연 창(PID 14444)은 건드리지 않음. 창이 화면에 실제로 그려졌는지는 보지 않고 제목 확인까지만.

## README에서 "검증하지 않음" 또는 단정하지 않은 항목
배율 125%/150%(DPI)·다중 모니터(구현·검증하지 않음), 원격 데스크톱·Windows 10 2004 미만에서의 자기 창 제외 실패(거울 현상)(이 PC에서 검증하지 않음), DRM 영상·전체 화면 독점 게임의 검은 화면 가능성(이 PC에서 확인하지 않았으며 단정 불가), 사진 폴더를 다른 곳으로 옮긴 PC에서 Java의 `user.home\Pictures`와 실제 "내 사진" 위치가 다를 수 있다는 점(검증하지 않음), 실제 동영상·게임에서의 체감과 DWM 합성 화면의 육안 확인(사용자 확인 항목), CPU 수치(640x360 약 13%, 1280x720 약 29%)는 5-5/5-7 요약 측정값 인용(640x360은 모자이크·정적 화면, 1280x720은 ASCII 효과 기준, PC마다 다름 표기).

## 코드와 대조해 README에 반영한 내용
- 효과 1~9 이름·옵션: 모자이크 블록 크기 2~64 단위 2 기본 10, 흑백 2색 임계값 0~255 단위 8 기본 128, 도트 블록 크기 기본 8(7·8번은 최소 4), ASCII 칸 크기 8x16/6x12/4x8과 줄 끝 공백 제거.
- 단축키: 1~9(숫자패드 포함), `+`/`-`(`=`, `Shift+=`, 숫자패드 포함), Space, Ctrl+C, ESC. 단축키가 없는 버튼은 이미지 저장·항상 위.
- 버튼 문구, 상태 문구 "창 변경: 일시정지 해제", 제목의 `[일시정지]`, `(제외 실패)`.
- 실행 옵션: `converter.fps`(기본 30, 1~240 범위 보정, `max` 또는 `0`은 무제한), `converter.metrics`(환경변수 `CONVERTER_METRICS`도 가능), `converter.bounds`, `converter.saveDir`, `swing.bufferPerWindow`(Main이 기본 true로 설정하고 사용자 지정값은 존중).
- 이미지 저장: 폴더 `user.home\Pictures\Converter`(실패하면 `user.home\Converter`), 파일명 `converter-yyyyMMdd-HHmmss.png`(중복이면 `-1`, `-2`). 시작 크기 640x360이 최소 창 크기보다 작으면 자동으로 늘어난다고 적음.

## 하지 않은 것
하네스·Robot·스크린샷 비교·성능 측정·육안 검토 보고서, `_checkjava.bat`의 오류 경로 시험(java 없음, 17 미만), `dist\run-max.bat` 등 개별 bat의 실제 실행(`run.bat`만 실행), git 커밋(저장소가 아님).
