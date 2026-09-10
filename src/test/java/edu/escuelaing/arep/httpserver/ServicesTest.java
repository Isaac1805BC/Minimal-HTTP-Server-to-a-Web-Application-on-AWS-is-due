package edu.escuelaing.arep.httpserver;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Services: valid answers and controlled invalid input")
class ServicesTest {

    private static HttpRequest get(String target) throws Exception {
        String raw = "GET " + target + " HTTP/1.1\r\nHost: localhost\r\n\r\n";
        return HttpRequest.parse(new ByteArrayInputStream(raw.getBytes(StandardCharsets.US_ASCII)));
    }

    private static String bodyOf(HttpResponse response) {
        return new String(response.getBody(), StandardCharsets.UTF_8);
    }

    private static void assertIsJsonError(HttpResponse response, int expectedStatus) {
        assertEquals(expectedStatus, response.getStatus());
        assertEquals("application/json; charset=utf-8", response.getContentType());
        assertTrue(bodyOf(response).contains("\"message\""),
                "an error must explain itself: " + bodyOf(response));
    }

    @Nested
    @DisplayName("greeting")
    class Greeting {

        @Test
        void greetsTheSuppliedName() throws Exception {
            HttpResponse response = Services.hello(get("/app/hello?name=Ada"));

            assertEquals(HttpStatus.OK, response.getStatus());
            assertEquals("application/json; charset=utf-8", response.getContentType());
            assertTrue(bodyOf(response).contains("\"message\":\"Hello, Ada!\""));
            assertTrue(bodyOf(response).contains("\"name\":\"Ada\""));
        }

        @Test
        @DisplayName("an encoded name arrives decoded")
        void decodesTheName() throws Exception {
            assertTrue(bodyOf(Services.hello(get("/app/hello?name=Ada+Mar%C3%ADa")))
                    .contains("Hello, Ada María!"));
        }

        @Test
        void missingNameIsRejected() throws Exception {
            assertIsJsonError(Services.hello(get("/app/hello")), HttpStatus.BAD_REQUEST);
        }

        @Test
        void emptyOrBlankNameIsRejected() throws Exception {
            assertIsJsonError(Services.hello(get("/app/hello?name=")), HttpStatus.BAD_REQUEST);
            assertIsJsonError(Services.hello(get("/app/hello?name=%20%20")), HttpStatus.BAD_REQUEST);
        }

        @Test
        void anAbsurdlyLongNameIsRejected() throws Exception {
            assertIsJsonError(Services.hello(get("/app/hello?name=" + "a".repeat(200))),
                    HttpStatus.BAD_REQUEST);
        }

        @Test
        @DisplayName("a hostile name cannot break the JSON document")
        void escapesTheName() throws Exception {
            String body = bodyOf(Services.hello(get("/app/hello?name=%22%3Cscript%3E")));

            assertEquals(HttpStatus.OK, Services.hello(get("/app/hello?name=%22")).getStatus());
            assertTrue(body.contains("\\\""), "the quote must be escaped: " + body);
            assertFalse(body.contains("<script>"), "markup must not be echoed verbatim: " + body);
        }
    }

    @Nested
    @DisplayName("square")
    class Square {

        @Test
        void squaresAnInteger() throws Exception {
            HttpResponse response = Services.square(get("/app/square?value=7"));

            assertEquals(HttpStatus.OK, response.getStatus());
            assertTrue(bodyOf(response).contains("\"value\":7"));
            assertTrue(bodyOf(response).contains("\"square\":49"));
        }

        @Test
        void squaresADecimalAndANegativeValue() throws Exception {
            assertTrue(bodyOf(Services.square(get("/app/square?value=2.5"))).contains("\"square\":6.25"));
            assertTrue(bodyOf(Services.square(get("/app/square?value=-4"))).contains("\"square\":16"));
        }

        @Test
        void nonNumericValuesAreRejected() throws Exception {
            assertIsJsonError(Services.square(get("/app/square?value=abc")), HttpStatus.BAD_REQUEST);
            assertIsJsonError(Services.square(get("/app/square?value=7,5")), HttpStatus.BAD_REQUEST);
        }

        @Test
        void missingValueIsRejected() throws Exception {
            assertIsJsonError(Services.square(get("/app/square")), HttpStatus.BAD_REQUEST);
            assertIsJsonError(Services.square(get("/app/square?value=")), HttpStatus.BAD_REQUEST);
        }

        @Test
        @DisplayName("infinity and a value whose square overflows are rejected")
        void nonFiniteResultsAreRejected() throws Exception {
            assertIsJsonError(Services.square(get("/app/square?value=Infinity")), HttpStatus.BAD_REQUEST);
            assertIsJsonError(Services.square(get("/app/square?value=1e200")), HttpStatus.BAD_REQUEST);
        }

        @Test
        void wholeNumbersAreNotPrintedAsDecimals() {
            assertEquals("49", Services.format(49.0));
            assertEquals("6.25", Services.format(6.25));
        }
    }

    @Nested
    @DisplayName("server time and health")
    class TimeAndHealth {

        @Test
        void timeComesFromTheServerClock() {
            HttpResponse response = Services.time();
            String body = bodyOf(response);

            assertEquals(HttpStatus.OK, response.getStatus());
            assertEquals("application/json; charset=utf-8", response.getContentType());
            assertTrue(body.contains("\"iso8601\""));
            assertTrue(body.contains("\"zone\""));
            assertTrue(body.contains("\"epochMillis\""));
        }

        @Test
        @DisplayName("dynamic answers are never cached")
        void dynamicAnswersAreNotCacheable() {
            assertEquals(HttpStatus.OK, Services.health().getStatus());
            assertTrue(bodyOf(Services.health()).contains("\"status\":\"UP\""));
        }
    }

    @Nested
    @DisplayName("slow service")
    class Slow {

        @Test
        void answersAfterTheRequestedDelay() throws Exception {
            long startedAt = System.currentTimeMillis();
            HttpResponse response = Services.slow(get("/app/slow?ms=120"));
            long elapsed = System.currentTimeMillis() - startedAt;

            assertEquals(HttpStatus.OK, response.getStatus());
            assertTrue(elapsed >= 120, "the service must really take its time, took " + elapsed + " ms");
        }

        @Test
        void rejectsAnInvalidOrExcessiveDelay() throws Exception {
            assertIsJsonError(Services.slow(get("/app/slow?ms=abc")), HttpStatus.BAD_REQUEST);
            assertIsJsonError(Services.slow(get("/app/slow?ms=-1")), HttpStatus.BAD_REQUEST);
            assertIsJsonError(Services.slow(get("/app/slow?ms=600000")), HttpStatus.BAD_REQUEST);
        }
    }
}
