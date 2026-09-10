package edu.escuelaing.arep.httpserver;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Drives the real server through real sockets.
 *
 * <p>The server runs in a background thread <em>of the test</em>, only because
 * the test also has to act as the client; the server itself stays strictly
 * sequential and single-threaded, and every request below is sent one after the
 * other.</p>
 */
@DisplayName("The running server: static resources, services and controlled errors")
class HttpServerIntegrationTest {

    private static HttpServer server;
    private static Thread serverThread;
    private static int port;
    private static HttpClient client;

    @BeforeAll
    static void startServer() throws Exception {
        // Port 0 asks the operating system for a free port: the suite never
        // collides with an application already running on 8080.
        server = new HttpServer(0);
        server.bind();
        port = server.getPort();

        serverThread = new Thread(server::serve, "test-http-server");
        serverThread.setDaemon(true);
        serverThread.start();

        client = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    @AfterAll
    static void stopServer() throws Exception {
        server.stop();
        serverThread.join(2000);
    }

    private static HttpResponse<byte[]> get(String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + path))
                .timeout(Duration.ofSeconds(30))
                .GET()
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofByteArray());
    }

    private static String text(HttpResponse<byte[]> response) {
        return new String(response.body(), StandardCharsets.UTF_8);
    }

    private static String contentType(HttpResponse<byte[]> response) {
        return response.headers().firstValue("Content-Type").orElse("");
    }

    /**
     * Sends a request line the high level client would refuse to build, and
     * returns the raw response.
     */
    private static String rawRequest(String requestLine) throws IOException {
        try (Socket socket = new Socket("localhost", port)) {
            socket.setSoTimeout(5000);
            OutputStream output = socket.getOutputStream();
            output.write((requestLine + "\r\nHost: localhost\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
            output.flush();

            InputStream input = socket.getInputStream();
            ByteArrayOutputStream received = new ByteArrayOutputStream();
            byte[] chunk = new byte[1024];
            int read;
            while ((read = input.read(chunk)) != -1) {
                received.write(chunk, 0, read);
            }
            return received.toString(StandardCharsets.UTF_8);
        }
    }

    /* ------------------------------------------------------------------ */
    /* Static resources                                                    */
    /* ------------------------------------------------------------------ */

    @Test
    @DisplayName("the root serves the home page as HTML")
    void servesTheHomePage() throws Exception {
        HttpResponse<byte[]> response = get("/");

        assertEquals(200, response.statusCode());
        assertEquals("text/html; charset=utf-8", contentType(response));
        assertTrue(text(response).contains("<!DOCTYPE html>"));
        assertEquals(response.body().length,
                Integer.parseInt(response.headers().firstValue("Content-Length").orElse("-1")),
                "Content-Length must match the bytes actually sent");
    }

    @Test
    @DisplayName("the script, the style sheet and both images have their own content type")
    void servesEveryStaticResource() throws Exception {
        assertEquals("text/javascript; charset=utf-8", contentType(get("/js/app.js")));
        assertEquals("text/css; charset=utf-8", contentType(get("/css/styles.css")));

        HttpResponse<byte[]> png = get("/images/logo.png");
        assertEquals(200, png.statusCode());
        assertEquals("image/png", contentType(png));
        assertEquals((byte) 0x89, png.body()[0], "the PNG signature must survive the transfer");

        HttpResponse<byte[]> jpeg = get("/images/cloud.jpg");
        assertEquals(200, jpeg.statusCode());
        assertEquals("image/jpeg", contentType(jpeg));
        assertEquals((byte) 0xFF, jpeg.body()[0], "the JPEG signature must survive the transfer");
        assertEquals((byte) 0xD8, jpeg.body()[1]);
    }

    @Test
    void missingStaticFileAnswersNotFound() throws Exception {
        HttpResponse<byte[]> response = get("/images/missing.png");

        assertEquals(404, response.statusCode());
        assertTrue(contentType(response).startsWith("text/html"));
    }

    /* ------------------------------------------------------------------ */
    /* Hardcoded services                                                  */
    /* ------------------------------------------------------------------ */

    @Test
    void greetingServiceAnswersJson() throws Exception {
        HttpResponse<byte[]> response = get("/app/hello?name=Ada%20Lovelace");

        assertEquals(200, response.statusCode());
        assertEquals("application/json; charset=utf-8", contentType(response));
        assertTrue(text(response).contains("Hello, Ada Lovelace!"));
    }

    @Test
    void squareServiceAnswersJson() throws Exception {
        HttpResponse<byte[]> response = get("/app/square?value=12");

        assertEquals(200, response.statusCode());
        assertTrue(text(response).contains("\"square\":144"));
    }

    @Test
    void serverTimeAndHealthAnswerJson() throws Exception {
        assertEquals(200, get("/app/time").statusCode());
        assertTrue(text(get("/app/time")).contains("\"iso8601\""));

        HttpResponse<byte[]> health = get("/app/health");
        assertEquals(200, health.statusCode());
        assertTrue(text(health).contains("\"status\":\"UP\""));
    }

    @Test
    @DisplayName("invalid input becomes a 400 with a message the client can display")
    void invalidServiceInputAnswersBadRequest() throws Exception {
        HttpResponse<byte[]> missingName = get("/app/hello");
        assertEquals(400, missingName.statusCode());
        assertEquals("application/json; charset=utf-8", contentType(missingName));
        assertTrue(text(missingName).contains("\"message\""));

        assertEquals(400, get("/app/square?value=not-a-number").statusCode());
        assertEquals(400, get("/app/square").statusCode());
    }

    @Test
    void unknownServiceAnswersNotFound() throws Exception {
        assertEquals(404, get("/app/does-not-exist").statusCode());
    }

    /* ------------------------------------------------------------------ */
    /* Controlled errors                                                   */
    /* ------------------------------------------------------------------ */

    @Test
    @DisplayName("an unsupported method answers 405 and says what is allowed")
    void unsupportedMethodAnswersMethodNotAllowed() throws Exception {
        String response = rawRequest("POST /app/hello?name=Ada HTTP/1.1");

        assertTrue(response.startsWith("HTTP/1.1 405 Method Not Allowed"), response);
        assertTrue(response.contains("Allow: GET"), response);
    }

    @Test
    @DisplayName("a path traversal is rejected and discloses nothing")
    void pathTraversalIsRejected() throws Exception {
        String plain = rawRequest("GET /../../../../etc/passwd HTTP/1.1");
        assertTrue(plain.startsWith("HTTP/1.1 403 Forbidden"), plain);
        assertFalse(plain.contains("root:"), "no file outside the public area may be disclosed");

        String encoded = rawRequest("GET /%2e%2e/%2e%2e/etc/passwd HTTP/1.1");
        assertTrue(encoded.startsWith("HTTP/1.1 403 Forbidden"), encoded);

        String deep = rawRequest("GET /images/../../pom.xml HTTP/1.1");
        assertTrue(deep.startsWith("HTTP/1.1 403 Forbidden"), deep);
        assertFalse(deep.contains("<artifactId>"), "the project descriptor must not leak");
    }

    @Test
    @DisplayName("a malformed request answers 400 and the server keeps serving")
    void malformedRequestDoesNotStopTheServer() throws Exception {
        String response = rawRequest("THIS IS NOT HTTP");
        assertTrue(response.startsWith("HTTP/1.1 400 Bad Request"), response);

        // The proof that the loop survived: the next request is served normally.
        assertEquals(200, get("/app/health").statusCode());
    }

    /* ------------------------------------------------------------------ */
    /* Sequential lifecycle                                                */
    /* ------------------------------------------------------------------ */

    @Test
    @DisplayName("more than ten consecutive operations succeed in one server run")
    void handlesManyConsecutiveRequests() throws Exception {
        String[] targets = {
                "/", "/css/styles.css", "/js/app.js", "/images/logo.png", "/images/cloud.jpg",
                "/app/health", "/app/time", "/app/hello?name=One", "/app/hello?name=Two",
                "/app/square?value=3", "/app/square?value=9", "/app/health", "/"
        };

        for (String target : targets) {
            assertEquals(200, get(target).statusCode(), "failed while requesting " + target);
        }
    }

    @Test
    @DisplayName("the slow service really occupies the server, and the next request is served afterwards")
    void slowRequestIsFollowedByANormalOne() throws Exception {
        long startedAt = System.currentTimeMillis();
        assertEquals(200, get("/app/slow?ms=400").statusCode());
        long afterSlow = System.currentTimeMillis() - startedAt;

        assertTrue(afterSlow >= 400, "the slow service answered too fast: " + afterSlow + " ms");
        assertEquals(200, get("/app/health").statusCode());
    }
}
