package edu.escuelaing.arep.httpserver;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

/**
 * The four hardcoded services of the laboratory, plus one deliberately slow
 * endpoint used to observe the sequential limitation.
 *
 * <p>There is no router, no reflection, no annotations and no dependency
 * injection here on purpose. Each service is a plain method selected by an
 * explicit comparison in {@link HttpServer#route(HttpRequest)}, so the
 * mechanism that turns a URL into behaviour stays completely visible. A
 * framework is exactly what would generalise this, and hide it.</p>
 *
 * <p>Every service is stateless: nothing supplied by a user is stored between
 * requests, and every value that reaches a JSON document goes through
 * {@link Json#escape(String)} first.</p>
 */
public final class Services {

    /** Common prefix of the special URLs. Anything else is a static resource. */
    public static final String PREFIX = "/app/";

    private static final int MAX_NAME_LENGTH = 64;
    private static final long MAX_DELAY_MILLIS = 15_000L;
    private static final long DEFAULT_DELAY_MILLIS = 5_000L;

    private static final long STARTED_AT = System.currentTimeMillis();

    private Services() {
    }

    /**
     * {@code GET /app/hello?name=Ada} &rarr; a JSON greeting.
     * The name is required and is escaped before being placed in the document.
     */
    public static HttpResponse hello(HttpRequest request) {
        String name = request.getQueryParam("name");
        if (name == null) {
            return HttpResponse.jsonError(HttpStatus.BAD_REQUEST,
                    "The 'name' parameter is required.");
        }
        name = name.trim();
        if (name.isEmpty()) {
            return HttpResponse.jsonError(HttpStatus.BAD_REQUEST,
                    "The 'name' parameter cannot be empty.");
        }
        if (name.length() > MAX_NAME_LENGTH) {
            return HttpResponse.jsonError(HttpStatus.BAD_REQUEST,
                    "The 'name' parameter cannot be longer than " + MAX_NAME_LENGTH + " characters.");
        }

        return HttpResponse.json(HttpStatus.OK, Json.object(
                Json.string("service", "greeting"),
                Json.string("name", name),
                Json.string("message", "Hello, " + name + "!")));
    }

    /**
     * {@code GET /app/square?value=7} &rarr; the value and its square, the same
     * calculation as the earlier socket exercise.
     */
    public static HttpResponse square(HttpRequest request) {
        String raw = request.getQueryParam("value");
        if (raw == null || raw.trim().isEmpty()) {
            return HttpResponse.jsonError(HttpStatus.BAD_REQUEST,
                    "The 'value' parameter is required.");
        }

        double value;
        try {
            value = Double.parseDouble(raw.trim());
        } catch (NumberFormatException notANumber) {
            return HttpResponse.jsonError(HttpStatus.BAD_REQUEST,
                    "The 'value' parameter must be a number.");
        }
        if (!Double.isFinite(value)) {
            return HttpResponse.jsonError(HttpStatus.BAD_REQUEST,
                    "The 'value' parameter must be a finite number.");
        }

        double square = value * value;
        if (!Double.isFinite(square)) {
            return HttpResponse.jsonError(HttpStatus.BAD_REQUEST,
                    "The 'value' parameter is too large to be squared.");
        }

        return HttpResponse.json(HttpStatus.OK, Json.object(
                Json.string("service", "square"),
                "\"value\":" + format(value),
                "\"square\":" + format(square)));
    }

    /** {@code GET /app/time} &rarr; the clock of the machine running the server. */
    public static HttpResponse time() {
        ZonedDateTime now = ZonedDateTime.now();
        return HttpResponse.json(HttpStatus.OK, Json.object(
                Json.string("service", "server-time"),
                Json.string("iso8601", now.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)),
                Json.string("readable", now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))),
                Json.string("zone", now.getZone().getId()),
                Json.number("epochMillis", now.toInstant().toEpochMilli())));
    }

    /** {@code GET /app/health} &rarr; proof that the process can still answer. */
    public static HttpResponse health() {
        long uptimeMillis = System.currentTimeMillis() - STARTED_AT;
        return HttpResponse.json(HttpStatus.OK, Json.object(
                Json.string("status", "UP"),
                Json.number("uptimeMillis", uptimeMillis),
                Json.string("uptime", humanUptime(uptimeMillis))));
    }

    /**
     * {@code GET /app/slow?ms=5000} &rarr; answers after the requested delay.
     *
     * <p>Not a concurrency mechanism: the single server thread simply stops
     * here. That is precisely the point. While this call is being served, every
     * other client waits in the accept queue, which is what section 6.2 asks to
     * observe.</p>
     */
    public static HttpResponse slow(HttpRequest request) {
        long delay = DEFAULT_DELAY_MILLIS;
        String raw = request.getQueryParam("ms");
        if (raw != null && !raw.trim().isEmpty()) {
            try {
                delay = Long.parseLong(raw.trim());
            } catch (NumberFormatException notANumber) {
                return HttpResponse.jsonError(HttpStatus.BAD_REQUEST,
                        "The 'ms' parameter must be an integer number of milliseconds.");
            }
            if (delay < 0 || delay > MAX_DELAY_MILLIS) {
                return HttpResponse.jsonError(HttpStatus.BAD_REQUEST,
                        "The 'ms' parameter must be between 0 and " + MAX_DELAY_MILLIS + ".");
            }
        }

        long startedAt = System.currentTimeMillis();
        try {
            Thread.sleep(delay);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return HttpResponse.jsonError(HttpStatus.INTERNAL_SERVER_ERROR,
                    "The request was interrupted.");
        }

        return HttpResponse.json(HttpStatus.OK, Json.object(
                Json.string("service", "slow"),
                Json.number("requestedDelayMillis", delay),
                Json.number("actualDelayMillis", System.currentTimeMillis() - startedAt),
                Json.string("note", "While this request was being served the server accepted nobody else.")));
    }

    /** Prints 7 instead of 7.0 when the value is a whole number. */
    static String format(double value) {
        if (value == Math.rint(value) && Math.abs(value) < 1e15) {
            return Long.toString((long) value);
        }
        return Double.toString(value);
    }

    private static String humanUptime(long millis) {
        Duration duration = Duration.ofMillis(millis);
        return String.format("%dh %02dm %02ds",
                duration.toHours(), duration.toMinutesPart(), duration.toSecondsPart());
    }
}
