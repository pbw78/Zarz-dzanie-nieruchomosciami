package pl.dealmaker.mobile;

import android.content.Context;
import org.jsoup.Jsoup;
import org.jsoup.nodes.*;
import org.jsoup.select.Elements;
import java.util.*;
import java.util.regex.*;

public final class BiznesOfertyScanner {
    public interface Progress { void onProgress(String message, int found, int pagesDone, int pagesTotal); }
    public static final class Result { public int found, saved, pages, errors; public String lastError=""; }

    private static final String BASE = "https://www.biznesoferty.pl";
    private static final String[] CATEGORIES = {
            "gastronomia", "nieruchomosci", "budownictwo", "przemysl-automatyzacja",
            "transport-komunikacja", "motoryzacja", "rolnictwo-zywnosc", "turystyka",
            "komputery-internet", "ekologia-srodowisko"
    };
    private static final Pattern DATE = Pattern.compile("\\b(\\d{2}\\.\\d{2}\\.\\d{4})\\b");
    private static final Pattern PRICE = Pattern.compile("(?<!\\d)(\\d[\\d .]{2,})\\s*(PLN|EUR|USD|DKK)(?![A-Z])", Pattern.CASE_INSENSITIVE);

    private BiznesOfertyScanner() {}

    public static Result scan(Context context, int pagesPerCategory, Progress progress) {
        Result r = new Result();
        MobileDb db = new MobileDb(context.getApplicationContext());
        int pages = Math.max(1, Math.min(pagesPerCategory, 5));
        int total = CATEGORIES.length * pages;
        Set<String> seen = new HashSet<>();
        int done = 0;
        for (String category : CATEGORIES) {
            for (int page = 1; page <= pages; page++) {
                String url = categoryUrl(category, page);
                if (progress != null) progress.onProgress("BiznesOferty · " + category + " · strona " + page, r.found, done, total);
                try {
                    Document doc = Jsoup.connect(url)
                            .userAgent("Mozilla/5.0 (Linux; Android 16) AppleWebKit/537.36 Chrome/140 Mobile Safari/537.36 DealmakerMobile/3.0")
                            .referrer(BASE + "/sprzedam-biznes/")
                            .timeout(12000).followRedirects(true).maxBodySize(3_000_000).get();
                    int before = r.found;
                    Elements links = offerLinks(doc);
                    for (Element a : links) {
                        String href = a.absUrl("href");
                        if (href.isEmpty() || !href.contains("biznesoferty.pl/o/")) continue;
                        String title = titleFor(a);
                        if (title.length() < 5 || title.equalsIgnoreCase("szczegóły oferty »")) continue;
                        if (!seen.add(href)) continue;
                        Element box = bestContainer(a);
                        String all = clean(box == null ? a.parent().text() : box.text());
                        String date = first(DATE, all, 1);
                        Matcher pm = PRICE.matcher(all);
                        Double price = null; String currency = "";
                        if (pm.find()) {
                            try { price = Double.parseDouble(pm.group(1).replace(" ","").replace(".","")); } catch(Exception ignored) {}
                            currency = pm.group(2).toUpperCase(Locale.ROOT);
                        }
                        String description = descriptionFrom(box, title, all);
                        String location = findLocation(box);
                        db.upsert(href, title, description, location, price, currency, "BiznesOferty.pl", date, 0,
                                "source_listing", "mobile_scan", 0, false);
                        r.found++; r.saved++;
                    }
                    if (r.found == before && links.isEmpty()) r.errors++;
                } catch (Exception e) {
                    r.errors++; r.lastError = e.getClass().getSimpleName()+": "+String.valueOf(e.getMessage());
                }
                done++; r.pages = done;
                if (progress != null) progress.onProgress("Zapisano lokalnie: " + r.saved, r.found, done, total);
            }
        }
        db.close();
        return r;
    }

    static Elements offerLinks(Document doc) { return doc.select("a[href*=\"/o/\"][href$=\".html\"]"); }
    static String titleFor(Element a) {
        String title = clean(a.text());
        if (title.length() < 5) title = clean(a.select("img[alt]").attr("alt"));
        return title;
    }
    static String categoryUrl(String category, int page) { return BASE + "/sprzedam-biznes/" + category + (page <= 1 ? "/" : "," + page + "/"); }

    private static Element bestContainer(Element a) {
        Element e = a;
        for (int i=0; i<7 && e!=null; i++, e=e.parent()) {
            String t = clean(e.text());
            if (DATE.matcher(t).find() && (t.contains("PLN") || t.contains("EUR") || t.length() > 120)) return e;
        }
        return a.parent();
    }

    private static String descriptionFrom(Element box, String title, String all) {
        if (box != null) {
            for (Element e : box.select("p, div, span")) {
                String t = clean(e.text());
                if (t.length() >= 55 && !t.equals(title) && !t.contains("Wyświetlone oferty")) {
                    if (t.length() > 550) t=t.substring(0,550);
                    return t;
                }
            }
        }
        String t = all.replace(title, "").trim();
        if (t.length() > 550) t=t.substring(0,550);
        return t;
    }

    private static String findLocation(Element box) {
        if (box == null) return "";
        for (Element a : box.select("a")) {
            String t = clean(a.text()).toLowerCase(Locale.ROOT);
            if (t.matches("(dolnośląskie|kujawsko-pomorskie|kujawsko-pom\\.|lubelskie|lubuskie|łódzkie|małopolskie|mazowieckie|opolskie|podkarpackie|podlaskie|pomorskie|śląskie|świętokrzyskie|warmińsko-mazurskie|wielkopolskie|zachodniopomorskie)")) return a.text();
        }
        return "";
    }

    private static String first(Pattern p, String s, int group) { Matcher m=p.matcher(s); return m.find()?m.group(group):""; }
    private static String clean(String s) { return s==null?"":s.replace('\u00a0',' ').replaceAll("\\s+"," ").trim(); }
}
