package edu.escuelaing.arep.httpserver;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("HttpRequest: reading the request line, the headers and the query string")
class HttpRequestTest {

    private static HttpRequest parse(String raw) throws Exception {
        return HttpRequest.parse(new ByteArrayInputStream(raw.getBytes(StandardCharsets.US_ASCII)));
    }

    @Test
    void readsMethodTargetAndVersion() throws Exception {
        HttpRequest request = parse("GET /index.html HTTP/1.1\r\nHost: localhost:8080\r\n\r\n");

        assertEquals("GET", request.getMethod());
        assertEquals("/index.html", request.getTarget());
        assertEquals("/index.html", request.getPath());
        assertEquals("HTTP/1.1", request.getVersion());
        assertEquals("localhost:8080", request.getHeader("Host"));
    }

    @Test
    @DisplayName("header names are matched without case sensitivity")
    void headerLookupIgnoresCase() throws Exception {
        HttpRequest request = parse("GET / HTTP/1.1\r\nUser-Agent: junit\r\n\r\n");
        assertEquals("junit", request.getHeader("user-agent"));
        assertEquals("junit", request.getHeader("User-Agent"));
    }

    @Test
    void separatesPathFromQueryString() throws Exception {
        HttpRequest request = parse("GET /app/square?value=7&debug=true HTTP/1.1\r\n\r\n");

        assertEquals("/app/square", request.getPath());
        assertEquals("7", request.getQueryParam("value"));
        assertEquals("true", request.getQueryParam("debug"));
        assertEquals(2, request.getQueryParams().size());
    }

    @Test
    @DisplayName("query values are decoded: %20 and + become spaces, %C3%A1 becomes á")
    void decodesQueryValues() throws Exception {
        HttpRequest request = parse("GET /app/hello?name=Ada+Mar%C3%ADa%20L. HTTP/1.1\r\n\r\n");
        assertEquals("Ada María L.", request.getQueryParam("name"));
    }

    @Test
    void handlesAnEmptyQueryValue() throws Exception {
        HttpRequest request = parse("GET /app/hello?name= HTTP/1.1\r\n\r\n");
        assertEquals("", request.getQueryParam("name"));
    }

    @Test
    void reportsMissingParametersAsNull() throws Exception {
        HttpRequest request = parse("GET /app/hello HTTP/1.1\r\n\r\n");
        assertNull(request.getQueryParam("name"));
    }

    @Test
    @DisplayName("the path is decoded, so a traversal hidden as %2e%2e becomes visible")
    void decodesThePathWithoutTreatingPlusAsSpace() throws Exception {
        assertEquals("/../secret.txt", parse("GET /%2e%2e/secret.txt HTTP/1.1\r\n\r\n").getPath());
        // In a path '+' is a literal character, not a space.
        assertEquals("/my+file.txt", parse("GET /my+file.txt HTTP/1.1\r\n\r\n").getPath());
    }

    @Test
    void acceptsAnyMethodTokenSoTheRouterCanAnswer405() throws Exception {
        assertEquals("POST", parse("POST /app/hello HTTP/1.1\r\n\r\n").getMethod());
        assertEquals("DELETE", parse("delete /app/hello HTTP/1.1\r\n\r\n").getMethod());
    }

    @Test
    void rejectsAMalformedRequestLine() {
        assertThrows(BadRequestException.class, () -> parse("NONSENSE\r\n\r\n"));
        assertThrows(BadRequestException.class, () -> parse("GET /index.html\r\n\r\n"));
        assertThrows(BadRequestException.class, () -> parse("GET /index.html SPDY/3\r\n\r\n"));
        assertThrows(BadRequestException.class, () -> parse("GET index.html HTTP/1.1\r\n\r\n"));
    }

    @Test
    void rejectsAMalformedHeader() {
        assertThrows(BadRequestException.class,
                () -> parse("GET / HTTP/1.1\r\nthis-is-not-a-header\r\n\r\n"));
    }

    @Test
    void rejectsBrokenPercentEncoding() {
        assertThrows(BadRequestException.class, () -> parse("GET /file%ZZ.txt HTTP/1.1\r\n\r\n"));
        assertThrows(BadRequestException.class, () -> parse("GET /file%2 HTTP/1.1\r\n\r\n"));
    }

    @Test
    @DisplayName("a peer that connects and says nothing produces no request at all")
    void returnsNullOnAnEmptyStream() throws Exception {
        assertNull(parse(""));
    }

    @Test
    void rejectsAnOverlongRequestLine() {
        String hugeTarget = "/" + "a".repeat(9000);
        assertThrows(BadRequestException.class, () -> parse("GET " + hugeTarget + " HTTP/1.1\r\n\r\n"));
    }

    @Test
    void toStringShowsTheRequestLine() throws Exception {
        assertTrue(parse("GET /app/time HTTP/1.1\r\n\r\n").toString().contains("GET /app/time HTTP/1.1"));
    }
}
