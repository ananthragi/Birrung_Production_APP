package au.com.birrung.tv.signage.status;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * The endpoint answers 200 for both outcomes, and the failure body arrives as text/html,
 * so the body is the only thing that can be trusted.
 */
public class StatusApiParseTest {

    @Test
    public void theDocumentedSuccessBodyIsASuccess() {
        assertTrue(StatusApi.isSuccessBody(
                "{ \"success\": \"Status Updated Successfully.\", \"status\": 1 }"));
    }

    @Test
    public void theDocumentedErrorBodyIsAFailure() {
        assertFalse(StatusApi.isSuccessBody(
                "{ \"error\": \"Invalid status updated\", \"status\": 0 }"));
    }

    @Test
    public void anErrorKeyIsAFailureEvenWithStatusOne() {
        assertFalse(StatusApi.isSuccessBody(
                "{ \"error\": \"something\", \"status\": 1 }"));
    }

    @Test
    public void statusZeroIsAFailureEvenWithASuccessKey() {
        assertFalse(StatusApi.isSuccessBody(
                "{ \"success\": \"ok\", \"status\": 0 }"));
    }

    @Test
    public void jsonWrappedInHtmlIsStillRead() {
        // The failure response is served as HTML; PHP notices can precede the JSON.
        assertTrue(StatusApi.isSuccessBody(
                "<br />\n<b>Notice</b>: something\n{\"success\":\"Status Updated Successfully.\",\"status\":1}"));
        assertFalse(StatusApi.isSuccessBody(
                "<b>Warning</b>: x {\"error\":\"Invalid status updated\",\"status\":0}"));
    }

    @Test
    public void unparseableOrEmptyBodiesAreFailuresNeverSuccesses() {
        assertFalse(StatusApi.isSuccessBody(null));
        assertFalse(StatusApi.isSuccessBody(""));
        assertFalse(StatusApi.isSuccessBody("   "));
        assertFalse(StatusApi.isSuccessBody("<html><body>500 Server Error</body></html>"));
        assertFalse(StatusApi.isSuccessBody("{ not json at all"));
        assertFalse(StatusApi.isSuccessBody("{}"));
    }
}
