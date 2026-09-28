package com.cilpl.Cynergytx_admin.Activites;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Window;
import android.widget.Button;
import android.widget.ImageView;

import com.bumptech.glide.Glide;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.ui.PlayerView;
import com.google.android.exoplayer2.upstream.RawResourceDataSource;
import android.text.Html;
import android.util.Log;
import android.util.Patterns;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.android.volley.VolleyError;
import com.cilpl.Cynergytx_admin.Activites.Interfaces.CarteResponse;
import com.cilpl.Cynergytx_admin.NetworkCalling.HelperClass;
import com.cilpl.Cynergytx_admin.NetworkCalling.OnServiceCallCompleteListener;
import com.cilpl.Cynergytx_admin.R;
import com.cilpl.Cynergytx_admin.Services.ApiDataSync;
import com.cilpl.Cynergytx_admin.utils.ButtonEditTwCenMTFont;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.messaging.FirebaseMessaging;

import org.json.JSONArray;
import org.json.JSONObject;

public class LoginActivity extends AppCompatActivity  {
    ButtonEditTwCenMTFont signbtn;
    EditText password_id, email_id;
    TextView offlineplayer, forgetpasssowrd;
    String deviceid = "", email, pass, authtoken = "";
    HelperClass helperClass;
    SharedPreferences userdata;
    ApiDataSync dataSync;
    MqttHelper mqttHelper;
    String topicname="mysign/chat/";
    CharSequence originalBtnText = "";
    Drawable originalBtnBackground;
    int originalBtnTextColor;
    Dialog loadingDialog;
    com.cilpl.Cynergytx_admin.utils.StripedProgressView loadingRing;
    android.animation.ValueAnimator loadingAnim;
    ExoPlayer logoPlayer;
    private final Handler uiHandler = new Handler(Looper.getMainLooper());

    // ---- Connectivity status, Wi-Fi shortcut and inline activation error ----
    private View activationErrorBox, offlineMessage, configureWifiBtn;
    private TextView activationErrorText, backOnlineStrip;
    // Connectivity is decided by an ACTUAL reachability probe, not the system's "validated"
    // flag: when the router loses internet but the box stays associated to the access point,
    // that flag stays stale for a long time and reports a false Online.
    private boolean networkPresent = false;    // an interface exists (instant, from the OS)
    private Boolean internetReachable = null;  // null = not probed yet; from NetworkSpeedMonitor
    private boolean inForeground = false;
    private boolean awaitingWifiReturn = false;
    private boolean pendingBackOnline = false;
    private final Handler netHandler = new Handler(Looper.getMainLooper());
    private android.net.ConnectivityManager connMgr;
    private android.net.ConnectivityManager.NetworkCallback netCallback;
    private Boolean lastOnline = null;               // null = not evaluated yet
    private static final long BACK_ONLINE_MS = 3000L; // how long the green strip stays up

    // ---- Low internet indicator (same behaviour as the player screen) ----
    private ImageView lowSpeedDot;
    private com.cilpl.Cynergytx_admin.signage.net.NetworkSpeedMonitor speedMonitor;
    private final Handler dotHandler = new Handler(Looper.getMainLooper());
    private boolean lowActive = false;
    private static final long DOT_VISIBLE_MS = 10_000L;  // icon stays on screen 10s
    private static final long DOT_HIDDEN_MS = 20_000L;   // then hides 20s -> ~2 blinks per minute

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login2);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);

        hideSystemUI();
        InitViews();
        onClicks();
        startSpeedMonitor();
        initConnectivity();
    }

    // ---------------- Connectivity status + Wi-Fi shortcut ----------------

    private void initConnectivity() {
        offlineMessage = findViewById(R.id.offlineMessage);
        configureWifiBtn = findViewById(R.id.configureWifiBtn);
        backOnlineStrip = findViewById(R.id.backOnlineStrip);
        activationErrorBox = findViewById(R.id.activationErrorBox);
        activationErrorText = findViewById(R.id.activationErrorText);

        if (configureWifiBtn != null) configureWifiBtn.setOnClickListener(v -> openWifiSettings());

        connMgr = (android.net.ConnectivityManager)
                getSystemService(Context.CONNECTIVITY_SERVICE);
        registerNetworkCallback();
        networkPresent = hasNetwork();
        applyConnectivity();
    }

    /** Combines the OS view (is there a network at all?) with the probe (does it reach the internet?). */
    private boolean computeOnline() {
        if (!networkPresent) return false;                 // no interface -> definitely offline
        if (internetReachable == null) return isOnline();  // not probed yet -> best available guess
        return internetReachable;
    }

    private void applyConnectivity() { updateConnectivity(computeOnline()); }

    /** True when any usable network interface exists (instant, but says nothing about the internet). */
    private boolean hasNetwork() {
        try {
            if (connMgr == null) return false;
            android.net.Network n = connMgr.getActiveNetwork();
            if (n == null) return false;
            android.net.NetworkCapabilities c = connMgr.getNetworkCapabilities(n);
            return c != null
                    && c.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET);
        } catch (Throwable t) {
            return false;
        }
    }

    /** The system's own opinion. Used only until the first probe result arrives. */
    private boolean isOnline() {
        try {
            if (connMgr == null) return false;
            android.net.Network n = connMgr.getActiveNetwork();
            if (n == null) return false;
            android.net.NetworkCapabilities c = connMgr.getNetworkCapabilities(n);
            return c != null
                    && c.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    && c.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_VALIDATED);
        } catch (Throwable t) {
            return false;
        }
    }

    /** Live connectivity updates while the screen is open (the system calls these off the main thread). */
    private void registerNetworkCallback() {
        if (connMgr == null || netCallback != null) return;
        netCallback = new android.net.ConnectivityManager.NetworkCallback() {
            @Override public void onAvailable(@NonNull android.net.Network network) { post(); }
            @Override public void onLost(@NonNull android.net.Network network) { post(); }
            @Override public void onCapabilitiesChanged(@NonNull android.net.Network network,
                                                        @NonNull android.net.NetworkCapabilities caps) { post(); }
            private void post() {
                netHandler.post(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    boolean present = hasNetwork();
                    if (!present) internetReachable = false;  // interface gone -> offline right away
                    networkPresent = present;
                    applyConnectivity();
                    // A network just appeared: probe now rather than waiting for the timer.
                    if (present && speedMonitor != null) speedMonitor.pokeNow();
                });
            }
        };
        try {
            connMgr.registerNetworkCallback(
                    new android.net.NetworkRequest.Builder()
                            .addCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
                            .build(),
                    netCallback);
        } catch (Throwable t) {
            netCallback = null;   // fall back to the onResume re-check
        }
    }

    /**
     * Applies the current connection state to the screen: status indicator, the offline guidance
     * and Wi-Fi button, and the transient "Back online" strip when the connection is restored.
     */
    private void updateConnectivity(boolean online) {
        boolean wasOffline = (lastOnline != null && !lastOnline);
        lastOnline = online;

        // There is no "Online" badge: the connection is only called out when it is a problem
        // (the offline guidance) or when it has just been fixed (the green strip). While
        // offline the existing no_wifi corner icon is the standing indicator.
        int offVis = online ? View.GONE : View.VISIBLE;
        if (offlineMessage != null) offlineMessage.setVisibility(offVis);
        if (configureWifiBtn != null) configureWifiBtn.setVisibility(offVis);

        if (online && wasOffline) {
            // Back after being sent to the Wi-Fi settings: pull this screen forward again so
            // the user does not have to find their own way back.
            if (awaitingWifiReturn && !inForeground) bringToFront();
            showBackOnline();
        }
    }

    private final Runnable hideBackOnline = new Runnable() {
        @Override public void run() {
            if (backOnlineStrip != null) backOnlineStrip.setVisibility(View.GONE);
            // Let the low-speed icon resume its own cycle once the strip is out of the way.
            if (lowActive && lowSpeedDot != null) lowSpeedDot.setVisibility(View.VISIBLE);
        }
    };

    private void showBackOnline() {
        if (backOnlineStrip == null) return;
        if (!inForeground) { pendingBackOnline = true; return; }  // show it once we are visible
        pendingBackOnline = false;
        // The strip occupies the bottom edge, where the low-speed icon also sits: hide the icon
        // for the few seconds the strip is up so the two never overlap.
        if (lowSpeedDot != null) lowSpeedDot.setVisibility(View.GONE);
        backOnlineStrip.setVisibility(View.VISIBLE);
        netHandler.removeCallbacks(hideBackOnline);
        netHandler.postDelayed(hideBackOnline, BACK_ONLINE_MS);
    }

    /**
     * Opens the Wi-Fi settings. Android TV boxes and phones expose this differently, so the
     * options are tried in order and the first one the device accepts is used.
     */
    private void openWifiSettings() {
        awaitingWifiReturn = true;
        String[] actions = {
                android.provider.Settings.ACTION_WIFI_SETTINGS,        // phones + most TV boxes
                "android.settings.panel.action.WIFI",                  // Android 10+ slide-up panel
                android.provider.Settings.ACTION_WIRELESS_SETTINGS,
                android.provider.Settings.ACTION_SETTINGS              // last resort
        };
        for (String action : actions) {
            try {
                // Deliberately NO FLAG_ACTIVITY_NEW_TASK: keeping the settings screen in this
                // app's task is what lets us legally call ourselves back to the front when the
                // connection returns (Android 10+ blocks background activity starts otherwise).
                startActivity(new Intent(action));
                return;
            } catch (Throwable ignored) { }
        }
        Toast.makeText(this, "Unable to open Wi-Fi settings", Toast.LENGTH_SHORT).show();
    }

    /**
     * Brings this screen back to the front after the user has enabled Wi-Fi in the settings.
     * Best effort: it works when the settings screen opened inside this app's task. Some
     * devices force it into a task of its own, and there the user simply presses Back --
     * onResume() re-checks the connection either way, so nothing is missed.
     */
    private void bringToFront() {
        try {
            Intent i = new Intent(this, LoginActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(i);
        } catch (Throwable ignored) { }
    }

    @Override
    protected void onPause() {
        super.onPause();
        inForeground = false;
    }

    @Override
    protected void onResume() {
        super.onResume();
        inForeground = true;
        awaitingWifiReturn = false;
        if (pendingBackOnline) showBackOnline();   // connection returned while we were hidden
        // The user may have just come back from the Wi-Fi settings: re-check and update.
        hideSystemUI();   // settings may have restored the system bars
        if (connMgr != null) {
            networkPresent = hasNetwork();
            applyConnectivity();
        }
        if (speedMonitor != null) speedMonitor.pokeNow();   // confirm with a fresh probe
    }

    // ---------------- Low internet indicator ----------------

    /**
     * Watches the connection while the activation screen is on display and shows the same
     * bottom-right "no wifi" icon the player screen uses, so the installer can tell straight
     * away that the box has no usable internet before entering the activation code.
     */
    private void startSpeedMonitor() {
        lowSpeedDot = findViewById(R.id.lowSpeedDot);
        if (lowSpeedDot == null) return;
        speedMonitor = new com.cilpl.Cynergytx_admin.signage.net.NetworkSpeedMonitor(
                this, (mbps, linkSpeed) -> {
            if (isFinishing() || isDestroyed()) return;
            // <= 5 Mbps (which also covers offline, reported as -1) -> show the icon.
            if (mbps <= 5.0) startLowSpeedIndicator();
            else stopLowSpeedIndicator();

            // The probe actually fetches bytes over the internet, so a failure (-1) means
            // genuinely unreachable even while the box is still associated to the Wi-Fi.
            internetReachable = mbps >= 0;
            applyConnectivity();
        });
        speedMonitor.start();
    }

    // The icon blinks: visible 10s, hidden 20s (~2 times a minute) while the connection is low.
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

    private void startLowSpeedIndicator() {
        if (lowActive) return;
        lowActive = true;
        dotHandler.removeCallbacks(dotShowStep);
        dotHandler.removeCallbacks(dotHideStep);
        dotHandler.post(dotShowStep);   // show immediately on the first low reading
    }

    private void stopLowSpeedIndicator() {
        lowActive = false;
        dotHandler.removeCallbacks(dotShowStep);
        dotHandler.removeCallbacks(dotHideStep);
        if (lowSpeedDot != null) lowSpeedDot.setVisibility(View.GONE);
    }

    private void onClicks() {
        signbtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                clearActivationError();
                if (Validation()) {
                    // signbtn.setBackground(getResources().getDrawable(R.drawable.btn_bg));
//
                    if (helperClass.checkInternetConnection(LoginActivity.this)) {

                        // Give visual feedback and block repeated taps while the request is in flight.
                        setSubmitting(true);

                        CarteResponse apiSucessResponse = (status, messgae) -> {

                            if ("1".equals(status)) {

                                // SUCCESS → keep the button green + "Done" all the way until we
                                // navigate. Do NOT revert to "Submit" (that caused the flicker).
                                Class<?> next = "1".equals(messgae)
                                        ? SignagePlayerActivity.class : FileBrowseActivity.class;
                                Intent intent = new Intent(LoginActivity.this, next);
                                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                                startActivity(intent);
                                finish();

                            } else {

                                // FAILURE (e.g. DUPLICATE_LOGIN) → revert the button so the user can
                                // retry, then show the server's activation error message.
                                setSubmitting(false);
                                showActivationError(messgae);
                            }

                            Log.e("printtest", status);
                        };

                        dataSync.LoginAPicall(email, pass, apiSucessResponse);

                    } else {

                        Toast.makeText(LoginActivity.this, R.string.noInternet, Toast.LENGTH_SHORT).show();
                    }
//
//
//
//
//
//                    Intent i = new Intent(LoginActivity.this, AndroidTVActivity.class);
//                startActivity(i);
                }
//                Intent i = new Intent(LoginActivity.this, HomeMainActivity.class);
//                startActivity(i);

//
            }


        });

        offlineplayer.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent i = new Intent(LoginActivity.this, FileBrowseActivity.class);
                startActivity(i);
            }
        });

//        signupPage.setOnClickListener(new View.OnClickListener() {
//            @Override
//            public void onClick(View view) {
////                Intent i = new Intent(LoginActivity.this, SignUpActivity.class);
////                startActivity(i);
//                //finish();
//            }
//        });
//        forgetpasssowrd.setOnClickListener(new View.OnClickListener() {
//            @Override
//            public void onClick(View view) {
////                Intent i = new Intent(LoginActivity.this, ForgetPasswordActivity.class);
////                i.putExtra("pass", "forget");
////                startActivity(i);
//
//
//                Intent i = new Intent(LoginActivity.this, AndroidTVActivity.class);
//                i.putExtra("pass", "forget");
//                startActivity(i);
//            }
//        });


    }
    /**
     * On submit: turn the button GREEN and change the text to "Done" (and block repeated taps).
     * On completion/error: revert to the original button so the user can act again.
     */
    private void setSubmitting(boolean submitting) {
        uiHandler.removeCallbacksAndMessages(null);
        if (submitting) {
            // Feedback while the login request runs = the button turns green and says "Done".
            // No separate loader popup here — the ONLY loader the user sees is the single
            // download-progress loader on the player screen (avoids two confusing loaders).
            signbtn.setEnabled(false);
            signbtn.setText("Done");
            signbtn.setTextColor(Color.WHITE);
            // Rounded green background (same corner radius as the submit button).
            signbtn.setBackground(androidx.core.content.ContextCompat.getDrawable(
                    this, R.drawable.btn_done_green));
            // Safety net: revert if no response arrives (e.g. server error with no callback).
            uiHandler.postDelayed(() -> setSubmitting(false), 20000);
        } else {
            signbtn.setEnabled(true);
            if (originalBtnText != null && originalBtnText.length() > 0) signbtn.setText(originalBtnText);
            signbtn.setTextColor(originalBtnTextColor);
            if (originalBtnBackground != null) signbtn.setBackground(originalBtnBackground);
        }
    }

    private static final int LOADING_GREEN = Color.parseColor("#4CAF50");

    /**
     * Popup shown while the login request runs: a GREEN horizontal candy-stripe progress bar with
     * the percentage in the centre, under a "Saving, please wait..." label, inside a rounded white
     * card. A login has no measurable percentage, so the bar animates smoothly to ~95% while the
     * request is pending and snaps to 100% on success (see {@link #finishLoginProgress()}).
     */
    private void showLoginProgress(boolean show) {
        if (!show) { finishLoginProgress(); return; }

        if (loadingDialog == null) {
            float d = getResources().getDisplayMetrics().density;

            LinearLayout box = new LinearLayout(this);
            box.setOrientation(LinearLayout.VERTICAL);
            box.setGravity(Gravity.CENTER);
            int pad = (int) (28 * d);
            box.setPadding(pad, pad, pad, pad);
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(Color.WHITE);
            bg.setCornerRadius(24 * d);
            box.setBackground(bg);
            box.setElevation(8 * d);

            TextView label = new TextView(this);
            label.setText("Done, please wait...");
            label.setTextColor(LOADING_GREEN);
            label.setTextSize(18);
            label.setGravity(Gravity.CENTER);
            box.addView(label, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));

            loadingRing = new com.cilpl.Cynergytx_admin.utils.StripedProgressView(this);
            loadingRing.setColors(LOADING_GREEN, Color.parseColor("#66BB6A"),
                    Color.parseColor("#E3E3E3"), Color.WHITE);
            LinearLayout.LayoutParams barLp = new LinearLayout.LayoutParams(
                    (int) (280 * d), (int) (34 * d));
            barLp.topMargin = (int) (18 * d);
            box.addView(loadingRing, barLp);

            loadingDialog = new Dialog(this);
            loadingDialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
            loadingDialog.setContentView(box);
            if (loadingDialog.getWindow() != null) {
                loadingDialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            }
            loadingDialog.setCancelable(false);
            loadingDialog.setCanceledOnTouchOutside(false);
        }

        // Reset to 0% and animate up to 95% while the network call is in flight.
        if (loadingAnim != null) loadingAnim.cancel();
        loadingRing.setProgress(0f);
        loadingAnim = android.animation.ValueAnimator.ofFloat(0f, 95f);
        loadingAnim.setDuration(6000);
        loadingAnim.setInterpolator(new android.view.animation.DecelerateInterpolator());
        loadingAnim.addUpdateListener(a -> {
            if (loadingRing != null) loadingRing.setProgress((float) a.getAnimatedValue());
        });
        loadingAnim.start();

        if (!isFinishing() && !loadingDialog.isShowing()) loadingDialog.show();
    }

    /** Complete the ring at 100% briefly, then dismiss the popup. */
    private void finishLoginProgress() {
        if (loadingAnim != null) { loadingAnim.cancel(); loadingAnim = null; }
        if (loadingDialog == null || !loadingDialog.isShowing()) return;
        if (loadingRing != null) loadingRing.setProgress(100f);
        uiHandler.postDelayed(() -> {
            if (loadingDialog != null && loadingDialog.isShowing() && !isFinishing()) loadingDialog.dismiss();
        }, 250);
    }

    /**
     * Shows the activation failure inline, directly under the submit button: a red error icon
     * with the server's message. Replaces the old full-screen popup, which needed a remote
     * click to dismiss and covered the code the user was trying to correct.
     */
    private void showActivationError(String message) {
        if (isFinishing() || isDestroyed()) return;
        if (message == null || message.trim().isEmpty()) {
            message = "Invalid Activation code";
        }
        if (activationErrorBox == null || activationErrorText == null) return;
        activationErrorText.setText(message.trim());
        activationErrorBox.setVisibility(View.VISIBLE);
    }

    /** Clears the inline error (on a new attempt, or as soon as the code is edited). */
    private void clearActivationError() {
        if (activationErrorBox != null) activationErrorBox.setVisibility(View.GONE);
    }

    /** Show the client's logo video (res/raw/splash_logo.mp4) looping seamlessly & muted; else the static logo. */
    /**
     * Show the client's logo animation as a looping GIF in a plain ImageView.
     * No video surface, so there is no black flash/border ever, and it loops seamlessly.
     */
    private void setupLogoVideo() {
        int gifId = getResources().getIdentifier("logo_anim", "drawable", getPackageName());
        ImageView logoImg = findViewById(R.id.loginLogo);
        View logoVideo = findViewById(R.id.loginLogoVideo);
        if (logoVideo != null) logoVideo.setVisibility(View.GONE); // no longer used

        if (gifId == 0 || logoImg == null) return; // keep the static logo
        logoImg.setVisibility(View.VISIBLE);
        // centerCrop = the animation FILLS its box (like a photo in a profile circle),
        // so there is no surrounding area for any border to appear in.
        Glide.with(this).asGif().load(gifId).centerCrop().into(logoImg);
    }

    @SuppressLint("InlinedApi")
    private void hideSystemUI() {
        View decorView = getWindow().getDecorView();
        decorView.setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
        );
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        uiHandler.removeCallbacksAndMessages(null);
        // Stop the probe thread and the blink timer before the player screen starts its own monitor.
        dotHandler.removeCallbacksAndMessages(null);
        netHandler.removeCallbacksAndMessages(null);
        lowActive = false;
        if (speedMonitor != null) { speedMonitor.stop(); speedMonitor = null; }
        if (connMgr != null && netCallback != null) {
            try { connMgr.unregisterNetworkCallback(netCallback); } catch (Throwable ignored) { }
            netCallback = null;
        }
        if (loadingAnim != null) { loadingAnim.cancel(); loadingAnim = null; }
        if (loadingDialog != null && loadingDialog.isShowing()) loadingDialog.dismiss();
        if (logoPlayer != null) {
            logoPlayer.release();
            logoPlayer = null;
        }
    }

    private boolean Validation() {
        email = email_id.getText().toString();


        if (email.isEmpty() || email.equals(" ")) {
            email_id.setError("Enter Your Activation Code");
            return false;
        }


        return true;
    }

    private void InitViews() {
        helperClass = new HelperClass(this);
        dataSync = new ApiDataSync(this);
        userdata = getSharedPreferences("Userdata", MODE_PRIVATE);
        authtoken = userdata.getString("authToken", "");
        signbtn = findViewById(R.id.signinbtn);
        originalBtnText = signbtn.getText();
        originalBtnBackground = signbtn.getBackground();
        originalBtnTextColor = signbtn.getCurrentTextColor();
//        signupPage = findViewById(R.id.signupPage);
        email_id = findViewById(R.id.email_id);

        // Editing the code clears a previous failure, so the message never contradicts the field.
        email_id.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { clearActivationError(); }
            @Override public void afterTextChanged(android.text.Editable s) { }
        });

        setupLogoVideo();
        offlineplayer = findViewById(R.id.offlineplayer);

//        startService(new Intent(this, RootAutoUpdateService.class));




//          mqttHelper.publish("cydef_test", "Incoming: " + System.currentTimeMillis());

//        password_id = findViewById(R.id.password_id);


        FirebaseMessaging.getInstance().getToken().addOnCompleteListener(new OnCompleteListener<String>() {
            @Override
            public void onComplete(@NonNull Task<String> task) {
                if (!task.isSuccessful()) {
                    Log.w("Token", "Fetching FCM registration token failed", task.getException());
                    return;
                }

                // Get new FCM registration token
                String token = task.getResult();

                // Log and toast
                String msg = token;
                deviceid = msg;
                Log.d("deviceid=", msg);
                //Toast.makeText(Login_Activity.this, msg, Toast.LENGTH_SHORT).show();
            }
        });

    }


    private class LoginActivityServiceCallCompleteListener implements OnServiceCallCompleteListener {
        @Override
        public void onJSONObjectResponse(JSONObject jsonObject) {
            try {
                //   Log.e("signupresponse", jsonObject.toString());
                String status = jsonObject.getString("status");
                String errorcode = jsonObject.getString("errorCode");
                String message = jsonObject.getString("message");
                if (status.equals("1")) {
                    String authtokenmessage = jsonObject.getString("authToken");
                    String uid = jsonObject.getString("id");
                    SharedPreferences.Editor editor = userdata.edit();
                    editor.putString("authToken", authtokenmessage);
                    editor.putString("userId", uid);
                    editor.putString("deviceId", deviceid);
                    editor.apply();
                    Intent i = new Intent(LoginActivity.this, AndroidTVActivity.class);
                    startActivity(i);
                    finish();
                }


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
    }
}