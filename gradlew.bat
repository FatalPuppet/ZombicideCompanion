@echo off
setlocal
set "APP_HOME=%~dp0"
for /f "tokens=2 delims=-" %%V in ('findstr /i "distributionUrl" "%APP_HOME%gradle\wrapper\gradle-wrapper.properties"') do set "VERSION=%%V"
set "VERSION=%VERSION:-bin.zip=%"
if not defined GRADLE_USER_HOME set "GRADLE_USER_HOME=%USERPROFILE%\.gradle"
set "BASE=%GRADLE_USER_HOME%\zombicide-gradle"
set "GRADLE_HOME=%BASE%\gradle-%VERSION%"
if not exist "%GRADLE_HOME%\bin\gradle.bat" (
  if not exist "%BASE%" mkdir "%BASE%"
  set "ZIP=%BASE%\gradle-%VERSION%-bin.zip"
  if not exist "%ZIP%" powershell -NoProfile -ExecutionPolicy Bypass -Command "Invoke-WebRequest -Uri 'https://services.gradle.org/distributions/gradle-%VERSION%-bin.zip' -OutFile '%BASE%\gradle-%VERSION%-bin.zip'"
  powershell -NoProfile -ExecutionPolicy Bypass -Command "Expand-Archive -Path '%BASE%\gradle-%VERSION%-bin.zip' -DestinationPath '%BASE%' -Force"
)
call "%GRADLE_HOME%\bin\gradle.bat" -p "%APP_HOME%" %*
exit /b %ERRORLEVEL%
