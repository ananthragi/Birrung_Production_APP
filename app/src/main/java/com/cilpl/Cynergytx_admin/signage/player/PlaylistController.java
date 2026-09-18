package com.cilpl.Cynergytx_admin.signage.player;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.pdf.PdfRenderer;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.FrameLayout;
import android.widget.ImageView;

import androidx.annotation.NonNull;

import com.bumptech.glide.Glide;
import com.cilpl.Cynergytx_admin.signage.model.MediaFile;
import com.cilpl.Cynergytx_admin.signage.model.Playlist;
import com.cilpl.Cynergytx_admin.signage.storage.LocalStorageManager;
import com.cilpl.Cynergytx_admin.signage.storage.MediaDownloadManager;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.PlaybackException;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.ui.PlayerView;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Plays a playlist, LOCAL-FIRST:
 *   - If a file is downloaded, it plays from local storage (the normal 99% mode) —
 *     images for {@code imageDurationSec} (default 8s), videos full length (single
 *     video loops seamlessly), PDFs rendered to the screen.
 *   - If a file could NOT be stored locally (storage full → "streamable"), it STREAMS
 *     from the HTTPS URL instead.
 *   - While streaming a VIDEO, if bandwidth drops below 5 Mbps the controller asks the
 *     host for a fallback image and shows it, then resumes streaming when speed recovers.
 *
 * All playback control happens on the main thread.
 */
public class PlaylistController {

    private static final String TAG = "PlaylistController";
    private static final double THRESHOLD_MBPS = 5.0; // minimum speed to stream video

    public interface Callback {
        void onNothingToPlay();
        /** Real content (or the holding frame) is on screen — hide loader / "no content" overlay. */
        void onContentPlaying();
        /** FIRST-TIME: nothing is playing yet and content is still downloading → full-screen loader. */
        void onWaitingForContent();
        /** UPDATE: old content keeps playing while new content downloads → small corner loader. */
        void onNewContentLoading();
        /** A local file failed to play (corrupt/partial) → host should re-sync (re-download) it. */
        void onContentError();
        /** Streaming video + bandwidth <= 5 Mbps: host may refresh the holding frame from the API. */
        void onLowBandwidth();
        /** Tell the host whether we're streaming a video now (so the speed monitor can probe throughput). */
        void onStreamingModeChanged(boolean streaming, String probeUrl);
    }

    private final Context ctx;
    private final ExoPlayer player;
    private final PlayerView playerView;
    private final ImageView imageView;
    private final LocalStorageManager storage;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Callback callback;

    private List<MediaFile> items = new ArrayList<>();
    private MediaFile currentFile;            // the item currently on screen (for error recovery)
    private int index = 0;
    private String currentPlaylistId = "";
    private String currentSignature = "";
    private String waitingSignature = "";     // content we're waiting to download (showing holding frame)
    private String holdingFramePath = null;   // local path of the "safe frame" from accessMediaFiles
    private String fallbackFileName = null;   // name of an on-disk file being shown as an offline fallback
    private ViewGroup webContainer;           // host for the YouTube player (streamed, never downloaded)
    private WebView webView;
    private boolean running = false;
    private volatile boolean released = false;

    // Files that must be STREAMED because they couldn't be stored locally (storage full).
    private final Set<String> streamable = new HashSet<>();
    private final Map<String, String> streamUrls = new HashMap<>();

    // Streaming-video bandwidth state.
    private volatile double lastMbps = -1;
    private boolean streamingVideoActive = false;
    private boolean showingFallback = false;
    private MediaFile streamingVideoFile;

    private final Runnable advance = this::next;
    // Replay whatever content is currently active (scheduled item or offline fallback) after a
    // transient playback error — WITHOUT deleting the file.
    private final Runnable retryCurrent = new Runnable() {
        @Override public void run() {
            if (released || !running) return;
            if (currentFile != null) playCurrent();
            else if (fallbackFileName != null) {
                File f = new File(storage.getCompanyDir(), fallbackFileName);
                currentSignature = ""; // force playLocalFallback to re-prepare
                playLocalFallback(f);
            }
        }
    };
    private final Player.Listener videoListener;

    public PlaylistController(Context ctx, ExoPlayer player, PlayerView playerView,
                              ImageView imageView, LocalStorageManager storage, Callback cb) {
        this.ctx = ctx;
        this.player = player;
        this.playerView = playerView;
        this.imageView = imageView;
        this.storage = storage;
        this.callback = cb;

        videoListener = new Player.Listener() {
            @Override public void onRenderedFirstFrame() {
                // The video is actually painted now → reveal it and hide the holding image.
                // Until this fires we keep the holding image on top, so a buffering large
                // video shows no black/blank gap.
                imageView.setVisibility(View.GONE);
                if (callback != null) callback.onContentPlaying();
            }
            @Override public void onPlaybackStateChanged(int state) {
                if (running && state == Player.STATE_ENDED) next();
            }
            @Override public void onPlayerError(@NonNull PlaybackException error) {
                Log.e(TAG, "Video playback error", error);
                if (!running) return;
                handler.removeCallbacks(advance); // never tight-loop on a broken file

                boolean online = isOnline();

                if (currentFile != null && storage.isDownloaded(currentFile)) {
                    if (online) {
                        // ONLINE: the local file may really be corrupt → delete it so the sync layer
                        // re-downloads a fresh copy.
                        try { storage.localFile(currentFile).delete(); } catch (Exception ignored) { }
                        currentSignature = "";
                        if (callback != null) callback.onContentError();
                    } else {
                        // OFFLINE: NEVER delete a downloaded file we cannot re-fetch — a transient
                        // cold-start playback error must not destroy the only content on the device.
                        // Just retry playing the same file after a short pause.
                        handler.postDelayed(retryCurrent, 2500L);
                    }
                } else if (fallbackFileName != null) {
                    // The offline-fallback file hiccuped → retry it (never delete it).
                    handler.postDelayed(retryCurrent, 2500L);
                } else {
                    // A stream URL failed → back off, then try the next item.
                    handler.postDelayed(advance, 3000L);
                }
            }
        };
        player.addListener(videoListener);
    }

    /** Files (by local name) that must be streamed because they can't be stored locally. */
    public void setStreamable(Set<String> fileNames, Map<String, String> fileNameToUrl) {
        streamable.clear();
        streamUrls.clear();
        if (fileNames != null) streamable.addAll(fileNames);
        if (fileNameToUrl != null) streamUrls.putAll(fileNameToUrl);
    }

    public void setPlaylist(Playlist p) {
        if (released) return;
        if (p == null || p.files.isEmpty()) {
            stop();
            if (callback != null) callback.onNothingToPlay();
            return;
        }

        String sig = signatureOf(p);

        if (hasPlayableItem(p)) {
            // Content is fully downloaded & verified → play / switch to it.
            if (sig.equals(currentSignature) && running) {
                if (!waitingSignature.isEmpty()) {        // any pending update finished → clear the hint
                    waitingSignature = "";
                    if (callback != null) callback.onContentPlaying();
                }
                return;
            }
            currentSignature = sig;
            waitingSignature = "";
            fallbackFileName = null;   // leaving any offline fallback → real scheduled content now
            currentPlaylistId = p.id;
            items = new ArrayList<>(p.files);
            index = 0;
            running = true;
            playCurrent();
            return;
        }

        // New content has been detected but is NOT downloaded/verified yet.
        if (isPlayingRealContent()) {
            // UPDATE: keep the CURRENT content playing WITHOUT interruption; only show a small
            // "New content loading..." indicator. We switch to the new content later (via
            // onFileReady → setPlaylist) once it is fully downloaded & verified. Old content
            // stays on screen for the whole download (20s / 2min / longer).
            if (!sig.equals(waitingSignature)) {
                waitingSignature = sig;
                if (callback != null) callback.onNewContentLoading();
            }
            // Do NOT stop the player and do NOT change currentSignature.
        } else {
            // FIRST-TIME (nothing playing yet): show the full-screen loader and wait for the
            // download to finish before playing. Never play a partial file.
            if (!sig.equals(waitingSignature)) {
                waitingSignature = sig;
                currentSignature = "";
                running = false;
                handler.removeCallbacks(advance);
                player.setPlayWhenReady(false);
                player.stop();
                hideWeb();
                if (callback != null) callback.onWaitingForContent();
            }
        }
    }

    /** True when real content (scheduled OR an offline fallback) is on screen (must not be interrupted). */
    private boolean isPlayingRealContent() {
        return running && ((currentFile != null && isPlayable(currentFile)) || fallbackFileName != null);
    }

    public void refreshAvailability() {
        if (running && !isShowingSomething()) playCurrent();
    }

    public String getCurrentPlaylistId() { return currentPlaylistId; }

    /** Local filename of the content currently on screen (so sync never deletes it). "" if none. */
    public String getCurrentFileName() {
        if (running && currentFile != null) return currentFile.localFileName();
        if (running && fallbackFileName != null) return fallbackFileName;
        return "";
    }

    /**
     * OFFLINE FALLBACK: play a raw file already on disk (looping video / static image), bypassing the
     * schedule. Used when the newly-scheduled content isn't downloaded yet and there's no internet, so
     * the screen keeps showing real content instead of the "I'M OFFLINE" image. It is replaced the
     * moment the scheduled content finishes downloading (setPlaylist switches away from it).
     */
    public void playLocalFallback(File file) {
        if (released || file == null || !file.exists()) return;
        hideWeb();
        String sig = "fallback:" + file.getName();
        if (sig.equals(currentSignature) && running) return; // already showing this fallback
        handler.removeCallbacks(advance);
        currentSignature = sig;
        waitingSignature = "";
        currentFile = null;
        fallbackFileName = file.getName();
        running = true;

        String n = file.getName().toLowerCase();
        if (isVideoFileName(n)) {
            playerView.setVisibility(View.VISIBLE);
            player.setRepeatMode(Player.REPEAT_MODE_ONE); // loop the fallback video seamlessly
            player.stop();
            player.clearMediaItems();
            player.setMediaItem(MediaItem.fromUri(Uri.fromFile(file)));
            player.prepare();
            player.setPlayWhenReady(true);
            // onRenderedFirstFrame hides the image overlay and calls onContentPlaying().
        } else {
            player.setPlayWhenReady(false);
            player.stop();
            playerView.setVisibility(View.GONE);
            imageView.setVisibility(View.VISIBLE);
            Glide.with(ctx.getApplicationContext()).load(file).into(imageView);
            if (callback != null) callback.onContentPlaying();
        }
    }

    /** Called by the host whenever a fresh network speed reading arrives (main thread). */
    public void onNetworkSpeed(double mbps) {
        lastMbps = mbps;
        if (!streamingVideoActive || released) return;

        if (mbps >= 0 && mbps <= THRESHOLD_MBPS) {
            // Too slow to stream video → switch to the holding frame (once).
            if (!showingFallback) {
                showingFallback = true;
                Log.e(TAG, "Bandwidth " + mbps + " Mbps <= " + THRESHOLD_MBPS + " → holding frame");
                player.stop();
                showHoldingFrame();                                  // show local safe frame immediately
                if (callback != null) callback.onLowBandwidth();     // host may refresh it from the API
            }
        } else if (mbps > THRESHOLD_MBPS) {
            // Recovered → resume streaming the video automatically.
            if (showingFallback) {
                showingFallback = false;
                Log.e(TAG, "Bandwidth recovered (" + mbps + " Mbps) → resume streaming");
                if (streamingVideoFile != null) startVideoStream(streamingVideoFile.url);
            }
        }
    }

    /** Set the locally-downloaded "safe frame" (from accessMediaFiles) used as the holding image. */
    public void setHoldingFrame(String localPath) {
        this.holdingFramePath = localPath;
    }

    /** Refresh the holding image on screen if we're not currently playing real content. */
    public void showHoldingFrameIfIdle() {
        if (!running) showHoldingFrame();
    }

    /**
     * Show the holding frame: the new content's first frame downloaded from accessMediaFiles.
     * Used while content is downloading, on an update, and during low-bandwidth streaming.
     * If no frame is available yet, ask the host to show the loader (never blank, never stale).
     */
    private void showHoldingFrame() {
        if (released) return;
        handler.post(() -> {
            if (released) return;
            if (holdingFramePath != null && !holdingFramePath.isEmpty()
                    && new File(holdingFramePath).exists()) {
                player.setPlayWhenReady(false);
                player.stop();
                playerView.setVisibility(View.GONE);
                imageView.setVisibility(View.VISIBLE);
                Glide.with(ctx.getApplicationContext()).load(new File(holdingFramePath)).into(imageView);
                if (callback != null) callback.onContentPlaying(); // hide loader / "no content"
            } else {
                // No holding frame → hide the (stopped) player and stale image so nothing white/stale
                // shows behind the loader, then ask the host to show the loading indicator.
                player.setPlayWhenReady(false);
                player.stop();
                playerView.setVisibility(View.GONE);
                imageView.setVisibility(View.GONE);
                if (callback != null) callback.onWaitingForContent(); // no frame yet → loader
            }
        });
    }

    // ---------------- internals ----------------

    private String signatureOf(Playlist p) {
        StringBuilder sb = new StringBuilder(p.id).append('|');
        for (MediaFile f : p.files) sb.append(f.localFileName()).append(',');
        return sb.toString();
    }

    private boolean isShowingSomething() {
        return player.isPlaying() || imageView.getVisibility() == View.VISIBLE;
    }

    private boolean isPlayable(MediaFile f) {
        // Web content is streamed, never stored — playable only while there is internet.
        if (f.isWeb()) return isOnline();
        return storage.isDownloaded(f) || streamable.contains(f.localFileName());
    }

    private boolean hasPlayableItem() {
        for (MediaFile f : items) if (isPlayable(f)) return true;
        return false;
    }

    private boolean hasPlayableItem(Playlist p) {
        for (MediaFile f : p.files) if (isPlayable(f)) return true;
        return false;
    }

    private void playCurrent() {
        if (released) return;
        handler.removeCallbacks(advance);

        if (!running || items.isEmpty() || !hasPlayableItem()) {
            if (callback != null) callback.onNothingToPlay();
            return;
        }

        // Reset streaming-video state; the chosen branch turns it back on if needed.
        streamingVideoActive = false;
        showingFallback = false;

        try {
            int tries = 0;
            while (tries < items.size() && !isPlayable(items.get(index % items.size()))) {
                index = (index + 1) % items.size();
                tries++;
            }

            MediaFile f = items.get(index % items.size());
            currentFile = f;
            boolean local = storage.isDownloaded(f);

            if (f.isWeb()) {
                // Streamed in a WebView (YouTube embed or plain web URL) — never stored locally.
                notifyStreaming(false, null);
                playYouTube(f);
            } else if (f.isVideo()) {
                if (local) {
                    notifyStreaming(false, null);
                    playLocalVideo(storage.localFile(f));
                } else {
                    streamVideoWithBandwidthCheck(f); // sets streaming mode on
                }
            } else if (f.isPdf()) {
                notifyStreaming(false, null);
                if (local) showPdf(storage.localFile(f), f.imageDurationSec);
                else streamPdf(f);
            } else { // image (default)
                notifyStreaming(false, null);
                if (local) showImage(storage.localFile(f), f.imageDurationSec);
                else streamImage(f.url, f.imageDurationSec);
            }
            storage.touch(f);
        } catch (Throwable t) {
            Log.e(TAG, "playCurrent failed — skipping item", t);
            handler.postDelayed(advance, 2000L);
        }
    }

    private void notifyStreaming(boolean streaming, String probeUrl) {
        streamingVideoActive = streaming;
        if (callback != null) callback.onStreamingModeChanged(streaming, probeUrl);
    }

    // ---- local playback ----

    private void showImage(File file, int seconds) {
        hideWeb();
        if (callback != null) callback.onContentPlaying();
        player.setPlayWhenReady(false);
        player.stop();
        playerView.setVisibility(View.GONE);
        imageView.setVisibility(View.VISIBLE);
        Glide.with(ctx.getApplicationContext()).load(file).into(imageView);
        handler.postDelayed(advance, Math.max(1, seconds) * 1000L);
    }

    private void playLocalVideo(File file) {
        startVideo(Uri.fromFile(file));
    }

    /** Host view for the YouTube player. Must be set by the Activity for YouTube links to work. */
    public void setWebContainer(ViewGroup container) { this.webContainer = container; }

    /**
     * Play a YouTube link through the official YouTube IFrame player inside a WebView.
     * YouTube cannot be downloaded or cached (their terms + no direct media URL), so this is
     * STREAMED and requires internet. Autoplay on TV requires the video to start muted.
     */
    @SuppressLint("SetJavaScriptEnabled")
    private void playYouTube(MediaFile f) {
        if (webContainer == null) {
            Log.e(TAG, "No web container set — cannot play: " + f.url);
            handler.postDelayed(advance, 3000L);
            return;
        }
        String id = f.isYouTube() ? f.youTubeId() : "";
        String listId = f.isYouTube() ? f.youTubePlaylistId() : "";
        if (f.isYouTube() && id.isEmpty() && listId.isEmpty()) {
            Log.e(TAG, "Could not parse a YouTube video/playlist id from: " + f.url);
            handler.postDelayed(advance, 3000L);
            return;
        }

        player.setPlayWhenReady(false);
        player.stop();
        playerView.setVisibility(View.GONE);
        imageView.setVisibility(View.GONE);

        if (webView == null) {
            webView = new WebView(ctx);
            WebSettings s = webView.getSettings();
            s.setJavaScriptEnabled(true);
            s.setDomStorageEnabled(true);
            s.setMediaPlaybackRequiresUserGesture(false); // required for autoplay
            s.setLoadWithOverviewMode(true);
            s.setUseWideViewPort(true);
            webView.setBackgroundColor(Color.BLACK);
            webView.setWebChromeClient(new WebChromeClient()); // required for HTML5 video
            webContainer.addView(webView, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        }

        if (!f.isYouTube()) {
            webView.loadUrl(f.url);            // plain "type":"url" web page
            webContainer.setVisibility(View.VISIBLE);
            if (callback != null) callback.onContentPlaying();
            return;
        }

        // The YouTube embed must be inside an <iframe> on a page served from a REAL, non-YouTube
        // origin:
        //   - iframe with a youtube.com base URL  → our page pretends to BE youtube.com  → error 152
        //   - loadUrl() of the embed page directly → no referrer at all                  → error 153
        // So we host the iframe on the project's own domain and pass a matching &origin=.
        final String base = "https://birrung.com.au";
        String common = "autoplay=1&mute=1&controls=0&playsinline=1&modestbranding=1"
                + "&rel=0&iv_load_policy=3&fs=0&origin=" + base;
        // loop=1 needs playlist=<id> for a single video, so it repeats continuously (signage).
        String src = !id.isEmpty()
                ? "https://www.youtube.com/embed/" + id + "?" + common + "&loop=1&playlist=" + id
                : "https://www.youtube.com/embed/videoseries?list=" + listId + "&" + common + "&loop=1";

        String html = "<!DOCTYPE html><html><head>"
                + "<meta name='viewport' content='width=device-width, initial-scale=1'>"
                + "<style>html,body{margin:0;padding:0;height:100%;background:#000;overflow:hidden}"
                + "iframe{border:0;display:block;width:100%;height:100%}</style></head><body>"
                + "<iframe src='" + src + "' frameborder='0'"
                + " allow='autoplay; encrypted-media'></iframe></body></html>";

        webView.loadDataWithBaseURL(base, html, "text/html", "utf-8", null);
        webContainer.setVisibility(View.VISIBLE);
        if (callback != null) callback.onContentPlaying();
        // No auto-advance: the iframe loops the video continuously.
    }

    /** Hide and silence the YouTube player when switching to any other content. */
    private void hideWeb() {
        if (webContainer != null && webContainer.getVisibility() != View.GONE) {
            webContainer.setVisibility(View.GONE);
        }
        if (webView != null) {
            try { webView.loadUrl("about:blank"); } catch (Throwable ignored) { } // stops audio
        }
    }

    private void showPdf(File file, int seconds) {
        hideWeb();
        if (callback != null) callback.onContentPlaying();
        player.setPlayWhenReady(false);
        player.stop();
        playerView.setVisibility(View.GONE);
        imageView.setVisibility(View.VISIBLE);

        Bitmap bmp = renderPdfFirstPage(file);
        if (bmp != null) {
            imageView.setImageBitmap(bmp);
            handler.postDelayed(advance, Math.max(1, seconds) * 1000L);
        } else {
            handler.postDelayed(advance, 2000L); // couldn't render → move on
        }
    }

    private Bitmap renderPdfFirstPage(File file) {
        ParcelFileDescriptor pfd = null;
        PdfRenderer renderer = null;
        try {
            pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
            renderer = new PdfRenderer(pfd);
            if (renderer.getPageCount() <= 0) return null;
            PdfRenderer.Page page = renderer.openPage(0);

            int pw = Math.max(1, page.getWidth());
            int ph = Math.max(1, page.getHeight());
            // Render at ~2x for sharpness, but cap the largest side so a big PDF page can't OOM.
            float scale = 2f;
            final int MAX_DIM = 2048;
            if (Math.max(pw, ph) * scale > MAX_DIM) scale = (float) MAX_DIM / Math.max(pw, ph);
            int w = Math.max(1, Math.round(pw * scale));
            int h = Math.max(1, Math.round(ph * scale));

            Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            bmp.eraseColor(Color.WHITE);   // white page background (PDFs are transparent otherwise)
            page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
            page.close();
            return bmp;
        } catch (Throwable t) {
            Log.e(TAG, "PDF render failed", t);
            return null;
        } finally {
            try { if (renderer != null) renderer.close(); } catch (Exception ignored) { }
            try { if (pfd != null) pfd.close(); } catch (Exception ignored) { }
        }
    }

    // ---- streaming fallback (storage full) ----

    private void streamImage(String url, int seconds) {
        if (callback != null) callback.onContentPlaying();
        player.setPlayWhenReady(false);
        player.stop();
        playerView.setVisibility(View.GONE);
        imageView.setVisibility(View.VISIBLE);
        Glide.with(ctx.getApplicationContext()).load(url).into(imageView);
        handler.postDelayed(advance, Math.max(1, seconds) * 1000L);
    }

    private void streamPdf(final MediaFile f) {
        // PDFs are small; fetch to cache then render (works even when main storage is full).
        final File dest = new File(ctx.getCacheDir(), "stream_" + f.localFileName());
        new Thread(() -> {
            boolean ok = dest.exists() && dest.length() > 0;
            if (!ok) ok = new MediaDownloadManager().download(f, dest, null);
            final boolean done = ok;
            handler.post(() -> {
                if (released) return;
                if (done) showPdf(dest, f.imageDurationSec);
                else next();
            });
        }).start();
    }

    private void streamVideoWithBandwidthCheck(MediaFile f) {
        streamingVideoFile = f;
        notifyStreaming(true, f.url); // start throughput probing on this URL

        if (lastMbps >= 0 && lastMbps <= THRESHOLD_MBPS) {
            showingFallback = true;
            if (callback != null) callback.onLowBandwidth();
        } else {
            showingFallback = false;
            startVideoStream(f.url);
        }
    }

    private void startVideoStream(String url) {
        startVideo(Uri.parse(url));
    }

    private void startVideo(Uri uri) {
        hideWeb();
        // Bring the player up UNDERNEATH the current holding/last image. We do NOT hide the
        // image yet — it stays on top and covers the player's shutter while a (possibly large)
        // video buffers. onRenderedFirstFrame() hides it the instant the real video is painted,
        // so the switch is seamless with no black/blank flash.
        playerView.setVisibility(View.VISIBLE);

        boolean loopSingle = items.size() == 1; // single video loops seamlessly, no black frame
        player.setRepeatMode(loopSingle ? Player.REPEAT_MODE_ONE : Player.REPEAT_MODE_OFF);
        player.stop();
        player.clearMediaItems();
        player.setMediaItem(MediaItem.fromUri(uri));
        player.prepare();
        player.setPlayWhenReady(true);
    }

    /** True if the connected device currently has internet (so a bad file can be re-downloaded). */
    private boolean isOnline() {
        try {
            ConnectivityManager cm =
                    (ConnectivityManager) ctx.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm == null) return false;
            NetworkInfo ni = cm.getActiveNetworkInfo();
            return ni != null && ni.isConnected();
        } catch (Throwable t) {
            return false;
        }
    }

    /** Recognise a local video file by extension (mp4, mov, mkv, ...). */
    private static boolean isVideoFileName(String lower) {
        return lower.endsWith(".mp4") || lower.endsWith(".mov") || lower.endsWith(".mkv")
                || lower.endsWith(".webm") || lower.endsWith(".3gp") || lower.endsWith(".m4v");
    }

    private void next() {
        if (released || !running) return;
        index = (index + 1) % Math.max(1, items.size());
        playCurrent();
    }

    /** True while real content is currently playing (used to detect a session ending). */
    public boolean isPlaying() { return running; }

    /**
     * True while NEW content has been detected but isn't downloaded/switched in yet (the old
     * content is still on screen). The host uses this to keep the "new content downloading"
     * indicator visible — the old content re-rendering must not hide it.
     */
    public boolean isWaitingForNewContent() { return !waitingSignature.isEmpty(); }

    public void stop() {
        hideWeb();
        running = false;
        fallbackFileName = null;
        handler.removeCallbacks(advance);
        player.setPlayWhenReady(false);
        player.stop();
    }

    public void release() {
        released = true;
        stop();
        player.removeListener(videoListener);
        if (webView != null) {
            try {
                if (webContainer != null) webContainer.removeView(webView);
                webView.destroy();
            } catch (Throwable ignored) { }
            webView = null;
        }
    }
}
