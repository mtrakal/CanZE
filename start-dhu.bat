@echo off
REM Start Desktop Head Unit for Android Auto testing

echo ========================================
echo Starting Desktop Head Unit (DHU)
echo ========================================
echo.

set DHU_PATH=%LOCALAPPDATA%\Android\Sdk\extras\google\auto\desktop-head-unit.exe

if exist "%DHU_PATH%" (
    echo DHU found: %DHU_PATH%
    echo.
    echo Starting DHU...
    echo.
    echo INSTRUCTIONS:
    echo 1. Make sure phone is connected via USB
    echo 2. Tap on "Android Auto" notification on phone
    echo 3. DHU window will show Android Auto interface
    echo 4. Find CanZE icon in app drawer
    echo.

    start "" "%DHU_PATH%"

    echo DHU started!
) else (
    echo [ERROR] DHU not found at: %DHU_PATH%
    echo.
    echo Please install DHU:
    echo 1. Open Android Studio
    echo 2. Tools ^> SDK Manager
    echo 3. SDK Tools tab
    echo 4. Check "Android Auto Desktop Head Unit"
    echo 5. Click Apply
)

echo.
pause
