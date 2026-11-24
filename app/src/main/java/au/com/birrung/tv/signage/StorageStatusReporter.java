package au.com.birrung.tv.signage;

import android.content.Context;
import android.util.Log;

import com.android.volley.Request;
import com.android.volley.VolleyError;
import au.com.birrung.tv.NetworkCalling.NetworkServiceCall;
import au.com.birrung.tv.NetworkCalling.OnServiceCallCompleteListener;
import au.com.birrung.tv.NetworkCalling.ServerURL;
import au.com.birrung.tv.signage.sync.SyncReport;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashMap;

/**
 * Reports per-screen storage + sync health to the backend so the operator dashboard
 * can warn about shortfalls (see BACKEND_REQUIREMENTS.md §8/§9).
 *
 * NOTE: This posts to the existing screen-status endpoint with extra fields. The exact
 * endpoint/field names should be finalised with the backend team; adjust here once agreed.
 */
public class StorageStatusReporter {

    private static final String TAG = "StorageStatus";

    public static void report(Context ctx, String sessionId, SyncReport r) {
        Log.e(TAG, "screen=" + sessionId + " " + r.summary());

        try {
            String url = ServerURL.GetScreenStatus(); // reuse existing status endpoint for now
            HashMap<String, String> params = new HashMap<>();
            params.put("userid", sessionId);
            params.put("status", "1");
            params.put("storageUsedMB", String.valueOf(r.usageBytes / (1024 * 1024)));
            params.put("storageFreeMB", String.valueOf(r.budgetBytes / (1024 * 1024)));
            params.put("filesDownloaded", String.valueOf(r.downloaded));
            params.put("filesPresent", String.valueOf(r.alreadyPresent));
            params.put("shortfall", String.valueOf(r.shortfall.size()));

            NetworkServiceCall call = new NetworkServiceCall(ctx, false);
            call.setOnServiceCallCompleteListener(new OnServiceCallCompleteListener() {
                @Override public void onJSONObjectResponse(JSONObject o) { }
                @Override public void onJSONArrayResponse(JSONArray a) { }
                @Override public void onErrorResponse(VolleyError e) { }
            });
            call.makeJSONObjectPostStringRequest(url, params, Request.Priority.LOW);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
