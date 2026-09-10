package edu.escuelaing.arep.httpserver;

/**
 * The small set of HTTP status codes this laboratory produces, together with
 * their reason phrases. Only what the lab needs: no attempt at a complete
 * status registry.
 */
public final class HttpStatus {

    public static final int OK = 200;
    public static final int BAD_REQUEST = 400;
    public static final int FORBIDDEN = 403;
    public static final int NOT_FOUND = 404;
    public static final int METHOD_NOT_ALLOWED = 405;
    public static final int URI_TOO_LONG = 414;
    public static final int INTERNAL_SERVER_ERROR = 500;

    private HttpStatus() {
    }

    public static String reason(int status) {
        switch (status) {
            case OK:
                return "OK";
            case BAD_REQUEST:
                return "Bad Request";
            case FORBIDDEN:
                return "Forbidden";
            case NOT_FOUND:
                return "Not Found";
            case METHOD_NOT_ALLOWED:
                return "Method Not Allowed";
            case URI_TOO_LONG:
                return "URI Too Long";
            case INTERNAL_SERVER_ERROR:
                return "Internal Server Error";
            default:
                return "Unknown";
        }
    }
}
