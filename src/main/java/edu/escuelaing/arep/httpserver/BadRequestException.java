package edu.escuelaing.arep.httpserver;

/**
 * Raised when the bytes received from the client cannot be interpreted as a
 * valid HTTP request line, header block or percent-encoded value.
 *
 * <p>A malformed request must produce a controlled {@code 400 Bad Request}
 * response for that single connection; it must never terminate the server
 * loop.</p>
 */
public class BadRequestException extends Exception {

    public BadRequestException(String message) {
        super(message);
    }
}
