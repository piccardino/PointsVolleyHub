# 🎮 Guida ai Gesti - PointsVolleyHub Wear OS

## ⚠️ Importante: Double Pinch di Sistema Samsung

Il **Double Pinch nativo** di Samsung Galaxy Watch Ultra (pizzicare indice e pollice **nell'aria** senza toccare lo schermo) è un **gesto di sistema** che usa sensori EMG proprietari.

**Questo gesto NON è accessibile alle app di terze parti** - Samsung non fornisce API pubbliche per intercettarlo.

### Cosa fa Samsung:
- Rileva il gesto a livello di sistema operativo
- Può essere mappato solo ad azioni di sistema (torna indietro, home, ecc.)
- Usa sensori EMG (elettromiografia) non disponibili pubblicamente

### Cosa fa questa app:
Usa l'**accelerometro** per rilevare movimenti simili del polso quando fai il gesto di pizzicare.

---

## 📱 Gesti Supportati

### Squadra A (Ciano) - Metodi Disponibili

| Gesto | Come farlo | Affidabilità |
|-------|------------|--------------|
| **Touch Pinch (2 dita sullo schermo)** | Appoggia 2 dita e avvicinale 2 volte rapidamente | ⭐⭐⭐⭐⭐ Ottima |
| **Double Tap** | Due tocchi veloci al centro schermo | ⭐⭐⭐⭐⭐ Ottima |
| **Swipe Destra** | Scorri verso destra | ⭐⭐⭐⭐⭐ Ottima |
| **Swipe Giù** | Scorri verso il basso | ⭐⭐⭐⭐⭐ Ottima |
| **Air Pinch (sperimentale)** | Pizzica nell'aria (l'accelerometro rileva il micro-movimento) | ⭐⭐⭐ Media |

### Squadra B (Rosa) - Metodi Disponibili

| Gesto | Come farlo | Affidabilità |
|-------|------------|--------------|
| **Double Wrist Flick** | Muovi il polso due volte (come guardare l'orologio) | ⭐⭐⭐⭐ Buona |
| **Swipe Sinistra** | Scorri verso sinistra | ⭐⭐⭐⭐⭐ Ottima |
| **Swipe Su** | Scorri verso l'alto | ⭐⭐⭐⭐⭐ Ottima |

---

## 🎯 Come Configurare i Gesti di Sistema Samsung

Anche se l'app non può intercettare il Double Pinch di sistema, puoi usarlo per altre azioni:

1. Sul telefono, apri **Galaxy Wearable**
2. Vai su **Impostazioni dell'orologio** → **Pulsanti e gesti**
3. Tocca **Gesti** → **Doppio pizzico**
4. Scegli un'azione di sistema (es: "Torna indietro", "Home")

**Nota:** Queste azioni non controllano l'app direttamente.

---

## 🔧 Configurazione Sensibilità

### Nel codice (`WearGestureDetector.java`):

```java
// Cooldown tra i punti
private static final long POINT_COOLDOWN = 5000; // 5 secondi

// Soglia per wrist flick (Squadra B)
private static final float SHAKE_THRESHOLD = 12.0f;

// Soglia per air pinch (Squadra A - sperimentale)
private static final float PINCH_TWITCH_THRESHOLD = 8.0f;
```

### Regolare la sensibilità:

| Valore | Effetto |
|--------|---------|
| `SHAKE_THRESHOLD` più basso (es: 8.0) | Wrist flick più sensibile |
| `SHAKE_THRESHOLD` più alto (es: 15.0) | Wrist flick meno sensibile |
| `PINCH_TWITCH_THRESHOLD` più basso (es: 5.0) | Air pinch più sensibile |
| `PINCH_TWITCH_THRESHOLD` più alto (es: 10.0) | Air pinch meno sensibile |

---

## 🧪 Testare l'Air Pinch (Sperimentale)

L'air pinch prova a rilevare il micro-movimento del polso quando pizzichi le dita:

1. Tieni il polso fermo
2. Pizzica indice e pollice **nell'aria** (senza toccare lo schermo)
3. Ripeti il pizzico entro 1.5 secondi
4. Controlla i log:

```powershell
adb logcat | findstr "WearGestureDetector"
```

Dovresti vedere:
```
D/WearGestureDetector: Air pinch detected (1/2). Waiting for second pinch...
D/WearGestureDetector: DOUBLE AIR PINCH detected - Team A point!
```

**Nota:** L'affidabilità dipende da quanto pronunciato è il movimento del polso.

---

## � Consigli per l'Uso

### Massima Affidabilità (Consigliato)
- Usa **Touch Pinch** (2 dita sullo schermo) per Squadra A
- Usa **Double Wrist Flick** per Squadra B
- I gesti touch hanno ~95-100% di accuratezza

### Se vuoi provare l'Air Pinch
- Esegui un movimento deciso ma non eccessivo
- Il polso deve avere un piccolo "scatto" quando pizzichi
- Potrebbe richiedere pratica per trovare il movimento giusto
- L'accuratezza è ~60-80%

---

## 🐛 Debug

```powershell
# Connettiti all'orologio
adb connect 192.168.1.XXX:5555

# Vedi tutti i log dei gesti
adb logcat | findstr "WearGestureDetector"

# Vedi solo i punti assegnati
adb logcat | findstr "Point awarded"

# Vedi quando il cooldown blocca i punti
adb logcat | findstr "cooldown active"
```

---

## � Riepilogo Comandi Build

```powershell
# Pulisci e compila
cd C:\Users\zeusp\Documents\PointsVolleyHubWearOS\PointsVolleyHub
.\gradlew.bat clean assembleDebug --no-daemon

# Installa
adb connect 192.168.1.XXX:5555
adb uninstall com.volleyhub.pro
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

---

## � Future Implementazioni

Se Samsung rilascerà API pubbliche per i gesti EMG in futuro, potremo implementare:

- ✅ Double Pinch nativo (EMG)
- ✅ Wrist Rotation (rotazione polso)
- ✅ Hand Open/Close (apertura/chiusura mano)

Per ora, usiamo quello che le API pubbliche Android mettono a disposizione.

---

**Ultimo aggiornamento:** Aprile 2026  
**Versione App:** 1.1  
**Testato su:** Samsung Galaxy Watch Ultra (Wear OS 5, One UI 8 Watch)
