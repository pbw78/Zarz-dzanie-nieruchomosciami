package pl.dealmaker.mobile;

import java.net.URI;
import java.util.Locale;

public final class ServerValidator {
    private ServerValidator() {}

    public static String normalize(String raw) {
        if (raw == null) return "";
        String s = raw.trim();
        if (s.isEmpty()) return "";
        if (!s.contains("://")) s = "http://" + s;
        while (s.endsWith("/")) s = s.substring(0, s.length() - 1);
        return s;
    }

    public static boolean isAllowed(String raw) {
        try {
            String s = normalize(raw);
            URI u = new URI(s);
            String scheme = u.getScheme() == null ? "" : u.getScheme().toLowerCase(Locale.ROOT);
            String host = u.getHost();
            if (host == null || host.trim().isEmpty()) return false;
            if (scheme.equals("https")) return true;
            if (!scheme.equals("http")) return false;
            host = host.toLowerCase(Locale.ROOT);
            if (host.equals("localhost") || host.equals("127.0.0.1") || host.endsWith(".local")) return true;
            return isPrivateIpv4(host);
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean isPrivateIpv4(String host) {
        String[] p = host.split("\\.");
        if (p.length != 4) return false;
        int[] n = new int[4];
        try {
            for (int i = 0; i < 4; i++) {
                n[i] = Integer.parseInt(p[i]);
                if (n[i] < 0 || n[i] > 255) return false;
            }
        } catch (NumberFormatException e) { return false; }
        if (n[0] == 10) return true;
        if (n[0] == 192 && n[1] == 168) return true;
        if (n[0] == 172 && n[1] >= 16 && n[1] <= 31) return true;
        if (n[0] == 169 && n[1] == 254) return true;
        return n[0] == 100 && n[1] >= 64 && n[1] <= 127;
    }
}
