package edu.escuelaing.arep.httpserver;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Serves the files of the public resources area.
 *
 * <p>Every resource, text or binary, is read as a byte array and written back
 * through the same path. Only the declared content type differs, so an HTML
 * page and a JPEG photograph never need two different mechanisms.</p>
 *
 * <p>Resources normally travel inside the jar (under {@code /public} in the
 * classpath), which makes the artifact self-contained. An external directory
 * can be configured with {@code STATIC_DIR} or {@code --static-dir=} to replace
 * files without rebuilding; that directory is searched first.</p>
 */
public class StaticResourceHandler {

    /** Location of the public resources inside the artifact. */
    public static final String CLASSPATH_ROOT = "/public";

    /** Served when the requested path is the root. */
    public static final String INDEX_FILE = "index.html";

    private final Path externalRoot;

    public StaticResourceHandler() {
        this(null);
    }

    public StaticResourceHandler(String externalDirectory) {
        this.externalRoot = externalDirectory == null || externalDirectory.isBlank()
                ? null
                : Paths.get(externalDirectory).toAbsolutePath().normalize();
    }

    public Path getExternalRoot() {
        return externalRoot;
    }

    /**
     * Turns the requested path into a safe path relative to the public area.
     *
     * <p>The rules are deliberately strict and easy to audit:</p>
     * <ul>
     *   <li>the path must be absolute;</li>
     *   <li>{@code NUL} and backslashes are rejected outright;</li>
     *   <li>empty and {@code .} segments are dropped;</li>
     *   <li>a {@code ..} segment is <em>rejected</em>, not resolved, so no
     *       combination of segments can ever climb above the root;</li>
     *   <li>a path ending in {@code /} is completed with the index file.</li>
     * </ul>
     *
     * @return a normalized path such as {@code /images/logo.png}
     * @throws UnsafePathException when the path tries to leave the public area
     */
    public static String normalize(String requestedPath) throws UnsafePathException {
        if (requestedPath == null || requestedPath.isEmpty() || "/".equals(requestedPath)) {
            return "/" + INDEX_FILE;
        }
        if (!requestedPath.startsWith("/")) {
            throw new UnsafePathException("Path is not absolute: " + requestedPath);
        }
        if (requestedPath.indexOf('\0') >= 0 || requestedPath.indexOf('\\') >= 0) {
            throw new UnsafePathException("Path contains an illegal character");
        }

        Deque<String> segments = new ArrayDeque<>();
        for (String segment : requestedPath.split("/")) {
            if (segment.isEmpty() || ".".equals(segment)) {
                continue;
            }
            if ("..".equals(segment)) {
                throw new UnsafePathException("Path traversal rejected: " + requestedPath);
            }
            segments.addLast(segment);
        }

        if (segments.isEmpty()) {
            return "/" + INDEX_FILE;
        }

        String normalized = "/" + String.join("/", segments);
        return requestedPath.endsWith("/") ? normalized + "/" + INDEX_FILE : normalized;
    }

    /**
     * Resolves and returns the requested resource.
     *
     * @return {@code 200} with the file bytes and its content type,
     *         {@code 403} for an unsafe path, {@code 404} when the file does not
     *         exist and {@code 500} when it cannot be read
     */
    public HttpResponse handle(String requestedPath) {
        String normalized;
        try {
            normalized = normalize(requestedPath);
        } catch (UnsafePathException unsafe) {
            return HttpResponse.jsonError(HttpStatus.FORBIDDEN,
                    "The requested path is not allowed.");
        }

        byte[] bytes;
        try {
            bytes = read(normalized);
        } catch (UnsafePathException unsafe) {
            return HttpResponse.jsonError(HttpStatus.FORBIDDEN,
                    "The requested path is not allowed.");
        } catch (IOException failure) {
            return HttpResponse.jsonError(HttpStatus.INTERNAL_SERVER_ERROR,
                    "The resource could not be read.");
        }

        if (bytes == null) {
            return notFound(normalized);
        }

        // Content-Length comes from bytes.length, never from a character count.
        return HttpResponse.of(HttpStatus.OK, MimeTypes.forPath(normalized), bytes)
                // Disabled on purpose: every reload must produce a real request,
                // which keeps the network timeline readable while testing the lab.
                .header("Cache-Control", "no-store");
    }

    /**
     * Reads the resource bytes, looking first in the external directory when one
     * is configured and then inside the artifact.
     *
     * @return the bytes, or {@code null} when the resource does not exist
     */
    byte[] read(String normalizedPath) throws IOException, UnsafePathException {
        if (externalRoot != null) {
            Path candidate = externalRoot.resolve(normalizedPath.substring(1)).normalize();
            // Second barrier: even after normalization the file must stay inside the root.
            if (!candidate.startsWith(externalRoot)) {
                throw new UnsafePathException("Resolved path escapes the public area");
            }
            if (Files.isRegularFile(candidate)) {
                return Files.readAllBytes(candidate);
            }
        }

        try (InputStream resource = StaticResourceHandler.class
                .getResourceAsStream(CLASSPATH_ROOT + normalizedPath)) {
            return resource == null ? null : resource.readAllBytes();
        }
    }

    private HttpResponse notFound(String normalizedPath) {
        // An HTML body, because a missing resource is usually requested by a browser.
        String body = "<!DOCTYPE html><html lang=\"en\"><head><meta charset=\"utf-8\">"
                + "<title>404 Not Found</title></head><body>"
                + "<h1>404 Not Found</h1>"
                + "<p>The resource <code>" + escapeHtml(normalizedPath) + "</code> does not exist "
                + "in the public resources area.</p>"
                + "<p><a href=\"/\">Back to the home page</a></p>"
                + "</body></html>";
        return HttpResponse.html(HttpStatus.NOT_FOUND, body);
    }

    /** The path is echoed back to the browser, so it is escaped before being inserted. */
    private static String escapeHtml(String value) {
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
