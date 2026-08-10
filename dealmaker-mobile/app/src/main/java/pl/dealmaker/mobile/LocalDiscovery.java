package pl.dealmaker.mobile;

import org.json.JSONObject;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

public final class LocalDiscovery {
    private LocalDiscovery() {}

    public static String find() throws Exception {
        String own = ownPrivateIpv4();
        if (own == null) return null;
        String prefix = own.substring(0, own.lastIndexOf('.') + 1);
        ExecutorService pool = Executors.newFixedThreadPool(28);
        AtomicReference<String> found = new AtomicReference<>();
        List<Future<?>> jobs = new ArrayList<>();
        for (int i = 1; i <= 254; i++) {
            final String host = prefix + i;
            if (host.equals(own)) continue;
            jobs.add(pool.submit(() -> {
                if (found.get() != null) return;
                String base = "http://" + host + ":8765";
                try {
                    HttpURLConnection c = (HttpURLConnection) new URL(base + "/api/health").openConnection();
                    c.setConnectTimeout(220); c.setReadTimeout(450); c.setRequestMethod("GET");
                    if (c.getResponseCode() == 200) {
                        try (Scanner s = new Scanner(c.getInputStream()).useDelimiter("\\A")) {
                            String txt = s.hasNext() ? s.next() : "{}";
                            JSONObject j = new JSONObject(txt);
                            if (j.optBoolean("ok")) found.compareAndSet(null, base);
                        }
                    }
                    c.disconnect();
                } catch (Exception ignored) {}
            }));
        }
        long deadline = System.currentTimeMillis() + 6500;
        while (found.get() == null && System.currentTimeMillis() < deadline) Thread.sleep(80);
        for (Future<?> f : jobs) f.cancel(true);
        pool.shutdownNow();
        return found.get();
    }

    private static String ownPrivateIpv4() throws SocketException {
        Enumeration<NetworkInterface> nics = NetworkInterface.getNetworkInterfaces();
        while (nics.hasMoreElements()) {
            NetworkInterface ni = nics.nextElement();
            if (!ni.isUp() || ni.isLoopback()) continue;
            Enumeration<InetAddress> addrs = ni.getInetAddresses();
            while (addrs.hasMoreElements()) {
                InetAddress a = addrs.nextElement();
                String h = a.getHostAddress();
                if (a instanceof Inet4Address && ServerValidator.isPrivateIpv4(h)) return h;
            }
        }
        return null;
    }
}
