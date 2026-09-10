package edu.escuelaing.arep.httpserver;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * Sequential HTTP server: the listening socket stays open, and every accepted
 * connection is served completely before the next one is accepted.
 *
 * <p>This is repetition, not concurrency. There is a single thread and a single
 * connection in flight at any moment, which is exactly the limitation this
 * laboratory wants to keep observable.</p>
 */
public class HttpServer {

    private static final int DEFAULT_PORT = 8080;
    /** A client that opens a connection and never speaks must not block the loop forever. */
    private static final int CLIENT_TIMEOUT_MILLIS = 15_000;

    private final int configuredPort;
    private final StaticResourceHandler staticResources;
    private ServerSocket serverSocket;
    private volatile boolean running;

    public HttpServer(int port) {
        this(port, new StaticResourceHandler());
    }

    public HttpServer(int port, StaticResourceHandler staticResources) {
        this.configuredPort = port;
        this.staticResources = staticResources;
    }

    public static void main(String[] args) throws IOException {
        HttpServer server = new HttpServer(resolvePort(args), new StaticResourceHandler(resolveStaticDir(args)));
        Runtime.getRuntime().addShutdownHook(new Thread(server::stop));
        server.bind();
        server.serve();
    }

    /**
     * Port resolution order: {@code --port=N} argument, then the {@code PORT}
     * environment variable, then the default. Making it configurable is what
     * allows the same artifact to run locally and on EC2 unchanged.
     */
    static int resolvePort(String[] args) {
        for (String argument : args) {
            if (argument.startsWith("--port=")) {
                return Integer.parseInt(argument.substring("--port=".length()).trim());
            }
        }
        String fromEnvironment = System.getenv("PORT");
        if (fromEnvironment != null && !fromEnvironment.isBlank()) {
            return Integer.parseInt(fromEnvironment.trim());
        }
        return DEFAULT_PORT;
    }

    /**
     * Optional external public resources directory: {@code --static-dir=PATH} or
     * the {@code STATIC_DIR} variable. When absent, the resources packaged
     * inside the artifact are used.
     */
    static String resolveStaticDir(String[] args) {
        for (String argument : args) {
            if (argument.startsWith("--static-dir=")) {
                return argument.substring("--static-dir=".length()).trim();
            }
        }
        return System.getenv("STATIC_DIR");
    }

    /**
     * Binds the listening socket on every network interface, which is what makes
     * the server reachable from outside the EC2 instance instead of only from
     * its loopback address.
     */
    public void bind() throws IOException {
        serverSocket = new ServerSocket();
        serverSocket.setReuseAddress(true);
        serverSocket.bind(new InetSocketAddress("0.0.0.0", configuredPort));
        running = true;
        log("Listening on http://0.0.0.0:" + getPort() + "  (sequential, one connection at a time)");
    }

    /** The actual port, which differs from the configured one when 0 was requested. */
    public int getPort() {
        return serverSocket == null ? configuredPort : serverSocket.getLocalPort();
    }

    /** Accepts and serves connections until {@link #stop()} is called. */
    public void serve() {
        while (running) {
            try {
                Socket clientSocket = serverSocket.accept();
                handleConnection(clientSocket);
            } catch (IOException accepting) {
                if (running) {
                    log("Could not accept a connection: " + accepting.getMessage());
                }
            }
        }
        log("Server loop finished.");
    }

    /**
     * Serves exactly one connection and always closes it, whatever happened.
     * Any failure is confined here: a single bad request can never stop the
     * loop.
     */
    private void handleConnection(Socket clientSocket) {
        long startedAt = System.currentTimeMillis();
        String peer = String.valueOf(clientSocket.getInetAddress().getHostAddress());
        try (Socket socket = clientSocket;
             InputStream input = socket.getInputStream();
             OutputStream output = socket.getOutputStream()) {

            socket.setSoTimeout(CLIENT_TIMEOUT_MILLIS);

            HttpRequest request;
            try {
                request = HttpRequest.parse(input);
            } catch (BadRequestException malformed) {
                log(peer + " -> 400 (" + malformed.getMessage() + ")");
                HttpResponse.jsonError(HttpStatus.BAD_REQUEST, "The request could not be understood.")
                        .writeTo(output);
                return;
            }

            if (request == null) {
                return; // The peer closed the connection before sending a request line.
            }

            HttpResponse response = route(request);
            response.writeTo(output);
            log(peer + " " + request + " -> " + response.getStatus()
                    + " " + response.getContentType()
                    + " " + response.getContentLength() + "B"
                    + " " + (System.currentTimeMillis() - startedAt) + "ms");

        } catch (IOException failure) {
            log("Connection with " + peer + " failed: " + failure.getMessage());
        } catch (RuntimeException unexpected) {
            // Never let an unexpected failure escape into the accept loop.
            log("Unexpected failure serving " + peer + ": " + unexpected);
        }
    }

    /**
     * Chooses the response for a request.
     *
     * <p>The special URLs are recognised with direct, explicit comparisons.
     * There is no routing table and no framework: the chain below <em>is</em>
     * the routing mechanism, and everything it does not match is looked up in
     * the public resources area.</p>
     */
    HttpResponse route(HttpRequest request) {
        if (!"GET".equals(request.getMethod())) {
            return HttpResponse.jsonError(HttpStatus.METHOD_NOT_ALLOWED,
                            "This server only supports GET.")
                    .header("Allow", "GET");
        }

        String path = request.getPath();

        if ("/app/hello".equals(path)) {
            return Services.hello(request);
        }
        if ("/app/square".equals(path)) {
            return Services.square(request);
        }
        if ("/app/time".equals(path)) {
            return Services.time();
        }
        if ("/app/health".equals(path)) {
            return Services.health();
        }
        if ("/app/slow".equals(path)) {
            return Services.slow(request);
        }
        if (path.startsWith(Services.PREFIX)) {
            // Under /app/ there are only the services above; do not fall back to files.
            return HttpResponse.jsonError(HttpStatus.NOT_FOUND, "Unknown service.");
        }

        return staticResources.handle(path);
    }

    /** Stops the loop and releases the listening socket. */
    public void stop() {
        running = false;
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException closing) {
            log("Could not close the listening socket: " + closing.getMessage());
        }
    }

    private static void log(String message) {
        System.out.println("[" + java.time.LocalDateTime.now() + "] " + message);
    }
}
