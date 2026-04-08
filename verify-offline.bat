@echo off
setlocal
where pwsh.exe >nul 2>nul
if %ERRORLEVEL% equ 0 (
  pwsh -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\verify-offline.ps1" %*
) else (
  powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\verify-offline.ps1" %*
)
exit /b %ERRORLEVEL%
