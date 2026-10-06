package jusePack.remote;

import static org.junit.jupiter.api.Assertions.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class MiniJsonTest {

    @Test
    void parsesNestedObjects() {
        Map<String, Object> m = MiniJson.parseObject(
            "{ \"type\":\"cmd\", \"robot\":3, \"v\":-1.5e0, \"ok\":true, \"x\":null, \"a\":[1, 2.5, \"s\"], \"o\":{\"k\":\"v\"} }");
        assertEquals("cmd", m.get("type"));
        assertEquals(3.0, m.get("robot"));
        assertEquals(-1.5, m.get("v"));
        assertEquals(Boolean.TRUE, m.get("ok"));
        assertNull(m.get("x"));
        assertTrue(m.containsKey("x"));
        assertEquals(List.of(1.0, 2.5, "s"), m.get("a"));
        assertEquals(Map.of("k", "v"), m.get("o"));
    }

    @Test
    void stringEscapesRoundTrip() {
        String s = "quote \" backslash \\ newline \n tab \t unicode é ✓";
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("s", s);
        assertEquals(s, MiniJson.parseObject(MiniJson.write(m)).get("s"));
        assertEquals("é", MiniJson.parseObject("{\"u\":\"\\u00e9\"}").get("u"));
    }

    @Test
    void writesNumbersAndArrays() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("i", 7);
        m.put("d", 0.25);
        m.put("whole", 3.0);
        m.put("ir", new int[] { 255, 12 });
        m.put("nan", Double.NaN);
        assertEquals("{\"i\":7,\"d\":0.25,\"whole\":3,\"ir\":[255,12],\"nan\":null}", MiniJson.write(m));
    }

    @Test
    void rejectsMalformedInput() {
        assertThrows(MiniJson.JsonException.class, () -> MiniJson.parseObject("{\"a\":1"));
        assertThrows(MiniJson.JsonException.class, () -> MiniJson.parseObject("{\"a\":1} extra"));
        assertThrows(MiniJson.JsonException.class, () -> MiniJson.parseObject("[1,2]"));
        assertThrows(MiniJson.JsonException.class, () -> MiniJson.parseObject("{a:1}"));
    }

}
