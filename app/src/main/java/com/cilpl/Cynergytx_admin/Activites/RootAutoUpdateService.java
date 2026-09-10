package com.cilpl.Cynergytx_admin.Activites;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class RootAutoUpdateService extends Service {

    private static final String VERSION_URL = "https://mysign.au/otaUpdate";
    private static final String APK_PATH = "https://mysign.au/uploads/ota/ota.apk";

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        new Thread(this::checkUpdate).start();
        return START_STICKY;
    }

    private void checkUpdate() {
        try {
            URL url = new URL(VERSION_URL);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(8000);

            InputStream in = conn.getInputStream();
            String json = convertStreamToString(in);
            JSONObject obj = new JSONObject(json);

            int latest = obj.getInt("versionCode");
            String apkUrl = obj.getString("apkUrl");

            int current = getPackageManager().getPackageInfo(getPackageName(), 0).versionCode;

            if (latest > current) {
                downloadApk(apkUrl);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void downloadApk(String apkUrl) {
        try {
            File apk = new File(APK_PATH);

            BufferedInputStream in =
                    new BufferedInputStream(new URL(apkUrl).openStream());

            FileOutputStream out = new FileOutputStream(apk);

            byte[] data = new byte[1024];
            int count;
            while ((count = in.read(data)) != -1) {
                out.write(data, 0, count);
            }

            out.flush();
            out.close();
            in.close();

            installSilentlyRoot(apk);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void installSilentlyRoot(File apk) {
        try {
            // Root command
            String command = "pm install -r -d " + apk.getAbsolutePath();

            Process su = Runtime.getRuntime().exec("su");
            DataOutputStream os = new DataOutputStream(su.getOutputStream());
            os.writeBytes(command + "\n");
            os.writeBytes("exit\n");
            os.flush();

            su.waitFor();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private String convertStreamToString(InputStream is) {
        java.util.Scanner s = new java.util.Scanner(is).useDelimiter("\\A");
        return s.hasNext() ? s.next() : "";
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }

}
