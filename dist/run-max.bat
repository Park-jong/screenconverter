@echo off
rem Converter - start with no frame limit (-Dconverter.fps=max).
rem WARNING: this runs the capture loop as fast as possible and uses a lot of CPU.
call "%~dp0_checkjava.bat"
if errorlevel 1 exit /b 1
if not exist "%~dp0converter.jar" (
  echo converter.jar was not found next to this script.
  pause
  exit /b 1
)
start "" javaw -Dconverter.fps=max %* -jar "%~dp0converter.jar"
