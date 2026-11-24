package au.com.birrung.tv.signage.storage;

import android.graphics.BitmapFactory;
import android.media.MediaMetadataRetriever;
import android.util.Log;

import au.com.birrung.tv.signage.model.MediaFile;

import java.io.File;

/**
 * Confirms a downloaded video is COMPLETE and playable before we trust it.
 *
 * The backend does not send a per-file size and sometimes serves large videos
 * incompletely (truncated). Without this check a half-downloaded MP4 is treated as
 * "ready", ExoPlayer then stalls forever ("buffering") or crashes the decoder.
 *
 * MediaMetadataRetriever parses the file's headers/index (the MP4 "moov" atom). A
 * truncated file has no valid index → it throws or reports no duration → we reject it.
 * This needs NO backend change and runs on the background sync thread.
 */
public final class MediaValidator {

    private static final String TAG = "MediaValidator";

    private MediaValidator() { }

    /**
     * Validate any downloaded media file for its type: videos must be playable (duration > 0),
     * images must decode to real pixels. PDFs/others are accepted if present (handled elsewhere).
     */
    public static boolean isComplete(MediaFile mf, File file) {
        if (mf == null || file == null) return false;
        if (!file.exists() || file.length() == 0) return false;

        // FAST PATH — the backend gave us the exact size and the file matches it byte-for-byte.
        // MediaDownloadManager only renames ".part" -> final file when the size matches, so a
        // size match already proves the download is complete. This check is instant and
        // device-independent, and it avoids running a MediaMetadataRetriever scan on a large
        // video — that scan takes minutes on a slow TV box and was delaying the switch to new
        // content (old video kept playing long after the download hit 100%).
        if (mf.fileSize > 0) return file.length() == mf.fileSize;

        // No size from the backend → fall back to inspecting the file contents.
        if (mf.isVideo()) return isPlayableVideo(file);
        if (mf.isImage()) return isDecodableImage(file);
        return true;
    }

    /** True only if the file exists and decodes as a real image (guards partial/corrupt JPG/PNG). */
    public static boolean isDecodableImage(File file) {
        if (file == null || !file.exists() || file.length() == 0) return false;
        try {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true; // reads headers only, no full bitmap in memory
            BitmapFactory.decodeFile(file.getAbsolutePath(), o);
            boolean ok = o.outWidth > 0 && o.outHeight > 0;
            if (!ok) Log.e(TAG, "Invalid/incomplete image: " + file.getName());
            return ok;
        } catch (Throwable t) {
            Log.e(TAG, "Image validation failed for " + file.getName() + ": " + t.getMessage());
            return false;
        }
    }

    /** True only if the file exists and is a complete, playable video (duration > 0). */
    public static boolean isPlayableVideo(File file) {
        if (file == null || !file.exists() || file.length() == 0) return false;

        MediaMetadataRetriever mmr = new MediaMetadataRetriever();
        try {
            mmr.setDataSource(file.getAbsolutePath());
            String dur = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            long ms = 0;
            if (dur != null) {
                try { ms = Long.parseLong(dur.trim()); } catch (NumberFormatException ignored) { }
            }
            if (ms <= 0) {
                Log.e(TAG, "Invalid/incomplete video (duration=" + dur + "): " + file.getName());
                return false;
            }
            return true;
        } catch (Throwable t) {
            // setDataSource throws on a corrupt / truncated / unparseable file.
            Log.e(TAG, "Video validation failed for " + file.getName() + ": " + t.getMessage());
            return false;
        } finally {
            try { mmr.release(); } catch (Throwable ignored) { }
        }
    }
}
