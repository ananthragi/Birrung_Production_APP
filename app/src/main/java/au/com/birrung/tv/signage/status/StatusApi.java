package au.com.birrung.tv.signage.status;

import android.util.Log;

import org.json.JSONObject;

import java.util.concurrent.TimeUnit;

import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * The one place that knows the status endpoint and how to read its reply.
 *
 * The backend may replace this with a dedicated sign-in status sync API later; swapping the
 * URL and the request body here is the whole change -- the queue never sees either.
 */
public class StatusApi implements StatusTransport {

    private static final String TAG = "StatusApi";

    /** Single source of truth for the endpoint. */
    // Single slash here, unlike ServerURL -- BASE_URL already ends with one.
    private static final String ENDPOINT =
            au.com.birrung.tv.BuildConfig.BASE_URL + "loginUpdates";

    private final OkHttpClient client;

    public StatusApi() {
        this(new OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .build());
    }

    StatusApi(OkHttpClient client) { this.client = client; }

    @Override
    public Result send(String sessionId, int status) {
        if (sessionId == null || sessionId.trim().isEmpty()) {
            return Result.fail("no sessionId on the record");
        }
        Request request = new Request.Builder()
                .url(ENDPOINT)
                .post(new FormBody.Builder()
                        .add("sessionId", sessionId.trim())
                        .add("status", String.valueOf(status))
                        .build())
                .build();

        try (Response response = client.newCall(request).execute()) {
            ResponseBody rb = response.body();
            String body = rb == null ? "" : rb.string();
            Log.e(TAG, "sessionId=" + sessionId + " status=" + status
                    + " -> HTTP " + response.code() + " body=" + body);

            // HTTP 200 alone proves nothing here: the failure case also returns 200, and it
            // arrives as text/html even though the body is JSON. The body is the only truth.
            if (!isSuccessBody(body)) {
                return Result.fail("server rejected it: " + shorten(body));
            }
            return Result.ok();
        } catch (Throwable t) {
            return Result.fail(t.getClass().getSimpleName() + ": " + t.getMessage());
        }
    }

    /**
     * Success only when the body is JSON carrying a "success" key AND "status" == 1.
     * An "error" key, status 0, or anything that will not parse counts as a failure, so it
     * stays queued and is retried rather than being quietly dropped.
     *
     * Note "status" here is the server's success flag, not the login/logout value we sent.
     */
    static boolean isSuccessBody(String body) {
        if (body == null) return false;
        String trimmed = body.trim();
        if (trimmed.isEmpty()) return false;
        // The failure response is served as HTML; find the JSON object inside whatever arrives.
        int start = trimmed.indexOf('{');
        int end = trimmed.lastIndexOf('}');
        if (start < 0 || end <= start) return false;
        try {
            JSONObject o = new JSONObject(trimmed.substring(start, end + 1));
            if (o.has("error")) return false;
            if (!o.has("success")) return false;
            return o.optInt("status", 0) == 1;
        } catch (Throwable t) {
            return false;   // unparseable -> treat as a failure, never as success
        }
    }

    private static String shorten(String s) {
        if (s == null) return "";
        String one = s.replaceAll("\\s+", " ").trim();
        return one.length() <= 180 ? one : one.substring(0, 180) + "...";
    }
}
