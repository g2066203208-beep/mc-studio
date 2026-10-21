@echo off
chcp 65001 >nul
echo Install 3D Skin Layers and moving 3D eyes (EyeMod) from official Modrinth.
echo Minecraft 1.21.1 NeoForge ONLY. Please close Minecraft first.
powershell.exe -NoProfile -STA -ExecutionPolicy Bypass -File "%~dp0Install-3D-Skin-and-EyeMod.ps1"
echo.
pause
