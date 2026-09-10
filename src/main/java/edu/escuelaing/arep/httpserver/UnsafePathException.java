package edu.escuelaing.arep.httpserver;

/**
 * Raised when a requested path tries to escape the public resources area, for
 * example {@code /../../etc/passwd} or its percent-encoded disguise
 * {@code /%2e%2e/%2e%2e/etc/passwd}.
 *
 * <p>The request is rejected before any file is opened, so nothing outside the
 * public area can ever be disclosed.</p>
 */
public class UnsafePathException extends Exception {

    public UnsafePathException(String message) {
        super(message);
    }
}
