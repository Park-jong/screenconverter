@echo off
rem Internal helper: exits 0 if java.exe (17+) is usable, otherwise prints a hint and pauses.
where java >nul 2>nul
if errorlevel 1 goto nojava
set "JV="
for /f "tokens=3" %%v in ('java -version 2^>^&1 ^| findstr /i "version"') do if not defined JV set "JV=%%~v"
if not defined JV goto ok
set "JMAJOR="
for /f "delims=." %%m in ("%JV%") do set "JMAJOR=%%m"
if not defined JMAJOR goto ok
if "%JMAJOR%"=="1" goto tooold
set /a JNUM=%JMAJOR% 2>nul
if %JNUM% LSS 17 goto tooold
:ok
exit /b 0
:nojava
echo Java was not found in PATH.
echo Install JDK or JRE 17 or newer ^(for example Microsoft Build of OpenJDK 17^) and try again.
pause
exit /b 1
:tooold
echo Java %JV% was found, but version 17 or newer is required.
echo Install JDK or JRE 17 or newer and make sure it is first in PATH.
pause
exit /b 1
