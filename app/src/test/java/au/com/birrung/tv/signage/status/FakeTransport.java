package au.com.birrung.tv.signage.status;

import java.util.ArrayList;
import java.util.List;

/** Scriptable transport: records what was sent and returns whatever the test wants. */
class FakeTransport implements StatusTransport {

    static class Sent {
        final String sessionId; final int status;
        Sent(String s, int st) { sessionId = s; status = st; }
    }

    final List<Sent> sent = new ArrayList<>();
    /** Sessions/statuses that should fail; empty means everything succeeds. */
    boolean failEverything = false;
    int failUntilAttempt = 0;      // fail the first N calls, then succeed
    private int calls = 0;

    @Override
    public Result send(String sessionId, int status) {
        calls++;
        sent.add(new Sent(sessionId, status));
        if (failEverything || calls <= failUntilAttempt) return Result.fail("boom");
        return Result.ok();
    }
}
