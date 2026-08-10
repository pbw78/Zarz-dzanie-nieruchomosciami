package pl.dealmaker.mobile;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends Activity {
    private static final String PREFS = "dealmaker_mobile";
    private static final String FIREWALL_CMD = "New-NetFirewallRule -DisplayName \"Dealmaker OS 8765\" -Direction Inbound -Protocol TCP -LocalPort 8765 -Action Allow -Profile Any";

    private final ExecutorService io = Executors.newFixedThreadPool(6);
    private final Handler ui = new Handler(Looper.getMainLooper());
    private SharedPreferences prefs;
    private LinearLayout root, content, nav;
    private TextView topStatus;
    private String baseUrl = "";
    private String serverVersion = "";
    private int serverSources = 368;
    private int activeScanId = -1;
    private boolean scanPolling = false;

    private final int bg = Color.rgb(9, 13, 18);
    private final int card = Color.rgb(18, 25, 34);
    private final int card2 = Color.rgb(25, 35, 49);
    private final int text = Color.rgb(246, 248, 251);
    private final int muted = Color.rgb(157, 170, 188);
    private final int accent = Color.rgb(91, 227, 154);
    private final int warn = Color.rgb(247, 198, 90);
    private final int bad = Color.rgb(255, 107, 107);

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        baseUrl = ServerValidator.normalize(prefs.getString("baseUrl", ""));
        buildShell();
        handleShare(getIntent());
        if (baseUrl.isEmpty()) showConnect(); else testAndShowDashboard();
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleShare(intent);
    }

    @Override protected void onDestroy() {
        scanPolling = false;
        io.shutdownNow();
        super.onDestroy();
    }

    private void buildShell() {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(bg);

        topStatus = tv("● OFFLINE", 12, muted);
        topStatus.setPadding(dp(18), dp(10), dp(18), dp(8));
        root.addView(topStatus);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(16), dp(8), dp(16), dp(100));
        scroll.addView(content);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        nav = new LinearLayout(this);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        nav.setPadding(dp(7), dp(7), dp(7), dp(10));
        nav.setBackgroundColor(card);
        addNav("Start", this::showDashboard);
        addNav("Leady", this::showLeads);
        addNav("Skan", this::showScan);
        addNav("+ Dodaj", this::showCapture);
        addNav("Połącz", this::showConnect);
        root.addView(nav, new LinearLayout.LayoutParams(-1, dp(74)));
        setContentView(root);
    }

    private void addNav(String label, Runnable action) {
        Button b = button(label);
        b.setOnClickListener(v -> action.run());
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, -1, 1);
        p.setMargins(dp(2), 0, dp(2), 0);
        nav.addView(b, p);
    }

    private TextView tv(String s, int sp, int color) {
        TextView v = new TextView(this);
        v.setText(s);
        v.setTextSize(sp);
        v.setTextColor(color);
        v.setPadding(dp(10), dp(8), dp(10), dp(8));
        v.setTextIsSelectable(true);
        return v;
    }

    private Button button(String s) {
        Button b = new Button(this);
        b.setText(s);
        b.setTextColor(text);
        b.setTextSize(13);
        b.setAllCaps(false);
        b.setBackgroundResource(R.drawable.bg_button);
        return b;
    }

    private EditText input(String hint) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setHintTextColor(muted);
        e.setTextColor(text);
        e.setTextSize(18);
        e.setSingleLine(true);
        e.setBackgroundColor(card2);
        e.setPadding(dp(16), dp(14), dp(16), dp(14));
        return e;
    }

    private LinearLayout cardBox() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(dp(12), dp(10), dp(12), dp(10));
        l.setBackgroundResource(R.drawable.bg_card);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.setMargins(0, dp(7), 0, dp(7));
        l.setLayoutParams(p);
        return l;
    }

    private void clear() { content.removeAllViews(); }

    private void title(String s, String sub) {
        TextView t = tv(s, 27, text);
        t.setTypeface(null, Typeface.BOLD);
        content.addView(t);
        if (sub != null && !sub.isEmpty()) content.addView(tv(sub, 13, muted));
    }

    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density + .5f); }
    private ApiClient api() { return new ApiClient(baseUrl); }

    private interface JsonOk { void done(JSONObject j); }
    private interface JsonFail { void fail(Exception e); }

    private void async(Callable<JSONObject> task, JsonOk ok) {
        async(task, ok, e -> toast("Błąd: " + shortErr(e)));
    }

    private void async(Callable<JSONObject> task, JsonOk ok, JsonFail fail) {
        io.submit(() -> {
            try {
                JSONObject j = task.call();
                ui.post(() -> ok.done(j));
            } catch (Exception e) {
                ui.post(() -> fail.fail(e));
            }
        });
    }

    private String shortErr(Exception e) {
        String s = e.getMessage();
        return s == null ? e.getClass().getSimpleName() : (s.length() > 180 ? s.substring(0, 180) : s);
    }

    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_LONG).show(); }

    private void markOnline(JSONObject health) {
        serverVersion = health.optString("version", serverVersion);
        serverSources = health.optInt("sources", serverSources);
        topStatus.setText("● ONLINE  " + (serverVersion.isEmpty() ? baseUrl : "Dealmaker " + serverVersion + "  ·  " + serverSources + " źródeł"));
        topStatus.setTextColor(accent);
    }

    private void markOffline() {
        topStatus.setText("● OFFLINE");
        topStatus.setTextColor(bad);
    }

    private void showConnect() {
        scanPolling = false;
        clear();
        title("Połącz z komputerem", "Wpisz tylko IP laptopa. Port 8765 aplikacja doda sama.");

        LinearLayout how = cardBox();
        TextView h = tv("NAJPROŚCIEJ", 12, accent); h.setTypeface(null, Typeface.BOLD); how.addView(h);
        how.addView(tv("1. Na laptopie zostaw uruchomiony START_LAN.bat\n2. Telefon i laptop muszą być w tym samym Wi‑Fi\n3. Kliknij „Znajdź i połącz automatycznie”", 15, text));
        content.addView(how);

        Button auto = button("⚡ Znajdź i połącz automatycznie");
        content.addView(auto);

        TextView divider = tv("— albo wpisz IP ręcznie —", 12, muted);
        divider.setGravity(Gravity.CENTER_HORIZONTAL);
        content.addView(divider);

        EditText server = input("np. 192.168.87.138");
        String display = baseUrl.replace("http://", "").replace("https://", "");
        if (display.endsWith(":" + ServerValidator.DEFAULT_PORT)) display = display.substring(0, display.lastIndexOf(':'));
        server.setText(display);
        content.addView(server);

        Button connect = button("Połącz");
        content.addView(connect);

        LinearLayout diagCard = cardBox();
        TextView diag = tv("Status: czekam na połączenie.", 14, muted);
        diagCard.addView(diag);
        content.addView(diagCard);

        Button copyFirewall = button("Skopiuj komendę naprawy Firewalla Windows");
        copyFirewall.setVisibility(View.GONE);
        content.addView(copyFirewall);
        copyFirewall.setOnClickListener(v -> {
            copyText(FIREWALL_CMD);
            toast("Skopiowano. Wklej w PowerShell uruchomionym jako administrator.");
        });

        Button openHealth = button("Otwórz test /api/health w Chrome");
        openHealth.setVisibility(View.GONE);
        content.addView(openHealth);

        Runnable manualConnect = () -> {
            String candidate = ServerValidator.normalize(server.getText().toString());
            if (!ServerValidator.isAllowed(candidate)) {
                diag.setText("Podaj lokalne IP laptopa, np. 192.168.87.138. Nie wpisuj 0.0.0.0.");
                diag.setTextColor(bad);
                return;
            }
            connectTo(candidate, connect, diag, copyFirewall, openHealth);
        };
        connect.setOnClickListener(v -> manualConnect.run());
        server.setOnEditorActionListener((v, actionId, event) -> { manualConnect.run(); return true; });

        auto.setOnClickListener(v -> {
            auto.setEnabled(false);
            auto.setText("Szukam komputera w Wi‑Fi…");
            diag.setText("Skanuję lokalną sieć na porcie 8765…");
            diag.setTextColor(warn);
            io.submit(() -> {
                try {
                    String found = LocalDiscovery.find();
                    ui.post(() -> {
                        auto.setEnabled(true);
                        auto.setText("⚡ Znajdź i połącz automatycznie");
                        if (found == null) {
                            diag.setText("Nie znalazłem Dealmaker. Najczęściej Windows Firewall blokuje port 8765 albo telefon jest w innym Wi‑Fi.");
                            diag.setTextColor(bad);
                            copyFirewall.setVisibility(View.VISIBLE);
                        } else {
                            server.setText(found.replace("http://", "").replace(":" + ServerValidator.DEFAULT_PORT, ""));
                            connectTo(found, connect, diag, copyFirewall, openHealth);
                        }
                    });
                } catch (Exception e) {
                    ui.post(() -> {
                        auto.setEnabled(true);
                        auto.setText("⚡ Znajdź i połącz automatycznie");
                        diag.setText("Nie udało się przeskanować Wi‑Fi: " + shortErr(e));
                        diag.setTextColor(bad);
                    });
                }
            });
        });
    }

    private void connectTo(String candidate, Button connect, TextView diag, Button copyFirewall, Button openHealth) {
        String normalized = ServerValidator.normalize(candidate);
        connect.setEnabled(false);
        connect.setText("Łączę…");
        diag.setText("Sprawdzam " + normalized + "/api/health");
        diag.setTextColor(warn);
        openHealth.setVisibility(View.VISIBLE);
        openHealth.setOnClickListener(v -> openUrl(normalized + "/api/health"));

        async(() -> new ApiClient(normalized).get("/api/health"), health -> {
            connect.setEnabled(true);
            connect.setText("Połącz");
            if (!health.optBoolean("ok")) {
                diag.setText("Serwer odpowiedział, ale /api/health nie zwróciło ok=true.");
                diag.setTextColor(bad);
                return;
            }
            baseUrl = normalized;
            prefs.edit().putString("baseUrl", baseUrl).apply();
            markOnline(health);
            diag.setText("Połączono z " + baseUrl + " · Dealmaker " + health.optString("version", "") + ".");
            diag.setTextColor(accent);
            copyFirewall.setVisibility(View.GONE);
            flushPendingQueue();
            ui.postDelayed(this::showDashboard, 500);
        }, e -> {
            connect.setEnabled(true);
            connect.setText("Połącz");
            markOffline();
            diag.setText(connectionMessage(e, normalized));
            diag.setTextColor(bad);
            copyFirewall.setVisibility(View.VISIBLE);
        });
    }

    private String connectionMessage(Exception e, String normalized) {
        String msg = shortErr(e).toLowerCase(Locale.ROOT);
        if (e instanceof ConnectException || msg.contains("failed to connect") || msg.contains("connection refused")) {
            return "Telefon widzi adres, ale port 8765 na laptopie nie odpowiada. Zostaw START_LAN.bat uruchomiony i odblokuj port 8765 w Windows Firewall.\n\nTest: " + normalized + "/api/health";
        }
        if (e instanceof SocketTimeoutException || msg.contains("timed out") || msg.contains("timeout")) {
            return "Połączenie z laptopem wygasło. Sprawdź, czy oba urządzenia są w tym samym Wi‑Fi i czy firewall nie blokuje portu 8765.\n\nTest: " + normalized + "/api/health";
        }
        return "Nie udało się połączyć z " + normalized + ".\n" + shortErr(e) + "\n\nNa laptopie powinno być: Uvicorn running on http://0.0.0.0:8765";
    }

    private void testAndShowDashboard() {
        async(() -> api().get("/api/health"), health -> {
            if (health.optBoolean("ok")) {
                markOnline(health);
                flushPendingQueue();
                showDashboard();
            } else {
                markOffline();
                showConnect();
            }
        }, e -> {
            markOffline();
            showConnect();
        });
    }

    private void showDashboard() {
        scanPolling = false;
        clear();
        title("Dealmaker Mobile", "Szybki dostęp do leadów i skanera z telefonu");

        LinearLayout serverCard = cardBox();
        TextView serverInfo = tv("Ładowanie statusu…", 15, text);
        serverCard.addView(serverInfo);
        content.addView(serverCard);

        LinearLayout stats = cardBox();
        TextView statsText = tv("Ładowanie danych…", 17, text);
        stats.addView(statsText);
        content.addView(stats);

        async(() -> api().get("/api/health"), health -> {
            markOnline(health);
            serverInfo.setText("Dealmaker OS " + health.optString("version", "") + "\n" + health.optInt("sources", serverSources) + " profili źródeł\n" + baseUrl);
        }, e -> serverInfo.setText("Brak połączenia z serwerem."));

        async(() -> api().get("/api/analytics"), j -> {
            int total = j.optInt("total");
            JSONObject pipe = j.optJSONObject("pipeline");
            int quality = pipe == null ? j.optInt("quality") : pipe.optInt("quality");
            int contact = pipe == null ? j.optInt("with_contact") : pipe.optInt("with_contact");
            int won = pipe == null ? j.optInt("won") : pipe.optInt("won");
            statsText.setText("LEADY  " + total + "\nWartościowe 70+  " + quality + "\nZ kontaktem  " + contact + "\nWygrane  " + won + "\nKolejka offline  " + PendingQueue.size(prefs));
        }, e -> statsText.setText("Nie udało się pobrać statystyk."));

        Button scan = button("▶ Uruchom pełny skan FAST");
        scan.setOnClickListener(v -> startScan());
        content.addView(scan);

        Button leads = button("🔎 Przejdź do leadów");
        leads.setOnClickListener(v -> showLeads());
        content.addView(leads);

        Button capture = button("＋ Dodaj link / notatkę");
        capture.setOnClickListener(v -> showCapture());
        content.addView(capture);

        Button web = button("Otwórz pełny Dealmaker w Chrome");
        web.setOnClickListener(v -> openUrl(baseUrl));
        content.addView(web);
    }

    private void showLeads() {
        scanPolling = false;
        clear();
        title("Leady", "Wyszukuj po tytule, opisie, lokalizacji i URL");

        EditText q = input("np. magazyn Śląsk / catering / 259201");
        content.addView(q);
        Button search = button("Szukaj");
        content.addView(search);
        TextView count = tv("", 12, muted);
        content.addView(count);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        content.addView(list);

        Runnable go = () -> {
            String query = q.getText().toString().trim();
            String path = query.isEmpty() ? "/api/leads?limit=60" : "/api/search?limit=60&q=" + enc(query);
            search.setEnabled(false);
            search.setText("Szukam…");
            async(() -> api().get(path), j -> {
                search.setEnabled(true);
                search.setText("Szukaj");
                list.removeAllViews();
                JSONArray a = j.optJSONArray("items");
                int n = a == null ? 0 : a.length();
                count.setText("Wyniki: " + n);
                if (n == 0) {
                    list.addView(tv("Brak wyników.", 14, muted));
                    return;
                }
                for (int i = 0; i < n; i++) {
                    JSONObject x = a.optJSONObject(i);
                    if (x != null) list.addView(leadCard(x));
                }
            }, e -> {
                search.setEnabled(true);
                search.setText("Szukaj");
                toast(connectionMessage(e, baseUrl));
            });
        };
        search.setOnClickListener(v -> go.run());
        q.setOnEditorActionListener((v, actionId, event) -> { go.run(); return true; });
        go.run();
    }

    private View leadCard(JSONObject x) {
        LinearLayout c = cardBox();
        String leadTitle = x.optString("title", "Oferta");
        double price = x.optDouble("price", Double.NaN);
        String cur = x.optString("currency", "");
        String loc = x.optString("location", "");
        String src = x.optString("source_name", x.optString("source", ""));
        int score = x.optInt("score", 0);
        String url = x.optString("source_url", x.optString("url", ""));

        TextView t = tv(leadTitle, 17, text);
        t.setTypeface(null, Typeface.BOLD);
        c.addView(t);
        String line = (loc.isEmpty() ? "" : loc + " · ") + (Double.isNaN(price) ? "" : String.format(Locale.ROOT, "%.0f %s · ", price, cur)) + "score " + score + (src.isEmpty() ? "" : " · " + src);
        c.addView(tv(line, 12, score >= 70 ? accent : muted));
        String desc = x.optString("description", "");
        if (desc.length() > 320) desc = desc.substring(0, 320) + "…";
        if (!desc.isEmpty()) c.addView(tv(desc, 13, muted));
        if (!url.isEmpty()) {
            Button o = button("Otwórz źródło");
            o.setOnClickListener(v -> openUrl(url));
            c.addView(o);
        }
        return c;
    }

    private void showScan() {
        clear();
        title("Live Scanner", "Podgląd skanu w czasie rzeczywistym");
        scanPolling = true;

        LinearLayout box = cardBox();
        TextView info = tv("Sprawdzam aktywny skan…", 16, text);
        box.addView(info);
        ProgressBar pb = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        pb.setMax(1000);
        box.addView(pb);
        TextView current = tv("", 13, muted);
        box.addView(current);
        content.addView(box);

        Button start = button("▶ Start pełnego skanu FAST");
        Button stop = button("■ Zatrzymaj skan");
        Button resume = button("↻ Wznów ostatni skan");
        content.addView(start);
        content.addView(stop);
        content.addView(resume);

        start.setOnClickListener(v -> startScan());
        stop.setOnClickListener(v -> {
            if (activeScanId > 0) async(() -> api().post("/api/scans/" + activeScanId + "/stop", new JSONObject()), j -> toast("Zatrzymywanie skanu #" + activeScanId));
            else toast("Brak aktywnego skanu.");
        });
        resume.setOnClickListener(v -> {
            if (activeScanId > 0) async(() -> api().post("/api/scans/" + activeScanId + "/resume", new JSONObject()), j -> {
                activeScanId = j.optInt("scan_id", activeScanId);
                toast("Wznowiono skan #" + activeScanId);
            });
            else toast("Najpierw otwórz/uruchom skan.");
        });
        pollActive(info, pb, current);
    }

    private void pollActive(TextView info, ProgressBar pb, TextView current) {
        if (!scanPolling) return;
        async(() -> api().get("/api/scans/active"), j -> {
            JSONArray a = j.optJSONArray("items");
            if (a != null && a.length() > 0) {
                activeScanId = a.optJSONObject(0).optInt("id", -1);
                pollProgress(info, pb, current);
            } else {
                activeScanId = -1;
                info.setText("Brak aktywnego skanu.");
                current.setText("Możesz uruchomić nowy skan przyciskiem poniżej.");
                pb.setProgress(0);
                ui.postDelayed(() -> pollActive(info, pb, current), 2400);
            }
        }, e -> {
            info.setText("Brak połączenia z Dealmaker.");
            current.setText(shortErr(e));
            ui.postDelayed(() -> pollActive(info, pb, current), 3000);
        });
    }

    private void pollProgress(TextView info, ProgressBar pb, TextView current) {
        if (!scanPolling || activeScanId < 0) return;
        int id = activeScanId;
        async(() -> api().get("/api/scans/" + id + "/progress?event_limit=15"), j -> {
            JSONObject s = j.optJSONObject("scan");
            if (s == null) s = j;
            int done = intField(s, "done_sources", "completed_sources", "processed_sources");
            int total = intField(s, "total_sources", "source_count");
            if (total <= 0) total = serverSources > 0 ? serverSources : 368;
            int found = intField(s, "found", "found_count", "discovered", "candidates");
            int inserted = intField(s, "inserted", "inserted_count", "new_leads", "new_count");
            int errors = intField(s, "failed_sources", "error_count", "errors");
            String st = s.optString("status", "running");
            int perm = total > 0 ? (int) (1000.0 * done / total) : 0;
            pb.setProgress(Math.max(0, Math.min(1000, perm)));
            info.setText("Skan #" + id + " · " + st.toUpperCase(Locale.ROOT) + "\n" + done + " / " + total + " źródeł  ·  " + String.format(Locale.ROOT, "%.1f%%", perm / 10.0) + "\nZnalezione: " + found + "  ·  Nowe: " + inserted + "  ·  Błędy: " + errors);
            current.setText(latestEvent(j));
            if (st.equals("running") || st.equals("stopping")) ui.postDelayed(() -> pollProgress(info, pb, current), 1200);
            else {
                activeScanId = -1;
                ui.postDelayed(() -> pollActive(info, pb, current), 2200);
            }
        }, e -> {
            current.setText("Błąd odświeżania: " + shortErr(e));
            ui.postDelayed(() -> pollActive(info, pb, current), 2500);
        });
    }

    private int intField(JSONObject j, String... keys) {
        for (String k : keys) {
            Object v = j.opt(k);
            if (v instanceof Number) return ((Number) v).intValue();
            if (v instanceof JSONArray) return ((JSONArray) v).length();
            if (v instanceof String) {
                try { return Integer.parseInt((String) v); } catch (Exception ignored) {}
            }
        }
        return 0;
    }

    private String latestEvent(JSONObject j) {
        JSONArray events = j.optJSONArray("events");
        if (events == null || events.length() == 0) return "Skan pracuje…";
        JSONObject e = events.optJSONObject(events.length() - 1);
        if (e == null) return "Skan pracuje…";
        String source = e.optString("source_name", e.optString("source", ""));
        String message = e.optString("message", e.optString("detail", e.optString("event", "")));
        if (!source.isEmpty() && !message.isEmpty()) return "Teraz: " + source + "\n" + message;
        if (!source.isEmpty()) return "Teraz: " + source;
        return message.isEmpty() ? "Skan pracuje…" : message;
    }

    private void startScan() {
        JSONObject b = new JSONObject();
        try {
            b.put("source_ids", new JSONArray());
            b.put("power_profile", "fast");
            b.put("limit_per_source", 20);
            b.put("verify_per_source", 20);
            b.put("only_new", true);
        } catch (Exception ignored) {}
        async(() -> api().post("/api/scans", b), j -> {
            activeScanId = j.optInt("scan_id", j.optInt("id", -1));
            toast("Skan #" + activeScanId + " uruchomiony");
            showScan();
        }, e -> toast("Nie udało się uruchomić skanu: " + shortErr(e)));
    }

    private void showCapture() {
        scanPolling = false;
        clear();
        title("Szybkie dodanie", "Link lub notatka zostanie zachowana nawet bez połączenia");

        TextView queue = tv("Kolejka offline: " + PendingQueue.size(prefs), 13, PendingQueue.size(prefs) > 0 ? warn : muted);
        content.addView(queue);
        EditText url = input("URL (opcjonalnie)");
        EditText ttl = input("Tytuł (opcjonalnie)");
        EditText note = input("Notatka / opis");
        note.setSingleLine(false);
        note.setMinLines(5);
        note.setGravity(Gravity.TOP);
        content.addView(url);
        content.addView(ttl);
        content.addView(note);

        Button add = button("Zapisz w Dealmaker");
        content.addView(add);
        add.setOnClickListener(v -> {
            JSONObject payload = new JSONObject();
            try {
                if (!url.getText().toString().trim().isEmpty()) payload.put("url", url.getText().toString().trim());
                if (!ttl.getText().toString().trim().isEmpty()) payload.put("title", ttl.getText().toString().trim());
                payload.put("note", note.getText().toString().trim());
            } catch (Exception ignored) {}
            add.setEnabled(false);
            submitCapture(payload, ok -> {
                add.setEnabled(true);
                url.setText(""); ttl.setText(""); note.setText("");
                queue.setText("Kolejka offline: " + PendingQueue.size(prefs));
            });
        });

        if (PendingQueue.size(prefs) > 0) {
            Button retry = button("Wyślij kolejkę offline teraz");
            retry.setOnClickListener(v -> flushPendingQueue());
            content.addView(retry);
        }
    }

    private interface BoolDone { void done(boolean ok); }

    private void submitCapture(JSONObject payload, BoolDone done) {
        if (baseUrl.isEmpty()) {
            PendingQueue.enqueue(prefs, payload);
            toast("Brak połączenia — zapisano do kolejki offline.");
            done.done(false);
            return;
        }
        async(() -> api().post("/api/mobile/capture", payload), r -> {
            toast("Dodano do Dealmaker" + (r.has("id") ? " (#" + r.optInt("id") + ")" : ""));
            done.done(true);
        }, e -> {
            PendingQueue.enqueue(prefs, payload);
            toast("Nie udało się wysłać — zapisano do kolejki offline.");
            done.done(false);
        });
    }

    private void flushPendingQueue() {
        if (baseUrl.isEmpty() || PendingQueue.size(prefs) == 0) return;
        io.submit(() -> {
            List<JSONObject> all = PendingQueue.all(prefs);
            ArrayList<JSONObject> failed = new ArrayList<>();
            int sent = 0;
            ApiClient client = new ApiClient(baseUrl);
            for (JSONObject item : all) {
                try { client.post("/api/mobile/capture", item); sent++; }
                catch (Exception e) { failed.add(item); }
            }
            PendingQueue.replace(prefs, failed);
            int finalSent = sent;
            ui.post(() -> {
                if (finalSent > 0) toast("Wysłano z kolejki offline: " + finalSent);
            });
        });
    }

    private void handleShare(Intent intent) {
        if (intent == null || !Intent.ACTION_SEND.equals(intent.getAction()) || !"text/plain".equals(intent.getType())) return;
        String shared = intent.getStringExtra(Intent.EXTRA_TEXT);
        if (shared == null || shared.trim().isEmpty()) return;
        submitCapture(captureFromSharedText(shared), ok -> {});
    }

    private JSONObject captureFromSharedText(String s) {
        String url = firstUrl(s);
        String note = url == null ? s.trim() : s.replace(url, "").trim();
        JSONObject j = new JSONObject();
        try {
            if (url != null) j.put("url", url);
            j.put("title", url != null ? "Link udostępniony z telefonu" : "Notatka udostępniona z telefonu");
            j.put("note", note);
        } catch (Exception ignored) {}
        return j;
    }

    private String firstUrl(String s) {
        Matcher m = Pattern.compile("https?://[^\\s]+", Pattern.CASE_INSENSITIVE).matcher(s);
        return m.find() ? trimTrailingPunctuation(m.group()) : null;
    }

    private String trimTrailingPunctuation(String s) {
        while (s.endsWith(")") || s.endsWith(",") || s.endsWith(".") || s.endsWith(";")) s = s.substring(0, s.length() - 1);
        return s;
    }

    private String enc(String s) {
        try { return URLEncoder.encode(s, "UTF-8"); }
        catch (Exception e) { return s; }
    }

    private void copyText(String s) {
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        cm.setPrimaryClip(ClipData.newPlainText("Dealmaker", s));
    }

    private void openUrl(String u) {
        try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(u))); }
        catch (Exception e) { toast("Nie mogę otworzyć linku."); }
    }
}
