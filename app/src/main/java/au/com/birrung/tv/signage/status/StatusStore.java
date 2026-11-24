package au.com.birrung.tv.signage.status;

import java.util.List;

/**
 * Storage for sign-in/sign-out events awaiting the server.
 *
 * An interface, not the SQLite class itself, so the sync logic can be exercised by plain
 * JVM unit tests -- Android's SQLiteDatabase does not exist outside a device.
 */
public interface StatusStore {

    /** Records an event as pending. Returns its id. */
    long insertPending(String sessionId, int status, long createdAt);

    /** Pending events, oldest first. The server must be told in the order things happened. */
    List<SessionStatusEvent> pendingOldestFirst();

    /** True when a sign-out is still waiting to reach the server. */
    boolean hasPendingLogout();

    void markSynced(long id, long syncedAt);

    /** Keeps the record pending, raises its retry count and stores why it failed. */
    void markAttemptFailed(long id, String error);

    /** Drops synced records older than the cut-off. Returns how many went. */
    int deleteSyncedBefore(long cutoffMs);
}
