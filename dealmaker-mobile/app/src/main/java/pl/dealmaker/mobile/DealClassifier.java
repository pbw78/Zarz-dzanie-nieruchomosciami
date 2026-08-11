package pl.dealmaker.mobile;

import org.json.JSONObject;
import java.text.Normalizer;
import java.util.Locale;

/** Lightweight deterministic classifier used offline on Android. */
public final class DealClassifier {
    private DealClassifier() {}

    public static String type(JSONObject x) {
        String s = norm(join(x));
        if (has(s,"szukam inwestora","poszukiwany inwestor","investor sought","investment opportunity","partial stake","equity stake","udzialowca","udziałowca")) return "SZUKAM INWESTORA";
        if (has(s,"szukam wspolnika","szukam wspólnika","partner biznesowy","business partner","joint venture","wspolpraca","współpraca")) return "WSPÓŁPRACA";
        if (has(s,"kupie firme","kupię firmę","poszukujemy firmy","acquisition criteria","looking to acquire","kupimy firme","kupimy firmę")) return "KUPIĘ";
        return "SPRZEDAŻ";
    }

    public static String category(JSONObject x) { return category(join(x)); }

    public static String category(String raw) {
        String s = norm(raw);
        if (has(s,"dzialka","działka","grunt","land for sale","deweloper","development site","wz","pnb","mpzp")) return "Grunty / deweloperka";
        if (has(s,"magazyn","hala","warehouse","logistics center","centrum logistyczne","park logistyczny")) return "Magazyny / hale / logistyka";
        if (has(s,"hotel","hostel","pensjonat","resort","apart hotel","aparthotel","turysty")) return "Hotele / turystyka";
        if (has(s,"restaurac","kawiar","pizzeria","kebab","gastronom","catering","bar ")) return "Gastronomia";
        if (has(s,"myjnia","warsztat samochod","serwis samochod","stacja demontazu","stacja kontroli","motoryzac")) return "Motoryzacja / myjnie / warsztaty";
        if (has(s,"produkc","manufactur","fabryk","factory","zaklad","zakład","przemysl","przemysł","metal","stal","steel","automatyka")) return "Produkcja / przemysł";
        if (has(s,"transport","spedyc","logistyk","trucking","fleet","przewoz","przewóz")) return "Transport / spedycja";
        if (has(s,"e-commerce","ecommerce","sklep internet","saas","software","it company","portal internet","aplikacj","marketplace")) return "E-commerce / IT";
        if (has(s,"fotowolta","solar","oze","energia","energety","wind farm","farma wiatrow","biogaz")) return "Energetyka / OZE";
        if (has(s,"rolnict","agricultur","farma","farm ","spożyw","spozyw","food","piekarni","bakery","dairy")) return "Rolnictwo / spożywcze";
        if (has(s,"biuro rachunk","accounting","ubezpiec","insurance","doradzt","consulting","agencja","agency")) return "Usługi B2B";
        if (has(s,"salon urody","fryzjer","beauty","kosmetycz","fitness","siłown","silown","medical","medycz","klinika","apteka","przedszkol","school","szkola","szkoła")) return "Usługi konsumenckie";
        if (has(s,"lokal komerc","nieruchomosc komerc","nieruchomość komerc","office building","biurowiec","retail park","galeria handlow","commercial property")) return "Nieruchomości komercyjne";
        if (has(s,"sklep","retail","handel detal","hurtown","wholesale","dystrybuc")) return "Handel / dystrybucja";
        return "Inne biznesy";
    }

    public static int quickScore(JSONObject x) {
        int score = 25;
        String title=x.optString("title",""); String desc=x.optString("description","");
        if(!x.optString("source_url","").isEmpty())score+=15;
        if(x.has("price"))score+=12;
        if(!x.optString("location","").isEmpty())score+=8;
        if(!x.optString("published_at","").isEmpty())score+=8;
        if(title.length()>=12)score+=8;
        if(desc.length()>=120)score+=12;
        if(desc.length()>=350)score+=7;
        return Math.min(100,score);
    }

    private static String join(JSONObject x){return x.optString("title","")+" "+x.optString("description","")+" "+x.optString("source_name","")+" "+x.optString("location","");}
    private static boolean has(String s,String... needles){for(String n:needles)if(s.contains(norm(n)))return true;return false;}
    private static String norm(String s){String n=Normalizer.normalize(s==null?"":s,Normalizer.Form.NFD).replaceAll("\\p{M}+","");return n.toLowerCase(Locale.ROOT);}
}
