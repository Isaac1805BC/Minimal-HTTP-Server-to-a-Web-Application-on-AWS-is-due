package edu.escuelaing.arep.httpserver;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("Json: untrusted values never break the document")
class JsonTest {

    @Test
    void keepsPlainTextUnchanged() {
        assertEquals("Ada Lovelace", Json.escape("Ada Lovelace"));
    }

    @Test
    void escapesQuotesAndBackslashes() {
        assertEquals("say \\\"hi\\\"", Json.escape("say \"hi\""));
        assertEquals("C:\\\\temp", Json.escape("C:\\temp"));
    }

    @Test
    void escapesControlCharacters() {
        assertEquals("a\\nb\\tc\\r", Json.escape("a\nb\tc\r"));
        assertEquals("\\u0001", Json.escape("\u0001"));
    }

    @Test
    @DisplayName("markup characters are neutralised so a reflected value stays inert")
    void escapesMarkupCharacters() {
        assertEquals("\\u003Cscript\\u003E", Json.escape("<script>"));
        assertEquals("a\\u0026b", Json.escape("a&b"));
    }

    @Test
    void nullBecomesAnEmptyString() {
        assertEquals("", Json.escape(null));
    }

    @Test
    void rendersObjectsFromMembers() {
        assertEquals("{\"service\":\"square\",\"square\":49}",
                Json.object(Json.string("service", "square"), Json.number("square", 49)));
    }
}
