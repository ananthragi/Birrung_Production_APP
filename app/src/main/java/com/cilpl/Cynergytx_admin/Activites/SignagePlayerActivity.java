package com.cilpl.Cynergytx_admin.Activites;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.drawable.ColorDrawable;
import android.net.ConnectivityManager;
import android.net.Network;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.android.volley.Request;
import com.android.volley.VolleyError;
import com.bumptech.glide.Glide;
import com.cilpl.Cynergytx_admin.NetworkCalling.HelperClass;
import com.cilpl.Cynergytx_admin.NetworkCalling.NetworkServiceCall;
import com.cilpl.Cynergytx_admin.NetworkCalling.OnServiceCallCompleteListener;
import com.cilpl.Cynergytx_admin.NetworkCalling.ServerURL;
import com.cilpl.Cynergytx_admin.R;
import com.cilpl.Cynergytx_admin.Services.ApiDataSync;
import com.cilpl.Cynergytx_admin.signage.StorageStatusReporter;
import com.cilpl.Cynergytx_admin.signage.model.ContentSchedule;
import com.cilpl.Cynergytx_admin.signage.model.MediaFile;
import com.cilpl.Cynergytx_admin.signage.model.Playlist;
import com.cilpl.Cynergytx_admin.signage.net.NetworkSpeedMonitor;
import com.cilpl.Cynergytx_admin.signage.player.PlaylistController;
import com.cilpl.Cynergytx_admin.signage.storage.LocalStorageManager;
import com.cilpl.Cynergytx_admin.signage.storage.MediaDownloadManager;
import com.cilpl.Cynergytx_admin.signage.sync.ContentSyncManager;
import com.cilpl.Cynergytx_admin.signage.sync.SyncReport;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.ui.PlayerView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * OFFLINE-FIRST signage player.
 *
 * Flow:
 *   1. Read the schedule (from SharedPreferences "schedule_json", written at login/MQTT refresh).
 *   2. Create the company folder and DOWNLOAD all playlist media into internal storage,
 *      budgeting the 16GB (active playlist downloaded first).
 *   3. PLAY the active playlist from LOCAL files — images 8s, videos full length, looping.
 *   4. A schedule ticker switches playlists as time windows change.
 *   5. MQTT messages trigger a re-fetch + re-sync so new content appears automatically.
 *
 * This activity is self-contained and does NOT change the existing streaming player
 * (AndroidTVActivity). To make it the live player, route to it from SplashActivity/
 * LoginActivity in place of AndroidTVActivity.
 */
public class SignagePlayerActivity extends AppCompatActivity implements MqttHelper.MqttListener {

    private static final String TAG = "SignagePlayer";
    private static final long SCHEDULE_TICK_MS = 1_000; // re-check active session every 1s → switch at the exact time

    private PlayerView playerView;
    private ImageView imageView, nocontent, loadingSpinner;
    private View lowSpeedDot;

    // Loading indicator (same green striped bar as the login page).
    private View loadingBox;                 // FIRST-TIME: full-screen "Loading, please wait..."
    private View updateLoadingBox;           // UPDATE: small corner "New content loading, please wait..."
    private TextView updateLoadingPercent;   // live download % shown inside the update chip
    private TextView updateLoadingText;      // state message inside the update chip
    private com.cilpl.Cynergytx_admin.utils.StripedProgressView loadingBar;
    private android.animation.ValueAnimator loadingBarAnim;

    // Full-screen status message (offline-no-content / outside-session).
    private View statusBox;
    private ImageView statusIcon;
    private TextView statusTitle, statusSubtitle;
    private ImageView offlineImage;   // full-screen "I'M OFFLINE" branded image

    // Low-speed "no wifi" icon blink cycle (visible 10s, hidden 20s → ~2x per minute).
    private final Handler dotHandler = new Handler(Looper.getMainLooper());
    private boolean lowActive = false;
    private static final long DOT_VISIBLE_MS = 10_000;
    private static final long DOT_HIDDEN_MS = 20_000;

    private ExoPlayer player;
    private LocalStorageManager storage;
    private ContentSyncManager syncManager;
    private PlaylistController controller;
    private ContentSchedule schedule;
    private NetworkSpeedMonitor speedMonitor;
    private String lastScheduleJson = "";       // to detect content changes across launches/updates
    private long lastContentErrorSync = 0;      // debounce for re-download after a playback error
    private long lastReconnectRefresh = 0;      // debounce the on-reconnect content check
    private static final long RECONNECT_REFRESH_MS = 3_000;

    private SharedPreferences prefs;
    private String sessionId = "", companyName = "Signage", mqttServer = "";
    private HelperClass helperClass;
    private ApiDataSync dataSync;
    private MqttHelper mqttHelper;

    private final Handler tickHandler = new Handler(Looper.getMainLooper());
    private boolean mqttConnected = false, mqttConnecting = false;
    private String lastMqttMsg = "";
    private long lastMqttTime = 0;

    // ---- Auto background retry (poor / no internet on first run) ----
    private static final long BASE_RETRY_MS = 15_000;   // first retry after 15s
    private static final long MAX_RETRY_MS  = 60_000;   // cap the backoff at 60s
    private final Handler retryHandler = new Handler(Looper.getMainLooper());
    private long retryDelayMs = BASE_RETRY_MS;
    private boolean isSyncing = false;       // guard against overlapping syncs
    private boolean hasOutstanding = false;  // true when files still need downloading
    private ConnectivityManager connectivityManager;
    private ConnectivityManager.NetworkCallback networkCallback;

    private final Runnable retryRunnable = new Runnable() {
        @Override public void run() {
            Log.e(TAG, "Auto-retry: re-attempting download sync");
            runSync();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_android_tv1activity);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        hideSystemUI();

        playerView = findViewById(R.id.playerView);
        imageView = findViewById(R.id.imageView);
        nocontent = findViewById(R.id.nocontentplay);
        loadingSpinner = findViewById(R.id.loadingSpinner);
        lowSpeedDot = findViewById(R.id.lowSpeedDot);
        loadingBox = findViewById(R.id.loadingBox);
        updateLoadingBox = findViewById(R.id.updateLoadingBox);
        updateLoadingPercent = findViewById(R.id.updateLoadingPercent);
        updateLoadingText = findViewById(R.id.updateLoadingText);
        loadingBar = findViewById(R.id.loadingBar);
        statusBox = findViewById(R.id.statusBox);
        statusIcon = findViewById(R.id.statusIcon);
        statusTitle = findViewById(R.id.statusTitle);
        statusSubtitle = findViewById(R.id.statusSubtitle);
        offlineImage = findViewById(R.id.offlineImage);
        // Load the client's offline image if it has been added to res/drawable/offline_image.(png|webp).
        try {
            int offId = getResources().getIdentifier("offline_image", "drawable", getPackageName());
            if (offId != 0 && offlineImage != null) offlineImage.setImageResource(offId);
        } catch (Throwable ignored) { }
        if (loadingBar != null) {
            loadingBar.setColors(android.graphics.Color.parseColor("#4CAF50"),
                    android.graphics.Color.parseColor("#66BB6A"),
                    android.graphics.Color.parseColor("#E3E3E3"), android.graphics.Color.WHITE);
        }

        prefs = getSharedPreferences("Userdata", MODE_PRIVATE);
        sessionId = prefs.getString("sessionId", "");

        // Tag crash reports with the licence so a crash (e.g. 73@dgt2) can be traced to a screen.
        try {
            com.google.firebase.crashlytics.FirebaseCrashlytics fc =
                    com.google.firebase.crashlytics.FirebaseCrashlytics.getInstance();
            fc.setUserId(sessionId);
            fc.setCustomKey("licence", sessionId);
        } catch (Throwable ignored) { }
        mqttServer = prefs.getString("mqttServer", "");
        helperClass = new HelperClass(this);
        dataSync = new ApiDataSync(this);

        player = new ExoPlayer.Builder(this).build();
        playerView.setPlayer(player);
        // If the player ever shows its shutter (e.g. a video→video switch in a multi-item
        // playlist), make it white instead of black so there is no black flash.
        playerView.setShutterBackgroundColor(android.graphics.Color.WHITE);

        loadScheduleFromPrefs();
        storage = new LocalStorageManager(this, companyName);
        syncManager = new ContentSyncManager(storage);
        controller = new PlaylistController(this, player, playerView, imageView, storage,
                new PlaylistController.Callback() {
                    @Override
                    public void onNothingToPlay() {
                        runOnUiThread(SignagePlayerActivity.this::showNoSession);
                    }
                    @Override
                    public void onContentPlaying() {
                        // Real content is on screen — hide loaders, BUT keep the "new content
                        // downloading" chip up while a new download is still pending.
                        runOnUiThread(() -> {
                            nocontent.setVisibility(View.GONE);
                            hideStatus();
                            hideLoading(controller != null && controller.isWaitingForNewContent());
                        });
                    }
                    @Override
                    public void onWaitingForContent() {
                        // Scheduled content isn't downloaded yet and nothing is playing.
                        runOnUiThread(() -> {
                            if (helperClass.checkInternetConnection(SignagePlayerActivity.this)) {
                                showLoading();            // online → download-progress loader
                            } else if (!tryPlayOfflineFallback()) {
                                // Offline: keep showing any content already on disk; only if there is
                                // NO downloaded content at all do we show the "I'M OFFLINE" image.
                                showNoInternet();
                            }
                        });
                    }
                    @Override
                    public void onNewContentLoading() {
                        // UPDATE (old content still playing) → small corner "New content loading...".
                        runOnUiThread(SignagePlayerActivity.this::showUpdateLoading);
                    }
                    @Override
                    public void onContentError() {
                        // A local file failed to play (corrupt/partial) → re-download it (debounced).
                        runOnUiThread(SignagePlayerActivity.this::handleContentError);
                    }
                    @Override
                    public void onNeedsInternet() {
                        // A YouTube/web item is scheduled but there is no usable internet →
                        // show the offline screen. It resumes automatically (from the same
                        // position) once the connection is back.
                        runOnUiThread(SignagePlayerActivity.this::showNoInternet);
                    }
                    @Override
                    public void onLowBandwidth() {
                        // Streaming video + speed <= 5 Mbps → no-op (holding frame removed; the
                        // controller already shows the loader while bandwidth is too low to stream).
                    }
                    @Override
                    public void onStreamingModeChanged(boolean streaming, String probeUrl) {
                        // While streaming a video, probe faster and measure the actual stream server;
                        // otherwise the monitor measures the internet reference (fast CDN).
                        if (speedMonitor != null) {
                            speedMonitor.setStreaming(streaming);
                            speedMonitor.setProbeUrl(streaming ? probeUrl : null);
                        }
                    }
                });

        // Host view for YouTube links (streamed via the YouTube player, never downloaded).
        controller.setWebContainer(findViewById(R.id.webContainer));

        // Continuous network speed monitoring (starts right after login → player).
        speedMonitor = new NetworkSpeedMonitor(this, (mbps, linkSpeed) ->
                runOnUiThread(() -> onNetworkSpeed(mbps, linkSpeed)));

        // Back button → show the logout confirmation instead of closing the app.
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                showLogoutPopup();
            }
        });

        showLoading();
        runSync();            // play cached content immediately (offline-first)
        startScheduleTicker();
        connectMqtt();
        registerNetworkListener();
        // Catches a sign-out that was queued before the app was last killed.
        com.cilpl.Cynergytx_admin.signage.status.SessionStatusManager.get(this).syncNow();
        speedMonitor.start();
        refreshFromServer();  // on every launch: check the server for newer content and download it
    }

    // ---------------- Network speed monitoring + low-bandwidth fallback ----------------

    /** Called on the main thread whenever a fresh speed reading arrives. */
    private void onNetworkSpeed(double mbps, boolean linkSpeed) {
        if (isFinishing() || isDestroyed()) return;
        // "No wifi" icon (bottom-right) when internet is at or below 5 Mbps (also covers offline).
        // The monitor probes continuously (~every 3s), so we react on the FIRST reading — the icon
        // appears the moment speed drops to <= 5 Mbps and clears the moment it recovers.
        boolean low = mbps <= 5.0;
        Log.d("NETWORK_SPEED", ""+ mbps);
        if (low) startLowSpeedIndicator();
        else stopLowSpeedIndicator();

        // Drive the streaming fallback decision.
        if (controller != null) controller.onNetworkSpeed(mbps);
    }

    // The "no wifi" icon blinks in a cycle: visible for 10s, hidden for 20s, so it appears
    // ~2 times per minute (per client request) while the connection stays low/offline.
    private final Runnable dotShowStep = new Runnable() {
        @Override public void run() {
            if (!lowActive || lowSpeedDot == null) return;
            lowSpeedDot.setVisibility(View.VISIBLE);
            dotHandler.postDelayed(dotHideStep, DOT_VISIBLE_MS);
        }
    };
    private final Runnable dotHideStep = new Runnable() {
        @Override public void run() {
            if (lowSpeedDot != null) lowSpeedDot.setVisibility(View.GONE);
            if (lowActive) dotHandler.postDelayed(dotShowStep, DOT_HIDDEN_MS);
        }
    };

    /** Connection dropped to <= 5 Mbps (or offline) → begin the blink cycle (shows immediately). */
    private void startLowSpeedIndicator() {
        if (lowActive) return;
        lowActive = true;
        dotHandler.removeCallbacks(dotShowStep);
        dotHandler.removeCallbacks(dotHideStep);
        dotHandler.post(dotShowStep);
    }

    /** Connection recovered above 5 Mbps → stop blinking and hide the icon immediately. */
    private void stopLowSpeedIndicator() {
        lowActive = false;
        dotHandler.removeCallbacks(dotShowStep);
        dotHandler.removeCallbacks(dotHideStep);
        if (lowSpeedDot != null) lowSpeedDot.setVisibility(View.GONE);
    }


    /** Play any content already on disk as an offline fallback. Returns false if there is none. */
    private boolean tryPlayOfflineFallback() {
        if (storage == null || controller == null) return false;
        java.io.File f = storage.mostRecentPlayableFile();
        if (f == null) return false;
        controller.playLocalFallback(f);
        return true;
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Finish an update that was waiting on the install permission being granted.
        com.cilpl.Cynergytx_admin.signage.update.AppUpdateManager.resumePendingInstall(this);
    }

    private void showLogoutPopup() {
        final Dialog dialog = new Dialog(SignagePlayerActivity.this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.getWindow().setBackgroundDrawable(new ColorDrawable(android.graphics.Color.TRANSPARENT));
        Window window = dialog.getWindow();
        WindowManager.LayoutParams wlp = window.getAttributes();
        wlp.gravity = Gravity.CENTER_HORIZONTAL;
        dialog.setContentView(R.layout.exit);
        dialog.setCanceledOnTouchOutside(true);
        window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT);

        Button alertCancel = dialog.findViewById(R.id.alert_clncel);
        Button alertYes = dialog.findViewById(R.id.alertyes_btn);
        TextView title = dialog.findViewById(R.id.title);
        title.setText("Are you sure you want to Logout ?");

        // Build version shown during the TESTING phase (re-hide for production).
        TextView versionInfo = dialog.findViewById(R.id.versionInfo);
        if (versionInfo != null) {
            try {
                android.content.pm.PackageInfo pInfo =
                        getPackageManager().getPackageInfo(getPackageName(), 0);
                long code = (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P)
                        ? pInfo.getLongVersionCode()
                        : pInfo.versionCode;
                versionInfo.setText("Version " + pInfo.versionName + "   •   Build " + code);
                versionInfo.setVisibility(View.VISIBLE);
            } catch (Exception ignored) { }
        }

        alertYes.setOnClickListener(v -> {
            // Tell the server first, while the sessionId is still available: clearing prefs
            // below wipes it. The request is queued on the application-wide Volley queue, so
            // it still completes after this activity is torn down. Nothing waits on it --
            // the device logs out locally even if the server is unreachable.
            final String endingSession = prefs.getString("sessionId", "");
            try {
                new com.cilpl.Cynergytx_admin.Services.ApiDataSync(SignagePlayerActivity.this)
                        .LogoutApiCall(endingSession);
            } catch (Throwable t) {
                android.util.Log.e("LogoutApi", "call skipped: " + t.getMessage());
            }

            // Separately, record the status change. Stored locally first, so a sign-out made
            // with no internet still reaches the server once the connection returns -- and
            // before the next sign-in is allowed. Logging out locally never waits on this.
            com.cilpl.Cynergytx_admin.signage.status.SessionStatusManager.get(SignagePlayerActivity.this).recordLogout(endingSession);

            if (mqttHelper != null) mqttHelper.disconnect();
            SharedPreferences.Editor editor = prefs.edit();
            editor.clear();
            editor.apply();

            Intent i = new Intent(SignagePlayerActivity.this, LoginActivity.class);
            i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(i);
            dialog.dismiss();
        });

        alertCancel.setOnClickListener(v -> dialog.dismiss());

        dialog.setOnKeyListener((DialogInterface d, int keyCode, KeyEvent event) -> {
            if (keyCode == KeyEvent.KEYCODE_BACK) {
                dialog.dismiss();
                return true;
            }
            return false;
        });

        dialog.show();
        dialog.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE);
        alertCancel.requestFocus(); // default focus for TV remote
    }

    private void loadScheduleFromPrefs() {
        String json = prefs.getString("schedule_json", "[]");
        lastScheduleJson = json;
        companyName = prefs.getString("companyName", companyName);
        schedule = ContentSchedule.parse(json, companyName, mqttServer);
        if (schedule.companyName != null && !schedule.companyName.isEmpty()) {
            companyName = schedule.companyName;
        }
    }

    private void runSync() {
        if (schedule == null || schedule.playlists.isEmpty()) {
            controller.stop();
            showNoSession();
            return;
        }
        // Reflect the current state immediately: play the active session if its content is local,
        // otherwise the controller shows the right loader (online) or the "No Internet" screen.
        Playlist activeNow = schedule.getActivePlaylist();
        if (activeNow != null) {
            controller.setPlaylist(activeNow);
        } else {
            // Outside any configured session → stop and show the empty-session screen, but still
            // sync in the background so upcoming sessions are downloaded and ready.
            controller.stop();
            showNoSession();
        }

        if (isSyncing) return; // don't stack syncs on top of each other
        isSyncing = true;

        boolean online = helperClass.checkInternetConnection(this);
        syncManager.sync(schedule, controller.getCurrentFileName(), online, new ContentSyncManager.SyncCallback() {
            @Override
            public void onFileReady(MediaFile file) {
                if (isFinishing() || isDestroyed()) return;

                // NOTE: the chip's "100% / Download Complete, Preparing Content" state is driven by
                // onProgress(), which only reports the ACTIVE content. We deliberately do not set it
                // here, because onFileReady also fires for background files belonging to OTHER
                // sessions — that would make the chip jump to 100% while the active download is
                // still in progress.

                // Switch to the new content IMMEDIATELY (no waiting for the rest of the sync).
                Playlist active = schedule.getActivePlaylist();
                if (active != null) {
                    controller.setPlaylist(active);
                    controller.refreshAvailability();
                }
            }

            @Override
            public void onProgress(MediaFile file, int percent) {
                if (isFinishing() || isDestroyed()) return;
                showDownloadProgress(percent);
            }

            @Override
            public void onComplete(SyncReport report) {
                isSyncing = false;
                if (isFinishing() || isDestroyed()) return;
                Log.e(TAG, report.summary());
                StorageStatusReporter.report(SignagePlayerActivity.this, sessionId, report);

                // Files that couldn't be stored locally (storage full) → stream from URL.
                Set<String> streamNames = new HashSet<>();
                Map<String, String> streamUrls = new HashMap<>();
                for (MediaFile f : report.shortfall) {
                    streamNames.add(f.localFileName());
                    streamUrls.put(f.localFileName(), f.url);
                }
                controller.setStreamable(streamNames, streamUrls);

                Playlist active = schedule.getActivePlaylist();
                // Why the screen did or did not start playing after a sync -- the single most
                // useful line when the loader appears stuck.
                if (active == null) {
                    Log.e(TAG, "After sync: no active playlist for this time -> no-content screen");
                } else {
                    int onDisk = 0;
                    for (MediaFile f : active.files) if (storage.isDownloaded(f)) onDisk++;
                    Log.e(TAG, "After sync: active playlist '" + active.id + "' has "
                            + active.files.size() + " file(s), " + onDisk + " present on disk");
                }
                if (active != null) controller.setPlaylist(active);
                else { controller.stop(); showNoSession(); }

                // Some files failed to download (usually poor / no internet) →
                // keep retrying quietly in the background until they arrive.
                if (!report.failed.isEmpty()) {
                    hasOutstanding = true;
                    scheduleRetry();
                } else {
                    hasOutstanding = false;
                    cancelRetry();
                }
            }
        });
    }

    /** Schedule the next retry with exponential backoff (capped). */
    private void scheduleRetry() {
        retryHandler.removeCallbacks(retryRunnable);
        Log.e(TAG, "Scheduling retry in " + (retryDelayMs / 1000) + "s");
        retryHandler.postDelayed(retryRunnable, retryDelayMs);
        retryDelayMs = Math.min(retryDelayMs * 2, MAX_RETRY_MS);
    }

    /** Stop retrying and reset the backoff (everything needed is downloaded). */
    private void cancelRetry() {
        retryHandler.removeCallbacks(retryRunnable);
        retryDelayMs = BASE_RETRY_MS;
    }

    /** Retry the download the instant the internet comes back. */
    private void registerNetworkListener() {
        connectivityManager = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        if (connectivityManager == null) return;

        networkCallback = new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(@NonNull Network network) {
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    // Re-measure speed immediately so the low-speed icon clears as soon as we're back online.
                    if (speedMonitor != null) speedMonitor.pokeNow();

                    // Internet came back → do the SAME thing as a fresh boot: check the backend for the
                    // latest content (/getMQTTscreens, which also reports the screen online via mystatus=1),
                    // reconnect the realtime MQTT channel, and let refreshFromServer decide — keep playing
                    // the downloaded video if unchanged, else download the new content while showing the
                    // holding image + loader. Debounced so repeated callbacks don't spam the server.
                    long now = System.currentTimeMillis();
                    if (now - lastReconnectRefresh > RECONNECT_REFRESH_MS) {
                        lastReconnectRefresh = now;
                        Log.e(TAG, "Internet available → check latest content + reconnect MQTT");
                        connectMqtt();        // re-establish realtime updates + mark online (no-op if already connected)
                        refreshFromServer();  // check latest content; keeps playing if nothing changed
                        com.cilpl.Cynergytx_admin.signage.status.SessionStatusManager.get(SignagePlayerActivity.this).syncNow();
                    }

                    // Resume any still-pending downloads immediately.
                    if (hasOutstanding && !isSyncing) {
                        Log.e(TAG, "Internet available → immediate retry");
                        retryDelayMs = BASE_RETRY_MS;     // reset backoff
                        retryHandler.removeCallbacks(retryRunnable);
                        runSync();
                    }
                });
            }
            @Override
            public void onLost(@NonNull Network network) {
                // Network fully gone → start the "no wifi" blink cycle instantly (don't wait for a probe).
                runOnUiThread(SignagePlayerActivity.this::startLowSpeedIndicator);
            }
        };
        try {
            connectivityManager.registerDefaultNetworkCallback(networkCallback);
        } catch (Exception e) {
            Log.e(TAG, "Failed to register network listener", e);
        }
    }

    private void startScheduleTicker() {
        tickHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (schedule != null && !schedule.playlists.isEmpty()) {
                    Playlist active = schedule.getActivePlaylist();
                    if (active != null) {
                        // setPlaylist is a no-op if the content hasn't actually changed.
                        controller.setPlaylist(active);
                    } else if (controller.isPlaying()) {
                        // A session just ended and no new session is active → stop and show the
                        // "No content configured at this time" screen (don't keep playing forever).
                        controller.stop();
                        showNoSession();
                    }
                }
                tickHandler.postDelayed(this, SCHEDULE_TICK_MS);
            }
        }, SCHEDULE_TICK_MS);
    }

    // ---------------- MQTT (real-time updates) ----------------

    private synchronized void connectMqtt() {
        if (mqttConnected || mqttConnecting) return;
        if (mqttServer == null || mqttServer.isEmpty()) return;

        mqttConnecting = true;
        mqttHelper = new MqttHelper(this);
        mqttHelper.setServerUrl(mqttServer);
        mqttHelper.setTopic("mysign/chat/" + sessionId);
        mqttHelper.connect(new MqttHelper.ConnectionCallback() {
            @Override public void onConnected() { mqttConnected = true; mqttConnecting = false; }
            @Override public void onDisconnected() { mqttConnected = false; mqttConnecting = false; }
            @Override public void onError(Throwable e) { mqttConnected = false; mqttConnecting = false; }
        });
    }

    @Override
    public void onMqttMessage(String topic, String message) {
        long now = System.currentTimeMillis();
        if (message.equals(lastMqttMsg) && now - lastMqttTime < 3000) return; // de-dupe
        lastMqttMsg = message;
        lastMqttTime = now;
        runOnUiThread(this::refreshFromServer);
    }

    /** Fetch the latest schedule from the server; if content changed, re-sync (download + switch). */
    private void refreshFromServer() {
        if (!helperClass.checkInternetConnection(this)) return;
        String time = new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date());
        dataSync.MQTTAPicall(sessionId, time, "", 0, 0, (status, mqttUrl) -> {
            if ("1".equals(status)) {
                mqttServer = prefs.getString("mqttServer", mqttServer);
                loadScheduleFromPrefs();
                runSync();
            }
        });
    }

    /** A local file failed to play → re-download it, but at most once every 30s to avoid a loop. */
    private void handleContentError() {
        if (isFinishing() || isDestroyed()) return;
        long now = System.currentTimeMillis();
        if (now - lastContentErrorSync < 30_000) return;
        lastContentErrorSync = now;
        Log.e(TAG, "Playback error → re-downloading content");
        runSync();
    }

    // ---------------- UI helpers ----------------

    /** FIRST-TIME loader: ONE full-screen download-progress bar (starts at 0% and counts up to 100%).
     *  It stays a percentage bar the whole time — no indeterminate→percentage switch (that looked
     *  like two loaders). */
    private void showLoading() {
        nocontent.setVisibility(View.GONE);
        hideStatus();
        loadingSpinner.setVisibility(View.GONE);
        if (updateLoadingBox != null) updateLoadingBox.setVisibility(View.GONE); // full-screen takes over
        if (loadingBox == null || loadingBar == null) return;
        if (loadingBox.getVisibility() != View.VISIBLE) {
            loadingBar.setIndeterminate(false);
            loadingBar.setProgress(0f);   // start the single percentage loader at 0%
        }
        loadingBox.setVisibility(View.VISIBLE);
    }

    /** Update the single first-time loader with the REAL download percentage of the active content. */
    private void showDownloadProgress(int percent) {
        if (percent < 0) return;   // unknown size → leave the indicators as they are

        // FIRST-TIME: full-screen bar shows the real 0..100% download progress.
        if (loadingBox != null && loadingBox.getVisibility() == View.VISIBLE && loadingBar != null) {
            loadingBar.setIndeterminate(false);
            loadingBar.setProgress(percent);
        }

        // UPDATE (old content still playing): show the same real % inside the small corner chip,
        // so it is visible that the new file is being saved to local storage and when it completes.
        if (updateLoadingBox != null && updateLoadingBox.getVisibility() == View.VISIBLE) {
            if (updateLoadingPercent != null) updateLoadingPercent.setText(percent + "%");
            if (updateLoadingText != null) {
                updateLoadingText.setText(percent >= 100
                        ? "Download Complete, Preparing Content"   // 100% → about to switch
                        : "New Content Loading, Please Wait");     // still downloading
            }
        }
    }

    /** UPDATE loader: small corner "New content loading, please wait..." — old content keeps playing. */
    private void showUpdateLoading() {
        // The full-screen loader must NOT be up while real content is playing.
        if (loadingBarAnim != null) { loadingBarAnim.cancel(); loadingBarAnim = null; }
        if (loadingBox != null) loadingBox.setVisibility(View.GONE);
        hideStatus();
        loadingSpinner.setVisibility(View.GONE);
        if (updateLoadingBox != null && updateLoadingBox.getVisibility() != View.VISIBLE) {
            // reset the chip for the new download
            if (updateLoadingPercent != null) updateLoadingPercent.setText("0%");
            if (updateLoadingText != null) updateLoadingText.setText("New Content Loading, Please Wait");
            updateLoadingBox.setVisibility(View.VISIBLE);
        }
    }

    private void hideLoading() { hideLoading(false); }

    /**
     * @param keepUpdateChip when true the small "new content downloading" chip stays on screen.
     *                       The old content keeps re-rendering (video loops / image re-shows) and
     *                       each re-render calls onContentPlaying() — without this the chip would
     *                       vanish part-way through the download and never come back.
     */
    private void hideLoading(boolean keepUpdateChip) {
        loadingSpinner.setVisibility(View.GONE);
        if (loadingBarAnim != null) { loadingBarAnim.cancel(); loadingBarAnim = null; }
        if (loadingBox != null) loadingBox.setVisibility(View.GONE);
        if (!keepUpdateChip && updateLoadingBox != null) updateLoadingBox.setVisibility(View.GONE);
    }

    /** Offline AND no complete content to play → full-screen "I'M OFFLINE" image (never a stuck loader). */
    private void showNoInternet() {
        hideLoading();
        playerView.setVisibility(View.GONE);
        imageView.setVisibility(View.GONE);
        nocontent.setVisibility(View.GONE);

        // Prefer the client's branded full-screen "I'M OFFLINE" image on a white background.
        if (offlineImage != null && offlineImage.getDrawable() != null) {
            if (statusBox != null) statusBox.setVisibility(View.GONE);
            offlineImage.setVisibility(View.VISIBLE);
            return;
        }

        // Fallback (image not bundled yet) → simple text screen.
        if (offlineImage != null) offlineImage.setVisibility(View.GONE);
        if (statusBox == null) return;
        if (statusIcon != null) statusIcon.setVisibility(View.VISIBLE);
        if (statusTitle != null) statusTitle.setText("No Internet Connection");
        if (statusSubtitle != null) {
            statusSubtitle.setText("Content is not available on this device yet. "
                    + "Please connect to the internet to download content.");
            statusSubtitle.setVisibility(View.VISIBLE);
        }
        statusBox.setVisibility(View.VISIBLE);
    }

    /**
     * Current time is outside every configured session → show the client's "no content configured"
     * animation (no_content.gif). Loaded with Glide so the GIF actually animates.
     */
    private void showNoSession() {
        hideLoading();
        playerView.setVisibility(View.GONE);
        imageView.setVisibility(View.GONE);
        if (offlineImage != null) offlineImage.setVisibility(View.GONE);
        if (statusBox != null) statusBox.setVisibility(View.GONE);
        if (nocontent == null) return;
        // Only (re)load the GIF when it isn't already on screen — this can be called repeatedly.
        if (nocontent.getVisibility() != View.VISIBLE) {
            try {
                Glide.with(this).asGif().load(R.drawable.no_content).into(nocontent);
            } catch (Throwable ignored) { }
            nocontent.setVisibility(View.VISIBLE);
        }
    }

    private void hideStatus() {
        if (statusBox != null) statusBox.setVisibility(View.GONE);
        if (offlineImage != null) offlineImage.setVisibility(View.GONE);
    }

    @SuppressLint("InlinedApi")
    private void hideSystemUI() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_FULLSCREEN);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        tickHandler.removeCallbacksAndMessages(null);
        retryHandler.removeCallbacks(retryRunnable);
        dotHandler.removeCallbacksAndMessages(null);
        if (loadingBarAnim != null) { loadingBarAnim.cancel(); loadingBarAnim = null; }
        if (speedMonitor != null) speedMonitor.stop();
        if (connectivityManager != null && networkCallback != null) {
            try { connectivityManager.unregisterNetworkCallback(networkCallback); } catch (Exception ignored) { }
        }
        if (controller != null) controller.release();
        if (syncManager != null) syncManager.shutdown();
        if (mqttHelper != null) mqttHelper.disconnect();
        if (player != null) {
            player.release();
            player = null;
        }
    }
}
