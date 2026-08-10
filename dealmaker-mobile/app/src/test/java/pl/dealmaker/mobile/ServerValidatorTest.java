package pl.dealmaker.mobile;

import org.junit.Test;
import static org.junit.Assert.*;

public class ServerValidatorTest {
    @Test public void acceptsLanHttp() {
        assertTrue(ServerValidator.isAllowed("192.168.1.20:8765"));
        assertTrue(ServerValidator.isAllowed("http://10.0.0.2:8765"));
        assertTrue(ServerValidator.isAllowed("http://172.20.0.2:8765"));
    }
    @Test public void blocksPublicCleartext() {
        assertFalse(ServerValidator.isAllowed("http://example.com:8765"));
        assertFalse(ServerValidator.isAllowed("ftp://192.168.1.2"));
    }
    @Test public void acceptsHttps() { assertTrue(ServerValidator.isAllowed("https://example.com")); }
    @Test public void normalizes() { assertEquals("http://192.168.1.2:8765", ServerValidator.normalize("192.168.1.2:8765/")); }
}
