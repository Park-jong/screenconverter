@echo off
setlocal enabledelayedexpansion
rem Rebuilds the project and (re)creates the dist folder: converter.jar, run*.bat, README.md.
rem Maven lookup order: MVN environment variable, mvn on PATH, cached Maven wrapper (3.8.7).
set "ROOT=%~dp0.."
pushd "%ROOT%" || exit /b 1
set "ROOT=%CD%"

if defined MVN if not exist "%MVN%" set "MVN="
if not defined MVN (
  for /f "delims=" %%p in ('where mvn.cmd 2^>nul') do if not defined MVN set "MVN=%%p"
)
if not defined MVN (
  for /d %%D in ("%USERPROFILE%\.m2\wrapper\dists\apache-maven-3.8.7-bin\*") do (
    if exist "%%D\apache-maven-3.8.7\bin\mvn.cmd" set "MVN=%%D\apache-maven-3.8.7\bin\mvn.cmd"
  )
)
if not defined MVN (
  echo Maven was not found.
  echo Set the MVN environment variable to the full path of mvn.cmd, or add Maven to PATH.
  popd
  exit /b 1
)
echo Using Maven: %MVN%

call "%MVN%" -B package
if errorlevel 1 (
  echo Build failed. dist was not updated.
  popd
  exit /b 1
)
if not exist "target\converter.jar" (
  echo target\converter.jar not found after build.
  popd
  exit /b 1
)

if not exist dist mkdir dist
copy /y "target\converter.jar" "dist\converter.jar" >nul || goto copyfail
copy /y "tools\dist-files\*.bat" "dist\" >nul || goto copyfail
copy /y "README.md" "dist\README.md" >nul || goto copyfail
rem README images (docs\images) are copied next to the README so its relative image links work in dist.
if exist "docs\images" (
  if not exist "dist\docs\images" mkdir "dist\docs\images"
  copy /y "docs\images\*.png" "dist\docs\images\" >nul || goto copyfail
)
echo.
echo dist is ready: %ROOT%\dist
popd
exit /b 0

:copyfail
echo Copy to dist failed.
popd
exit /b 1
