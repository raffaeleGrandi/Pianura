package io.github.raffaelegrandi.pianura.remote;

import java.util.Locale;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal JSON reader/writer for the remote-control protocol, so that JUSE keeps
 * having no dependencies beyond the JDK.
 *
 * Reading produces: {@code Map<String,Object>} (objects, insertion order kept),
 * {@code List<Object>} (arrays), {@code Double} (all numbers), {@code String},
 * {@code Boolean} and {@code null}.
 * Writing accepts the same types plus any {@code Number}, {@code int[]} and {@code double[]}.
 */
public final class MiniJson {

    private MiniJson() {}

    /** Thrown for malformed input. */
    public static class JsonException extends RuntimeException {
        public JsonException(String msg) { super(msg); }
    }

    // ------------------------------------------------------------------ reading

    public static Object parse(String text) {
        Parser p = new Parser(text);
        p.skipWs();
        Object v = p.value();
        p.skipWs();
        if (p.i != text.length()) throw new JsonException("unexpected trailing characters at " + p.i);
        return v;
    }

    /** Parses a JSON object (the only top-level type used by the protocol). */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> parseObject(String text) {
        Object v = parse(text);
        if (!(v instanceof Map)) throw new JsonException("a JSON object was expected");
        return (Map<String, Object>) v;
    }

    private static final class Parser {
        final String s;
        int i = 0;
        Parser(String s) { this.s = s; }

        void skipWs() { while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++; }

        char peek() {
            if (i >= s.length()) throw new JsonException("unexpected end of input");
            return s.charAt(i);
        }

        void expect(char c) {
            if (peek() != c) throw new JsonException("expected '" + c + "' at " + i);
            i++;
        }

        Object value() {
            skipWs();
            char c = peek();
            switch (c) {
                case '{': return object();
                case '[': return array();
                case '"': return string();
                case 't': literal("true"); return Boolean.TRUE;
                case 'f': literal("false"); return Boolean.FALSE;
                case 'n': literal("null"); return null;
                default:
                    if (c == '-' || (c >= '0' && c <= '9')) return number();
                    throw new JsonException("unexpected character '" + c + "' at " + i);
            }
        }

        void literal(String w) {
            if (!s.startsWith(w, i)) throw new JsonException("invalid literal at " + i);
            i += w.length();
        }

        Map<String, Object> object() {
            expect('{');
            Map<String, Object> m = new LinkedHashMap<>();
            skipWs();
            if (peek() == '}') { i++; return m; }
            while (true) {
                skipWs();
                String k = string();
                skipWs();
                expect(':');
                m.put(k, value());
                skipWs();
                if (peek() == ',') { i++; continue; }
                expect('}');
                return m;
            }
        }

        List<Object> array() {
            expect('[');
            List<Object> a = new ArrayList<>();
            skipWs();
            if (peek() == ']') { i++; return a; }
            while (true) {
                a.add(value());
                skipWs();
                if (peek() == ',') { i++; continue; }
                expect(']');
                return a;
            }
        }

        String string() {
            expect('"');
            StringBuilder b = new StringBuilder();
            while (true) {
                char c = peek();
                i++;
                if (c == '"') return b.toString();
                if (c != '\\') { b.append(c); continue; }
                char e = peek();
                i++;
                switch (e) {
                    case '"': b.append('"'); break;
                    case '\\': b.append('\\'); break;
                    case '/': b.append('/'); break;
                    case 'b': b.append('\b'); break;
                    case 'f': b.append('\f'); break;
                    case 'n': b.append('\n'); break;
                    case 'r': b.append('\r'); break;
                    case 't': b.append('\t'); break;
                    case 'u':
                        if (i + 4 > s.length()) throw new JsonException("bad unicode escape at " + i);
                        b.append((char) Integer.parseInt(s.substring(i, i + 4), 16));
                        i += 4;
                        break;
                    default: throw new JsonException("bad escape at " + i);
                }
            }
        }

        Double number() {
            int start = i;
            if (peek() == '-') i++;
            while (i < s.length() && "0123456789.eE+-".indexOf(s.charAt(i)) >= 0) i++;
            try { return Double.valueOf(s.substring(start, i)); }
            catch (NumberFormatException ex) { throw new JsonException("bad number at " + start); }
        }
    }

    // ------------------------------------------------------------------ writing

    public static String write(Object v) {
        StringBuilder b = new StringBuilder();
        write(b, v);
        return b.toString();
    }

    private static void write(StringBuilder b, Object v) {
        if (v == null) { b.append("null"); return; }
        if (v instanceof String str) { quote(b, str); return; }
        if (v instanceof Boolean) { b.append(v); return; }
        if (v instanceof Number n) { number(b, n.doubleValue(), v instanceof Integer || v instanceof Long); return; }
        if (v instanceof int[] arr) {
            b.append('[');
            for (int k = 0; k < arr.length; k++) { if (k > 0) b.append(','); b.append(arr[k]); }
            b.append(']');
            return;
        }
        if (v instanceof double[] arr) {
            b.append('[');
            for (int k = 0; k < arr.length; k++) { if (k > 0) b.append(','); number(b, arr[k], false); }
            b.append(']');
            return;
        }
        if (v instanceof Map<?, ?> m) {
            b.append('{');
            boolean first = true;
            for (Map.Entry<?, ?> e : m.entrySet()) {
                if (!first) b.append(',');
                first = false;
                quote(b, String.valueOf(e.getKey()));
                b.append(':');
                write(b, e.getValue());
            }
            b.append('}');
            return;
        }
        if (v instanceof Iterable<?> it) {
            b.append('[');
            boolean first = true;
            for (Object o : it) { if (!first) b.append(','); first = false; write(b, o); }
            b.append(']');
            return;
        }
        throw new JsonException("cannot write " + v.getClass().getSimpleName());
    }

    private static void number(StringBuilder b, double d, boolean integral) {
        if (Double.isNaN(d) || Double.isInfinite(d)) { b.append("null"); return; }
        if (integral || (d == Math.rint(d) && Math.abs(d) < 1e15)) b.append((long) d);
        else b.append(d);
    }

    private static void quote(StringBuilder b, String s) {
        b.append('"');
        for (int k = 0; k < s.length(); k++) {
            char c = s.charAt(k);
            switch (c) {
                case '"': b.append("\\\""); break;
                case '\\': b.append("\\\\"); break;
                case '\n': b.append("\\n"); break;
                case '\r': b.append("\\r"); break;
                case '\t': b.append("\\t"); break;
                default:
                    if (c < 0x20) b.append(String.format(Locale.ROOT, "\\u%04x", (int) c));
                    else b.append(c);
            }
        }
        b.append('"');
    }

    // ------------------------------------------------------------------ helpers

    /** Numeric field, or {@code def} if missing / not a number. */
    public static double num(Map<String, Object> m, String key, double def) {
        Object v = m.get(key);
        return v instanceof Number n ? n.doubleValue() : def;
    }

    /** String field, or {@code def} if missing / not a string. */
    public static String str(Map<String, Object> m, String key, String def) {
        Object v = m.get(key);
        return v instanceof String s ? s : def;
    }

}
