package server;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JsonSupportTest {
    @Test
    void errorJson_shouldEscapeAllJsonControlCharacters() throws Exception {
        var value = "quote=\" slash=\\ tab=\t line=\n control=" + (char) 0x01;
        var json = JsonSupport.errorJson(value);

        assertEquals(value, new ObjectMapper().readTree(json).get("error").textValue());
    }
}
