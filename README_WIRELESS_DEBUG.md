# Build e Installazione Wireless su Samsung Galaxy Watch Ultra

Guida per compilare e installare l'app PointsVolleyHub sulla Galaxy Watch Ultra tramite ADB wireless.

---

## 📋 Prerequisiti

1. **Samsung Galaxy Watch Ultra** con Wear OS
2. **PC Windows** con ADB installato
3. **Stessa rete Wi-Fi** per PC e smartwatch
4. **Modalità sviluppatore** abilitata sull'orologio

---

## 🔧 Configurazione Iniziale

### 1. Abilita Modalità Sviluppatore sull'Orologio

1. Apri **Impostazioni** sull'orologio
2. Vai su **Informazioni** → **Versione software**
3. Tocca 7 volte su **Versione software** per abilitare le opzioni sviluppatore
4. Torna indietro e vai su **Impostazioni** → **Opzioni sviluppatore**
5. Attiva **Debug USB**
6. Attiva **Debug via Wi-Fi** (se disponibile)

### 2. Installa ADB sul PC

Se non hai già ADB installato:

```powershell
# Scarica SDK Platform Tools da:
# https://developer.android.com/studio/releases/platform-tools

# Estrai e aggiungi la cartella al PATH di Windows
```

Verifica l'installazione:
```powershell
adb version
```

---

## 📲 Connessione Wireless

### Metodo 1: Connessione Diretta Wi-Fi (Consigliato)

1. **Sull'orologio:**
   - Vai su **Impostazioni** → **Opzioni sviluppatore**
   - Attiva **Debug via Wi-Fi**
   - Prendi nota dell'**indirizzo IP** e della **porta** (es: `192.168.1.100:5555`)

2. **Sul PC (PowerShell):**
```powershell
# Connettiti all'orologio
adb connect 192.168.1.100:5555

# Verifica la connessione
adb devices
```

### Metodo 2: Tramite Telefono Android (Ponte USB-WiFi)

Se il tuo orologio è connesso a un telefono Android:

1. **Collega il telefono al PC via USB**
2. **Abilita debug USB** sul telefono
3. **Sul PC:**
```powershell
# Inoltra la porta ADB
adb forward tcp:5555 localabstract:adb-hub

# Connettiti in wireless
adb connect localhost:5555

# Verifica
adb devices
```

---

## 🔨 Build e Installazione

### Opzione A: Build + Install Automatico (Consigliato)

```powershell
# Dalla cartella del progetto
cd c:\Users\zeusp\Documents\PointsVolleyHubWearOS\PointsVolleyHub

# Build e installa in un comando
.\gradlew.bat installDebug --no-daemon
```

### Opzione B: Build Manuale + Install con ADB

```powershell
# 1. Build del progetto
.\gradlew.bat assembleDebug --no-daemon

# 2. Installa l'APK
adb install -r app\build\outputs\apk\debug\app-debug.apk

# 3. Verifica installazione
adb shell pm list packages | findstr volleyhub
```

### Opzione C: Build Release (per distribuzione)

```powershell
# Build release (richiede keystore configurato)
.\gradlew.bat assembleRelease --no-daemon

# L'APK sarà in:
# app\build\outputs\apk\release\app-release.apk
```

---

## 🚀 Comandi Utili ADB

```powershell
# Verifica dispositivi connessi
adb devices

# Disconnetti dall'orologio
adb disconnect

# Riavvia l'app
adb shell am force-stop com.volleyhub.pro
adb shell am start -n com.volleyhub.pro/.MainActivity

# Cattura logcat (debug)
adb logcat -c && adb logcat | findstr "WearGestureDetector"

# Cattura screenshot
adb shell screencap -p /sdcard/screenshot.png
adb pull /sdcard/screenshot.png

# Disinstalla l'app
adb uninstall com.volleyhub.pro

# Vedi spazio di archiviazione
adb shell df /data

# Riavvia l'orologio
adb reboot
```

---

## 🐛 Risoluzione Problemi

### "No devices/emulators found"

```powershell
# Uccidi il server ADB e riavvia
adb kill-server
adb start-server
adb connect <IP_OROLOGIO>:5555
```

### "Unauthorized" nella lista dispositivi

- Sull'orologio: accetta il prompt **"Consenti debug USB?"**
- Se non appare, disattiva/riattiva il debug USB

### La connessione wireless cade

- Verifica che PC e orologio siano sulla **stessa rete Wi-Fi**
- L'indirizzo IP dell'orologio potrebbe cambiare - controlla nelle impostazioni
- Prova a impostare un **IP statico** per l'orologio nel router

### L'app non si avvia

```powershell
# Controlla i log
adb logcat -c
adb logcat | findstr "MainActivity"

# Forza stop e riavvia
adb shell am force-stop com.volleyhub.pro
adb shell am start -n com.volleyhub.pro/.MainActivity
```

### Build fallisce con errori Gradle

```powershell
# Pulisci il progetto
.\gradlew.bat clean --no-daemon

# Invalida cache di Android Studio
# File → Invalidate Caches → Invalidate and Restart
```

---

## 📱 Test dei Gesti

Dopo l'installazione:

1. **Apri l'app** PointsVolleyHub sull'orologio
2. **Accedi** con le tue credenziali Firebase
3. **Testa i gesti:**
   - **Double Pinch**: Avvicina 2 dita due volte rapidamente → Punto Team A
   - **Swipe Verticale**: Scorri su/giù → Punto Team B
4. **Verifica i log:**
```powershell
adb logcat | findstr "WearGestureDetector"
```

---

## 🔐 Sicurezza

⚠️ **Importante:**
- Disattiva il **debug USB** quando non lo usi
- Non lasciare il debug attivo in reti Wi-Fi pubbliche
- La porta 5555 dovrebbe essere accessibile solo sulla tua rete locale

---

## 📞 Supporto

Per problemi specifici della Galaxy Watch Ultra:
- [Samsung Developers - Wear OS](https://developer.samsung.com/galaxy-watch/home)
- [Android Studio - Wear OS Guide](https://developer.android.com/training/wearables)

---

**Ultimo aggiornamento:** Aprile 2026  
**Versione App:** 1.0  
**Target:** Samsung Galaxy Watch Ultra (Wear OS 5+)
