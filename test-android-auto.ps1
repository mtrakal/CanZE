# CanZE Android Auto - Testovací skripty
# Použití: Spusťte v PowerShell z kořenového adresáře projektu

Write-Host "=== CanZE Android Auto - Testovací nástroje ===" -ForegroundColor Cyan
Write-Host ""

function Show-Menu {
    Write-Host "Vyberte akci:" -ForegroundColor Yellow
    Write-Host "1. Build a instalace na telefon"
    Write-Host "2. Spuštění DHU (Desktop Head Unit)"
    Write-Host "3. Sledování logů (Logcat)"
    Write-Host "4. Odinstalace aplikace"
    Write-Host "5. Restart Android Auto"
    Write-Host "6. Kontrola připojení ADB"
    Write-Host "7. Kompletní test (build + install + DHU + logs)"
    Write-Host "8. Zobrazit info o zařízení"
    Write-Host "0. Konec"
    Write-Host ""
}

function Build-And-Install {
    Write-Host "Building a instalace aplikace..." -ForegroundColor Green

    # Build
    Write-Host "1/2 Sestavování APK..." -ForegroundColor Yellow
    .\gradlew assembleFdroidDebug

    if ($LASTEXITCODE -eq 0) {
        Write-Host "✓ Build úspěšný!" -ForegroundColor Green

        # Install
        Write-Host "2/2 Instalace na zařízení..." -ForegroundColor Yellow
        .\gradlew installFdroidDebug

        if ($LASTEXITCODE -eq 0) {
            Write-Host "✓ Aplikace nainstalována!" -ForegroundColor Green
        } else {
            Write-Host "✗ Chyba při instalaci!" -ForegroundColor Red
        }
    } else {
        Write-Host "✗ Build selhal!" -ForegroundColor Red
    }

    Write-Host ""
    Pause
}

function Start-DHU {
    Write-Host "Spouštění Desktop Head Unit..." -ForegroundColor Green

    $dhuPath = "$env:LOCALAPPDATA\Android\Sdk\extras\google\auto\desktop-head-unit.exe"

    if (Test-Path $dhuPath) {
        Write-Host "✓ DHU nalezeno" -ForegroundColor Green
        Write-Host "Spouštím DHU..." -ForegroundColor Yellow
        Write-Host "TIP: Na telefonu klepněte na notifikaci 'Android Auto'" -ForegroundColor Cyan
        Start-Process $dhuPath
    } else {
        Write-Host "✗ DHU nenalezeno na: $dhuPath" -ForegroundColor Red
        Write-Host "Nainstalujte DHU přes Android SDK Manager" -ForegroundColor Yellow
    }

    Write-Host ""
    Pause
}

function Watch-Logs {
    Write-Host "Sledování logů CanZE..." -ForegroundColor Green
    Write-Host "Stiskněte Ctrl+C pro ukončení" -ForegroundColor Yellow
    Write-Host ""

    adb logcat -c
    adb logcat | Select-String "CanZE|CarAppService|CarScreen"
}

function Uninstall-App {
    Write-Host "Odinstalace aplikace..." -ForegroundColor Green

    adb uninstall lu.fisch.canze

    if ($LASTEXITCODE -eq 0) {
        Write-Host "✓ Aplikace odinstalována!" -ForegroundColor Green
    } else {
        Write-Host "✗ Chyba při odinstalaci!" -ForegroundColor Red
    }

    Write-Host ""
    Pause
}

function Restart-AndroidAuto {
    Write-Host "Restart Android Auto..." -ForegroundColor Green

    adb shell am force-stop com.google.android.projection.gearhead
    Start-Sleep -Seconds 2

    Write-Host "✓ Android Auto restartováno!" -ForegroundColor Green
    Write-Host ""
    Pause
}

function Check-ADB {
    Write-Host "Kontrola ADB připojení..." -ForegroundColor Green
    Write-Host ""

    Write-Host "Připojená zařízení:" -ForegroundColor Yellow
    adb devices

    Write-Host ""
    Write-Host "Info o zařízení:" -ForegroundColor Yellow
    adb shell getprop ro.product.model
    adb shell getprop ro.build.version.release

    Write-Host ""
    Pause
}

function Complete-Test {
    Write-Host "=== Kompletní test ===" -ForegroundColor Cyan
    Write-Host ""

    # 1. Check ADB
    Write-Host "1/5 Kontrola ADB..." -ForegroundColor Yellow
    $devices = adb devices | Select-String "device$"
    if ($devices) {
        Write-Host "✓ Zařízení připojeno" -ForegroundColor Green
    } else {
        Write-Host "✗ Žádné zařízení připojeno!" -ForegroundColor Red
        Write-Host "Připojte telefon přes USB a povolte USB debugging" -ForegroundColor Yellow
        Pause
        return
    }

    # 2. Build
    Write-Host "2/5 Build aplikace..." -ForegroundColor Yellow
    .\gradlew assembleFdroidDebug
    if ($LASTEXITCODE -ne 0) {
        Write-Host "✗ Build selhal!" -ForegroundColor Red
        Pause
        return
    }
    Write-Host "✓ Build úspěšný" -ForegroundColor Green

    # 3. Install
    Write-Host "3/5 Instalace..." -ForegroundColor Yellow
    .\gradlew installFdroidDebug
    if ($LASTEXITCODE -ne 0) {
        Write-Host "✗ Instalace selhala!" -ForegroundColor Red
        Pause
        return
    }
    Write-Host "✓ Aplikace nainstalována" -ForegroundColor Green

    # 4. DHU
    Write-Host "4/5 Spouštění DHU..." -ForegroundColor Yellow
    $dhuPath = "$env:LOCALAPPDATA\Android\Sdk\extras\google\auto\desktop-head-unit.exe"
    if (Test-Path $dhuPath) {
        Start-Process $dhuPath
        Write-Host "✓ DHU spuštěno" -ForegroundColor Green
    } else {
        Write-Host "⚠ DHU nenalezeno" -ForegroundColor Yellow
    }

    # 5. Logs
    Write-Host "5/5 Sledování logů..." -ForegroundColor Yellow
    Write-Host ""
    Write-Host "=== PŘIPRAVENO K TESTOVÁNÍ ===" -ForegroundColor Green
    Write-Host "1. Na telefonu klepněte na notifikaci 'Android Auto'" -ForegroundColor Cyan
    Write-Host "2. V DHU najděte ikonu CanZE" -ForegroundColor Cyan
    Write-Host "3. Sledujte logy níže" -ForegroundColor Cyan
    Write-Host ""
    Write-Host "Stiskněte Ctrl+C pro ukončení logů" -ForegroundColor Yellow
    Write-Host ""

    Start-Sleep -Seconds 3
    adb logcat -c
    adb logcat | Select-String "CanZE|CarAppService|CarScreen"
}

function Show-DeviceInfo {
    Write-Host "=== Informace o zařízení ===" -ForegroundColor Cyan
    Write-Host ""

    Write-Host "Model:" -ForegroundColor Yellow
    adb shell getprop ro.product.model

    Write-Host "Android verze:" -ForegroundColor Yellow
    adb shell getprop ro.build.version.release

    Write-Host "API Level:" -ForegroundColor Yellow
    adb shell getprop ro.build.version.sdk

    Write-Host "Výrobce:" -ForegroundColor Yellow
    adb shell getprop ro.product.manufacturer

    Write-Host ""
    Write-Host "Nainstalované aplikace (Android Auto related):" -ForegroundColor Yellow
    adb shell pm list packages | Select-String "auto|projection|car"

    Write-Host ""
    Write-Host "CanZE služby:" -ForegroundColor Yellow
    adb shell dumpsys activity services | Select-String "CanZe|CarApp"

    Write-Host ""
    Pause
}

# Main menu loop
do {
    Clear-Host
    Show-Menu
    $choice = Read-Host "Volba"

    switch ($choice) {
        "1" { Build-And-Install }
        "2" { Start-DHU }
        "3" { Watch-Logs }
        "4" { Uninstall-App }
        "5" { Restart-AndroidAuto }
        "6" { Check-ADB }
        "7" { Complete-Test }
        "8" { Show-DeviceInfo }
        "0" {
            Write-Host "Ukončuji..." -ForegroundColor Green
            exit
        }
        default {
            Write-Host "Neplatná volba!" -ForegroundColor Red
            Start-Sleep -Seconds 1
        }
    }
} while ($true)
