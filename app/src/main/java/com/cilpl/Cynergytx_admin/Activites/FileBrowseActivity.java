package com.cilpl.Cynergytx_admin.Activites;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.provider.Settings;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.cilpl.Cynergytx_admin.R;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.ui.PlayerView;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class FileBrowseActivity extends AppCompatActivity  implements VideoAdapter.OnItemClickListener   {
    private static final int STORAGE_PERMISSION_CODE = 101;
    private RecyclerView recyclerView;
    private VideoAdapter adapter;
    private List<File> videoList = new ArrayList<>();

    private ExoPlayer exoPlayer;
    private PlayerView playerView;
    TextView txtNoFiles;
    private boolean isPlaying = false;
    private boolean isFileOpened = false;

    private SharedPreferences userdata;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_file_browse);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);

        hideSystemUI();
        userdata = getSharedPreferences("Userdata", MODE_PRIVATE);
        recyclerView = findViewById(R.id.recyclerView);
        playerView = findViewById(R.id.exoPlayerView);
         txtNoFiles = findViewById(R.id.txtNoFiles);
        recyclerView.setLayoutManager(new GridLayoutManager(this,2));
        videoList = new ArrayList<>();


        adapter = new VideoAdapter(videoList, (VideoAdapter.OnItemClickListener) this);
        recyclerView.setAdapter(adapter);
        OnBackPressedCallback callback = new OnBackPressedCallback(true /* enabled by default */) {
            @Override
            public void handleOnBackPressed() {
                if (isFileOpened) {
                    // Video is open → go back to grid
                    if (exoPlayer != null) {
                        exoPlayer.stop();
                        exoPlayer.release();
                        exoPlayer = null;
                    }

                    isFileOpened = false;
                    isPlaying = false;

                    playerView.setVisibility(View.GONE);
                    recyclerView.setVisibility(View.VISIBLE);

                } else {
                    // Grid screen → show logout popup
                    ShowExitPopup();
                }
            }
        };
        FileBrowseActivity.this.getOnBackPressedDispatcher().addCallback(this, callback);
        checkStoragePermission();
    }
    private void ShowExitPopup() {

        final Dialog dialog = new Dialog(FileBrowseActivity.this);
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
        title.setText("Are you sure you want to Exit ?");


        alert_Yes.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                SharedPreferences.Editor editor = userdata.edit();
                editor.clear();
                editor.apply();
                Intent i = new Intent(FileBrowseActivity.this, LoginActivity.class);
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
                }
                return true;
            }
        });
        dialog.show();

    }

    private void checkStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                intent.setData(Uri.parse("package:" + getPackageName()));
                startActivity(intent);
            } else {
                loadAllVideos();
            }
        } else {
            if (ContextCompat.checkSelfPermission(this,
                    Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, STORAGE_PERMISSION_CODE);
            } else {
                loadAllVideos();
            }
        }
    }

    private void loadAllVideos() {
        videoList.clear();

        Uri uri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
        String[] projection = {MediaStore.Video.Media.DATA};

        try (android.database.Cursor cursor = getContentResolver().query(uri, projection, null, null, null)) {
            if (cursor != null) {
                int columnIndex = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATA);
                while (cursor.moveToNext()) {
                    String path = cursor.getString(columnIndex);
                    File file = new File(path);

                    // filter only mp4/mkv
                    if (file.exists() && (path.endsWith(".mp4") || path.endsWith(".mkv"))) {
                        videoList.add(file);
                    }
                }
            }
        }
        if (videoList.isEmpty()) {
            recyclerView.setVisibility(View.GONE);
            txtNoFiles.setVisibility(View.VISIBLE);
            txtNoFiles.setText("No videos found");
        } else {
            recyclerView.setVisibility(View.VISIBLE);
            txtNoFiles.setVisibility(View.GONE);
        }
        adapter.notifyDataSetChanged();
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
    private void playVideo(String path) {
        isFileOpened = true;
        isPlaying = true;

        recyclerView.setVisibility(View.GONE);
        playerView.setVisibility(View.VISIBLE);

        if (exoPlayer != null) {
            exoPlayer.release();
        }

        exoPlayer = new ExoPlayer.Builder(this).build();
        playerView.setPlayer(exoPlayer);
        playerView.setUseController(false);

        MediaItem mediaItem = MediaItem.fromUri(Uri.fromFile(new File(path)));
        exoPlayer.setMediaItem(mediaItem);
        exoPlayer.prepare();
        exoPlayer.play();
    }

   // @Override
//    public void onBackPressed() {
//        super.onBackPressed();
//        if (isPlaying) {
//            // Stop video and go back to list
//            if (exoPlayer != null) {
//                exoPlayer.stop();
//                exoPlayer.release();
//                exoPlayer = null;
//            }
//            isPlaying = false;
//            playerView.setVisibility(PlayerView.GONE);
//            recyclerView.setVisibility(RecyclerView.VISIBLE);
//        } else {
//            // Back → go to Login screen
//            startActivity(new Intent(this, LoginActivity.class));
//            finish();
//        }
//    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (exoPlayer != null) {
            exoPlayer.release();
            exoPlayer = null;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == STORAGE_PERMISSION_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                loadAllVideos();
            } else {
                Toast.makeText(this, "Storage permission required", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    public void onItemClick(File file) {
        playVideo(file.getAbsolutePath());
    }
}