package au.com.birrung.tv.signage.net;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.io.InputStream;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * Continuously reports internet speed in Mbps to a listener (on the main thread).
 *
 *  - When NOT streaming: reports the Wi-Fi LINK speed via WifiManager. This is cheap,
 *    instant, and uses no data — perfect for the always-on top indicator during the
 *    99% of the time we play from local storage.
 *  - When streaming: actively PROBES real throughput by range-downloading a small
 *    chunk (~256 KB) of the current media URL, giving an accurate figure for the
 *    5 Mbps fallback decision.
 */
public class NetworkSpeedMonitor {

    public interface Listener {
        /** @param mbps speed in Mbps (-1 if unknown/offline); @param linkSpeed true = Wi-Fi link, false = measured throughput */
        void onSpeed(double mbps, boolean linkSpeed);
    }

    private static final String TAG = "SpeedMonitor";
    // Fast, reliable CDN endpoint for measuring the DEVICE'S internet speed (like a speed test).
    // Measuring the content server instead would report that server's per-connection limit,
    // not the real internet speed. Configurable via setDefaultSpeedUrl().
    private static final String DEFAULT_SPEED_URL = "https://speed.cloudflare.com/__down?bytes=1048576";
    // Probing costs real bandwidth AND CPU on a low-power TV box (TLS + decompress), which competes
    // with the video decoder and adds heat. Probing 512KB every 3s was ~600 MB/hour of traffic and
    // constant load. A signage screen only needs this for the low-speed indicator, so probe less
    // often and with a smaller sample (~60 MB/hour). Full disconnection is still detected INSTANTLY
    // via the ConnectivityManager onLost callback, so the indicator stays responsive.
    private static final long INTERVAL_STREAMING_MS = 5000;   // react faster only while streaming video
    private static final long INTERVAL_IDLE_MS = 15000;       // normal local playback
    // We download a larger sample and IGNORE the first part, because a short transfer
    // finishes during TCP slow-start and under-reports speed on fast links (false "low").
    private static final long WARMUP_BYTES = 64 * 1024;        // skip TCP slow-start
    private static final long PROBE_TOTAL_BYTES = 256 * 1024;  // download up to 256 KB
    private static final long MIN_MEASURED_BYTES = 64 * 1024;  // need >=64 KB measured to trust
    private static final double OK_SENTINEL = 1000.0;          // "measured fine / can't measure" → not low

    private final Context ctx;
    private final Listener listener;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(4, TimeUnit.SECONDS)   // detect "offline" quickly
            .readTimeout(4, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true) // retry on a fresh connection if a pooled one is dead
            .build();

    private Thread worker;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private volatile boolean streaming = false;
    private volatile String probeUrl = null;          // stream URL, used ONLY while streaming a video
    private volatile String defaultSpeedUrl = DEFAULT_SPEED_URL;
    private final Object sleepLock = new Object();

    public NetworkSpeedMonitor(Context ctx, Listener listener) {
        this.ctx = ctx.getApplicationContext();
        this.listener = listener;
    }

    /** Turn on active throughput probing (used while streaming a video). */
    public void setStreaming(boolean streaming) { this.streaming = streaming; }

    /** Stream URL to measure while streaming a video (null = measure the internet reference instead). */
    public void setProbeUrl(String url) { this.probeUrl = url; }

    /** Override the internet-speed reference endpoint (e.g. a backend-hosted CDN test file). */
    public void setDefaultSpeedUrl(String url) {
        if (url != null && !url.isEmpty()) this.defaultSpeedUrl = url;
    }

    public void start() {
        if (running.getAndSet(true)) return;
        worker = new Thread(this::loop, "net-speed-monitor");
        worker.setDaemon(true);
        worker.start();
    }

    public void stop() {
        running.set(false);
        synchronized (sleepLock) { sleepLock.notifyAll(); }
    }

    /** Re-measure immediately (e.g. the instant connectivity returns) instead of waiting for the timer. */
    public void pokeNow() {
        synchronized (sleepLock) { sleepLock.notifyAll(); }
    }

    private void loop() {
        while (running.get()) {
            // While streaming a video, measure the actual stream server (buffering risk).
            // Otherwise measure the device's real internet speed against a fast CDN reference.
            String url = (streaming && probeUrl != null && !probeUrl.isEmpty())
                    ? probeUrl : defaultSpeedUrl;
            double mbps = probeThroughput(url);

            final double fMbps = mbps;
            main.post(() -> listener.onSpeed(fMbps, false));

            long waitMs = streaming ? INTERVAL_STREAMING_MS : INTERVAL_IDLE_MS;
            synchronized (sleepLock) {
                try { sleepLock.wait(waitMs); } catch (InterruptedException ignored) { }
            }
        }
    }

    /**
     * Measures download throughput accurately by timing ONLY the data transfer
     * (first byte → last byte), excluding DNS/TCP/TLS/server-response latency.
     * Returns:
     *   - the measured Mbps when the sample is big enough to trust,
     *   - OK_SENTINEL when connected but the sample was too small/fast to measure,
     *   - -1 when the connection failed (offline / server unreachable).
     */
    private double probeThroughput(String url) {
        try {
            Request req = new Request.Builder()
                    .url(url)
                    .addHeader("Range", "bytes=0-" + (PROBE_TOTAL_BYTES - 1))
                    .addHeader("Cache-Control", "no-cache")
                    .build();

            try (Response resp = client.newCall(req).execute()) {
                ResponseBody body = resp.body();
                if (body == null) return -1;

                InputStream in = body.byteStream();
                byte[] buf = new byte[16 * 1024];
                long total = 0;      // all bytes read (incl. warm-up)
                long measured = 0;   // bytes counted for the speed calc (after warm-up)
                long start = -1;     // timer starts AFTER warm-up, so TCP slow-start is excluded
                int r;
                while ((r = in.read(buf)) != -1) {
                    total += r;
                    if (total <= WARMUP_BYTES) continue;   // ignore slow-start ramp-up
                    if (start < 0) start = System.nanoTime();
                    measured += r;
                    if (total >= PROBE_TOTAL_BYTES) break; // cap download even if server ignored Range
                }

                // Small file or not enough steady-state data to measure → treat as OK (no false "low").
                if (start < 0 || measured < MIN_MEASURED_BYTES) return OK_SENTINEL;
                long elapsedNs = System.nanoTime() - start;
                if (elapsedNs < 2_000_000L) return OK_SENTINEL; // finished almost instantly → clearly fast

                double seconds = elapsedNs / 1_000_000_000.0;
                double mbps = (measured * 8.0 / seconds) / 1_000_000.0;
                Log.d(TAG, "Probe: " + measured + " bytes in " + String.format("%.3f", seconds) + "s = "
                        + String.format("%.1f", mbps) + " Mbps");
                return mbps;
            }
        } catch (Exception e) {
            Log.w(TAG, "Speed probe failed (offline/unreachable): " + e.getMessage());
            return -1; // connection failed → offline
        }
    }
}
