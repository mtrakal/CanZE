# 🚗 CanZE Android Auto - Rychlý Start

## 📋 Co potřebujete

1. **Telefon s Android 6.0+**
2. **USB kabel** pro připojení telefonu k PC
3. **Android Auto** aplikace na telefonu
4. **Desktop Head Unit (DHU)** - pro testování bez auta

## 🚀 Rychlý start (5 minut)

### Krok 1: Příprava telefonu

1. Na telefonu: **Nastavení → O telefonu**
2. Klepněte **7× na "Číslo sestavení"**
3. **Nastavení → Možnosti pro vývojáře**
4. Zapněte **"Ladění USB"**

### Krok 2: Příprava Android Auto

1. Otevřete **Android Auto** aplikaci
2. Klepněte na **≡ (hamburger menu)**
3. Klepněte **10× na verzi** v záhlaví
4. Otevře se **"Developer settings"**
5. Zapněte:
   - ✅ **Unknown sources**
   - ✅ **Developer mode**

### Krok 3: Instalace DHU

1. Otevřete **Android Studio**
2. **Tools → SDK Manager**
3. **SDK Tools** tab
4. Zaškrtněte **"Android Auto Desktop Head Unit"**
5. Klikněte **Apply**

### Krok 4: Build a instalace

Spusťte jeden z těchto skriptů:

**Varianta A: PowerShell** (doporučeno)
```powershell
.\test-android-auto.ps1
```
Vyberte možnost **7** (Kompletní test)

**Varianta B: Batch soubor**
```cmd
build-and-install.bat
```

**Varianta C: Manuálně**
```cmd
gradlew assembleFdroidDebug
gradlew installFdroidDebug
```

### Krok 5: Testování

1. Připojte telefon k PC přes USB
2. Spusťte DHU:
   ```cmd
   start-dhu.bat
   ```
3. Na telefonu klepněte na notifikaci **"Android Auto"**
4. V DHU klikněte na **ikonu aplikací** (9 teček)
5. Najděte **CanZE** ikonu
6. Klikněte na ni → měla by se zobrazit data!

## 📱 Dostupné skripty

### `test-android-auto.ps1` ⭐
Interaktivní PowerShell menu s těmito možnostmi:
- **1** - Build a instalace
- **2** - Spuštění DHU
- **3** - Sledování logů
- **4** - Odinstalace
- **5** - Restart Android Auto
- **6** - Kontrola ADB
- **7** - Kompletní test (vše najednou)
- **8** - Info o zařízení

### `build-and-install.bat`
Rychlý build a instalace na telefon

### `start-dhu.bat`
Spuštění Desktop Head Unit

### `watch-logs.bat`
Sledování logů v reálném čase

## 🐛 Řešení problémů

### "Zařízení nenalezeno"
```cmd
# Restartujte ADB
adb kill-server
adb start-server
adb devices
```

### "Aplikace se nezobrazuje v Android Auto"
1. Zkontrolujte **Unknown sources** v Android Auto
2. Restartujte Android Auto: `adb shell am force-stop com.google.android.projection.gearhead`
3. Přeinstalujte aplikaci

### "Data se nezobrazují"
1. Ujistěte se, že **hlavní aplikace CanZE je spuštěna**
2. Ověřte **připojení k vozidlu** (Bluetooth/USB/Gateway)
3. Sledujte logy: `watch-logs.bat`

## 📊 Sledování logů

Během testování můžete sledovat logy:

```cmd
# V novém okně terminálu
watch-logs.bat

# Nebo přímo:
adb logcat | findstr "CanZE CarAppService CarScreen"
```

## 🎯 Testovací checklist

Před testováním:
- [ ] ✅ Build je úspěšný
- [ ] ✅ Aplikace je na telefonu
- [ ] ✅ USB debugging je zapnutý
- [ ] ✅ Android Auto má povolené "Unknown sources"
- [ ] ✅ CanZE je připojeno k vozidlu
- [ ] ✅ DHU je spuštěno

Během testování:
- [ ] ✅ Aplikace se zobrazí v Android Auto
- [ ] ✅ Data se zobrazují správně
- [ ] ✅ Data se aktualizují real-time
- [ ] ✅ Žádné crashes v logách

## 🔍 Debug tipy

```cmd
# Informace o zařízení
adb shell getprop ro.product.model
adb shell getprop ro.build.version.release

# Seznam Android Auto služeb
adb shell dumpsys activity services | findstr "CarAppService"

# Screenshot z telefonu
adb shell screencap -p /sdcard/screenshot.png
adb pull /sdcard/screenshot.png

# Vyčištění dat aplikace
adb shell pm clear lu.fisch.canze

# Přeinstalace
adb uninstall lu.fisch.canze
gradlew installFdroidDebug
```

## 📚 Dokumentace

Detailní návody:
- `ANDROID_AUTO_TESTING_GUIDE.md` - Kompletní testovací průvodce
- `ANDROID_AUTO_README.md` - Dokumentace pro uživatele
- `ANDROID_AUTO_IMPLEMENTATION_SUMMARY.md` - Technická implementace

## 🎥 Video návod (doporučeno)

1. [Android Auto App Testing with DHU](https://www.youtube.com/watch?v=0h9vRjGJRzE)
2. [Building Your First Android Auto App](https://www.youtube.com/watch?v=Q96Sw6v4ULg)

## ⚡ Nejčastější workflow

**Pro každodenní vývoj:**
```powershell
# V PowerShell
.\test-android-auto.ps1
# Vyberte: 7 (Kompletní test)
```

**Pro rychlé testování změn:**
```cmd
build-and-install.bat
```

**Pro debugging:**
```cmd
# Okno 1: Build a instalace
build-and-install.bat

# Okno 2: Logy
watch-logs.bat

# Okno 3: DHU (pokud není spuštěno)
start-dhu.bat
```

---

**🎉 Teď jste připraveni testovat CanZE v Android Auto!**

Pro další pomoc nebo problémy, zkontrolujte logy nebo kontaktujte tým.
