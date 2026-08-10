package pl.dealmaker.mobile;

import org.json.JSONObject;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

public final class LocalDiscovery {
    private LocalDiscovery() {}

    public static String find() throws Exception {
        List<String> ownIps = ownPrivateIpv4s();
        if (ownIps.isEmpty()) return null;

        LinkedHashSet<String> prefixes = new LinkedHashSet<>();
        ownIps.sort((a,b) -> priority(a) - priority(b));
        for (String ip : ownIps) prefixes.add(ip.substring(0, ip.lastIndexOf('.') + 1));

        ExecutorService pool = Executors.newFixedThreadPool(40);
        AtomicReference<String> found = new AtomicReference<>();
        List<Future<?>> jobs = new ArrayList<>();

        for (String prefix : prefixes) {
            for (int i = 1; i <= 254; i++) {
                final String host = prefix + i;
                if (ownIps.contains(host)) continue;
                jobs.add(pool.submit(() -> probe(host, found)));
            }
        }

        long deadline = System.currentTimeMillis() + 7000;
        while (found.get() == null && System.currentTimeMillis() < deadline) Thread.sleep(60);
        for (Future<?> f : jobs) f.cancel(true);
        pool.shutdownNow();
        return found.get();
    }

    private static void probe(String host, AtomicReference<String> found) {
        if (found.get() != null) return;
        String base = "http://" + host + ":" + ServerValidator.DEFAULT_PORT;
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(base + "/api/health").openConnection();
            c.setConnectTimeout(260);
            c.setReadTimeout(550);
            c.setRequestMethod("GET");
            c.setUseCaches(false);
            if (c.getResponseCode() == 200) {
                try (Scanner s = new Scanner(c.getInputStream()).useDelimiter("\\A")) {
                    String txt = s.hasNext() ? s.next() : "{}";
                    JSONObject j = new JSONObject(txt);
                    if (j.optBoolean("ok")) found.compareAndSet(null, base);
                }
            }
        } catch (Exception ignored) {
        } finally {
            if (c != null) c.disconnect();
        }
    }

    static int priority(String ip) {
        if (ip.startsWith("192.168.")) return 0;
        if (ip.startsWith("10.")) return 1;
        if (ip.startsWith("172.")) return 2;
        if (ip.startsWith("169.254.")) return 4;
        return 3;
    }

    private static List<String> ownPrivateIpv4s() throws SocketException {
        ArrayList<String> result = new ArrayList<>();
        Enumeration<NetworkInterface> nics = NetworkInterface.getNetworkInterfaces();
        while (nics.hasMoreElements()) {
            NetworkInterface ni = nics.nextElement();
            if (!ni.isUp() || ni.isLoopback() || ni.isVirtual()) continue;
            Enumeration<InetAddress> addrs = ni.getInetAddresses();
            while (addrs.hasMoreElements()) {
                InetAddress a = addrs.nextElement();
                String h = a.getHostAddress();
                if (a instanceof Inet4Address && ServerValidator.isPrivateIpv4(h)) result.add(h);
            }
        }
        return result;
    }
}
