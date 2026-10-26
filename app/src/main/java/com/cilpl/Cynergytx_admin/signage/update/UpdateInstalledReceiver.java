package com.cilpl.Cynergytx_admin.signage.update;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/**
 * Brings the screen back after the app has updated itself.
 *
 * Installing over ourselves stops the process, so without this the TV would sit on the
 * launcher until somebody opened the app by hand.
 *
 * Note: Android 10 and later restrict starting an activity from the background, so this
 * cannot be relied on alone for a fully unattended fleet -- that needs the app to be the
 * device's home/launcher app, or device-owner provisioning. It costs nothing and works
 * where the platform still allows it.
 */
public class UpdateInstalledReceiver extends BroadcastReceiver {

    private static final String TAG = "AppUpdate";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !Intent.ACTION_MY_PACKAGE_REPLACED.equals(intent.getAction())) return;
        Log.e(TAG, "Update installed — restarting the app");
        try {
            Intent launch = context.getPackageManager()
                    .getLaunchIntentForPackage(context.getPackageName());
            if (launch == null) return;
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            context.startActivity(launch);
        } catch (Throwable t) {
            Log.e(TAG, "Could not restart after the update: " + t.getMessage());
        }
    }
}
