package pl.dealmaker.mobile;

import org.junit.Test;
import static org.junit.Assert.*;

public class ConnectionDiagnosticsTest {
    @Test public void sameSubnet24Works() {
        assertTrue(ConnectionDiagnostics.same24("192.168.87.21", "192.168.87.138"));
        assertFalse(ConnectionDiagnostics.same24("192.168.86.21", "192.168.87.138"));
        assertFalse(ConnectionDiagnostics.same24("10.0.0.2", "192.168.87.138"));
    }
}
