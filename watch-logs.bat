@echo off
REM Watch CanZE logs in real-time

echo ========================================
echo CanZE Android Auto - Live Logs
echo ========================================
echo.
echo Watching logs for: CanZE, CarAppService, CarScreen
echo Press Ctrl+C to stop
echo.

adb logcat -c
adb logcat | findstr "CanZE CarAppService CarScreen"
