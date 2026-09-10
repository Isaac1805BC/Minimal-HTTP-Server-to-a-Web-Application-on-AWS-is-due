package edu.escuelaing.arep.httpserver;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("StaticResourceHandler: safe paths and correct response metadata")
class StaticResourceHandlerTest {

    private final StaticResourceHandler handler = new StaticResourceHandler();

    @Nested
    @DisplayName("path normalization")
    class Normalization {

        @Test
        void theRootIsTheHomePage() throws Exception {
            assertEquals("/index.html", StaticResourceHandler.normalize("/"));
            assertEquals("/index.html", StaticResourceHandler.normalize(""));
            assertEquals("/index.html", StaticResourceHandler.normalize(null));
        }

        @Test
        void aDirectoryPathIsCompletedWithTheIndexFile() throws Exception {
            assertEquals("/docs/index.html", StaticResourceHandler.normalize("/docs/"));
        }

        @Test
        void emptyAndCurrentDirectorySegmentsAreDropped() throws Exception {
            assertEquals("/css/styles.css", StaticResourceHandler.normalize("/css//styles.css"));
            assertEquals("/css/styles.css", StaticResourceHandler.normalize("/./css/./styles.css"));
        }

        @Test
        void normalPathsSurviveUntouched() throws Exception {
            assertEquals("/images/logo.png", StaticResourceHandler.normalize("/images/logo.png"));
        }

        @ParameterizedTest(name = "{0} is rejected")
        @DisplayName("any attempt to leave the public area is rejected, not resolved")
        @ValueSource(strings = {
                "/../pom.xml",
                "/../../etc/passwd",
                "/images/../../etc/passwd",
                "/css/../../../../../../etc/shadow",
                "/..",
                "/valid/path/../../.."
        })
        void rejectsTraversal(String path) {
            assertThrows(UnsafePathException.class, () -> StaticResourceHandler.normalize(path));
        }

        @Test
        @DisplayName("a traversal that arrives percent-encoded is caught too, because the path was decoded first")
        void rejectsDecodedTraversal() throws Exception {
            String decoded = HttpRequest.percentDecode("/%2e%2e/%2e%2e/etc/passwd", false);
            assertEquals("/../../etc/passwd", decoded);
            assertThrows(UnsafePathException.class, () -> StaticResourceHandler.normalize(decoded));
        }

        @Test
        void rejectsIllegalCharactersAndRelativePaths() {
            assertThrows(UnsafePathException.class, () -> StaticResourceHandler.normalize("/windows\\style"));
            assertThrows(UnsafePathException.class, () -> StaticResourceHandler.normalize("/bad\0name"));
            assertThrows(UnsafePathException.class, () -> StaticResourceHandler.normalize("index.html"));
        }
    }

    @Nested
    @DisplayName("resources packaged inside the artifact")
    class PackagedResources {

        @Test
        void servesTheHomePageAsHtml() {
            HttpResponse response = handler.handle("/");

            assertEquals(HttpStatus.OK, response.getStatus());
            assertEquals("text/html; charset=utf-8", response.getContentType());
            assertTrue(response.getContentLength() > 0);
            assertTrue(new String(response.getBody(), StandardCharsets.UTF_8).contains("<html"));
        }

        @Test
        void servesTheScriptAndTheStyleSheet() {
            assertEquals("text/javascript; charset=utf-8", handler.handle("/js/app.js").getContentType());
            assertEquals("text/css; charset=utf-8", handler.handle("/css/styles.css").getContentType());
        }

        @Test
        @DisplayName("images keep their bytes: the PNG and JPEG signatures survive")
        void servesImagesAsBinary() {
            HttpResponse png = handler.handle("/images/logo.png");
            assertEquals("image/png", png.getContentType());
            byte[] pngBytes = png.getBody();
            assertEquals((byte) 0x89, pngBytes[0]);
            assertEquals('P', pngBytes[1]);
            assertEquals('N', pngBytes[2]);
            assertEquals('G', pngBytes[3]);

            HttpResponse jpeg = handler.handle("/images/cloud.jpg");
            assertEquals("image/jpeg", jpeg.getContentType());
            byte[] jpegBytes = jpeg.getBody();
            assertEquals((byte) 0xFF, jpegBytes[0]);
            assertEquals((byte) 0xD8, jpegBytes[1]);
        }

        @Test
        @DisplayName("the declared length is the number of bytes, not the number of characters")
        void lengthIsMeasuredInBytes() {
            HttpResponse response = handler.handle("/index.html");
            assertEquals(response.getBody().length, response.getContentLength());
        }

        @Test
        void missingResourcesAnswerNotFound() {
            HttpResponse response = handler.handle("/images/does-not-exist.png");

            assertEquals(HttpStatus.NOT_FOUND, response.getStatus());
            assertTrue(response.getContentType().startsWith("text/html"));
        }

        @Test
        void unsafePathsAnswerForbiddenWithoutDisclosingAnything() {
            HttpResponse response = handler.handle("/../../etc/passwd");

            assertEquals(HttpStatus.FORBIDDEN, response.getStatus());
            String body = new String(response.getBody(), StandardCharsets.UTF_8);
            assertFalse(body.contains("etc/passwd"));
        }
    }

    @Nested
    @DisplayName("external resources directory")
    class ExternalDirectory {

        @Test
        void readsFilesFromTheConfiguredDirectory(@TempDir Path root) throws Exception {
            Files.createDirectories(root.resolve("images"));
            Files.write(root.resolve("images/extra.png"), new byte[]{1, 2, 3, 4});

            StaticResourceHandler external = new StaticResourceHandler(root.toString());
            HttpResponse response = external.handle("/images/extra.png");

            assertEquals(HttpStatus.OK, response.getStatus());
            assertEquals("image/png", response.getContentType());
            assertArrayEquals(new byte[]{1, 2, 3, 4}, response.getBody());
        }

        @Test
        @DisplayName("a file next to the directory cannot be reached through it")
        void cannotEscapeTheConfiguredDirectory(@TempDir Path parent) throws Exception {
            Path root = Files.createDirectories(parent.resolve("public"));
            Files.writeString(parent.resolve("secret.txt"), "top secret");

            StaticResourceHandler external = new StaticResourceHandler(root.toString());
            HttpResponse response = external.handle("/../secret.txt");

            assertEquals(HttpStatus.FORBIDDEN, response.getStatus());
            assertFalse(new String(response.getBody(), StandardCharsets.UTF_8).contains("top secret"));
        }

        @Test
        @DisplayName("what the directory does not have still comes from the artifact")
        void fallsBackToThePackagedResources(@TempDir Path root) {
            StaticResourceHandler external = new StaticResourceHandler(root.toString());
            assertEquals(HttpStatus.OK, external.handle("/index.html").getStatus());
        }
    }
}
