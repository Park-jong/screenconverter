@echo off
rem Converter - normal start (no console window). Extra JVM options can be passed,
rem for example: run.bat -Dconverter.fps=60
call "%~dp0_checkjava.bat"
if errorlevel 1 exit /b 1
if not exist "%~dp0converter.jar" (
  echo converter.jar was not found next to this script.
  pause
  exit /b 1
)
start "" javaw %* -jar "%~dp0converter.jar"
