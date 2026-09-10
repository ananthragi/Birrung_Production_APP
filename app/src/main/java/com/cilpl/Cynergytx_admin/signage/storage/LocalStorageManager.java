package com.cilpl.Cynergytx_admin.signage.storage;

import android.content.Context;
import android.util.Log;

import com.cilpl.Cynergytx_admin.signage.model.MediaFile;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * Owns the company-named folder in internal storage and manages the 16GB budget.
 *
 * Design notes:
 *  - Files are stored in getFilesDir()/<CompanyName>/ — private to the app, NOT
 *    visible in the gallery, and survive reboots. A .nomedia file is added as belt-and-braces.
 *  - The local filename encodes the file version, so change-detection and eviction of
 *    stale versions are automatic (see MediaFile.localFileName()).
 *  - Budget = min(app quota left, actual free disk minus a safety margin).
 */
public class LocalStorageManager {

    private static final String TAG = "LocalStorage";

    /** Hard cap for this app's media on a 16GB device (leaves OS + headroom). Configurable. */
    private long maxAppStorageBytes = 8L * 1024 * 1024 * 1024; // 8 GB
    /** Never let free disk drop below this. */
    private static final long SAFETY_MARGIN = 1L * 1024 * 1024 * 1024; // 1 GB

    private final File companyDir;

    public LocalStorageManager(Context context, String companyName) {
        String safe = (companyName == null || companyName.trim().isEmpty())
                ? "Signage"
                : companyName.trim().replaceAll("[^a-zA-Z0-9._-]", "_");

        this.companyDir = new File(context.getFilesDir(), safe);
        if (!companyDir.exists()) companyDir.mkdirs();

        File noMedia = new File(companyDir, ".nomedia");
        if (!noMedia.exists()) {
            try { noMedia.createNewFile(); } catch (IOException ignored) { }
        }
        Log.e(TAG, "Company dir: " + companyDir.getAbsolutePath());
    }

    public void setMaxAppStorageBytes(long bytes) { this.maxAppStorageBytes = bytes; }

    public File getCompanyDir() { return companyDir; }

    public File localFile(MediaFile f) { return new File(companyDir, f.localFileName()); }

    /** A file counts as downloaded only if it exists and (when known) matches the expected size. */
    public boolean isDownloaded(MediaFile f) {
        File file = localFile(f);
        if (!file.exists() || file.length() == 0) return false;
        if (f.fileSize > 0 && file.length() != f.fileSize) return false;
        return true;
    }

    /** Update last-used time so the LRU evictor keeps recently played files. */
    public void touch(MediaFile f) {
        File file = localFile(f);
        if (file.exists()) file.setLastModified(System.currentTimeMillis());
    }

    /**
     * The most-recently-downloaded media file still on disk — used as an OFFLINE FALLBACK so the
     * screen keeps showing real content when the newly-scheduled content isn't downloaded yet and
     * there is no internet. Returns null if there is no usable content at all.
     */
    public File mostRecentPlayableFile() {
        File[] files = companyDir.listFiles();
        if (files == null) return null;
        File best = null;
        for (File f : files) {
            if (!f.isFile()) continue;
            String n = f.getName();
            if (n.equals(".nomedia") || n.endsWith(".part") || f.length() == 0) continue;
            if (best == null || f.lastModified() > best.lastModified()) best = f;
        }
        return best;
    }

    public long currentUsageBytes() {
        long total = 0;
        File[] files = companyDir.listFiles();
        if (files != null) for (File f : files) if (f.isFile()) total += f.length();
        return total;
    }

    public long usableSpaceBytes() { return companyDir.getUsableSpace(); }

    /** How many bytes we can safely download right now. */
    public long availableBudgetBytes() {
        long quotaLeft = maxAppStorageBytes - currentUsageBytes();
        long diskLeft = usableSpaceBytes() - SAFETY_MARGIN;
        return Math.max(0, Math.min(quotaLeft, diskLeft));
    }

    public boolean canFit(long sizeBytes) {
        if (sizeBytes <= 0) {
            // Unknown size (legacy files): allow only with comfortable headroom.
            return availableBudgetBytes() > SAFETY_MARGIN;
        }
        return availableBudgetBytes() >= sizeBytes;
    }

    /**
     * Delete files no longer referenced by the current schedule (removed content and
     * stale versions), plus orphaned .part temp files. Returns count removed.
     */
    public int cleanupUnreferenced(Set<String> referencedFileNames) {
        int removed = 0;
        File[] files = companyDir.listFiles();
        if (files == null) return 0;

        for (File f : files) {
            if (!f.isFile() || f.getName().equals(".nomedia")) continue;

            if (f.getName().endsWith(".part")) {
                String base = f.getName().substring(0, f.getName().length() - ".part".length());
                if (!referencedFileNames.contains(base) && f.delete()) removed++;
                continue;
            }
            if (!referencedFileNames.contains(f.getName()) && f.delete()) {
                removed++;
                Log.e(TAG, "Evicted stale/removed: " + f.getName());
            }
        }
        return removed;
    }

    /**
     * Free at least {@code bytesNeeded} by deleting least-recently-used files that are
     * NOT in the protected set. Returns bytes actually freed.
     */
    public long evictLruExcept(Set<String> protectedFileNames, long bytesNeeded) {
        List<File> candidates = new ArrayList<>();
        File[] files = companyDir.listFiles();
        if (files == null) return 0;

        for (File f : files) {
            if (f.isFile()
                    && !f.getName().equals(".nomedia")
                    && !protectedFileNames.contains(f.getName())) {
                candidates.add(f);
            }
        }
        Collections.sort(candidates, new Comparator<File>() {
            @Override public int compare(File a, File b) {
                return Long.compare(a.lastModified(), b.lastModified());
            }
        });

        long freed = 0;
        for (File f : candidates) {
            if (freed >= bytesNeeded) break;
            long len = f.length();
            if (f.delete()) {
                freed += len;
                Log.e(TAG, "LRU evicted: " + f.getName());
            }
        }
        return freed;
    }
}
