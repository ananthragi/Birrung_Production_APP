package com.cilpl.Cynergytx_admin.Activites;

import static android.view.View.GONE;

import org.json.JSONArray;
import org.json.JSONObject;

import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.app.Application;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Html;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;


import com.android.volley.Request;
import com.android.volley.VolleyError;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;
import com.cilpl.Cynergytx_admin.Activites.Interfaces.CarteResponse;
import com.cilpl.Cynergytx_admin.NetworkCalling.HelperClass;
import com.cilpl.Cynergytx_admin.NetworkCalling.NetworkServiceCall;
import com.cilpl.Cynergytx_admin.NetworkCalling.OnServiceCallCompleteListener;
import com.cilpl.Cynergytx_admin.NetworkCalling.ServerURL;
import com.cilpl.Cynergytx_admin.R;
import com.cilpl.Cynergytx_admin.Services.ApiDataSync;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.PlaybackException;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.database.ExoDatabaseProvider;
import com.google.android.exoplayer2.ui.PlayerView;
import com.google.android.exoplayer2.upstream.cache.LeastRecentlyUsedCacheEvictor;
import com.google.android.exoplayer2.upstream.cache.SimpleCache;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

public class AndroidTVActivity extends AppCompatActivity implements MqttHelper.MqttListener {
    private PlayerView exoPlayerView;
    private ImageView imageView, placeholderImage, nocontentplay;
    private TextView audioPlaceholder;

    private ExoPlayer exoPlayer;
    private ScheduledExecutorService scheduler;
    private String currentType = "", deviceName = "";
    private String currentUrl = "", sessionId = "";
    private static SimpleCache simpleCache;
    private long availMem, totalMem;
    private Handler apiHandler = new Handler();
    private Runnable apiRunnable;
    private HelperClass helperClass;
    private SharedPreferences userdata;
    private String currentMqttUrl = "";

    private static final long API_INTERVAL = 2 * 60000;
    ImageView loadingSpinner;
    MqttHelper mqttHelper;
    ApiDataSync dataSync;
    String userTopic;
    String topicname = "mysign/chat/";
    private String lastProcessedMessage = "";
    private boolean apiCalledForCurrentSignal = false;
    private WebView youtubeWebView;
    private Handler slotHandler = new Handler(Looper.getMainLooper());
    private Runnable slotTimeoutRunnable;

    private boolean isYoutubePlaying = false;

    private static ExoDatabaseProvider exoDatabaseProvider;
    private static final long CACHE_SIZE = 50 * 1024 * 1024; // 50MB
    private boolean forceContentRefresh = false;


    private final ExecutorService scheduleExecutor = Executors.newSingleThreadExecutor();

    private List<JSONObject> cachedSchedule = new ArrayList<>();
    private boolean isScheduleTimerRunning = false;

    private Runnable scheduleRunnable;
    private boolean scheduleJustUpdated = false;

    private volatile boolean mqttContentOverride = false;

    //    private static final long CACHE_SIZE = 100 * 1024 * 1024; // 100 MB
    private boolean isSwitchingContent = false;
    private boolean mqttOverrideActive = false;
    private boolean mqttConnected = false;
    private boolean mqttConnecting = false;

    private String lastMqttMessage = "";
    private long lastMqttTime = 0;
    private Player.Listener videoListener;
    private long lastUiSwitch = 0;
    private FrameLayout webContainer;
    private boolean appStarted = false;
    private final Handler scheduleHandler = new Handler(Looper.getMainLooper());

    private boolean mqttStarted = false;
    private long lastMqttUiUpdate = 0;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_android_tv1activity);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);

        hideSystemUI();


        // ONLY view binding
        exoPlayerView = findViewById(R.id.playerView);
        imageView = findViewById(R.id.imageView);
        audioPlaceholder = findViewById(R.id.audioPlaceholder);
        nocontentplay = findViewById(R.id.nocontentplay);
        placeholderImage = findViewById(R.id.placeholderImage);
        loadingSpinner = findViewById(R.id.loadingSpinner);
        webContainer = findViewById(R.id.webContainer);
        helperClass = new HelperClass(this);
        dataSync = new ApiDataSync(this);

        // 🔥 STATIC PLACEHOLDER ONLY (NO GIF)
//        Glide.with(this).asGif().load(R.drawable.loader).dontTransform().skipMemoryCache(true).into(loadingSpinner);
        Glide.with(this).asGif().load(R.drawable.no_content).dontTransform().skipMemoryCache(true).into(nocontentplay);


        loadingSpinner.setVisibility(View.GONE);
        userdata = getSharedPreferences("Userdata", MODE_PRIVATE);
        sessionId = userdata.getString("sessionId", "");
        OnBackPressedCallback callback = new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                ShowExitPopup();
            }
        };
        getOnBackPressedDispatcher().addCallback(this, callback);
//        // 🔥 DO NOTHING ELSE HERE

//        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
//            @Override
//            public void handleOnBackPressed() {
//                ShowExitPopup();
//            }
//        });


//        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
//            @Override
//            public void handleOnBackPressed() {
//                ShowExitPopup();
//            }
//        });
    }
    //  @Override
//    public boolean dispatchKeyEvent(KeyEvent event) {
//
//        if (event.getKeyCode() == KeyEvent.KEYCODE_BACK
//                && event.getAction() == KeyEvent.ACTION_UP) {
//
//            ShowExitPopup();
//            return true;   // VERY IMPORTANT → prevents app from closing
//        }
//
//        return super.dispatchKeyEvent(event);
//    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        Log.d("BACK_TEST", "Back Pressed");

        ShowExitPopup();
    }


//    @Override
//    public boolean dispatchKeyEvent(KeyEvent event) {
//
//        if (event.getKeyCode() == KeyEvent.KEYCODE_BACK
//                && event.getAction() == KeyEvent.ACTION_DOWN) {
//
//            ShowExitPopup();
//            return true;   // block system from finishing activity
//        }
//
//        return super.dispatchKeyEvent(event);
//    }

//    @Override
//    public boolean dispatchKeyEvent(KeyEvent event) {
//
//        if (event.getKeyCode() == KeyEvent.KEYCODE_BACK
//                && event.getAction() == KeyEvent.ACTION_UP) {
//
//            ShowExitPopup();
//            return true;
//        }
//
//        return super.dispatchKeyEvent(event);
//    }

    @SuppressLint("InlinedApi")
    private void hideSystemUI() {
        View decorView = getWindow().getDecorView();
        decorView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_FULLSCREEN);


    }

    private void stopAllContentSafely(String nextType) {

        if (isFinishing() || isDestroyed()) return;

        // 🔥 HIDE WebView FIRST (never clear immediately)
        if (youtubeWebView != null) {
            youtubeWebView.setVisibility(View.GONE);

            // Only clear WebView if we are NOT switching to another URL
            try {

                youtubeWebView.stopLoading();  // 🔥 VERY IMPORTANT

                if (!"url".equals(nextType)) {
                    youtubeWebView.loadUrl("about:blank");
                }

                youtubeWebView.setVisibility(View.GONE);

            } catch (Exception ignored) {
            }
        }

        // 🔥 Stop ExoPlayer safely
        if (exoPlayer != null) {
            exoPlayer.setPlayWhenReady(false);
            exoPlayer.stop();
            exoPlayer.clearMediaItems();
        }

        exoPlayerView.setVisibility(View.GONE);
        imageView.setVisibility(View.GONE);
        audioPlaceholder.setVisibility(View.GONE);
    }

    private void ensureWebView() {
        if (youtubeWebView != null) return;

        youtubeWebView = new WebView(this);

        WebSettings settings = youtubeWebView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);

        // Android TV user agent (IMPORTANT)
        settings.setUserAgentString("Mozilla/5.0 (Linux; Android 10; Android TV) AppleWebKit/537.36 " + "(KHTML, like Gecko) Chrome/96.0.4664.45 Safari/537.36");

        youtubeWebView.setWebChromeClient(new WebChromeClient());
        youtubeWebView.setVisibility(View.GONE);

        webContainer.addView(youtubeWebView, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
    }


    private JSONObject getCurrentSchedule(JSONArray scheduleArray) {

        int nowSeconds = getCurrentSeconds();

        for (int i = 0; i < scheduleArray.length(); i++) {

            try {
                JSONObject obj = scheduleArray.getJSONObject(i);

                String start = obj.getString("startTime");
                String end = obj.getString("endTime");

                int startSec = toSeconds(start);
                int endSec = toSeconds(end);

                // FULL DAY SUPPORT
                if (start.equals("00:00") && (end.equals("24:00") || end.equals("23:59:59"))) {
                    return obj;
                }

                // NORMAL SLOT
                if (startSec <= endSec) {
                    if (nowSeconds >= startSec && nowSeconds <= endSec) {
                        return obj;
                    }
                }
                // MIDNIGHT CROSS SLOT
                else {
                    if (nowSeconds >= startSec || nowSeconds <= endSec) {
                        return obj;
                    }
                }

            } catch (Exception ignored) {
            }
        }

        return null;
    }

    private int toSeconds(String time) {

        String[] parts = time.split(":");

        int hour = Integer.parseInt(parts[0]);
        int minute = Integer.parseInt(parts[1]);
        int second = 0;

        if (parts.length == 3) {
            second = Integer.parseInt(parts[2]);
        }

        return hour * 3600 + minute * 60 + second;
    }

    private int getCurrentSeconds() {

        Calendar calendar = Calendar.getInstance();

        int hour = calendar.get(Calendar.HOUR_OF_DAY);
        int minute = calendar.get(Calendar.MINUTE);
        int second = calendar.get(Calendar.SECOND);

        return hour * 3600 + minute * 60 + second;
    }

    private int getCurrentMinutes() {
        Calendar calendar = Calendar.getInstance();
        int hour = calendar.get(Calendar.HOUR_OF_DAY);
        int minute = calendar.get(Calendar.MINUTE);
        return hour * 60 + minute;
    }

    private int toMinutes(String time) {
        if (time.equals("24:00")) return 1440;

        String[] parts = time.split(":");
        int hour = Integer.parseInt(parts[0]);
        int minute = Integer.parseInt(parts[1]);
        return hour * 60 + minute;
    }


    private void stopAllContent() {

        if (isFinishing() || isDestroyed()) return;

        // 🔥 SAFE WebView stop
        hideWebView();

        // 🔥 SAFE ExoPlayer stop
        if (exoPlayer != null) {
            exoPlayer.setPlayWhenReady(false);
            exoPlayer.stop();
            exoPlayer.clearMediaItems();
        }

//            exoPlayerView.setVisibility(View.GONE);
        imageView.setVisibility(View.GONE);
        audioPlaceholder.setVisibility(View.GONE);
    }


    private void stopExoPlayer() {
        try {
            if (exoPlayer != null) {

                // Stop playback safely
                exoPlayer.setPlayWhenReady(false);
                exoPlayer.stop();

                // Remove media without killing internal threads
                exoPlayer.clearMediaItems();

                Log.e("PLAYER", "ExoPlayer stopped safely");
            }
        } catch (Exception e) {
            Log.e("PLAYER", "Safe stop error", e);
        }
    }

//        private void startMqttSafely1() {
//
//            if (mqttHelper == null) { mqttHelper = new MqttHelper(this); mqttHelper.setTopic(topicname + sessionId); } mqttHelper.connect();
//        }


    private synchronized void startMqttSafely() {

        if (mqttConnected || mqttConnecting) {
            Log.e("MQTT", "Already connected or connecting");
            return;
        }
        SharedPreferences prefs = getSharedPreferences("Userdata", MODE_PRIVATE);
        String mqttUrl = prefs.getString("mqttServer", "");

        if (mqttUrl == null || mqttUrl.isEmpty()) {
            Log.e("MQTT", "No MQTT URL found");
            return;
        }
        mqttConnecting = true;

        userTopic = topicname + sessionId;

        mqttHelper = new MqttHelper(this);
        mqttHelper.setServerUrl(mqttUrl);   // 🔥 Dynamic URL

        mqttHelper.setTopic(userTopic);

        mqttHelper.connect(new MqttHelper.ConnectionCallback() {
            @Override
            public void onConnected() {
                mqttConnected = true;
                mqttConnecting = false;
                Log.e("MQTT", "Connected successfully");
            }

            @Override
            public void onDisconnected() {
                mqttConnected = false;
                mqttConnecting = false;
                Log.e("MQTT", "Disconnected");
            }

            @Override
            public void onError(Throwable e) {
                mqttConnected = false;
                mqttConnecting = false;
                Log.e("MQTT", "Connection error", e);
            }
        });
    }


    @Override
    protected void onResume() {
        super.onResume();
//        adjustMediaOrientation();

        Log.e("ORIENTATION_TEST", "" + getResources().getConfiguration().orientation);


        if (appStarted) return;
        appStarted = true;

        // Let first frame render fully
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            startAppSafely();
        }, 700);
//        UpdateApicall(sessionId);
    }


    private void adjustMediaOrientation() {

        FrameLayout mediaContainer = findViewById(R.id.mediaContainer);

        DisplayMetrics dm = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(dm);

        int width = dm.widthPixels;
        int height = dm.heightPixels;

        if (height > width) {

            // Portrait screen → rotate content
            mediaContainer.setRotation(90f);

            ViewGroup.LayoutParams params = mediaContainer.getLayoutParams();
            params.width = height;
            params.height = width;
            mediaContainer.setLayoutParams(params);

        } else {

            // Landscape screen → normal
            mediaContainer.setRotation(0f);

            ViewGroup.LayoutParams params = mediaContainer.getLayoutParams();
            params.width = ViewGroup.LayoutParams.MATCH_PARENT;
            params.height = ViewGroup.LayoutParams.MATCH_PARENT;
            mediaContainer.setLayoutParams(params);
        }
    }

    private void startAppSafely() {

        showLoadingPlaceholder(); // Always show something immediately

        scheduleExecutor.execute(() -> {

            parseScheduleOnce(); // Background thread

            runOnUiThread(() -> {

                initExoPlayer(); // NOW safe

                playCurrentScheduleContent();

                scheduleHandler.postDelayed(this::startScheduleTimer, 1000);

                startMqttSafely();
            });
        });
    }

//    private void startAppSafely() {
//
//        // 1️⃣ Init player AFTER UI is stable
//        initExoPlayer();
//
//        // 2️⃣ Parse schedule in background (ONLY ONCE)
//        scheduleExecutor.execute(() -> {
//
//            parseScheduleOnce();
//
//            runOnUiThread(() -> {
//
//                // 3️⃣ Show first content immediately
//                playCurrentScheduleContent();
//
//                // 4️⃣ Start scheduler after UI stabilizes
//                scheduleHandler.postDelayed(() -> {
//                    startScheduleTimer();
//                }, 1000);
//
//                // 5️⃣ Start MQTT (non-blocking)
//                startMqttSafely();
//            });
//        });
//    }

    private void playCurrentScheduleContent() {
        try {
            String scheduleJson = userdata.getString("schedule_json", "[]");
            Log.e("Scheduledjson", scheduleJson);

            JSONArray arr = new JSONArray(scheduleJson);

            if (arr.length() == 0) {
                showNoContentState();
                return;
            }
            JSONObject currentSlot = getCurrentSchedule(arr);
//            JSONObject currentSlot = getCurrentSchedule(jsonArrayToList(arr));

            if (currentSlot != null) {

                String type = currentSlot.getString("type");
                String url = currentSlot.getString("url");

                // 🔥 SHOW PLACEHOLDER IMMEDIATELY (NO WHITE SCREEN)
//                showLoadingPlaceholder();
//
//                currentType = type;
//                currentUrl = url;

                updateContent(type, url);

            } else {
                showNoContentState();
            }

        } catch (Exception e) {
            e.printStackTrace();
            showNoContentState();
        }
    }

    private void showLoadingPlaceholder() {

//        placeholderImage.setVisibility(View.VISIBLE);
        showLoader();

        Glide.with(loadingSpinner).asGif().load(R.drawable.loader).dontTransform().skipMemoryCache(true).into(loadingSpinner);

        imageView.setVisibility(View.GONE);
        exoPlayerView.setVisibility(View.GONE);
        audioPlaceholder.setVisibility(View.GONE);
        nocontentplay.setVisibility(View.GONE);
    }

    private void playCurentScheduleContent() {
        try {
            String scheduleJson = userdata.getString("schedule_json", "[]");

            Log.e("Scheduledjson", scheduleJson);
            JSONArray arr = new JSONArray(scheduleJson);
            Log.e("FLOW", "playCurrent" + ",666---" + arr.toString());

            // 🔥 NO DATA AT ALL
            if (arr.length() == 0) {
                showNoContentState();
                return;
            }
            JSONObject currentSlot = getCurrentSchedule(arr);
//            JSONObject currentSlot = getCurrentSchedule(jsonArrayToList(arr));

            if (currentSlot != null) {
                String type = currentSlot.getString("type");
                String url = currentSlot.getString("url");

                currentUrl = url;
                currentType = type;
                updateContent(type, url);

            } else {
                // 🔥 NO SLOT FOR CURRENT TIME
                showNoContentState();
            }

        } catch (Exception e) {
            e.printStackTrace();
            showNoContentState();
        }
    }

    private void showNoContentState() {

        stopAllContent();   // stop player/webview/audio
        currentType = "";
        currentUrl = "";
        nocontentplay.setVisibility(View.VISIBLE);
        imageView.setVisibility(View.GONE);
        exoPlayerView.setVisibility(View.GONE);
        audioPlaceholder.setVisibility(View.GONE);
        loadingSpinner.setVisibility(View.GONE);
        placeholderImage.setVisibility(View.GONE);
    }


    private void startScheduleTimer() {

        if (isScheduleTimerRunning) return;
        isScheduleTimerRunning = true;

        scheduleRunnable = () -> {

            scheduleExecutor.execute(() -> {

                JSONObject slot = findCurrentSlotFast();

                if (slot != null) {

                    String type = slot.optString("type");
                    String url = slot.optString("url");

                    // ❌ DO NOT update currentType/currentUrl here
                    runOnUiThread(() -> updateContent(type, url));

                } else {

                    if (!currentType.isEmpty() || !currentUrl.isEmpty()) {

                        Log.e("SCHEDULE", "No active slot → clearing content");

                        currentType = "";
                        currentUrl = "";

                        runOnUiThread(() -> {
                            stopAllContent();
                            showNoContentState();
                        });
                    }
                }
            });

            scheduleHandler.postDelayed(scheduleRunnable, 500);
        };

        scheduleHandler.post(scheduleRunnable);
    }


    private void startScheduleTimer804() {

        if (isScheduleTimerRunning) return;
        isScheduleTimerRunning = true;

        scheduleRunnable = () -> {

            scheduleExecutor.execute(() -> {

                JSONObject slot = findCurrentSlotFast();

                if (slot != null) {

                    String type = slot.optString("type");
                    String url = slot.optString("url");

                    // ❌ DO NOT update currentType/currentUrl here
                    runOnUiThread(() -> updateContent(type, url));

                } else {

                    if (!currentType.isEmpty() || !currentUrl.isEmpty()) {

                        Log.e("SCHEDULE", "No active slot → clearing content");

                        currentType = "";
                        currentUrl = "";

                        runOnUiThread(() -> {
                            stopAllContent();
                            showNoContentState();
                        });
                    }
                }
            });

            scheduleHandler.postDelayed(scheduleRunnable, 1000);
        };

        scheduleHandler.post(scheduleRunnable);
    }

    private void startScheduleTimerold() {
        if (isScheduleTimerRunning) return;
        isScheduleTimerRunning = true;


        scheduleRunnable = () -> {

            scheduleExecutor.execute(() -> {

                JSONObject slot = findCurrentSlotFast();

                if (slot != null) {

                    String type = slot.optString("type");
                    String url = slot.optString("url");

                    // 🔥 HARD BLOCK SAME CONTENT
                    if (type.equals(currentType) && url.equals(currentUrl)) {
                        Log.e("SCHEDULE", "No change — keep playing");
                        return;
                    }

// consume flag
                    forceContentRefresh = false;

                    currentUrl = url;
                    currentType = type;

                    runOnUiThread(() -> updateContent(type, url));

                } else {
                    runOnUiThread(this::showPlaceholder);
                }
            });

            scheduleHandler.postDelayed(scheduleRunnable, 1000);
        };

        scheduleHandler.post(scheduleRunnable);
    }


    private void parseScheduleOnce() {
        try {
            cachedSchedule.clear();
            String json = userdata.getString("schedule_json", "[]");
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                cachedSchedule.add(arr.getJSONObject(i));
            }
            Log.e("SCHEDULE", "Parsed slots = " + cachedSchedule.size());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private JSONObject findCurrentSlotFast() {

        int nowSeconds = getCurrentSeconds();

        for (JSONObject slot : cachedSchedule) {

            String start = slot.optString("startTime");
            String end = slot.optString("endTime");

            int startSec = toSeconds(start);
            int endSec = toSeconds(end);

            // FULL DAY
            if ("00:00".equals(start) && ("24:00".equals(end) || "23:59:59".equals(end))) {
                return slot;
            }

            // NORMAL SLOT
            if (startSec <= endSec) {
                if (nowSeconds >= startSec && nowSeconds <= endSec) {
                    return slot;
                }
            }
            // MIDNIGHT CROSS
            else {
                if (nowSeconds >= startSec || nowSeconds <= endSec) {
                    return slot;
                }
            }
        }

        return null;
    }


    private JSONObject[] jsonArrayToList(JSONArray arr) throws Exception {
        JSONObject[] list = new JSONObject[arr.length()];
        for (int i = 0; i < arr.length(); i++) {
            list[i] = arr.getJSONObject(i);
        }
        return list;
    }

    private void showCustomToast(String message, int durationInMillis) {
        final Toast toast = Toast.makeText(this, message, Toast.LENGTH_LONG);

        int delay = 500; // LENGTH_LONG duration
        int repeats = durationInMillis / delay;

        Handler handler = new Handler(Looper.getMainLooper());
        for (int i = 0; i < repeats; i++) {
            handler.postDelayed(toast::show, i * delay);
        }
    }

    private String getRamInfo() {
        ActivityManager activityManager = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
        ActivityManager.MemoryInfo memoryInfo = new ActivityManager.MemoryInfo();
        activityManager.getMemoryInfo(memoryInfo);

        totalMem = memoryInfo.totalMem / (1024 * 1024); // in MB
        availMem = memoryInfo.availMem / (1024 * 1024); // in MB
        boolean lowMemory = memoryInfo.lowMemory;

        Log.e("RAM_INFO", "Total RAM: " + totalMem + " MB");
        Log.e("RAM_INFO", "Available RAM: " + availMem + " MB");
        Log.e("RAM_INFO", "Is Low Memory: " + lowMemory);
        return availMem + ",  " + lowMemory + ",  " + totalMem;
    }

    private void clearAppCache(Context context) {
        try {
            // Clear Glide memory (UI thread)
            Glide.get(context).clearMemory();

            // Clear Glide disk cache (BG thread)
            new Thread(() -> {
                try {
                    Glide.get(context).clearDiskCache();
                } catch (Exception ignored) {
                }
            }).start();

            // Clear Volley cache
            MySingleton.getInstance(context).getRequestQueue().getCache().clear();

            // Optionally delete only your app-specific subfolders
            File videoCacheDir = new File(context.getCacheDir(), "video_cache");
            deleteDir(videoCacheDir);

            // Suggest GC
            Runtime.getRuntime().gc();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    private static boolean deleteDir(File dir) {
        if (dir != null && dir.isDirectory()) {
            String[] children = dir.list();
            if (children != null) {
                for (String child : children) {
                    boolean success = deleteDir(new File(dir, child));
                    if (!success) return false;
                }
            }
        }
        return dir != null && dir.delete();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mqttHelper != null) {
            mqttHelper.disconnect();
        }

        mqttConnected = false;
        mqttConnecting = false;

        Log.e("MQTT", "Disconnected onPause");
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdownNow(); // stop periodic calls
        }
    }


    private void ShowExitPopupno() {

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setCancelable(false);

        View view = getLayoutInflater().inflate(R.layout.exit, null);
        builder.setView(view);

        AlertDialog dialog = builder.create();
        dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));

        Button alert_cancel = view.findViewById(R.id.alert_clncel);
        Button alert_yes = view.findViewById(R.id.alertyes_btn);
        TextView title = view.findViewById(R.id.title);

        title.setText("Are you sure you want to Logout ?");

        dialog.show();

        // 🔥 Fix dialog size (important for TV)
        dialog.getWindow().setLayout(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);

        dialog.getWindow().setGravity(Gravity.CENTER);

        // 🔥 Focus for Android TV
        alert_yes.requestFocus();

        alert_yes.setOnClickListener(v -> {

            if (mqttHelper != null) mqttHelper.disconnect();

            userdata.edit().clear().apply();

            Intent i = new Intent(AndroidTVActivity.this, LoginActivity.class);
            i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(i);

            dialog.dismiss();
        });

        alert_cancel.setOnClickListener(v -> dialog.dismiss());

        dialog.setOnKeyListener((d, keyCode, event) -> {
            if (keyCode == KeyEvent.KEYCODE_BACK && event.getAction() == KeyEvent.ACTION_UP) {
                dialog.dismiss();
                return true;
            }
            return false;
        });
    }

    private void ShowExitPopupk() {


        final Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.exit);
        dialog.getWindow().setBackgroundDrawable(new ColorDrawable(android.graphics.Color.TRANSPARENT));

        dialog.setCanceledOnTouchOutside(false);

        Button alert_clncel = dialog.findViewById(R.id.alert_clncel);
        Button alert_Yes = dialog.findViewById(R.id.alertyes_btn);
        TextView title = dialog.findViewById(R.id.title);

        title.setText("Are you sure you want to Logout ?");

        // 🔥 Android TV Focus Fix
        alert_Yes.setFocusable(true);
        alert_clncel.setFocusable(true);

        alert_Yes.setFocusableInTouchMode(true);
        alert_clncel.setFocusableInTouchMode(true);

        alert_Yes.requestFocus();

        alert_Yes.setNextFocusDownId(R.id.alert_clncel);
        alert_clncel.setNextFocusUpId(R.id.alertyes_btn);

        alert_Yes.setOnClickListener(v -> {
            if (mqttHelper != null) mqttHelper.disconnect();

            SharedPreferences.Editor editor = userdata.edit();
            editor.clear();
            editor.apply();

            Intent i = new Intent(this, LoginActivity.class);
            i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(i);

            dialog.dismiss();
        });

        alert_clncel.setOnClickListener(v -> dialog.dismiss());

        dialog.setOnKeyListener((d, keyCode, event) -> {
            if (keyCode == KeyEvent.KEYCODE_BACK && event.getAction() == KeyEvent.ACTION_UP) {
                dialog.dismiss();
                return true;
            }
            return false;
        });

        dialog.show();
    }

    private void ShowExitPopup() {
        final Dialog dialog = new Dialog(AndroidTVActivity.this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.getWindow().setBackgroundDrawable(new ColorDrawable(android.graphics.Color.TRANSPARENT));
        Window window = dialog.getWindow();
        WindowManager.LayoutParams wlp = window.getAttributes();
        wlp.gravity = Gravity.CENTER_HORIZONTAL;
        dialog.setContentView(R.layout.exit);
        dialog.setCanceledOnTouchOutside(true);

        window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT);
        Button alert_clncel = dialog.findViewById(R.id.alert_clncel);
        Button alert_Yes = dialog.findViewById(R.id.alertyes_btn);
        TextView title = dialog.findViewById(R.id.title);
        title.setText("Are you sure you want to Logout ?");


        alert_Yes.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mqttHelper.disconnect();
                SharedPreferences.Editor editor = userdata.edit();
                editor.clear();
                editor.apply();
                Intent i = new Intent(AndroidTVActivity.this, LoginActivity.class);
                i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                startActivity(i);
                dialog.dismiss();

            }
        });
        alert_clncel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
            }
        });

        dialog.setOnKeyListener(new Dialog.OnKeyListener() {

            @Override
            public boolean onKey(DialogInterface arg0, int keyCode, KeyEvent event) {
                // TODO Auto-generated method stub
                if (keyCode == KeyEvent.KEYCODE_BACK) {

                    dialog.dismiss();
                    return true;
                }
                return false;
            }
        });
        dialog.show();
        dialog.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE);
        alert_clncel.requestFocus();

    }


    private void quit() {
        Intent start = new Intent(Intent.ACTION_MAIN);
        start.addCategory(Intent.CATEGORY_HOME);
        start.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        start.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(start);

    }


    private void initExoPlayer() {
        if (exoPlayer != null) return;

        exoPlayer = new ExoPlayer.Builder(this).build();
        exoPlayerView.setPlayer(exoPlayer);
        exoPlayer.setRepeatMode(Player.REPEAT_MODE_ONE);
        exoPlayer.setPlayWhenReady(true);

        exoPlayer.addListener(new Player.Listener() {

            @Override
            public void onPlaybackStateChanged(int state) {
                Log.e("PLAYER_STATE", "state=" + state);

                if (state == Player.STATE_READY) {
                    loadingSpinner.setVisibility(View.GONE);
                    placeholderImage.setVisibility(View.GONE);
                }
            }

            @Override
            public void onPlayerError(@NonNull PlaybackException error) {
                Log.e("PLAYER_ERROR", error.getMessage());
            }
        });

        Log.e("PLAYER", "ExoPlayer initialized once");
    }


    public static synchronized SimpleCache getSimpleCache(Application context) {
        if (simpleCache == null) {
            File cacheDir = new File(context.getCacheDir(), "video_cache");
            LeastRecentlyUsedCacheEvictor evictor = new LeastRecentlyUsedCacheEvictor(CACHE_SIZE);
            exoDatabaseProvider = new ExoDatabaseProvider(context);
            simpleCache = new SimpleCache(cacheDir, evictor, exoDatabaseProvider);
        }
        return simpleCache;
    }

    public void fetchAndDisplayContent() {
        String currentTime = new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date());

        if (!helperClass.checkInternetConnection(this)) {

            Toast.makeText(this, R.string.noInternet, Toast.LENGTH_SHORT).show();// stop video/audio if playing
            showPlaceholder();
            return;
        }

        try {
            String checkouApi = ServerURL.getDisplaycontent();
            HashMap<String, String> hashMap = new HashMap<>();
            hashMap.put("userid", sessionId);
            hashMap.put("time", currentTime);
            hashMap.put("mystatus", "1");
            hashMap.put("deviceName", deviceName);
            hashMap.put("availableMem", String.valueOf(availMem));
            hashMap.put("totalMem", String.valueOf(totalMem));
            Log.e("checklog", checkouApi + "," + currentTime + "," + sessionId + ",---availblemameory=" + availMem + "---totalmemory=" + totalMem + "---devicename=" + deviceName);

            NetworkServiceCall serviceCall = new NetworkServiceCall(this, false);
            serviceCall.setOnServiceCallCompleteListener(new OnServiceCallCompleteListener() {
                @Override
                public void onJSONObjectResponse(JSONObject jsonObject) {
                    try {
                        Log.e("checklog", jsonObject.toString());
                        String path = jsonObject.getString("path");
                        String newType = jsonObject.getString("type"); // image/audio/video
                        String newUrl = path + jsonObject.getString("imageName");

                        // Only update if type or URL changed
                        if (newType.equals(currentType) && newUrl.equals(currentUrl)) {
                            return;
                        }

                        currentType = newType;
                        currentUrl = newUrl;
                        placeholderImage.setVisibility(View.VISIBLE);

                        new Handler(Looper.getMainLooper()).post(() -> {
                            updateContent(newType, newUrl);
                        });

                        // runOnUiThread(() -> updateContent(newType, newUrl));

                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }

                @Override
                public void onJSONArrayResponse(JSONArray jsonArray) {
                }

                @Override
                public void onErrorResponse(VolleyError error) {
                }
            });

            serviceCall.makeJSONObjectPostStringRequest(checkouApi, hashMap, Request.Priority.HIGH);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public String getCurrentUrl() {
        return currentUrl;
    }

    public String getCurrentType() {
        return currentType;
    }

    private void showPlaceholder() {
        // stopPlayer(); // stop any video/audio

        stopAllContent();

        nocontentplay.setVisibility(View.VISIBLE);
        imageView.setVisibility(View.GONE);
        exoPlayerView.setVisibility(View.GONE);
        audioPlaceholder.setVisibility(View.GONE);
        loadingSpinner.setVisibility(View.GONE);
        placeholderImage.setVisibility(View.GONE);// add your default image in res/drawable
    }

    private synchronized void updateContent(String type, String url) {

        String newUrl = normalizeUrl(url);
        String oldUrl = normalizeUrl(currentUrl);

        Log.e("DEBUG", "New Type: " + type);
        Log.e("DEBUG", "Current Type: " + currentType);
        Log.e("DEBUG", "New URL: " + newUrl);
        Log.e("DEBUG", "Current URL: " + oldUrl);

        // 🔥 Skip ONLY if truly same
//        if (type != null && type.equals(currentType) && newUrl.equals(oldUrl)) {
//
//            Log.e("SCHEDULE", "Same content — skip");
//            return;
//        }


        if (nocontentplay.getVisibility() != View.VISIBLE &&
                type != null &&
                type.equals(currentType) &&
                newUrl.equals(oldUrl)) {

            return;
        }
//        if (nocontentplay.getVisibility() != View.VISIBLE
//                && type != null
//                && type.equals(currentType)
//                && newUrl.equals(oldUrl)) {
//
//            Log.e("SCHEDULE", "Same content — skip");
//            return;
//        }


        Log.e("FLOW", "Switching content");

        runOnUiThread(() -> {
            nocontentplay.setVisibility(View.GONE);
            // 🔥 ALWAYS stop old content first
            boolean isImageToImage = "image".equals(currentType) && "image".equals(type);

// Only stop fully if NOT image → image
            if (!isImageToImage) {
                stopAllContentSafely(type);
//                showLoadingPlaceholder();
            }

            // Update state AFTER stopping
            currentType = type;
            currentUrl = newUrl;

            switch (type) {

                case "video":
                    showVideo(newUrl);
                    break;

                case "image":
                    showImage(newUrl);
                    break;

                case "audio":
                    showAudio(newUrl);
                    break;

                case "url":
                    String id = extractYoutubeVideoId(newUrl);
                    showWebViewUrl("https://birrung.com.au/media?tv=" + id);
                 //   showWebViewUrl("https://mysign.au/media?tv=" + id);
//                    showWebViewUrl("https://sandbox.mysign.au/media?tv=" + id);
                    break;

                default:
                    showPlaceholder();
            }
        });
    }

    private boolean isContentActuallyVisible(String type) {

        switch (type) {
            case "video":
                return exoPlayer != null && exoPlayer.isPlaying();

            case "image":
                return imageView.getVisibility() == View.VISIBLE;

            case "url":
                return youtubeWebView != null && youtubeWebView.getVisibility() == View.VISIBLE;

            case "audio":
                return exoPlayer != null && exoPlayer.isPlaying();

            default:
                return false;
        }
    }

    private synchronized void updateContentold(String type, String url) {
        long now = System.currentTimeMillis();
        if (now - lastUiSwitch < 800) {
            Log.e("UI", "Switch throttled");
            return;
        }
        lastUiSwitch = now;
        //  showLoadingPlaceholder();


        Log.e("FLOW", "updateContent type=" + type + " url=" + url);
        if (isSwitchingContent) return;
        isSwitchingContent = true;

        runOnUiThread(() -> {
            try {
                stopAllContentSafely(type);


                switch (type) {
                    case "video":
                        showVideo(url);
                        break;

                    case "image":
                        stopExoPlayer();
                        showImage(url);
                        break;

                    case "audio":
                        stopExoPlayer();
                        showAudio(url);
                        break;

                    case "url":
                        String id = extractYoutubeVideoId(url);
                        stopExoPlayer();
                        showWebViewUrl("https://sandbox.mysign.au/media?tv=" + id);
                        break;

                    default:
                        showPlaceholder();
                }
            } finally {
                isSwitchingContent = false;
            }
        });
    }

    private String normalizeUrl(String url) {

        if (url == null) return "";

        url = url.replace("\\/", "/").replace("\\", "").trim();

        // Fix protocol safely
        if (url.startsWith("https:/") && !url.startsWith("https://")) {
            url = url.replaceFirst("https:/+", "https://");
        }

        if (url.startsWith("http:/") && !url.startsWith("http://")) {
            url = url.replaceFirst("http:/+", "http://");
        }

        return url;
    }

    private void showWebViewUrl(String tvUrl) {

        ensureWebView();

        webContainer.setVisibility(View.VISIBLE);
        youtubeWebView.setVisibility(View.VISIBLE);

        loadingSpinner.setVisibility(View.VISIBLE);
        placeholderImage.setVisibility(View.GONE);

        youtubeWebView.setWebViewClient(new WebViewClient() {

            @Override
            public void onPageFinished(WebView view, String url) {

                loadingSpinner.setVisibility(View.GONE);
                placeholderImage.setVisibility(View.GONE);

                // 🔥 Important for portrait
                youtubeWebView.requestLayout();
                youtubeWebView.invalidate();
            }

            @Override
            public void onReceivedError(WebView view, int errorCode,
                                        String description, String failingUrl) {
                showPlaceholder();
            }
        });

        youtubeWebView.post(() -> {
            youtubeWebView.loadUrl(tvUrl);
            youtubeWebView.requestLayout();   // 🔥 important
        });
    }


    private void hideWebView() {
        if (youtubeWebView != null) {
            youtubeWebView.loadUrl("about:blank");
            youtubeWebView.setVisibility(View.GONE);
        }
    }

    private void destroyWebView() {
        if (youtubeWebView != null) {
            webContainer.removeView(youtubeWebView);
            youtubeWebView.removeAllViews();
            youtubeWebView.destroy();
            youtubeWebView = null;
        }
    }


    private String extractYoutubeVideoId(String url) {
        try {
            Uri uri = Uri.parse(url);

            // ✅ Standard YouTube
            String v = uri.getQueryParameter("v");
            if (v != null && !v.isEmpty()) {
                return v;
            }

            // ✅ youtu.be links
            if ("youtu.be".equals(uri.getHost())) {
                return uri.getLastPathSegment();
            }

            // ✅ YouTube Shorts
            if (uri.getPath() != null && uri.getPath().startsWith("/shorts/")) {
                return uri.getLastPathSegment();
            }

        } catch (Exception e) {
            Log.e("YOUTUBE", "Failed to parse video ID", e);
        }
        return null;
    }

    private void showVideo(String url) {

        if (isFinishing() || isDestroyed()) return;
        if (youtubeWebView != null) {
            youtubeWebView.setVisibility(View.GONE);
        }
        nocontentplay.setVisibility(View.GONE);
        try {

            initExoPlayer();

            // Always show player first

            imageView.setVisibility(View.GONE);
            audioPlaceholder.setVisibility(View.GONE);

            if (youtubeWebView != null) {
                youtubeWebView.setVisibility(View.GONE);
            }

            // Remove old listener
            if (videoListener != null) {
                exoPlayer.removeListener(videoListener);
            }

            // Full reset
            exoPlayer.setPlayWhenReady(false);
            exoPlayer.stop();
            exoPlayer.clearMediaItems();

            exoPlayer.setRepeatMode(Player.REPEAT_MODE_ONE);

            MediaItem mediaItem = MediaItem.fromUri(Uri.parse(url));
            exoPlayer.setMediaItem(mediaItem);

            videoListener = new Player.Listener() {

                @Override
                public void onPlaybackStateChanged(int state) {

                    Log.e("VIDEO_STATE", "State = " + state);

                    if (state == Player.STATE_BUFFERING) {
                        showLoader();
//                        placeholderImage.setVisibility(View.VISIBLE);
                        exoPlayerView.setVisibility(GONE);
                    }

                    if (state == Player.STATE_READY) {
                        exoPlayerView.setVisibility(View.VISIBLE);
                        hideLoader();
//                        placeholderImage.setVisibility(View.GONE);
                    }
                }


                @Override
                public void onPlayerError(@NonNull PlaybackException error) {
                    Log.e("VIDEO_ERROR", error.getMessage());
                    showPlaceholder();
                }
            };

            exoPlayer.addListener(videoListener);

            // 6️⃣ IMPORTANT: prepare AFTER view visible
            exoPlayer.prepare();

            // 7️⃣ Start playback
            exoPlayer.setPlayWhenReady(true);

        } catch (Exception e) {
            Log.e("VIDEO_ERROR", "Exception", e);
            showPlaceholder();
        }
    }

    private void showVideoold(String rawUrl) {

        if (isFinishing() || isDestroyed()) return;

        Log.e("FLOW", "showVideo STARTED");

        try {
            String url = rawUrl.replace("\\", "");
            Log.e("VIDEO", "Playing: " + url);

            initExoPlayer();

            // 🔥 If same video already playing → skip
            if (url.equals(currentUrl) && exoPlayer.getPlaybackState() == Player.STATE_READY) {
                Log.e("VIDEO", "Same URL already playing — skip");
                return;
            }

            currentUrl = url;

            // 🔥 UI: SHOW PLACEHOLDER FIRST (NO WHITE SCREEN)
            placeholderImage.setVisibility(View.VISIBLE);
            loadingSpinner.setVisibility(View.VISIBLE);

            nocontentplay.setVisibility(GONE);
            exoPlayerView.setVisibility(View.VISIBLE);
            imageView.setVisibility(GONE);
            audioPlaceholder.setVisibility(GONE);

            // 🔥 Remove old listener (IMPORTANT)
            if (videoListener != null) {
                exoPlayer.removeListener(videoListener);
            }

            // 🔥 Stop previous media safely
            exoPlayer.stop();
            exoPlayer.clearMediaItems();

            MediaItem mediaItem = MediaItem.fromUri(Uri.parse(url));
            exoPlayer.setMediaItem(mediaItem);

            videoListener = new Player.Listener() {

                @Override
                public void onPlaybackStateChanged(int state) {

                    if (state == Player.STATE_BUFFERING) {
                        placeholderImage.setVisibility(View.VISIBLE);
                        loadingSpinner.setVisibility(View.VISIBLE);
                    }

                    if (state == Player.STATE_READY) {
                        // 🔥 First frame ready → hide placeholder
                        loadingSpinner.setVisibility(View.GONE);
                        placeholderImage.setVisibility(View.GONE);
                    }
                }

                @Override
                public void onPlayerError(@NonNull PlaybackException error) {
                    Log.e("VIDEO", "Playback error", error);
                    showPlaceholder();
                }
            };

            exoPlayer.addListener(videoListener);

            exoPlayer.prepare();
            exoPlayer.play();

        } catch (Exception e) {
            Log.e("VIDEO", "Error", e);
            showPlaceholder();
        }
    }

    private void showImage(String url) {

        if (isFinishing() || isDestroyed()) return;

        Log.e("IMAGE", "Loading image → " + url);

        Glide.with(this).clear(imageView);
        imageView.setImageDrawable(null);

        loadingSpinner.setVisibility(View.VISIBLE);
        placeholderImage.setVisibility(View.GONE);

        exoPlayerView.setVisibility(View.GONE);
        imageView.setVisibility(View.VISIBLE);
        audioPlaceholder.setVisibility(View.GONE);
        webContainer.setVisibility(View.GONE);

        Glide.with(this)
                .load(url)
                .dontAnimate()
                .skipMemoryCache(true)
                .diskCacheStrategy(DiskCacheStrategy.NONE)
                .listener(new RequestListener<Drawable>() {

                    @Override
                    public boolean onLoadFailed(@Nullable GlideException e,
                                                Object model,
                                                Target<Drawable> target,
                                                boolean isFirstResource) {

                        loadingSpinner.setVisibility(View.GONE);
                        return false;
                    }

                    @Override
                    public boolean onResourceReady(Drawable resource,
                                                   Object model,
                                                   Target<Drawable> target,
                                                   DataSource dataSource,
                                                   boolean isFirstResource) {

                        loadingSpinner.setVisibility(View.GONE);
                        return false;
                    }
                })
                .into(imageView);
    }

    private void showLoader() {

        Glide.with(this).clear(loadingSpinner);

        Glide.with(this).asGif().load(R.drawable.loader).dontTransform().into(loadingSpinner);

        loadingSpinner.setVisibility(View.VISIBLE);
    }

    private void hideLoader() {
        loadingSpinner.setVisibility(View.GONE);
    }

    private void showImageoo(String url) {

        if (isFinishing() || isDestroyed()) return;

        Log.e("IMAGE", "Loading image → " + url);

        // 🔥 1️⃣ Show placeholder FIRST
        placeholderImage.setVisibility(View.VISIBLE);
        loadingSpinner.setVisibility(View.VISIBLE);

        exoPlayerView.setVisibility(View.GONE);
        imageView.setVisibility(View.VISIBLE);
        audioPlaceholder.setVisibility(View.GONE);

        if (youtubeWebView != null) {
            youtubeWebView.setVisibility(View.GONE);
        }

        Glide.with(imageView).load(url).override(1280, 720).fitCenter().placeholder(R.drawable.loader).error(R.drawable.loader).skipMemoryCache(true).diskCacheStrategy(DiskCacheStrategy.NONE).listener(new RequestListener<Drawable>() {


            @Override
            public boolean onLoadFailed(@Nullable GlideException e, Object model, Target<Drawable> target, boolean isFirstResource) {

                loadingSpinner.setVisibility(View.GONE);
                return false;
            }

            @Override
            public boolean onResourceReady(Drawable resource, Object model, Target<Drawable> target, DataSource dataSource, boolean isFirstResource) {

                loadingSpinner.setVisibility(View.GONE);
                placeholderImage.setVisibility(View.GONE);
                return false;
            }
        }).into(imageView);
    }

    private void showImageol(String url) {

        if (isFinishing() || isDestroyed()) return;

        Log.e("IMAGE", "Loading image → " + url);

        // 🔥 STOP placeholder GIF completely
        Glide.with(this).clear(placeholderImage);
        placeholderImage.setImageDrawable(null);
        placeholderImage.setVisibility(View.GONE);

        loadingSpinner.setVisibility(View.GONE);

        imageView.setVisibility(View.VISIBLE);
        imageView.setImageDrawable(null);

        Glide.with(imageView).load(url).skipMemoryCache(true).diskCacheStrategy(com.bumptech.glide.load.engine.DiskCacheStrategy.NONE).listener(new com.bumptech.glide.request.RequestListener<android.graphics.drawable.Drawable>() {

            @Override
            public boolean onLoadFailed(@Nullable com.bumptech.glide.load.engine.GlideException e, Object model, com.bumptech.glide.request.target.Target<android.graphics.drawable.Drawable> target, boolean isFirstResource) {

                loadingSpinner.setVisibility(View.GONE);
                placeholderImage.setVisibility(View.VISIBLE);
                return false;
            }

            @Override
            public boolean onResourceReady(android.graphics.drawable.Drawable resource, Object model, com.bumptech.glide.request.target.Target<android.graphics.drawable.Drawable> target, com.bumptech.glide.load.DataSource dataSource, boolean isFirstResource) {

                // 🔥 Ensure GIF is removed
                Glide.with(placeholderImage).clear(placeholderImage);
                placeholderImage.setImageDrawable(null);
                placeholderImage.setVisibility(View.GONE);
                loadingSpinner.setVisibility(View.GONE);

                return false;
            }
        }).into(imageView);
    }


    private void showAudio(String url) {


        exoPlayer.stop();
        exoPlayer.clearMediaItems();
        // Hide other views
        placeholderImage.setVisibility(GONE);
        loadingSpinner.setVisibility(GONE);
        exoPlayerView.setVisibility(View.VISIBLE);
        imageView.setVisibility(GONE);
        audioPlaceholder.setVisibility(View.VISIBLE);

        // Prepare ExoPlayer for audio
        MediaItem mediaItem = new MediaItem.Builder().setUri(url).build();

        exoPlayer.setMediaItem(mediaItem);
        exoPlayer.setRepeatMode(Player.REPEAT_MODE_ONE); // loop audio continuously
        exoPlayer.prepare();
        exoPlayer.play();
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (exoPlayer != null) exoPlayer.play();
    }


    @Override
    protected void onStop() {
        super.onStop();
        if (exoPlayer != null) {
            exoPlayer.pause();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
//        apiHandler.removeCallbacks(apiRunnable);
        stopAllContent();

        if (exoPlayer != null) {
            exoPlayer.release();
            exoPlayer = null;
        }
        if (scheduleHandler != null && scheduleRunnable != null) {
            scheduleHandler.removeCallbacks(scheduleRunnable);
        }
        hideWebView();
        VideoCache.release(); // release cache cleanly

    }

    @Override
    public void onMqttMessage(String topic, String message) {
        Log.e("mqttstausupdate", message);

        if (message.equals("GET_STATUS")) {
            Log.e("mqttstausupdate", message);
            if (helperClass.checkInternetConnection(AndroidTVActivity.this)) {
                SendStatusApiCall(sessionId, "1");
            } else {
                Toast.makeText(this, R.string.noInternet, Toast.LENGTH_SHORT).show();
            }
        } else {
            Log.e("mqtthelper", topic + "," + message);

            long now = System.currentTimeMillis();

            if (message.equals(lastMqttMessage) && (now - lastMqttTime) < 3000) {
                Log.e("MQTT", "Duplicate MQTT ignored");
                return;
            }

            lastMqttMessage = message;
            lastMqttTime = now;

            Log.e("MQTT", "Processing MQTT message");

            runOnUiThread(() -> UpdateApicall(sessionId));
        }


        //  fetchAndDisplayContent();

    }

    private void SendStatusApiCall(String sessionId, String number) {
        try {
            String regdetails = ServerURL.GetScreenStatus();
            HashMap<String, String> hashMap = new HashMap<>();
            hashMap.put("userid", sessionId);
            hashMap.put("status", number);
            Log.e("mqttstausupdate", regdetails + "," + sessionId + "," + number);
            NetworkServiceCall serviceCall = new NetworkServiceCall(AndroidTVActivity.this, false);
            serviceCall.setOnServiceCallCompleteListener(new OnServiceCallCompleteListener() {
                @Override
                public void onJSONObjectResponse(JSONObject jsonObject) {

                }

                @Override
                public void onJSONArrayResponse(JSONArray jsonArray) {

                }

                @Override
                public void onErrorResponse(VolleyError error) {

                }
            });
            serviceCall.makeJSONObjectPostStringRequest(regdetails, hashMap, Request.Priority.HIGH);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void UpdateApicall(String sessionId) {
        if (helperClass.checkInternetConnection(AndroidTVActivity.this)) {
            String currentTime = new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date());


            CarteResponse apiSucessResponse = (status, mqttUrl) -> {
                //insertEventDB(name, payload);
                if (status.equals("1")) {

                    mqttUrl = mqttUrl.replace("/:", ":").trim();

//                    if (!mqttUrl.equals(currentMqttUrl)) {
//
//                        currentMqttUrl = mqttUrl;
//
//                        mqttHelper.changeServer(mqttUrl,
//                                new MqttHelper.ConnectionCallback() {
//
//                                    @Override
//                                    public void onConnected() {
//                                        Log.e("MQTT", "Reconnected to new broker");
//                                    }
//
//                                    @Override
//                                    public void onDisconnected() {}
//
//                                    @Override
//                                    public void onError(Throwable e) {
//                                        Log.e("MQTT", "Connection error", e);
//                                    }
//                                });
//                    }


                    // 🔥 1. STOP old scheduler FIRST
                    stopScheduleTimer();

                    // 🔥 2. Mark fresh update
                    scheduleJustUpdated = true;
                    mqttOverrideActive = true;
                    forceContentRefresh = true;
                    // 🔥 3. Re-parse updated schedule_json
                    parseScheduleOnce();

                    // 🔥 4. Play correct content immediately
                    playCurrentScheduleContent();

                    // 🔥 5. Start scheduler cleanly (only ONE instance)
                    startScheduleTimer();


                } else {
                    Toast toast = Toast.makeText(AndroidTVActivity.this, Html.fromHtml("<font color='#F44336' ><b>" + mqttUrl + "</b></font>"), Toast.LENGTH_LONG);
                    toast.setGravity(Gravity.TOP, 0, 0);
                    toast.show();

                    // Toast.makeText(LoginActivity.this, messgae, Toast.LENGTH_SHORT).show();
                }
                Log.e("printtest", status);
            };
            dataSync.MQTTAPicall(sessionId, currentTime, deviceName, availMem, totalMem, apiSucessResponse);
//            new Thread(() -> {
//                          }).start();

        } else {
            Toast.makeText(AndroidTVActivity.this, R.string.noInternet, Toast.LENGTH_SHORT).show();
        }
    }

    private void stopScheduleTimer() {
        isScheduleTimerRunning = false;

        if (scheduleHandler != null && scheduleRunnable != null) {
            scheduleHandler.removeCallbacks(scheduleRunnable);
        }

        scheduleRunnable = null;
    }

    public String getCurrentMqttUrl() {
        return currentMqttUrl;
    }

    public void setCurrentMqttUrl(String currentMqttUrl) {
        this.currentMqttUrl = currentMqttUrl;
    }
}


