package pl.dealmaker.mobile;

import org.json.JSONObject;

import java.net.ConnectException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

/** Small, user-facing LAN diagnostic used by pairing and settings. */
public final class ConnectionDiagnostics {
    private ConnectionDiagnostics() {}

    public static final class Result {
        public String normalized = "";
        public String host = "";
        public int port = ServerValidator.DEFAULT_PORT;
        public String phoneIps = "";
        public boolean sameSubnet = false;
        public boolean portOpen = false;
        public boolean healthOk = false;
        public String code = "UNKNOWN";
        public String message = "";

        public String summary() {
            StringBuilder b = new StringBuilder();
            b.append("Telefon: ").append(phoneIps.isEmpty() ? "brak lokalnego IPv4" : phoneIps).append('\n');
            b.append("Laptop: ").append(host).append(':').append(port).append('\n');
            b.append("Ta sama podsieć: ").append(sameSubnet ? "TAK" : "NIE / nie mogę potwierdzić").append('\n');
            b.append("Port 8765: ").append(portOpen ? "OTWARTY" : "BRAK ODPOWIEDZI").append('\n');
            b.append("Dealmaker API: ").append(healthOk ? "OK" : "NIEDOSTĘPNE").append("\n\n");
            b.append(message);
            return b.toString();
        }
    }

    public static Result test(String raw) {
        Result r = new Result();
        try {
            r.normalized = ServerValidator.normalize(raw);
            if (!ServerValidator.isAllowed(r.normalized)) {
                r.code = "BAD_ADDRESS";
                r.message = "Adres jest nieprawidłowy. Wpisz np. 192.168.87.138 — port 8765 dopiszę automatycznie.";
                return r;
            }
            URI u = new URI(r.normalized);
            r.host = u.getHost() == null ? "" : u.getHost();
            r.port = u.getPort() > 0 ? u.getPort() : ServerValidator.DEFAULT_PORT;
            List<String> ips = localPrivateIpv4s();
            r.phoneIps = join(ips);
            for (String ip : ips) if (same24(ip, r.host)) { r.sameSubnet = true; break; }

            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(r.host, r.port), 1800);
                r.portOpen = true;
            } catch (SocketTimeoutException e) {
                r.code = "TIMEOUT";
                r.message = "Telefon nie dostaje odpowiedzi z portu 8765. Najczęściej blokuje go Windows Firewall albo router ma izolację urządzeń Wi‑Fi.";
                return r;
            } catch (ConnectException e) {
                r.code = "REFUSED";
                r.message = "Laptop odpowiada, ale port 8765 jest zamknięty. Uruchom START_LAN.bat i sprawdź regułę firewalla.";
                return r;
            } catch (Exception e) {
                r.code = "PORT_ERROR";
                r.message = "Nie mogę otworzyć portu 8765: " + safe(e.getMessage());
                return r;
            }

            try {
                JSONObject h = new ApiClient(r.normalized).get("/api/health");
                if (h.optBoolean("ok")) {
                    r.healthOk = true;
                    r.code = "OK";
                    r.message = "Połączenie działa. Możesz sparować telefon z laptopem.";
                } else {
                    r.code = "BAD_HEALTH";
                    r.message = "Port jest otwarty, ale /api/health nie zwróciło ok=true. Sprawdź, czy pod tym adresem działa Dealmaker OS.";
                }
            } catch (Exception e) {
                r.code = "HTTP_ERROR";
                r.message = "Port jest otwarty, ale API Dealmaker nie odpowiada prawidłowo: " + safe(e.getMessage());
            }
        } catch (Exception e) {
            r.code = "ERROR";
            r.message = "Błąd diagnostyki: " + safe(e.getMessage());
        }
        return r;
    }

    public static List<String> localPrivateIpv4s() {
        ArrayList<String> out = new ArrayList<>();
        try {
            Enumeration<NetworkInterface> nics = NetworkInterface.getNetworkInterfaces();
            while (nics != null && nics.hasMoreElements()) {
                NetworkInterface ni = nics.nextElement();
                if (!ni.isUp() || ni.isLoopback()) continue;
                Enumeration<InetAddress> addrs = ni.getInetAddresses();
                while (addrs.hasMoreElements()) {
                    InetAddress a = addrs.nextElement();
                    String h = a.getHostAddress();
                    if (a instanceof Inet4Address && ServerValidator.isPrivateIpv4(h) && !out.contains(h)) out.add(h);
                }
            }
        } catch (Exception ignored) {}
        out.sort((a,b) -> LocalDiscovery.priority(a) - LocalDiscovery.priority(b));
        return out;
    }

    static boolean same24(String a, String b) {
        if (a == null || b == null) return false;
        String[] aa = a.split("\\.");
        String[] bb = b.split("\\.");
        return aa.length == 4 && bb.length == 4 && aa[0].equals(bb[0]) && aa[1].equals(bb[1]) && aa[2].equals(bb[2]);
    }

    private static String join(List<String> xs) {
        if (xs == null || xs.isEmpty()) return "";
        StringBuilder b = new StringBuilder();
        for (String x : xs) { if (b.length() > 0) b.append(", "); b.append(x); }
        return b.toString();
    }
    private static String safe(String s) { return s == null || s.trim().isEmpty() ? "brak szczegółów" : s.trim(); }
}
