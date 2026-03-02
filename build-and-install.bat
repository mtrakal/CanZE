@echo off
REM Quick build and install script for CanZE Android Auto testing

echo ========================================
echo CanZE Android Auto - Build and Install
echo ========================================
echo.

echo [1/3] Building APK...
call gradlew assembleFdroidDebug

if errorlevel 1 (
    echo.
    echo [ERROR] Build failed!
    pause
    exit /b 1
)

echo.
echo [2/3] Installing to device...
call gradlew installFdroidDebug

if errorlevel 1 (
    echo.
    echo [ERROR] Installation failed!
    echo Make sure:
    echo - Device is connected via USB
    echo - USB debugging is enabled
    echo - ADB drivers are installed
    pause
    exit /b 1
)

echo.
echo [3/3] Restarting Android Auto...
adb shell am force-stop com.google.android.projection.gearhead

echo.
echo ========================================
echo SUCCESS! Application installed.
echo ========================================
echo.
echo Next steps:
echo 1. Connect phone to DHU or car
echo 2. Open Android Auto
echo 3. Find CanZE icon in app drawer
echo.

pause
