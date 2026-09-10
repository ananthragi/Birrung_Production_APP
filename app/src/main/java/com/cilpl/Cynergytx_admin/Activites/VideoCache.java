package com.cilpl.Cynergytx_admin.Activites;

import android.content.Context;

import com.google.android.exoplayer2.database.ExoDatabaseProvider;
import com.google.android.exoplayer2.upstream.cache.LeastRecentlyUsedCacheEvictor;
import com.google.android.exoplayer2.upstream.cache.SimpleCache;

import java.io.File;

// VideoCache.java
public class VideoCache {
    private static SimpleCache simpleCache;
    private static ExoDatabaseProvider databaseProvider;
    private static final long CACHE_SIZE = 50 * 1024 * 1024; // 🔹 50 MB for low RAM TVs

    public static synchronized SimpleCache getInstance(Context context) {
        if (simpleCache == null) {
            File cacheDir = new File(context.getCacheDir(), "video_cache");
            LeastRecentlyUsedCacheEvictor evictor = new LeastRecentlyUsedCacheEvictor(CACHE_SIZE);
            databaseProvider = new ExoDatabaseProvider(context);
            simpleCache = new SimpleCache(cacheDir, evictor, databaseProvider);
        }
        return simpleCache;
    }

    public static synchronized void release() {
        if (simpleCache != null) {
            try {
                simpleCache.release();
            } catch (Exception ignored) {}
            simpleCache = null;
        }
    }
}


