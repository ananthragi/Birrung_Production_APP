package com.cilpl.Cynergytx_admin.signage.status;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * SQLite storage for pending sign-in/sign-out events.
 *
 * Deliberately its OWN database file rather than a table inside the existing "Cynergytx"
 * helper: that helper's onUpgrade() is entirely commented out, so raising its version would
 * silently fail to create a new table on every device already in the field. A separate file
 * also means this feature cannot disturb the legacy booking table.
 */
public class SqliteStatusStore extends SQLiteOpenHelper implements StatusStore {

    private static final String TAG = "StatusStore";

    private static final String DB_NAME = "birrung_status.db";
    private static final int DB_VERSION = 1;

    static final String TABLE = "session_status_events";
    static final String C_ID = "id";
    static final String C_SESSION = "session_id";
    static final String C_STATUS = "status";
    static final String C_CREATED = "created_at";
    static final String C_STATE = "sync_state";
    static final String C_RETRIES = "retry_count";
    static final String C_ERROR = "last_error";
    static final String C_SYNCED = "synced_at";
    static final String C_ATTEMPT = "last_attempt_at";

    public SqliteStatusStore(Context context) {
        super(context.getApplicationContext(), DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS " + TABLE + " (" +
                C_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                C_SESSION + " TEXT NOT NULL, " +
                C_STATUS + " INTEGER NOT NULL, " +
                C_CREATED + " INTEGER NOT NULL, " +
                C_STATE + " TEXT NOT NULL, " +
                C_RETRIES + " INTEGER NOT NULL DEFAULT 0, " +
                C_ERROR + " TEXT, " +
                C_SYNCED + " INTEGER, " +
                C_ATTEMPT + " INTEGER NOT NULL DEFAULT 0)");
        // Pending events are read on every connectivity change and every launch.
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_state_created ON "
                + TABLE + " (" + C_STATE + ", " + C_CREATED + ")");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // Nothing to migrate yet. onCreate is idempotent, so make sure the table exists.
        onCreate(db);
    }

    // ------------------------------------------------------------------ StatusStore

    @Override
    public long insertPending(String sessionId, int status, long createdAt) {
        try {
            ContentValues v = new ContentValues();
            v.put(C_SESSION, sessionId == null ? "" : sessionId);
            v.put(C_STATUS, status);
            v.put(C_CREATED, createdAt);
            v.put(C_STATE, SessionStatusEvent.STATE_PENDING);
            v.put(C_RETRIES, 0);
            v.put(C_ATTEMPT, 0L);
            long id = getWritableDatabase().insert(TABLE, null, v);
            Log.e(TAG, "Queued " + (status == SessionStatusEvent.STATUS_LOGOUT ? "logout" : "login")
                    + " for " + sessionId + " as #" + id);
            return id;
        } catch (Throwable t) {
            Log.e(TAG, "Could not queue status event: " + t.getMessage());
            return -1;
        }
    }

    @Override
    public List<SessionStatusEvent> pendingOldestFirst() {
        List<SessionStatusEvent> out = new ArrayList<>();
        Cursor c = null;
        try {
            c = getReadableDatabase().query(TABLE, null,
                    C_STATE + " = ?", new String[]{ SessionStatusEvent.STATE_PENDING },
                    null, null, C_CREATED + " ASC, " + C_ID + " ASC");
            while (c.moveToNext()) out.add(read(c));
        } catch (Throwable t) {
            Log.e(TAG, "Could not read pending events: " + t.getMessage());
        } finally {
            if (c != null) try { c.close(); } catch (Throwable ignored) { }
        }
        return out;
    }

    @Override
    public boolean hasPendingLogout() {
        Cursor c = null;
        try {
            c = getReadableDatabase().query(TABLE, new String[]{ C_ID },
                    C_STATE + " = ? AND " + C_STATUS + " = ?",
                    new String[]{ SessionStatusEvent.STATE_PENDING,
                                  String.valueOf(SessionStatusEvent.STATUS_LOGOUT) },
                    null, null, null, "1");
            return c.moveToFirst();
        } catch (Throwable t) {
            Log.e(TAG, "Could not check for a pending logout: " + t.getMessage());
            return false;
        } finally {
            if (c != null) try { c.close(); } catch (Throwable ignored) { }
        }
    }

    @Override
    public void markSynced(long id, long syncedAt) {
        try {
            ContentValues v = new ContentValues();
            v.put(C_STATE, SessionStatusEvent.STATE_SYNCED);
            v.put(C_SYNCED, syncedAt);
            v.putNull(C_ERROR);
            getWritableDatabase().update(TABLE, v, C_ID + " = ?", new String[]{ String.valueOf(id) });
            Log.e(TAG, "Synced #" + id);
        } catch (Throwable t) {
            Log.e(TAG, "Could not mark #" + id + " synced: " + t.getMessage());
        }
    }

    @Override
    public void markAttemptFailed(long id, String error) {
        try {
            // Stays pending on purpose: a failure is a retry, not a dead end.
            getWritableDatabase().execSQL(
                    "UPDATE " + TABLE + " SET " + C_RETRIES + " = " + C_RETRIES + " + 1, "
                            + C_ERROR + " = ?, " + C_ATTEMPT + " = ? WHERE " + C_ID + " = ?",
                    new Object[]{ error == null ? "" : error, System.currentTimeMillis(), id });
            Log.e(TAG, "Attempt failed for #" + id + ": " + error);
        } catch (Throwable t) {
            Log.e(TAG, "Could not record the failure for #" + id + ": " + t.getMessage());
        }
    }

    @Override
    public int deleteSyncedBefore(long cutoffMs) {
        try {
            int n = getWritableDatabase().delete(TABLE,
                    C_STATE + " = ? AND " + C_SYNCED + " IS NOT NULL AND " + C_SYNCED + " < ?",
                    new String[]{ SessionStatusEvent.STATE_SYNCED, String.valueOf(cutoffMs) });
            if (n > 0) Log.e(TAG, "Removed " + n + " synced record(s) older than the cut-off");
            return n;
        } catch (Throwable t) {
            Log.e(TAG, "Cleanup failed: " + t.getMessage());
            return 0;
        }
    }

    private static SessionStatusEvent read(Cursor c) {
        @Nullable Long synced = c.isNull(c.getColumnIndexOrThrow(C_SYNCED))
                ? null : c.getLong(c.getColumnIndexOrThrow(C_SYNCED));
        return new SessionStatusEvent(
                c.getLong(c.getColumnIndexOrThrow(C_ID)),
                c.getString(c.getColumnIndexOrThrow(C_SESSION)),
                c.getInt(c.getColumnIndexOrThrow(C_STATUS)),
                c.getLong(c.getColumnIndexOrThrow(C_CREATED)),
                c.getString(c.getColumnIndexOrThrow(C_STATE)),
                c.getInt(c.getColumnIndexOrThrow(C_RETRIES)),
                c.getString(c.getColumnIndexOrThrow(C_ERROR)),
                synced,
                c.getLong(c.getColumnIndexOrThrow(C_ATTEMPT)));
    }
}
