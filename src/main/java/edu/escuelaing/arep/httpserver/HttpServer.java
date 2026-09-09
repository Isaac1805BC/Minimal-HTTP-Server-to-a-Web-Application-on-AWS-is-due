package edu.escuelaing.arep.httpserver;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/**
 * Baseline of section 4.4 of the networking guide: a minimal HTTP server that
 * accepts exactly one TCP connection, reads one HTTP request, answers with a
 * small HTML document and stops.
 *
 * <p>This class is the starting point of the laboratory. The next commits turn
 * it into a sequential loop able to serve static resources and hardcoded
 * services, but the protocol exchange demonstrated here never changes:
 * request line, headers, blank line, body.</p>
 */
public class HttpServer {

    private static final int DEFAULT_PORT = 8080;

    public static void main(String[] args) throws IOException {
        int port = DEFAULT_PORT;

        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("Listening on port " + port + " (single connection)");

            try (Socket clientSocket = serverSocket.accept();
                 BufferedReader in = new BufferedReader(
                         new InputStreamReader(clientSocket.getInputStream(), StandardCharsets.UTF_8));
                 PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true)) {

                System.out.println("Connection from " + clientSocket.getInetAddress());

                // Echo the request line and the headers so the protocol exchange is visible.
                String line;
                while ((line = in.readLine()) != null && !line.isEmpty()) {
                    System.out.println("< " + line);
                }

                out.print("HTTP/1.1 200 OK\r\n");
                out.print("Content-Type: text/html; charset=utf-8\r\n");
                out.print("\r\n");
                out.print("<!DOCTYPE html><html><body><h1>Minimal HTTP server</h1></body></html>");
                out.flush();
            }
        }

        System.out.println("Server stopped after one connection.");
    }
}
