package au.com.birrung.tv.signage.status;

/** One sign-in or sign-out that the server still needs to be told about. */
public class SessionStatusEvent {

    public static final int STATUS_LOGIN = 1;
    public static final int STATUS_LOGOUT = 2;

    public static final String STATE_PENDING = "pending";
    public static final String STATE_SYNCED  = "synced";
    public static final String STATE_FAILED  = "failed";

    public final long id;
    public final String sessionId;   // kept per record: a pending logout must still know
    public final int status;         // which session to close after prefs are cleared
    public final long createdAt;     // epoch ms, when it happened on the device
    public final String syncState;
    public final int retryCount;
    public final String lastError;
    public final Long syncedAt;
    public final long lastAttemptAt;   // 0 = never attempted

    public SessionStatusEvent(long id, String sessionId, int status, long createdAt,
                              String syncState, int retryCount, String lastError, Long syncedAt,
                              long lastAttemptAt) {
        this.id = id;
        this.sessionId = sessionId == null ? "" : sessionId;
        this.status = status;
        this.createdAt = createdAt;
        this.syncState = syncState == null ? STATE_PENDING : syncState;
        this.retryCount = retryCount;
        this.lastError = lastError;
        this.syncedAt = syncedAt;
        this.lastAttemptAt = lastAttemptAt;
    }

    public boolean isLogout() { return status == STATUS_LOGOUT; }

    @Override public String toString() {
        return "#" + id + " " + (isLogout() ? "logout" : "login")
                + " session=" + sessionId + " state=" + syncState + " retries=" + retryCount;
    }
}
