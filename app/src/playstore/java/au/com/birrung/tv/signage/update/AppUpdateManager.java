package au.com.birrung.tv.signage.update;

import android.content.Context;

/**
 * Play Store builds do not update themselves -- Google Play does it.
 *
 * This is the playstore flavour's stand-in for the sideload engine in
 * src/sideload/java/.../AppUpdateManager.java. It keeps the same public shape so the rest of
 * the app compiles untouched, and does nothing when the API advertises a new build.
 *
 * Why it has to be this way: Play's Device and Network Abuse policy prohibits an app
 * distributed on Play from downloading and installing its own updates, and Play App Signing
 * re-signs the app with Google's key, so the sideload engine's signature check would refuse
 * our own APK in any case.
 */
public final class AppUpdateManager {

    private AppUpdateManager() { }

    /** No-op: Play delivers updates. Kept so the API listeners need no flavour-specific code. */
    public static void check(Context context, String apiVersion, String apkPath) { }

    /** No-op: nothing is ever pending, because nothing is ever downloaded. */
    public static void resumePendingInstall(Context context) { }

    /** Still real -- the login request reports the running build as "currentVersion". */
    public static String installedVersionName(Context ctx) {
        try {
            return String.valueOf(
                    ctx.getPackageManager().getPackageInfo(ctx.getPackageName(), 0).versionName);
        } catch (Throwable t) {
            return "";
        }
    }
}
