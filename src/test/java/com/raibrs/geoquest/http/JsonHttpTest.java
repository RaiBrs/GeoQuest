package com.raibrs.geoquest.http;

import com.raibrs.geoquest.support.TestHttpServer;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

class JsonHttpTest {
    @Test void headErrorCompletesWithoutWritingToTheClosedResponseStream() throws Exception {
        var failure = new AtomicReference<IOException>();
        var finished = new CountDownLatch(1);
        try (var http = TestHttpServer.start(exchange -> {
            try { JsonHttp.error(exchange, 404, "Endpoint not found."); }
            catch (IOException error) { failure.set(error); }
            finally { exchange.close(); finished.countDown(); }
        })) {
            var response = http.head("/api/missing");
            assertTrue(finished.await(5, TimeUnit.SECONDS));
            assertEquals(404, response.statusCode());
            assertEquals("", response.body());
            assertNull(failure.get(), "HEAD must not write a response body");
        }
    }
}
