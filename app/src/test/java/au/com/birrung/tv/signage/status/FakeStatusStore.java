package au.com.birrung.tv.signage.status;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** In-memory StatusStore so the queue can be tested without a device. */
class FakeStatusStore implements StatusStore {

    final List<SessionStatusEvent> rows = new ArrayList<>();
    private long nextId = 1;

    @Override
    public long insertPending(String sessionId, int status, long createdAt) {
        long id = nextId++;
        rows.add(new SessionStatusEvent(id, sessionId, status, createdAt,
                SessionStatusEvent.STATE_PENDING, 0, null, null, 0));
        return id;
    }

    @Override
    public List<SessionStatusEvent> pendingOldestFirst() {
        List<SessionStatusEvent> out = new ArrayList<>();
        for (SessionStatusEvent e : rows) {
            if (SessionStatusEvent.STATE_PENDING.equals(e.syncState)) out.add(e);
        }
        out.sort(Comparator.comparingLong((SessionStatusEvent e) -> e.createdAt)
                .thenComparingLong(e -> e.id));
        return out;
    }

    @Override
    public boolean hasPendingLogout() {
        for (SessionStatusEvent e : rows) {
            if (SessionStatusEvent.STATE_PENDING.equals(e.syncState) && e.isLogout()) return true;
        }
        return false;
    }

    @Override
    public void markSynced(long id, long syncedAt) {
        replace(id, e -> new SessionStatusEvent(e.id, e.sessionId, e.status, e.createdAt,
                SessionStatusEvent.STATE_SYNCED, e.retryCount, null, syncedAt, e.lastAttemptAt));
    }

    @Override
    public void markAttemptFailed(long id, String error) {
        replace(id, e -> new SessionStatusEvent(e.id, e.sessionId, e.status, e.createdAt,
                SessionStatusEvent.STATE_PENDING, e.retryCount + 1, error, e.syncedAt, now));
    }

    @Override
    public int deleteSyncedBefore(long cutoffMs) {
        int before = rows.size();
        rows.removeIf(e -> SessionStatusEvent.STATE_SYNCED.equals(e.syncState)
                && e.syncedAt != null && e.syncedAt < cutoffMs);
        return before - rows.size();
    }

    /** The clock the queue sees; tests move it to step over backoff windows. */
    long now = 1_000L;

    SessionStatusEvent byId(long id) {
        for (SessionStatusEvent e : rows) if (e.id == id) return e;
        return null;
    }

    private interface Mapper { SessionStatusEvent map(SessionStatusEvent e); }

    private void replace(long id, Mapper m) {
        for (int i = 0; i < rows.size(); i++) {
            if (rows.get(i).id == id) { rows.set(i, m.map(rows.get(i))); return; }
        }
    }
}
