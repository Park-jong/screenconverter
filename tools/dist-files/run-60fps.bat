@echo off
rem Converter - start with a 60 fps target (-Dconverter.fps=60). Uses more CPU than the default 30.
call "%~dp0_checkjava.bat"
if errorlevel 1 exit /b 1
if not exist "%~dp0converter.jar" (
  echo converter.jar was not found next to this script.
  pause
  exit /b 1
)
start "" javaw -Dconverter.fps=60 %* -jar "%~dp0converter.jar"
