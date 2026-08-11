package pl.dealmaker.mobile;

import org.json.JSONObject;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

public final class LocalDiscovery {
    private LocalDiscovery() {}

    public static String find() throws Exception { return find(null); }

    /** Tries the typed/saved address first, then scans every private /24 visible on the phone. */
    public static String find(String preferredRaw) throws Exception {
        String preferred = ServerValidator.normalize(preferredRaw);
        if (ServerValidator.isAllowed(preferred) && probeBase(preferred, 700, 900)) return preferred;

        List<String> ownIps = ConnectionDiagnostics.localPrivateIpv4s();
        if (ownIps.isEmpty()) return null;
        LinkedHashSet<String> prefixes = new LinkedHashSet<>();
        for (String ip : ownIps) {
            int dot = ip.lastIndexOf('.');
            if (dot > 0) prefixes.add(ip.substring(0, dot + 1));
        }

        ExecutorService pool = Executors.newFixedThreadPool(56);
        AtomicReference<String> found = new AtomicReference<>();
        List<Future<?>> jobs = new ArrayList<>();
        for (String prefix : prefixes) {
            for (int i = 1; i <= 254; i++) {
                final String host = prefix + i;
                if (ownIps.contains(host)) continue;
                jobs.add(pool.submit(() -> {
                    if (found.get() != null) return;
                    String base = "http://" + host + ":" + ServerValidator.DEFAULT_PORT;
                    if (probeBase(base, 300, 600)) found.compareAndSet(null, base);
                }));
            }
        }

        long deadline = System.currentTimeMillis() + 6500;
        while (found.get() == null && System.currentTimeMillis() < deadline) Thread.sleep(50);
        for (Future<?> f : jobs) f.cancel(true);
        pool.shutdownNow();
        return found.get();
    }

    static boolean probeBase(String base, int connectMs, int readMs) {
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(base + "/api/health").openConnection();
            c.setConnectTimeout(connectMs);
            c.setReadTimeout(readMs);
            c.setRequestMethod("GET");
            c.setUseCaches(false);
            if (c.getResponseCode() != 200) return false;
            try (Scanner s = new Scanner(c.getInputStream()).useDelimiter("\\A")) {
                String txt = s.hasNext() ? s.next() : "{}";
                return new JSONObject(txt).optBoolean("ok");
            }
        } catch (Exception ignored) {
            return false;
        } finally {
            if (c != null) c.disconnect();
        }
    }

    static int priority(String ip) {
        if (ip == null) return 9;
        if (ip.startsWith("192.168.")) return 0;
        if (ip.startsWith("10.")) return 1;
        if (ip.startsWith("172.")) return 2;
        if (ip.startsWith("100.")) return 3;
        if (ip.startsWith("169.254.")) return 5;
        return 4;
    }
}
