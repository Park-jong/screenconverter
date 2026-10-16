@echo off
rem Converter - start with a console window. Prints fps and timing every 5 seconds
rem (-Dconverter.metrics=true) and shows errors. Use this to diagnose problems.
call "%~dp0_checkjava.bat"
if errorlevel 1 exit /b 1
if not exist "%~dp0converter.jar" (
  echo converter.jar was not found next to this script.
  pause
  exit /b 1
)
java -Dconverter.metrics=true %* -jar "%~dp0converter.jar"
echo.
echo Converter exited with code %errorlevel%.
pause
