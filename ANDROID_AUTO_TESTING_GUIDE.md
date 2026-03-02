# Android Auto - Návod k testování a spuštění

## 🚗 Možnosti testování Android Auto

### 1. Desktop Head Unit (DHU) - Doporučeno pro vývoj ⭐

DHU je oficiální nástroj od Google pro testování Android Auto aplikací bez fyzického vozidla.

#### Instalace DHU

1. **Stáhněte DHU**:
   - Otevřete Android SDK Manager
   - Stáhněte "Android Auto Desktop Head Unit" z SDK Tools
   - Nebo stáhněte přímo: https://developer.android.com/training/cars/testing

2. **Spuštění DHU**:

```bash
cd %LOCALAPPDATA%\Android\Sdk\extras\google\auto
desktop-head-unit.exe
```

#### Použití DHU

1. **Připravte telefon**:
   ```bash
   # Povolte ADB debugging na telefonu
   # Nastavení > O telefonu > Klepněte 7x na číslo sestavení
   # Nastavení > Možnosti pro vývojáře > Ladění USB (zapnout)
   ```

2. **Připojte telefon přes USB**:
   ```bash
   # Ověřte připojení
   adb devices
   ```

3. **Nainstalujte CanZE na telefon**:
   ```bash
   cd D:\Projects\github\CanZE
   .\gradlew installFdroidDebug
   
   # Nebo manuálně:
   adb install app\build\outputs\apk\fdroid\debug\app-fdroid-debug.apk
   ```

4. **Spusťte DHU**:
   ```bash
   desktop-head-unit.exe
   ```

5. **Na telefonu**:
   - Když se DHU spustí, na telefonu se objeví notifikace "Android Auto"
   - Klepněte na notifikaci
   - Povolte potřebná oprávnění
   - DHU na počítači zobrazí Android Auto rozhraní

6. **Najděte CanZE**:
   - V DHU klikněte na ikonu aplikací (9 teček)
   - Měli byste vidět ikonu CanZE
   - Klikněte na ni a měla by se zobrazit data z vozidla

### 2. Android Automotive OS Emulátor

Pro testování na Android Automotive OS (vestavěný systém v autě):

#### Vytvoření AVD (Android Virtual Device)

1. **Otevřete AVD Manager** v Android Studio
2. **Create Virtual Device**
3. **Vyberte kategorii "Automotive"**
4. **Zvolte profil**: např. "Automotive (1024p landscape)"
5. **Systémový obraz**: Vyberte nejnovější API (API 33+)
6. **Dokončete vytvoření AVD**

#### Spuštění na Automotive emulátu

```bash
# Spusťte emulátor
emulator -avd Automotive_1024p_landscape_API_33

# V jiném terminálu nainstalujte aplikaci
.\gradlew installFdroidDebug

# Nebo přímo:
adb install app\build\outputs\apk\fdroid\debug\app-fdroid-debug.apk
```

**⚠️ PROBLÉM**: Android Automotive OS emulátor NEpotřebuje propojení s telefonem, ale naše aplikace potřebuje připojení k vozidlu (Bluetooth/USB/Gateway). To je komplikace pro testování.

### 3. Fyzické testování v autě (nejlepší pro finální test)

1. **Nainstalujte aplikaci na telefon**
2. **Připojte telefon k vozidlu** (USB nebo bezdrátově)
3. **Spusťte Android Auto** na displeji vozidla
4. **Najděte CanZE** v menu aplikací

## 🔧 Příprava aplikace pro testování

### Povolení testování v Developer Settings

Na telefonu s Android Auto:

1. Otevřete **Android Auto** aplikaci na telefonu
2. Klepněte 10x na záhlaví (verze) v menu hamburger (≡)
3. Objeví se "Developer settings"
4. V Developer settings povolte:
   - ✅ "Unknown sources" - Pro testování neoficiálních aplikací
   - ✅ "Developer mode"
   - ✅ "Start head unit server" (pokud chcete použít DHU)

### Debug build konfigurace

Pro testování přidejte do `AndroidManifest.xml` v `<application>` tagu:

```xml
<meta-data
    android:name="com.google.android.gms.car.application.theme"
    android:resource="@style/CarAppTheme" />
```

## 🐛 Debugging Android Auto

### ADB Logcat pro Android Auto

```bash
# Sledujte logy z CanZE
adb logcat | findstr "CanZE\|CarAppService\|CarScreen"

# Nebo kompletní logy Android Auto
adb logcat | findstr "AndroidAuto\|CarApp"
```

### Debug v Android Studio

1. **Run > Edit Configurations**
2. **Vyberte "Android App"**
3. **Launch**: Vyberte "Nothing" (protože Android Auto startuje službu)
4. **Deployment target**: Váš telefon
5. **Attach debugger**: Ano
6. Nyní můžete nastavit breakpointy v `CanZeCarScreen.java`

## 📋 Checklist pro testování

Před testováním ověřte:

- [ ] ✅ Build je úspěšný (`.\gradlew assembleFdroidDebug`)
- [ ] ✅ Aplikace je nainstalována na telefonu
- [ ] ✅ Android Auto je nainstalované na telefonu
- [ ] ✅ Developer settings v Android Auto jsou povolené
- [ ] ✅ "Unknown sources" je zapnuté
- [ ] ✅ CanZE je připojeno k vozidlu (nebo simulátoru)
- [ ] ✅ `MainActivity.device` není null
- [ ] ✅ DHU je spuštěno (nebo fyzické auto je připojeno)

## 🎯 Testovací scénáře

### Scénář 1: První spuštění

1. Nainstalujte aplikaci
2. Spusťte CanZE na telefonu
3. Připojte se k vozidlu (Bluetooth/USB/Gateway)
4. Připojte telefon k DHU nebo fyzickému auto
5. V Android Auto menu najděte CanZE
6. Ověřte, že se zobrazují data

### Scénář 2: Real-time aktualizace

1. Spusťte CanZE v Android Auto
2. V hlavní aplikaci CanZE změňte nějakou hodnotu
3. Ověřte, že se aktualizuje i v Android Auto
4. Zkontrolujte latenci aktualizací

### Scénář 3: Lifecycle

1. Spusťte CanZE v Android Auto
2. Minimalizujte Android Auto
3. Vraťte se zpět
4. Ověřte, že data jsou aktuální
5. Odpojte telefon
6. Ověřte, že nedochází k crash

## ⚠️ Známé problémy při testování

### Problém: "Aplikace se nezobrazuje v Android Auto"

**Řešení**:
- Zkontrolujte `AndroidManifest.xml` - služba musí být `exported="true"`
- Ověřte `automotive_app_desc.xml`
- Zkontrolujte logcat: `adb logcat | findstr "CarAppService"`
- Restartujte Android Auto aplikaci
- Znovu nainstalujte APK

### Problém: "Data se nezobrazují"

**Řešení**:
- Ověřte, že `MainActivity.device != null`
- Zkontrolujte připojení k vozidlu v hlavní aplikaci
- Ověřte SID konstanty v `CanZeCarScreen.java`
- Zkontrolujte logcat: `adb logcat | findstr "CanZeCarScreen"`

### Problém: "DHU se nepřipojí k telefonu"

**Řešení**:
```bash
# Restartujte ADB
adb kill-server
adb start-server

# Ověřte připojení
adb devices

# Zkontrolujte USB debugging na telefonu
adb shell settings get global development_settings_enabled
```

### Problém: "Permission denied"

**Řešení**:
- Na telefonu v Android Auto povolte "Unknown sources"
- Udělte všechna oprávnění CanZE aplikaci
- Restartujte telefon

## 🔍 Užitečné příkazy

```bash
# Instalace debug APK
.\gradlew installFdroidDebug

# Odinstalace
adb uninstall lu.fisch.canze

# Čištění dat aplikace
adb shell pm clear lu.fisch.canze

# Restart Android Auto
adb shell am force-stop com.google.android.projection.gearhead

# Sledování logů
adb logcat -c && adb logcat | findstr "CanZE"

# Screenshot z telefonu
adb shell screencap -p /sdcard/screenshot.png
adb pull /sdcard/screenshot.png

# Seznam běžících služeb
adb shell dumpsys activity services | findstr "CarAppService"
```

## 📊 Monitoring výkonu

```bash
# CPU využití
adb shell top -n 1 | findstr "lu.fisch.canze"

# Paměť
adb shell dumpsys meminfo lu.fisch.canze

# Network traffic
adb shell dumpsys netstats | findstr "lu.fisch.canze"
```

## 📚 Další zdroje

- [Android for Cars Documentation](https://developer.android.com/training/cars)
- [Android Auto Developer Guide](https://developer.android.com/training/cars/apps)
- [Car App Library Documentation](https://developer.android.com/reference/androidx/car/app/package-summary)
- [Testing Car Apps](https://developer.android.com/training/cars/testing)

## 🎓 Doporučený workflow pro vývoj

1. **Desktop Head Unit (DHU)** - Pro rychlý vývoj a debugging
2. **Automotive OS Emulátor** - Pro testování na vestavěném systému
3. **Fyzické auto** - Pro finální testování UX a výkonu

---

**Pro začátek doporučuji použít DHU, protože je nejrychlejší a nejjednodušší na nastavení!**
