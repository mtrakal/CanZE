# Android Auto integrace pro CanZE

## Popis

CanZE nyní podporuje Android Auto! Můžete zobrazit důležité informace o vašem elektrickém vozidle přímo na displeji Android Auto ve vašem autě.

## Zobrazované informace

Aplikace zobrazuje následující data na displeji Android Auto:

- **Baterie (User SOC)** - Stav nabití baterie zobrazený uživateli (%)
- **Skutečný stav (Real SOC)** - Skutečný stav nabití baterie (%)
- **Zbývající dosah** - Odhadovaný zbývající dojezd (km)
- **Rychlost** - Aktuální rychlost vozidla (km/h)
- **Teplota HV baterie** - Teplota vysokonapěťové baterie (°C)
- **Nabíjecí výkon** - Aktuální nabíjecí výkon (kW) - zobrazuje se pouze při nabíjení
- **Stav baterie (SOH)** - State of Health - celkový stav baterie (%)

## Jak používat

### Požadavky

1. Android zařízení s Android 6.0 (API 23) nebo vyšším
2. Nainstalovaná aplikace Android Auto
3. Vozidlo s podporou Android Auto nebo kompatibilní head unit
4. Funkční připojení CanZE k vozidlu (přes Bluetooth, USB nebo HTTP gateway)

### Spuštění

1. Připojte telefon k vozidlu pomocí USB kabelu nebo bezdrátově (pokud je podporováno)
2. Ujistěte se, že CanZE je připojeno k vozidlu (Bluetooth/USB/Gateway)
3. Otevřete Android Auto na displeji vozidla
4. V menu aplikací najděte ikonu CanZE
5. Klikněte na ikonu pro zobrazení dat z vozidla

## Technické informace

### Implementované komponenty

- `CanZeCarAppService` - Hlavní služba pro Android Auto
- `CanZeCarScreen` - Obrazovka zobrazující data z vozidla
- Registrace v `AndroidManifest.xml`
- Konfigurace v `automotive_app_desc.xml`

### Použité závislosti

```gradle
implementation 'androidx.car.app:app:1.7.0'
implementation 'androidx.car.app:app-projected:1.7.0'
implementation 'androidx.car.app:app-automotive:1.7.0'
```

### Kategorie aplikace

Aplikace je registrována jako `NAVIGATION` kategorie, což umožňuje zobrazení i během jízdy.

## Bezpečnost

- Aplikace zobrazuje pouze čtecí data bez možnosti ovládání
- Data se automaticky aktualizují v reálném čase
- Aplikace respektuje bezpečnostní omezení Android Auto pro minimální rozptýlení řidiče

## Řešení problémů

### Aplikace se nezobrazuje v Android Auto

1. Zkontrolujte, že máte nainstalovanou nejnovější verzi Android Auto
2. Ujistěte se, že CanZE má všechna potřebná oprávnění
3. V nastavení Android Auto povolte neznámé zdroje (pouze pro vývoj)

### Data se nezobrazují nebo neaktualizují

1. Zkontrolujte připojení CanZE k vozidlu
2. Ujistěte se, že máte správně nakonfigurované zařízení (Bluetooth/USB/Gateway)
3. Restartujte aplikaci CanZE

### Debug režim

Pro vývoj a testování je v debug buildu povolen `ALLOW_ALL_HOSTS_VALIDATOR`, který umožňuje připojení z DHU (Desktop Head Unit) simulátoru.

## Budoucí vylepšení

Plánovaná rozšíření:

- [ ] Přidání více obrazovek s detailními informacemi
- [ ] Navigační pokyny s ohledem na stav baterie
- [ ] Notifikace při nízkém stavu baterie
- [ ] Zobrazení nabíjecích stanic v okolí
- [ ] Hlasové ovládání

## Testování

Pro testování Android Auto bez fyzického vozidla můžete použít Desktop Head Unit (DHU):

```bash
# Instalace DHU
adb install desktop-head-unit.apk

# Spuštění DHU
./desktop-head-unit
```

## Poznámky pro vývojáře

- Screen komponenta implementuje `FieldListener` pro příjem real-time dat z vozidla
- Lifecycle management je řešen pomocí `DefaultLifecycleObserver`
- Všechna data jsou formátována pomocí `Locale.getDefault()` pro správnou lokalizaci
- Field listenery jsou automaticky registrovány při prvním zobrazení a odregistrovány při zničení obrazovky

## Licence

Stejná jako hlavní projekt CanZE - GNU General Public License v3.0
