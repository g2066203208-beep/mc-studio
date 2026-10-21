@echo off
setlocal
chcp 65001 >nul
echo REME NPC - Minecraft 1.21.1 NeoForge HMCL installer
echo Do NOT use while Minecraft is running. Select the active HMCL GAME folder.
powershell.exe -NoLogo -NoProfile -STA -ExecutionPolicy Bypass -File "%~dp0Install-REME-NPC.ps1"
if errorlevel 1 echo Install failed or cancelled. No gameplay changes were made.
echo.
pause
