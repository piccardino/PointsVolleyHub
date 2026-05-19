@echo off
setlocal EnableExtensions

set "SCRIPT_DIR=%~dp0"
set "APP_ID=com.volleyhub.pro"
set "APK_PATH=%SCRIPT_DIR%app\build\outputs\apk\debug\app-debug.apk"
set "GRADLEW=%SCRIPT_DIR%gradlew.bat"
set "ADB="
set "DEVICE_FLAG="
set "DEFAULT_DEVICE=10.211.83.249:35325"

if not "%~1"=="" (
    set "DEVICE_FLAG=-s %~1"
)

if "%~1"=="" (
    set "DEVICE_FLAG=-s %DEFAULT_DEVICE%"
)

if defined ANDROID_HOME if exist "%ANDROID_HOME%\platform-tools\adb.exe" set "ADB=%ANDROID_HOME%\platform-tools\adb.exe"
if not defined ADB if defined ANDROID_SDK_ROOT if exist "%ANDROID_SDK_ROOT%\platform-tools\adb.exe" set "ADB=%ANDROID_SDK_ROOT%\platform-tools\adb.exe"
if not defined ADB if exist "%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe" set "ADB=%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe"
if not defined ADB if exist "%USERPROFILE%\Android\Sdk\platform-tools\adb.exe" set "ADB=%USERPROFILE%\Android\Sdk\platform-tools\adb.exe"
if not defined ADB for /f "delims=" %%I in ('where adb 2^>nul') do if not defined ADB set "ADB=%%I"

if not defined ADB (
    echo [ERRORE] adb non trovato. Installa Android Platform Tools o aggiungi adb al PATH.
    exit /b 1
)

echo === Build debug ===
call "%GRADLEW%" assembleDebug --console=plain
if errorlevel 1 (
    echo [ERRORE] Build fallita.
    exit /b 1
)

if not exist "%APK_PATH%" (
    echo [ERRORE] APK non trovato: "%APK_PATH%"
    exit /b 1
)

echo.
echo === Dispositivi ADB ===
"%ADB%" devices

echo.
echo === Disinstallazione app precedente ===
"%ADB%" %DEVICE_FLAG% uninstall %APP_ID%
if errorlevel 1 (
    echo [INFO] L'app potrebbe non essere installata. Continuo comunque.
)

echo.
echo === Installazione nuova build ===
"%ADB%" %DEVICE_FLAG% install "%APK_PATH%"
if errorlevel 1 (
    echo [ERRORE] Installazione fallita.
    exit /b 1
)

echo.
echo === Avvio app ===
"%ADB%" %DEVICE_FLAG% shell monkey -p %APP_ID% -c android.intent.category.LAUNCHER 1 >nul 2>nul

echo.
echo [OK] Reinstall completato.
echo APK installato: "%APK_PATH%"

endlocal
exit /b 0
