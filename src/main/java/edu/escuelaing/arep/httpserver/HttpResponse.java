package edu.escuelaing.arep.httpserver;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One HTTP response held entirely in memory.
 *
 * <p>The body is <em>always</em> a byte array. Text and binary resources
 * therefore follow exactly one path to the socket, and {@code Content-Length}
 * is computed from the real number of bytes instead of the number of
 * characters, which would be wrong for any non-ASCII text and for every
 * image.</p>
 */
public class HttpResponse {

    private static final String SERVER_NAME = "arep-minimal-http-server/1.0";

    private final int status;
    private final String contentType;
    private final byte[] body;
    private final Map<String, String> headers = new LinkedHashMap<>();

    public HttpResponse(int status, String contentType, byte[] body) {
        this.status = status;
        this.contentType = contentType;
        this.body = body == null ? new byte[0] : body;
    }

    public static HttpResponse of(int status, String contentType, byte[] body) {
        return new HttpResponse(status, contentType, body);
    }

    public static HttpResponse text(int status, String body) {
        return new HttpResponse(status, "text/plain; charset=utf-8", body.getBytes(StandardCharsets.UTF_8));
    }

    public static HttpResponse html(int status, String body) {
        return new HttpResponse(status, "text/html; charset=utf-8", body.getBytes(StandardCharsets.UTF_8));
    }

    /** A JSON response. Dynamic data is never cached: every call must reach the server. */
    public static HttpResponse json(int status, String body) {
        HttpResponse response = new HttpResponse(status, "application/json; charset=utf-8",
                body.getBytes(StandardCharsets.UTF_8));
        response.header("Cache-Control", "no-store");
        return response;
    }

    /**
     * A controlled error described as JSON, so the browser client can display a
     * friendly message without ever seeing an implementation detail.
     */
    public static HttpResponse jsonError(int status, String message) {
        String body = "{\"status\":" + status
                + ",\"error\":\"" + Json.escape(HttpStatus.reason(status)) + "\""
                + ",\"message\":\"" + Json.escape(message) + "\"}";
        return json(status, body);
    }

    public HttpResponse header(String name, String value) {
        headers.put(name, value);
        return this;
    }

    public int getStatus() {
        return status;
    }

    public String getContentType() {
        return contentType;
    }

    public byte[] getBody() {
        return body;
    }

    public int getContentLength() {
        return body.length;
    }

    /**
     * Serialises status line, headers, the mandatory blank line and the body.
     * Headers are US-ASCII; the body keeps the bytes it already had.
     */
    public void writeTo(OutputStream output) throws IOException {
        StringBuilder head = new StringBuilder(256);
        head.append("HTTP/1.1 ").append(status).append(' ').append(HttpStatus.reason(status)).append("\r\n");
        head.append("Content-Type: ").append(contentType).append("\r\n");
        head.append("Content-Length: ").append(body.length).append("\r\n");
        head.append("Date: ").append(DateTimeFormatter.RFC_1123_DATE_TIME.format(ZonedDateTime.now())).append("\r\n");
        head.append("Server: ").append(SERVER_NAME).append("\r\n");
        for (Map.Entry<String, String> header : headers.entrySet()) {
            head.append(header.getKey()).append(": ").append(header.getValue()).append("\r\n");
        }
        // The server closes every connection after answering: it handles one at a time.
        head.append("Connection: close\r\n");
        head.append("\r\n");

        output.write(head.toString().getBytes(StandardCharsets.US_ASCII));
        output.write(body);
        output.flush();
    }
}
