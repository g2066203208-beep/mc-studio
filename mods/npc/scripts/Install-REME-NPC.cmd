@echo off
setlocal
cd /d "%~dp0"
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0Install-REME-NPC.ps1"
set "RESULT=%ERRORLEVEL%"
if not "%RESULT%"=="0" (
  echo.
  echo NPC installation failed. Copy the error shown above and send it with current HMCL logs/latest.log.
)
pause
exit /b %RESULT%
