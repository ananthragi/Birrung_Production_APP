package com.cilpl.Cynergytx_admin.signage.update;

import android.app.Activity;
import java.lang.ref.WeakReference;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.util.Log;

import androidx.core.content.FileProvider;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.security.MessageDigest;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * Keeps the app up to date from the content server.
 *
 * The login and refresh responses carry "version" and "apkPath". When the advertised version
 * differs from the one running, the APK is fetched quietly in the background and the system
 * installer is opened once it is on disk and verified. Playback is never interrupted by the
 * download itself -- only the final install confirmation is visible, and that is unavoidable:
 * a normal app cannot install silently on Android without root or device-owner privileges.
 *
 * Safety: the downloaded APK must declare this same package AND be signed with the same
 * certificate as the running app before it is offered for install. Without that check,
 * anything able to change the apkPath or intercept the download could put a different
 * application on every screen.
 */
public final class AppUpdateManager {

    private static final String TAG = "AppUpdate";

    private static final OkHttpClient CLIENT = new OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build();

    /** One update at a time; check() is called from login AND from every refresh. */
    private static volatile boolean busy = false;
    /** Avoid re-opening the installer repeatedly for a version the user has already been shown. */
    private static volatile String promptedVersion = "";
    /** The screen that triggered the check, so we can come back to it after a settings trip. */
    private static volatile WeakReference<Activity> host = new WeakReference<>(null);
    /** A verified APK waiting only for permission to install. */
    private static volatile File awaitingPermission = null;
    private static volatile boolean watchingForPermission = false;

    private AppUpdateManager() { }

    /**
     * Entry point. Safe to call often and from any thread; returns immediately.
     *
     * @param apiVersion the "version" field from the API (e.g. "1.0.1")
     * @param apkPath    the "apkPath" field from the API
     */
    public static void check(final Context context, final String apiVersion, final String apkPath) {
        if (context == null) return;
        final Context ctx = context.getApplicationContext();

        if (context instanceof Activity) host = new WeakReference<>((Activity) context);

        final String want = clean(apiVersion);
        final String url = clean(apkPath);

        // Nothing advertised -> nothing to do. The backend sends these empty when a screen is
        // not being updated, so this is the normal case, not an error.
        if (want.isEmpty() || url.isEmpty()) return;
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            Log.e(TAG, "Ignoring an apkPath that is not a URL: " + url);
            return;
        }

        final String running = installedVersionName(ctx);
        if (want.equals(running)) return;                      // already on the advertised build

        if (busy) return;
        if (want.equals(promptedVersion)) return;              // already offered this one
        busy = true;

        Log.e(TAG, "Update available: running=" + running + " advertised=" + want + " url=" + url);

        Thread t = new Thread(() -> {
            try {
                File apk = fetch(ctx, want, url);
                if (apk == null) return;
                if (!isTrustworthy(ctx, apk)) {
                    Log.e(TAG, "Refusing update: it is not this app, or not signed by us");
                    // Do not keep a file we will never install.
                    if (!apk.delete()) Log.e(TAG, "Could not delete the rejected APK");
                    return;
                }
                // Marked only if the installer actually opens. Marking it before meant that a
                // device without the install permission recorded the version as "offered",
                // then never retried it -- the update stalled until the app was restarted.
                if (launchInstaller(ctx, apk)) promptedVersion = want;
            } catch (Throwable e) {
                Log.e(TAG, "Update failed: " + e.getMessage());
            } finally {
                busy = false;
            }
        }, "app-update");
        t.setDaemon(true);
        t.start();
    }

    // ------------------------------------------------------------------ download

    /** Downloads (or resumes) the APK into the app's private cache. Returns null on failure. */
    private static File fetch(Context ctx, String version, String url) {
        File dir = new File(ctx.getCacheDir(), "updates");
        if (!dir.exists() && !dir.mkdirs()) {
            Log.e(TAG, "Cannot create the update directory");
            return null;
        }
        // Old versions left behind by an interrupted update are of no use.
        File[] stale = dir.listFiles();
        String keep = fileNameFor(version);
        if (stale != null) {
            for (File f : stale) {
                if (!f.getName().equals(keep) && !f.getName().equals(keep + ".part")) f.delete();
            }
        }

        File apk = new File(dir, keep);
        if (apk.exists() && apk.length() > 0) {
            Log.e(TAG, "Update already downloaded: " + apk.getName());
            return apk;                                   // verified by the caller
        }

        File part = new File(apk.getAbsolutePath() + ".part");
        long have = part.exists() ? part.length() : 0;

        Request.Builder rb = new Request.Builder().url(url);
        if (have > 0) rb.addHeader("Range", "bytes=" + have + "-");   // resume an interrupted try

        try (Response resp = CLIENT.newCall(rb.build()).execute()) {
            boolean append = have > 0 && resp.code() == 206;
            if (!resp.isSuccessful() && resp.code() != 206) {
                Log.e(TAG, "Download rejected: HTTP " + resp.code());
                return null;
            }
            ResponseBody body = resp.body();
            if (body == null) return null;

            try (InputStream in = body.byteStream();
                 RandomAccessFile out = new RandomAccessFile(part, "rw")) {
                if (append) out.seek(have); else out.setLength(0);
                byte[] buf = new byte[64 * 1024];
                int r;
                while ((r = in.read(buf)) != -1) out.write(buf, 0, r);
            }
        } catch (IOException e) {
            Log.e(TAG, "Download failed, will retry on the next check: " + e.getMessage());
            return null;                                   // keep the .part so the next try resumes
        }

        if (part.length() == 0) return null;
        if (apk.exists() && !apk.delete()) return null;
        if (!part.renameTo(apk)) {
            Log.e(TAG, "Could not move the downloaded update into place");
            return null;
        }
        Log.e(TAG, "Downloaded update: " + apk.getName() + " (" + apk.length() + " bytes)");
        return apk;
    }

    // ------------------------------------------------------------------ verification

    /** True only when the APK is this same app, signed with the same certificate. */
    private static boolean isTrustworthy(Context ctx, File apk) {
        try {
            PackageManager pm = ctx.getPackageManager();
            int flags = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
                    ? PackageManager.GET_SIGNING_CERTIFICATES
                    : PackageManager.GET_SIGNATURES;

            PackageInfo downloaded = pm.getPackageArchiveInfo(apk.getAbsolutePath(), flags);
            if (downloaded == null) {
                Log.e(TAG, "Not a readable APK");
                return false;
            }
            if (!ctx.getPackageName().equals(downloaded.packageName)) {
                Log.e(TAG, "APK is a different application: " + downloaded.packageName);
                return false;
            }
            PackageInfo installed = pm.getPackageInfo(ctx.getPackageName(), flags);

            Set<String> ours = fingerprints(installed);
            Set<String> theirs = fingerprints(downloaded);
            if (ours.isEmpty() || theirs.isEmpty()) {
                Log.e(TAG, "Could not read signing certificates");
                return false;
            }
            if (!ours.equals(theirs)) {
                Log.e(TAG, "Signing certificate does not match the installed app");
                return false;
            }
            Log.e(TAG, "Update verified: " + downloaded.packageName + " v" + downloaded.versionName);
            return true;
        } catch (Throwable t) {
            Log.e(TAG, "Verification failed: " + t.getMessage());
            return false;
        }
    }

    private static Set<String> fingerprints(PackageInfo info) {
        Set<String> out = new HashSet<>();
        Signature[] sigs = null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && info.signingInfo != null) {
            // Always the CURRENT signers, for both the installed app and the downloaded file.
            // Using the rotation history for one and the current signers for the other would
            // make the two sets disagree and silently block every update.
            sigs = info.signingInfo.getApkContentsSigners();
        } else {
            sigs = info.signatures;
        }
        if (sigs == null) return out;
        for (Signature s : sigs) {
            try {
                MessageDigest md = MessageDigest.getInstance("SHA-256");
                byte[] d = md.digest(s.toByteArray());
                StringBuilder sb = new StringBuilder();
                for (byte b : d) sb.append(String.format("%02x", b));
                out.add(sb.toString());
            } catch (Exception ignored) { }
        }
        return out;
    }

    // ------------------------------------------------------------------ install

    /** @return true when the system installer was actually opened. */
    private static boolean launchInstaller(Context ctx, File apk) {
        if (!canInstall(ctx)) {
            // One-time setting. Remember the APK, send the user there, and finish the job
            // ourselves the moment the permission appears -- see watchForPermission().
            awaitingPermission = apk;
            Log.e(TAG, "\"Install unknown apps\" is not granted — opening that setting");
            try {
                Intent s = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:" + ctx.getPackageName()));
                Activity a = host.get();
                if (a != null && !a.isFinishing()) {
                    // Deliberately no NEW_TASK: keeping the settings screen in this app's task
                    // is what lets us bring ourselves back to the front afterwards.
                    a.startActivity(s);
                } else {
                    s.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    ctx.startActivity(s);
                }
                watchForPermission(ctx);
            } catch (Throwable t) {
                Log.e(TAG, "Could not open the unknown-sources setting: " + t.getMessage());
            }
            return false;
        }

        try {
            Uri uri = FileProvider.getUriForFile(ctx, ctx.getPackageName() + ".provider", apk);
            Intent i = new Intent(Intent.ACTION_VIEW);
            i.setDataAndType(uri, "application/vnd.android.package-archive");
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
            Activity a = host.get();
            if (a != null && !a.isFinishing()) a.startActivity(i); else ctx.startActivity(i);
            awaitingPermission = null;
            Log.e(TAG, "Installer opened for " + apk.getName());
            return true;
        } catch (Throwable t) {
            Log.e(TAG, "Could not open the installer: " + t.getMessage());
            return false;
        }
    }

    private static boolean canInstall(Context ctx) {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.O
                || ctx.getPackageManager().canRequestPackageInstalls();
    }

    /**
     * Watches for the user granting "install unknown apps" while they are in that settings
     * screen, then pulls this app back to the front and carries on with the install, so the
     * update finishes without them having to find their own way back.
     */
    private static void watchForPermission(final Context ctx) {
        if (watchingForPermission) return;
        watchingForPermission = true;
        Thread t = new Thread(() -> {
            try {
                // Give up after a couple of minutes rather than polling forever.
                for (int i = 0; i < 120; i++) {
                    Thread.sleep(1000);
                    File apk = awaitingPermission;
                    if (apk == null) return;                 // installed or abandoned elsewhere
                    if (!canInstall(ctx)) continue;

                    Activity a = host.get();
                    if (a != null && !a.isFinishing()) {
                        // Bring our screen back in front of the settings screen first.
                        Intent back = new Intent(ctx, a.getClass());
                        back.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                                | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                        a.startActivity(back);
                        Thread.sleep(600);                    // let it come forward
                    }
                    Log.e(TAG, "Permission granted — resuming the install");
                    if (launchInstaller(ctx, apk)) promptedVersion = "";
                    return;
                }
                Log.e(TAG, "Gave up waiting for the install permission");
            } catch (Throwable ignored) {
            } finally {
                watchingForPermission = false;
            }
        }, "update-permission-watch");
        t.setDaemon(true);
        t.start();
    }

    /**
     * Called when a screen comes back to the foreground. Covers the case where the user
     * granted the permission and returned by pressing Back rather than being brought back.
     */
    public static void resumePendingInstall(Context context) {
        if (context == null) return;
        if (context instanceof Activity) host = new WeakReference<>((Activity) context);
        final File apk = awaitingPermission;
        if (apk == null || !apk.exists()) return;
        if (!canInstall(context)) return;
        Log.e(TAG, "Back in the app with permission granted — resuming the install");
        launchInstaller(context, apk);
    }

    private static String clean(String s) {
        if (s == null) return "";
        String t = s.trim();
        return ("null".equalsIgnoreCase(t)) ? "" : t;
    }

    // ------------------------------------------------------------------ helpers

    /** The versionName this build was compiled with, e.g. "1.0.1". Empty if it cannot be read. */
    public static String installedVersionName(Context ctx) {
        try {
            return String.valueOf(
                    ctx.getPackageManager().getPackageInfo(ctx.getPackageName(), 0).versionName);
        } catch (Throwable t) {
            return "";
        }
    }

    private static String fileNameFor(String version) {
        return "update-" + version.replaceAll("[^a-zA-Z0-9._-]", "_") + ".apk";
    }
}
