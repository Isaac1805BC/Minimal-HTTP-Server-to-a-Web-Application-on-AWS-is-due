package edu.escuelaing.arep.httpserver;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

@DisplayName("MimeTypes: the extension decides what the browser is told")
class MimeTypesTest {

    @ParameterizedTest(name = "{0} is announced as {1}")
    @CsvSource({
            "/index.html,          text/html; charset=utf-8",
            "/css/styles.css,      text/css; charset=utf-8",
            "/js/app.js,           text/javascript; charset=utf-8",
            "/images/logo.png,     image/png",
            "/images/cloud.jpg,    image/jpeg",
            "/images/cloud.jpeg,   image/jpeg",
            "/data/report.json,    application/json; charset=utf-8",
            "/notes.txt,           text/plain; charset=utf-8"
    })
    void mapsKnownExtensions(String path, String expectedType) {
        assertEquals(expectedType, MimeTypes.forPath(path));
    }

    @Test
    @DisplayName("the comparison ignores case")
    void isCaseInsensitive() {
        assertEquals("image/png", MimeTypes.forPath("/images/LOGO.PNG"));
    }

    @Test
    @DisplayName("an unknown or missing extension is served as an opaque stream")
    void fallsBackToOctetStream() {
        assertEquals(MimeTypes.DEFAULT_TYPE, MimeTypes.forPath("/archive.xyz"));
        assertEquals(MimeTypes.DEFAULT_TYPE, MimeTypes.forPath("/README"));
        assertEquals(MimeTypes.DEFAULT_TYPE, MimeTypes.forPath(null));
    }

    @Test
    void extractsTheExtension() {
        assertEquals("png", MimeTypes.extensionOf("/images/logo.png"));
        assertNull(MimeTypes.extensionOf("/images/logo"));
        assertNull(MimeTypes.extensionOf("/images/logo."));
        // A dot in a directory name must not be mistaken for an extension.
        assertNull(MimeTypes.extensionOf("/v1.0/README"));
    }
}
