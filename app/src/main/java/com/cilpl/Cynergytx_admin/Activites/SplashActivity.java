package com.cilpl.Cynergytx_admin.Activites;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;

import androidx.core.splashscreen.SplashScreen;

import com.bumptech.glide.Glide;
import com.cilpl.Cynergytx_admin.R;

public class SplashActivity extends AppCompatActivity {

    private static final int SPLASH_DISPLAY_LENGTH = 1200;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable navigateRunnable;

    SharedPreferences userdata;
    String userId = "", offlinestatus = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        SplashScreen splashScreen = SplashScreen.installSplashScreen(this);
        super.onCreate(savedInstanceState);
        splashScreen.setKeepOnScreenCondition(() -> false); // dismiss instantly
        setContentView(R.layout.aplash_main);

        userdata = getSharedPreferences("Userdata", MODE_PRIVATE);
        userId = userdata.getString("sessionId", "");
        offlinestatus = userdata.getString("offline", "");

        navigateRunnable = () -> {
            if (isFinishing() || isDestroyed()) return;

            Intent intent;

            if (userId == null || userId.isEmpty()) {
                intent = new Intent(this, LoginActivity.class);
            } else if ("1".equals(offlinestatus)) {
                intent = new Intent(this, SignagePlayerActivity.class);
            } else {
                intent = new Intent(this, FileBrowseActivity.class);
            }

            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_CLEAR_TASK);

            startActivity(intent);

            // 🔥 finish AFTER activity switch
            handler.post(this::finish);
        };

        // Play the client's splash video (res/raw/splash_logo.mp4) if it's present;
        // otherwise fall back to the static logo with the usual short delay.
        // Logo animation as a looping GIF in a plain ImageView (no video surface → no black border).
        int gifId = getResources().getIdentifier("logo_anim", "drawable", getPackageName());
        View splashVideo = findViewById(R.id.splashVideo);
        ImageView splashLogo = findViewById(R.id.splashLogo);
        if (splashVideo != null) splashVideo.setVisibility(View.GONE); // no longer used

        if (gifId != 0 && splashLogo != null) {
            splashLogo.setVisibility(View.VISIBLE);
            // centerCrop = the animation FILLS its box (like a photo in a profile circle),
            // so there is no surrounding area for any border to appear in.
            Glide.with(this).asGif().load(gifId).centerCrop().into(splashLogo);
            // Show the animation (~one loop of the 4s clip), then continue.
            getWindow().getDecorView().post(() -> handler.postDelayed(navigateRunnable, 4200));
        } else {
            getWindow().getDecorView().post(() -> handler.postDelayed(navigateRunnable, SPLASH_DISPLAY_LENGTH));
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(navigateRunnable);
    }
}
