package edu.escuelaing.arep.httpserver;

/**
 * The minimum amount of JSON support the laboratory needs, written by hand so
 * the project keeps zero runtime dependencies.
 *
 * <p>Its only real responsibility is {@link #escape(String)}: a value supplied
 * by a user must never be concatenated into a JSON document without escaping,
 * otherwise a name such as {@code "} would break the document and a value such
 * as {@code </script>} could be reflected into the page.</p>
 */
public final class Json {

    private Json() {
    }

    /**
     * Escapes a string so it can be placed between double quotes in a JSON
     * document. Quotes, backslashes and control characters are replaced;
     * {@code <}, {@code >} and {@code &} are escaped as unicode sequences so the
     * payload stays inert even if a browser ever interprets it as markup.
     */
    public static String escape(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder escaped = new StringBuilder(value.length() + 16);
        for (int i = 0; i < value.length(); i++) {
            char current = value.charAt(i);
            switch (current) {
                case '"':
                    escaped.append("\\\"");
                    break;
                case '\\':
                    escaped.append("\\\\");
                    break;
                case '\b':
                    escaped.append("\\b");
                    break;
                case '\f':
                    escaped.append("\\f");
                    break;
                case '\n':
                    escaped.append("\\n");
                    break;
                case '\r':
                    escaped.append("\\r");
                    break;
                case '\t':
                    escaped.append("\\t");
                    break;
                case '<':
                    escaped.append("\\u003C");
                    break;
                case '>':
                    escaped.append("\\u003E");
                    break;
                case '&':
                    escaped.append("\\u0026");
                    break;
                default:
                    if (current < 0x20) {
                        escaped.append(String.format("\\u%04X", (int) current));
                    } else {
                        escaped.append(current);
                    }
            }
        }
        return escaped.toString();
    }

    /** Renders {@code "name":"escaped value"}. */
    public static String string(String name, String value) {
        return "\"" + escape(name) + "\":\"" + escape(value) + "\"";
    }

    /** Renders {@code "name":<number>} without quotes. */
    public static String number(String name, Number value) {
        return "\"" + escape(name) + "\":" + value;
    }

    /** Joins already rendered members into a JSON object. */
    public static String object(String... members) {
        return "{" + String.join(",", members) + "}";
    }
}
