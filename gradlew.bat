@echo off
setlocal
set GRADLE_VERSION=9.2.0
set WRAPPER_DIR=%USERPROFILE%\.gradle\wrapper\dists\gradle-%GRADLE_VERSION%-bin
for /d %%D in ("%WRAPPER_DIR%\*") do if exist "%%D\bin\gradle.bat" set GRADLE_HOME=%%D
if not defined GRADLE_HOME (
  echo Gradle %GRADLE_VERSION% not found. Downloading...
  powershell -NoProfile -ExecutionPolicy Bypass -Command "$ErrorActionPreference='Stop'; $u='https://services.gradle.org/distributions/gradle-%GRADLE_VERSION%-bin.zip'; $d=Join-Path $env:USERPROFILE '.gradle\wrapper\dists\gradle-%GRADLE_VERSION%-bin\maikura'; New-Item -ItemType Directory -Force -Path $d | Out-Null; $z=Join-Path $d 'gradle.zip'; Invoke-WebRequest -Uri $u -OutFile $z; Expand-Archive -Path $z -DestinationPath $d -Force"
  for /d %%D in ("%WRAPPER_DIR%\*\gradle-%GRADLE_VERSION%") do if exist "%%D\bin\gradle.bat" set GRADLE_HOME=%%D
)
if not defined GRADLE_HOME (
  echo Failed to prepare Gradle %GRADLE_VERSION%.
  exit /b 1
)
call "%GRADLE_HOME%\bin\gradle.bat" %*
endlocal
