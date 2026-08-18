package com.volleyhub.pro;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class UpdateManager {
    private static final String TAG = "UpdateManager";
    private static final String GITHUB_REPO = "piccardino/PointsVolleyHub";
    private static final String API_URL = "https://api.github.com/repos/" + GITHUB_REPO + "/releases/latest";

    private final Activity activity;
    private final Handler mainHandler;
    private final ExecutorService executor;

    public UpdateManager(Activity activity) {
        this.activity = activity;
        this.mainHandler = new Handler(Looper.getMainLooper());
        this.executor = Executors.newSingleThreadExecutor();
    }

    public void checkForUpdates(boolean silent) {
        if (!silent) {
            Toast.makeText(activity, "Controllo aggiornamenti...", Toast.LENGTH_SHORT).show();
        }

        executor.execute(() -> {
            try {
                URL url = new URL(API_URL);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("User-Agent", "PointsVolleyHub-WearOS");
                conn.setRequestProperty("Accept", "application/vnd.github.v3+json");
                conn.setConnectTimeout(8000);
                conn.setReadTimeout(8000);

                int responseCode = conn.getResponseCode();
                if (responseCode != HttpURLConnection.HTTP_OK) {
                    Log.w(TAG, "GitHub API returned code: " + responseCode);
                    if (!silent) {
                        mainHandler.post(() -> Toast.makeText(activity, "Nessun rilascio trovato su GitHub", Toast.LENGTH_SHORT).show());
                    }
                    return;
                }

                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                reader.close();
                conn.disconnect();

                JSONObject releaseJson = new JSONObject(response.toString());
                String tagName = releaseJson.optString("tag_name", "");
                String releaseName = releaseJson.optString("name", tagName);
                String releaseBody = releaseJson.optString("body", "");

                // Find APK download URL from assets
                String apkUrl = null;
                JSONArray assets = releaseJson.optJSONArray("assets");
                if (assets != null) {
                    for (int i = 0; i < assets.length(); i++) {
                        JSONObject asset = assets.getJSONObject(i);
                        String assetName = asset.optString("name", "");
                        if (assetName.toLowerCase().endsWith(".apk")) {
                            apkUrl = asset.optString("browser_download_url", null);
                            break;
                        }
                    }
                }

                if (apkUrl == null || apkUrl.isEmpty()) {
                    Log.w(TAG, "No APK asset found in release: " + tagName);
                    if (!silent) {
                        mainHandler.post(() -> Toast.makeText(activity, "Nessun file APK trovato nella release", Toast.LENGTH_SHORT).show());
                    }
                    return;
                }

                String currentVersion = getCurrentVersionName();
                boolean isNewer = isNewerVersion(tagName, currentVersion);

                final String finalApkUrl = apkUrl;
                final String displayVersion = tagName.isEmpty() ? releaseName : tagName;

                mainHandler.post(() -> {
                    if (activity == null || activity.isFinishing()) return;

                    if (isNewer) {
                        showUpdateAvailableDialog(displayVersion, releaseBody, finalApkUrl);
                    } else {
                        if (!silent) {
                            Toast.makeText(activity, "L'app è aggiornata (v" + currentVersion + ")", Toast.LENGTH_SHORT).show();
                        }
                    }
                });

            } catch (Exception e) {
                Log.e(TAG, "Error checking for updates", e);
                if (!silent) {
                    mainHandler.post(() -> Toast.makeText(activity, "Errore verifica: " + e.getMessage(), Toast.LENGTH_SHORT).show());
                }
            }
        });
    }

    private void showUpdateAvailableDialog(String newVersion, String releaseNotes, String downloadUrl) {
        String message = "Versione disponibile: " + newVersion + "\nVersione attuale: v" + getCurrentVersionName();
        if (releaseNotes != null && !releaseNotes.trim().isEmpty()) {
            message += "\n\n" + releaseNotes.trim();
        }

        new AlertDialog.Builder(activity)
                .setTitle("Aggiornamento OTA")
                .setMessage(message)
                .setPositiveButton("Aggiorna", (dialog, which) -> startApkDownload(downloadUrl))
                .setNegativeButton("Più tardi", null)
                .show();
    }

    private void startApkDownload(String downloadUrl) {
        ProgressDialog progressDialog = new ProgressDialog(activity);
        progressDialog.setTitle("Download OTA");
        progressDialog.setMessage("Scaricamento aggiornamento...");
        progressDialog.setIndeterminate(false);
        progressDialog.setMax(100);
        progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        progressDialog.setCancelable(false);
        progressDialog.show();

        executor.execute(() -> {
            File apkFile = new File(activity.getCacheDir(), "PointsVolleyHub-update.apk");
            try {
                downloadFileWithRedirects(downloadUrl, apkFile, progressDialog);

                mainHandler.post(() -> {
                    if (progressDialog.isShowing()) {
                        progressDialog.dismiss();
                    }
                    installApk(apkFile);
                });

            } catch (Exception e) {
                Log.e(TAG, "Download error", e);
                mainHandler.post(() -> {
                    if (progressDialog.isShowing()) {
                        progressDialog.dismiss();
                    }
                    Toast.makeText(activity, "Errore download: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void downloadFileWithRedirects(String initialUrl, File destinationFile, ProgressDialog progressDialog) throws Exception {
        String currentUrl = initialUrl;
        HttpURLConnection conn = null;
        int redirectCount = 0;
        final int MAX_REDIRECTS = 6;

        while (redirectCount < MAX_REDIRECTS) {
            URL url = new URL(currentUrl);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestProperty("User-Agent", "PointsVolleyHub-WearOS");
            conn.setInstanceFollowRedirects(true);
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(20000);

            int status = conn.getResponseCode();
            if (status == HttpURLConnection.HTTP_MOVED_TEMP ||
                status == HttpURLConnection.HTTP_MOVED_PERM ||
                status == HttpURLConnection.HTTP_SEE_OTHER ||
                status == 307 || status == 308) {
                
                String newUrl = conn.getHeaderField("Location");
                conn.disconnect();
                if (newUrl == null) {
                    throw new Exception("Redirect senza header Location");
                }
                currentUrl = newUrl;
                redirectCount++;
            } else if (status == HttpURLConnection.HTTP_OK) {
                break;
            } else {
                throw new Exception("HTTP errore: " + status);
            }
        }

        int fileLength = conn.getContentLength();
        InputStream input = conn.getInputStream();
        FileOutputStream output = new FileOutputStream(destinationFile, false);

        byte[] data = new byte[4096];
        long total = 0;
        int count;

        while ((count = input.read(data)) != -1) {
            total += count;
            if (fileLength > 0) {
                final int progress = (int) (total * 100 / fileLength);
                mainHandler.post(() -> progressDialog.setProgress(progress));
            }
            output.write(data, 0, count);
        }

        output.flush();
        output.close();
        input.close();
        conn.disconnect();
    }

    public void installApk(File apkFile) {
        if (apkFile == null || !apkFile.exists()) {
            Toast.makeText(activity, "File APK non trovato", Toast.LENGTH_SHORT).show();
            return;
        }

        // On Android 8.0+ check unknown sources permission
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            PackageManager pm = activity.getPackageManager();
            if (!pm.canRequestPackageInstalls()) {
                new AlertDialog.Builder(activity)
                        .setTitle("Permesso Richiesto")
                        .setMessage("Per aggiornare l'app, abilita 'Installa app sconosciute' per PointsVolleyHub.")
                        .setPositiveButton("Impostazioni", (dialog, which) -> {
                            Intent intent = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES);
                            intent.setData(Uri.parse("package:" + activity.getPackageName()));
                            activity.startActivity(intent);
                        })
                        .setNegativeButton("Annulla", null)
                        .show();
                return;
            }
        }

        try {
            Uri apkUri = FileProvider.getUriForFile(
                    activity,
                    activity.getPackageName() + ".provider",
                    apkFile
            );

            Intent installIntent = new Intent(Intent.ACTION_VIEW);
            installIntent.setDataAndType(apkUri, "application/vnd.android.package-archive");
            installIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            installIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            activity.startActivity(installIntent);
        } catch (Exception e) {
            Log.e(TAG, "Install error", e);
            Toast.makeText(activity, "Errore installazione: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private String getCurrentVersionName() {
        try {
            PackageInfo pInfo = activity.getPackageManager().getPackageInfo(activity.getPackageName(), 0);
            return pInfo.versionName != null ? pInfo.versionName : "1.0";
        } catch (Exception e) {
            return "1.0";
        }
    }

    public static boolean isNewerVersion(String remoteTag, String currentVersion) {
        if (remoteTag == null || remoteTag.trim().isEmpty()) return false;
        if (currentVersion == null || currentVersion.trim().isEmpty()) return true;

        String cleanRemote = remoteTag.trim().replaceAll("^[vV]", "");
        String cleanCurrent = currentVersion.trim().replaceAll("^[vV]", "");

        if (cleanRemote.equals(cleanCurrent)) {
            return false;
        }

        String[] remoteParts = cleanRemote.split("[.-]");
        String[] currentParts = cleanCurrent.split("[.-]");

        int length = Math.max(remoteParts.length, currentParts.length);
        for (int i = 0; i < length; i++) {
            int remoteNum = 0;
            int currentNum = 0;

            if (i < remoteParts.length) {
                try {
                    remoteNum = Integer.parseInt(remoteParts[i]);
                } catch (NumberFormatException e) {
                    remoteNum = 0;
                }
            }

            if (i < currentParts.length) {
                try {
                    currentNum = Integer.parseInt(currentParts[i]);
                } catch (NumberFormatException e) {
                    currentNum = 0;
                }
            }

            if (remoteNum > currentNum) return true;
            if (remoteNum < currentNum) return false;
        }

        return false;
    }
}
