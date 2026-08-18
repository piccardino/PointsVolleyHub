# PointsVolleyHub Wear OS

App Wear OS per la gestione del punteggio di partite di pallavolo con sincronizzazione Firebase in tempo reale.

## Funzionalità

- **Login Firebase**: Autenticazione sicura con email e password
- **Punteggio Team A/B**: Aggiungi o rimuovi punti per entrambi i team
- **Timer**: Avvia, ferma e resetta il timer della partita
- **Set**: Visualizza i set vinti da ciascun team
- **Sincronizzazione Realtime**: I dati si sincronizzano istantaneamente con Firebase

## Setup Firebase

### 1. Crea un progetto Firebase
1. Vai su [Firebase Console](https://console.firebase.google.com/)
2. Crea un nuovo progetto
3. Abilita **Authentication** → **Email/Password**
4. Abilita **Realtime Database**

### 2. Configura l'app Android
1. Nella console Firebase, aggiungi un'app Android
2. Package name: `com.example.pointsvolleyhub`
3. Scarica il file `google-services.json`
4. Sostituisci il file `app/google-services.json` con quello scaricato

### 3. Crea un utente per il login
1. Nella console Firebase, vai su Authentication → Users
2. Aggiungi un nuovo utente con email e password
3. Aggiorna le credenziali di default in `LoginActivity.java` (righe 49-50)

## Struttura Database Firebase

```
users/
  {userId}/
    match/
      scoreA: 0
      scoreB: 0
      setsWonA: 0
      setsWonB: 0
      elapsedTime: 0
      isTimerRunning: false
      lastUpdateTime: timestamp
      teamAName: "Team A"
      teamBName: "Team B"
```

## Build e Deploy

```bash
# Build del progetto
./gradlew assembleDebug

# Installa su Wear OS (via ADB)
adb install app/build/outputs/apk/debug/app-debug.apk

# Installa su dispositivo Wear OS connesso
adb -d install app/build/outputs/apk/debug/app-debug.apk
```

## Nuove Funzionalità

### 🔄 Conferma al Reset
- Premendo il pulsante **RESET**, viene mostrato un popup di conferma (*"Resetta Partita? Vuoi davvero azzerare punteggio, set e timer?"*) per prevenire azzeramenti accidentali durante il gioco.

### 🚀 OTA Auto-Updater (Senza ADB via GitHub Releases)
- **Controllo automatico all'avvio**: l'app verifica se è presente una nuova versione su GitHub.
- **Controllo manuale**: nella schermata dei giocatori/opzioni (swipe laterale) è presente il pulsante **"AGGIORNAMENTO OTA"**.
- **Installazione 1-Tap**: scarica l'APK direttamente dall'orologio e lancia il Package Installer nativo di Wear OS senza dover collegare cavi o usare comandi ADB.
- **Rilascio con GitHub Actions**: quando crei un tag (es. `git tag v1.1 && git push origin v1.1`), GitHub compila e pubblica automaticamente la release con l'APK allegato.

## Utilizzo

1. **Login**: Inserisci email e password del tuo account Firebase
2. **Aggiungi Punto**: Premi `+ A` o `+ B` per aggiungere un punto
3. **Rimuovi Punto**: Premi `- A` o `- B` per rimuovere un punto
4. **Timer**: Premi `START` per avviare, `STOP` per fermare
5. **Reset**: Premi `RESET` (ti verrà chiesta conferma prima di azzerare)
6. **Logout**: Premi `EXIT` per uscire
7. **Aggiornamento OTA**: Fai swipe a sinistra verso la pagina giocatori e tocca **"AGGIORNAMENTO OTA"**

## Note

- Lo schermo rimane sempre acceso durante l'uso
- I dati si sincronizzano in tempo reale tra tutti i dispositivi connessi
- Il timer continua a funzionare anche in background grazie a Firebase
