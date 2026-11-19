@echo off
setlocal
powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -File "%~dp0executar.ps1"
set "resultado=%errorlevel%"
if not "%resultado%"=="0" pause
exit /b %resultado%
