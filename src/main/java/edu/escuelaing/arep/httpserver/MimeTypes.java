package edu.escuelaing.arep.httpserver;

import java.util.Locale;
import java.util.Map;

/**
 * Association between a file extension and the content type announced to the
 * browser.
 *
 * <p>The browser does not guess how to treat a response from its file name: it
 * obeys the {@code Content-Type} header. The same bytes are a page, a script or
 * an image depending only on what the server declares here.</p>
 */
public final class MimeTypes {

    /** Used when the extension is unknown: an opaque stream the browser will not execute. */
    public static final String DEFAULT_TYPE = "application/octet-stream";

    private static final Map<String, String> TYPES_BY_EXTENSION = Map.ofEntries(
            Map.entry("html", "text/html; charset=utf-8"),
            Map.entry("htm", "text/html; charset=utf-8"),
            Map.entry("css", "text/css; charset=utf-8"),
            Map.entry("js", "text/javascript; charset=utf-8"),
            Map.entry("mjs", "text/javascript; charset=utf-8"),
            Map.entry("json", "application/json; charset=utf-8"),
            Map.entry("txt", "text/plain; charset=utf-8"),
            Map.entry("csv", "text/csv; charset=utf-8"),
            Map.entry("svg", "image/svg+xml"),
            Map.entry("png", "image/png"),
            Map.entry("jpg", "image/jpeg"),
            Map.entry("jpeg", "image/jpeg"),
            Map.entry("gif", "image/gif"),
            Map.entry("webp", "image/webp"),
            Map.entry("ico", "image/x-icon"),
            Map.entry("woff", "font/woff"),
            Map.entry("woff2", "font/woff2"),
            Map.entry("pdf", "application/pdf"));

    private MimeTypes() {
    }

    /**
     * @param path a resource path such as {@code /images/logo.png}
     * @return the declared content type, or {@link #DEFAULT_TYPE} when the
     *         extension is unknown or absent
     */
    public static String forPath(String path) {
        return forExtension(extensionOf(path));
    }

    public static String forExtension(String extension) {
        if (extension == null) {
            return DEFAULT_TYPE;
        }
        return TYPES_BY_EXTENSION.getOrDefault(extension.toLowerCase(Locale.ROOT), DEFAULT_TYPE);
    }

    /** @return the extension without the dot, or {@code null} when there is none */
    public static String extensionOf(String path) {
        if (path == null) {
            return null;
        }
        int lastSlash = path.lastIndexOf('/');
        int lastDot = path.lastIndexOf('.');
        if (lastDot < 0 || lastDot < lastSlash || lastDot == path.length() - 1) {
            return null;
        }
        return path.substring(lastDot + 1);
    }
}
