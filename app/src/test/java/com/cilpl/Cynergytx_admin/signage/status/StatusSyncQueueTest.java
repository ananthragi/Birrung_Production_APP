package com.cilpl.Cynergytx_admin.signage.status;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;

public class StatusSyncQueueTest {

    private FakeStatusStore store;
    private FakeTransport transport;
    private StatusSyncQueue queue;

    @Before
    public void setUp() {
        store = new FakeStatusStore();
        transport = new FakeTransport();
        queue = new StatusSyncQueue(store, transport, () -> store.now);
    }

    @Test
    public void sendsEventsOldestFirst() {
        store.insertPending("s1", SessionStatusEvent.STATUS_LOGIN, 300);
        store.insertPending("s1", SessionStatusEvent.STATUS_LOGOUT, 100);
        store.insertPending("s1", SessionStatusEvent.STATUS_LOGIN, 200);

        assertTrue(queue.syncPending());

        assertEquals(3, transport.sent.size());
        assertEquals(SessionStatusEvent.STATUS_LOGOUT, transport.sent.get(0).status); // created 100
        assertEquals(SessionStatusEvent.STATUS_LOGIN, transport.sent.get(1).status);  // created 200
        assertEquals(SessionStatusEvent.STATUS_LOGIN, transport.sent.get(2).status);  // created 300
    }

    @Test
    public void aFailureStopsThePassSoOrderIsPreserved() {
        long first = store.insertPending("s1", SessionStatusEvent.STATUS_LOGOUT, 100);
        store.insertPending("s1", SessionStatusEvent.STATUS_LOGIN, 200);
        transport.failEverything = true;

        assertFalse(queue.syncPending());

        // Only the oldest was attempted; the later login must not overtake it.
        assertEquals(1, transport.sent.size());
        assertEquals(SessionStatusEvent.STATUS_LOGOUT, transport.sent.get(0).status);
        assertEquals(SessionStatusEvent.STATE_PENDING, store.byId(first).syncState);
    }

    @Test
    public void aFailureIncrementsRetryCountAndKeepsTheRecordPending() {
        long id = store.insertPending("s1", SessionStatusEvent.STATUS_LOGOUT, 100);
        transport.failEverything = true;

        queue.syncPending();

        SessionStatusEvent e = store.byId(id);
        assertEquals(SessionStatusEvent.STATE_PENDING, e.syncState);
        assertEquals(1, e.retryCount);
        assertEquals("boom", e.lastError);
    }

    @Test
    public void aRetryWaitsForTheBackoffWindow() {
        store.insertPending("s1", SessionStatusEvent.STATUS_LOGOUT, 100);
        transport.failUntilAttempt = 1;         // first attempt fails, later ones succeed

        queue.syncPending();                     // attempt 1: fails, records the attempt time
        assertEquals(1, transport.sent.size());

        queue.syncPending();                     // immediately after: still inside the backoff
        assertEquals("should not retry before the backoff elapses", 1, transport.sent.size());

        store.now += 60_000;                     // move past it
        assertTrue(queue.syncPending());
        assertEquals(2, transport.sent.size());
    }

    @Test
    public void loginIsBlockedWhileASignOutIsUndelivered() {
        store.insertPending("s1", SessionStatusEvent.STATUS_LOGOUT, 100);
        transport.failEverything = true;         // e.g. still offline

        assertFalse("login must not proceed with a logout still queued", queue.canLoginNow());
    }

    @Test
    public void loginProceedsOnceTheSignOutGetsThrough() {
        store.insertPending("s1", SessionStatusEvent.STATUS_LOGOUT, 100);

        assertTrue(queue.canLoginNow());
        assertEquals(1, transport.sent.size());
        assertEquals(SessionStatusEvent.STATUS_LOGOUT, transport.sent.get(0).status);
    }

    @Test
    public void loginProceedsWhenNothingIsQueued() {
        assertTrue(queue.canLoginNow());
        assertEquals(0, transport.sent.size());
    }

    @Test
    public void aSecondPassIsIgnoredWhileOneIsRunning() throws Exception {
        store.insertPending("s1", SessionStatusEvent.STATUS_LOGIN, 100);
        final boolean[] reentrantResult = { true };

        StatusSyncQueue reentrant = new StatusSyncQueue(store, (sessionId, status) -> {
            // Called from inside a pass: a second pass must refuse to start.
            reentrantResult[0] = StatusSyncQueueTest.this.queueRef.syncPending();
            return StatusTransport.Result.ok();
        }, () -> store.now);
        queueRef = reentrant;

        assertTrue(reentrant.syncPending());
        assertFalse("overlapping passes must not run", reentrantResult[0]);
    }

    private StatusSyncQueue queueRef;

    @Test
    public void syncedRecordsOlderThanTheCutoffAreRemoved() {
        long id = store.insertPending("s1", SessionStatusEvent.STATUS_LOGIN, 100);
        store.now = 1_000L;
        queue.syncPending();                                     // marks it synced at now
        assertEquals(SessionStatusEvent.STATE_SYNCED, store.byId(id).syncState);

        store.now += StatusSyncQueue.KEEP_SYNCED_MS + 1;         // age past the cut-off
        store.insertPending("s1", SessionStatusEvent.STATUS_LOGOUT, store.now);
        queue.syncPending();

        assertEquals("the old synced record should be gone", null, store.byId(id));
    }

    @Test
    public void recordingQueuesBeforeAnythingIsSent() {
        transport.failEverything = true;
        queue.recordLogout("s1");

        assertEquals(1, store.pendingOldestFirst().size());
        assertEquals(SessionStatusEvent.STATUS_LOGOUT, store.pendingOldestFirst().get(0).status);
        assertTrue(store.hasPendingLogout());
    }
}
