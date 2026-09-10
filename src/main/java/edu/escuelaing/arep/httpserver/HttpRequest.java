package edu.escuelaing.arep.httpserver;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * An immutable view of one HTTP request: the request line, the header block and
 * the query string already decoded.
 *
 * <p>The request is read byte by byte instead of through a {@code Reader} so
 * that the exact position where the header block ends (the empty line) is under
 * our control. This laboratory only accepts {@code GET}, therefore no body is
 * ever consumed.</p>
 */
public class HttpRequest {

    /** Defensive limits: a client must not be able to make the server allocate without bound. */
    private static final int MAX_LINE_LENGTH = 8 * 1024;
    private static final int MAX_HEADERS = 64;

    private final String method;
    private final String target;
    private final String version;
    private final String path;
    private final Map<String, String> queryParams;
    private final Map<String, String> headers;

    HttpRequest(String method,
                String target,
                String version,
                String path,
                Map<String, String> queryParams,
                Map<String, String> headers) {
        this.method = method;
        this.target = target;
        this.version = version;
        this.path = path;
        this.queryParams = Collections.unmodifiableMap(queryParams);
        this.headers = Collections.unmodifiableMap(headers);
    }

    /**
     * Reads and parses one request from the client stream.
     *
     * @return the parsed request, or {@code null} when the peer closed the
     *         connection without sending anything (a browser probe, for example)
     * @throws BadRequestException when the bytes are not a valid HTTP request
     */
    public static HttpRequest parse(InputStream input) throws IOException, BadRequestException {
        String requestLine = readLine(input);
        if (requestLine == null) {
            return null;
        }
        if (requestLine.isEmpty()) {
            throw new BadRequestException("Empty request line");
        }

        String[] parts = requestLine.split(" ");
        if (parts.length != 3) {
            throw new BadRequestException("Malformed request line: " + requestLine);
        }

        String method = parts[0].toUpperCase(Locale.ROOT);
        String target = parts[1];
        String version = parts[2];
        if (!version.startsWith("HTTP/")) {
            throw new BadRequestException("Unsupported protocol token: " + version);
        }
        if (!target.startsWith("/")) {
            // Absolute-form and authority-form targets are out of scope for this lab.
            throw new BadRequestException("Unsupported request target: " + target);
        }

        int questionMark = target.indexOf('?');
        String rawPath = questionMark < 0 ? target : target.substring(0, questionMark);
        String rawQuery = questionMark < 0 ? "" : target.substring(questionMark + 1);

        // The path is decoded here on purpose: '..' hidden as '%2e%2e' must be
        // visible to the traversal check performed later by the static handler.
        String path = percentDecode(rawPath, false);
        Map<String, String> queryParams = parseQuery(rawQuery);
        Map<String, String> headers = readHeaders(input);

        return new HttpRequest(method, target, version, path, queryParams, headers);
    }

    private static Map<String, String> readHeaders(InputStream input) throws IOException, BadRequestException {
        Map<String, String> headers = new LinkedHashMap<>();
        String line;
        while ((line = readLine(input)) != null && !line.isEmpty()) {
            if (headers.size() >= MAX_HEADERS) {
                throw new BadRequestException("Too many headers");
            }
            int colon = line.indexOf(':');
            if (colon <= 0) {
                throw new BadRequestException("Malformed header: " + line);
            }
            String name = line.substring(0, colon).trim().toLowerCase(Locale.ROOT);
            String value = line.substring(colon + 1).trim();
            headers.put(name, value);
        }
        return headers;
    }

    /**
     * Splits {@code a=1&b=2} and decodes both names and values. Inside a query
     * string {@code '+'} means a space, which is not true for the path.
     */
    static Map<String, String> parseQuery(String rawQuery) throws BadRequestException {
        Map<String, String> params = new LinkedHashMap<>();
        if (rawQuery == null || rawQuery.isEmpty()) {
            return params;
        }
        for (String pair : rawQuery.split("&")) {
            if (pair.isEmpty()) {
                continue;
            }
            int equals = pair.indexOf('=');
            String name = equals < 0 ? pair : pair.substring(0, equals);
            String value = equals < 0 ? "" : pair.substring(equals + 1);
            params.put(percentDecode(name, true), percentDecode(value, true));
        }
        return params;
    }

    /**
     * Percent-decoding without {@code java.net.URLDecoder}, so the rules are
     * explicit: {@code %XX} becomes a byte, and the resulting byte sequence is
     * interpreted as UTF-8.
     *
     * @param plusAsSpace {@code true} for query components, {@code false} for paths
     */
    static String percentDecode(String value, boolean plusAsSpace) throws BadRequestException {
        ByteArrayOutputStream decoded = new ByteArrayOutputStream(value.length());
        for (int i = 0; i < value.length(); i++) {
            char current = value.charAt(i);
            if (current == '%') {
                if (i + 2 >= value.length()) {
                    throw new BadRequestException("Truncated percent-encoding in: " + value);
                }
                int high = Character.digit(value.charAt(i + 1), 16);
                int low = Character.digit(value.charAt(i + 2), 16);
                if (high < 0 || low < 0) {
                    throw new BadRequestException("Invalid percent-encoding in: " + value);
                }
                decoded.write((high << 4) + low);
                i += 2;
            } else if (current == '+' && plusAsSpace) {
                decoded.write(' ');
            } else {
                decoded.writeBytes(String.valueOf(current).getBytes(StandardCharsets.UTF_8));
            }
        }
        return decoded.toString(StandardCharsets.UTF_8);
    }

    /**
     * Reads one CRLF-terminated line as US-ASCII, tolerating a bare LF.
     *
     * @return the line without its terminator, or {@code null} at end of stream
     */
    private static String readLine(InputStream input) throws IOException, BadRequestException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream(128);
        int read;
        while ((read = input.read()) != -1) {
            if (read == '\n') {
                byte[] bytes = buffer.toByteArray();
                int length = bytes.length;
                if (length > 0 && bytes[length - 1] == '\r') {
                    length--;
                }
                return new String(bytes, 0, length, StandardCharsets.US_ASCII);
            }
            if (buffer.size() >= MAX_LINE_LENGTH) {
                throw new BadRequestException("Request line or header too long");
            }
            buffer.write(read);
        }
        return buffer.size() == 0 ? null : new String(buffer.toByteArray(), StandardCharsets.US_ASCII);
    }

    public String getMethod() {
        return method;
    }

    /** The raw request target, path and query string included. */
    public String getTarget() {
        return target;
    }

    public String getVersion() {
        return version;
    }

    /** The decoded path, always starting with {@code /}. */
    public String getPath() {
        return path;
    }

    public Map<String, String> getQueryParams() {
        return queryParams;
    }

    /** @return the decoded query parameter, or {@code null} when absent */
    public String getQueryParam(String name) {
        return queryParams.get(name);
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    public String getHeader(String name) {
        return headers.get(name.toLowerCase(Locale.ROOT));
    }

    @Override
    public String toString() {
        return method + " " + target + " " + version;
    }
}
