package com.cilpl.Cynergytx_admin.signage.status;

/** Sends one status change to the server. Swappable, and faked in unit tests. */
public interface StatusTransport {

    class Result {
        public final boolean success;
        public final String error;   // why it failed; null on success
        private Result(boolean success, String error) { this.success = success; this.error = error; }
        public static Result ok() { return new Result(true, null); }
        public static Result fail(String why) { return new Result(false, why == null ? "unknown" : why); }
    }

    Result send(String sessionId, int status);
}
