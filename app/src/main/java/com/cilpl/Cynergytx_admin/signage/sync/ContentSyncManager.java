package com.cilpl.Cynergytx_admin.signage.sync;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.cilpl.Cynergytx_admin.signage.model.ContentSchedule;
import com.cilpl.Cynergytx_admin.signage.model.MediaFile;
import com.cilpl.Cynergytx_admin.signage.model.Playlist;
import com.cilpl.Cynergytx_admin.signage.storage.LocalStorageManager;
import com.cilpl.Cynergytx_admin.signage.storage.MediaDownloadManager;
import com.cilpl.Cynergytx_admin.signage.storage.MediaValidator;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Orchestrates the download-then-play flow on a background thread:
 *   1. De-duplicate all files, ACTIVE playlist first (so the current screen is ready fastest).
 *   2. Delete stale / removed files.
 *   3. For each needed file: skip if present, evict LRU to make room, download (resume-aware),
 *      or record a shortfall if it genuinely cannot fit in the 16GB budget.
 *
 * Callbacks are delivered on the main thread.
 */
public class ContentSyncManager {

    private static final String TAG = "ContentSync";

    public interface SyncCallback {
        void onFileReady(MediaFile file);   // a file just became available locally
        void onComplete(SyncReport report);
        /** Real download progress (0..100, or -1 if size unknown) for the ACTIVE content. */
        void onProgress(MediaFile file, int percent);
    }

    private final LocalStorageManager storage;
    private final MediaDownloadManager downloader;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private volatile boolean cancelled = false;

    public ContentSyncManager(LocalStorageManager storage) {
        this.storage = storage;
        this.downloader = new MediaDownloadManager();
    }

    public void cancel() { cancelled = true; }

    public void sync(final ContentSchedule schedule, final String protectedFileName,
                     final boolean allowCleanup, final SyncCallback cb) {
        cancelled = false;
        executor.execute(() -> {
            final SyncReport report = new SyncReport();
            try {

            // 1) Ordered, de-duplicated file list (active playlist first).
            Map<String, MediaFile> ordered = new LinkedHashMap<>();
            Playlist active = schedule.getActivePlaylist();
            if (active != null) {
                for (MediaFile f : active.files) ordered.put(f.localFileName(), f);
            }
            for (Playlist p : schedule.playlists) {
                for (MediaFile f : p.files) ordered.putIfAbsent(f.localFileName(), f);
            }

            // The files the user is actually waiting to see (active session) — report their
            // real download % so the single loader shows genuine progress.
            Set<String> activeNames = new HashSet<>();
            if (active != null) for (MediaFile f : active.files) activeNames.add(f.localFileName());

            Set<String> referenced = new HashSet<>(ordered.keySet());
            // NEVER delete or evict the content that is currently playing on screen — even if the
            // schedule changed — until its replacement is fully downloaded, validated, and switched
            // to. (Interrupted download of new content must not remove the working content.)
            if (protectedFileName != null && !protectedFileName.isEmpty()) referenced.add(protectedFileName);

            // 2) Download each needed file within the storage budget.
            //    NOTE: we do NOT delete old content yet — the currently-playing file
            //    must stay on disk until its replacement is ready, so playback swaps
            //    seamlessly with no "no content" gap. Old files are cleaned up in step 3.
            for (MediaFile f : ordered.values()) {
                if (cancelled) break;
                if (f.url == null || f.url.isEmpty()) continue;

                // YouTube links are streamed by the YouTube player — they are never downloaded or
                // stored locally. Skip them entirely so the downloader doesn't try to fetch the
                // YouTube page as a media file (which would fail and retry forever).
                if (f.isWeb()) {
                    main.post(() -> cb.onFileReady(f));
                    continue;
                }

                // A raw PowerPoint deck is never rendered -- the backend sends its slides as
                // separate images. Downloading the deck would waste bandwidth and storage on a
                // file nothing can display. (Defensive: schedules now carry slides, not decks.)
                if (f.isPpt()) {
                    main.post(() -> cb.onFileReady(f));
                    continue;
                }

                // Already on disk?
                //  - ONLINE: verify it's complete & playable; if broken, delete it and re-download.
                //  - OFFLINE: NEVER validate/delete it. We cannot re-fetch it, and some media-player
                //    devices wrongly report a good file as "invalid" (MediaMetadataRetriever /
                //    BitmapFactory quirks) — deleting it offline permanently loses the content and
                //    shows the "I'M OFFLINE" screen. So offline we trust the file and play it.
                if (storage.isDownloaded(f)) {
                    boolean online = allowCleanup; // this flag is set from the host's connectivity check
                    if (!online || MediaValidator.isComplete(f, storage.localFile(f))) {
                        storage.touch(f);
                        report.alreadyPresent++;
                        main.post(() -> cb.onFileReady(f));
                        continue;
                    }
                    Log.e(TAG, "Stored file is broken/incomplete → re-downloading: " + f.localFileName());
                    try { storage.localFile(f).delete(); } catch (Exception ignored) { }
                }

                // Only if space is tight, evict old (unreferenced) files to make room.
                if (f.fileSize > 0 && !storage.canFit(f.fileSize)) {
                    storage.evictLruExcept(referenced, f.fileSize);
                }

                if (!storage.canFit(f.fileSize)) {
                    Log.e(TAG, "Shortfall — cannot fit " + f.id + " (" + f.fileSize + " bytes)");
                    report.shortfall.add(f);
                    continue; // keep going; smaller files may still fit
                }

                // For the active content, stream real download progress to the loader (throttled to
                // whole-percent changes so we don't flood the main thread).
                MediaDownloadManager.ProgressListener pl = null;
                if (activeNames.contains(f.localFileName())) {
                    final MediaFile ff = f;
                    final int[] lastPct = { -1 };
                    pl = (downloaded, total) -> {
                        int pct = total > 0 ? (int) (downloaded * 100L / total) : -1;
                        if (pct != lastPct[0]) {
                            lastPct[0] = pct;
                            main.post(() -> cb.onProgress(ff, pct));
                        }
                    };
                }

                boolean ok = downloader.download(f, storage.localFile(f), pl);

                // Guard: a "successful" video download can still be incomplete when the backend
                // truncates it (no fileSize to verify against). Confirm it actually plays; if not,
                // delete it and mark it failed so it re-downloads later — never hand a broken file
                // to the player (that causes the endless "buffering" and the decoder crash).
                if (ok && !MediaValidator.isComplete(f, storage.localFile(f))) {
                    Log.e(TAG, "Downloaded file failed validation (incomplete?): " + f.localFileName());
                    try { storage.localFile(f).delete(); } catch (Exception ignored) { }
                    ok = false;
                }

                if (ok) {
                    storage.touch(f);
                    report.downloaded++;
                    main.post(() -> cb.onFileReady(f));
                } else {
                    report.failed.add(f);
                }
            }

            // 3) Now that the new files are in place, remove old / removed content
            //    and any orphaned temp files. Skip this if the sync was cancelled
            //    (e.g. screen closing) so we never wipe good content on shutdown.
            // Only clean up old/unreferenced content when we're allowed to (i.e. online). When
            // OFFLINE we keep everything on disk so it can serve as a fallback until the new
            // content is successfully downloaded.
            if (allowCleanup && !cancelled && !Thread.currentThread().isInterrupted()) {
                storage.cleanupUnreferenced(referenced);
            }

            report.usageBytes = storage.currentUsageBytes();
            report.budgetBytes = storage.availableBudgetBytes();
            Log.e(TAG, "Sync complete: " + report.summary());

            } catch (Throwable t) {
                // Never let a background error kill the app — recover and report.
                Log.e(TAG, "Sync failed safely", t);
            }
            main.post(() -> cb.onComplete(report));
        });
    }

    public void shutdown() { executor.shutdownNow(); }
}
