package au.com.birrung.tv.signage.storage;

import android.util.Log;

import au.com.birrung.tv.signage.model.MediaFile;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.RandomAccessFile;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * Downloads a media file to local storage with RESUME support (HTTP Range requests).
 *
 * Strategy:
 *  - Write to "<dest>.part" first, then atomically rename to dest on success.
 *  - If a ".part" exists from a previous interrupted attempt, continue from where it
 *    stopped using a Range header (needs backend range support — see BACKEND_REQUIREMENTS.md §3).
 *  - Verify the final size against the expected fileSize when known.
 *  - On failure the ".part" is kept so the next sync resumes instead of restarting.
 */
public class MediaDownloadManager {

    private static final String TAG = "MediaDownload";

    private final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build();

    public interface ProgressListener {
        void onProgress(long downloaded, long total);
    }

    public boolean download(MediaFile mf, File dest, ProgressListener listener) {
        if (mf.url == null || mf.url.isEmpty()) return false;

        File part = new File(dest.getAbsolutePath() + ".part");
        long existing = part.exists() ? part.length() : 0;

        Request.Builder rb = new Request.Builder().url(mf.url);
        if (existing > 0) rb.addHeader("Range", "bytes=" + existing + "-");

        try (Response response = client.newCall(rb.build()).execute()) {
            int code = response.code();
            boolean append;

            if (code == 206) {
                append = true;                    // server honored the resume
            } else if (code == 200) {
                append = false;
                existing = 0;                     // server ignored Range -> full re-download
            } else {
                Log.e(TAG, "HTTP " + code + " for " + mf.url);
                return false;
            }

            ResponseBody body = response.body();
            if (body == null) return false;

            long contentLen = body.contentLength();
            long total = mf.fileSize > 0
                    ? mf.fileSize
                    : (contentLen > 0 ? existing + contentLen : -1);

            try (InputStream in = body.byteStream();
                 RandomAccessFile out = new RandomAccessFile(part, "rw")) {

                if (append) out.seek(existing);
                else out.setLength(0);

                byte[] buf = new byte[64 * 1024];
                long done = existing;
                int r;
                while ((r = in.read(buf)) != -1) {
                    out.write(buf, 0, r);
                    done += r;
                    if (listener != null) listener.onProgress(done, total);
                }
            }
        } catch (IOException e) {
            // Interruption = the sync was cancelled (e.g. screen closing). Normal — resume later, stay quiet.
            if (e instanceof InterruptedIOException || Thread.currentThread().isInterrupted()) {
                Log.w(TAG, "Download cancelled, will resume later: " + mf.url);
            } else {
                Log.w(TAG, "Download failed, will resume later: " + mf.url + " (" + e.getMessage() + ")");
            }
            return false; // keep the .part file so the next attempt continues
        }

        // Verify size when the backend told us the expected size.
        if (mf.fileSize > 0 && part.length() != mf.fileSize) {
            Log.e(TAG, "Size mismatch " + part.length() + " != " + mf.fileSize + " for " + mf.url);
            return false; // keep .part; next attempt continues/repairs
        }

        if (dest.exists()) dest.delete();
        boolean ok = part.renameTo(dest);
        if (!ok) Log.e(TAG, "Rename failed for " + dest.getName());
        return ok;
    }
}
