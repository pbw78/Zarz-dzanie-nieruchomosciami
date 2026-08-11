package pl.dealmaker.mobile;

import android.content.Context;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Everyday Android scanner. It scans public listing pages without requiring the PC.
 * Target is a goal, not a fabricated result count: the scan stops when the target
 * is reached or when the configured public profiles are exhausted.
 */
public final class DailyScanner {
    public interface Progress { void onProgress(String message,int found,int profile,int totalProfiles); }
    public static final class Result {
        public int target, found, saved, errors, profilesDone, profilesOk, profilesFailed;
        public final LinkedHashMap<String,String> status=new LinkedHashMap<>();
        public String summary(){StringBuilder b=new StringBuilder();for(Map.Entry<String,String>e:status.entrySet())b.append(e.getKey()).append(": ").append(e.getValue()).append('\n');return b.toString().trim();}
    }

    private static final String UA="Mozilla/5.0 (Linux; Android 16; Mobile) AppleWebKit/537.36 Chrome/140 Mobile Safari/537.36 DealmakerMobile/3.2";
    private static final Pattern MONEY=Pattern.compile("(?<!\\d)([0-9][0-9 .,'’]{2,})(?:\\s*)(PLN|zł|EUR|€|USD|\\$|DKK)(?![A-Za-z])",Pattern.CASE_INSENSITIVE);
    private static final Pattern DATE=Pattern.compile("\\b(20\\d{2}[-./]\\d{1,2}[-./]\\d{1,2}|\\d{1,2}[./]\\d{1,2}[./]20\\d{2})\\b");

    private DailyScanner(){}

    public static int logicalProfiles(){ return 68; }
    public static String sourceSummary(){return "BiznesOferty · SprzedamBiznes · SprzedamFirme · OLX · SMERGERS · BusinessesForSale · Business-in-Poland · DealStream · Sprzedam.pl · Morizon · Gratka · Nieruchomosci-Online · Nieruchomosci-na-sprzedaz";}

    public static Result scan(Context context,int requested,Progress progress){
        Result out=new Result();out.target=normalizeTarget(requested);
        int depth=out.target<=50?1:(out.target<=200?3:5);
        MobileOfferScanner.Result base=MobileOfferScanner.scan(context,Math.min(depth,3),(m,n,p,t)->{
            if(progress!=null)progress.onProgress(m,n,p,logicalProfiles());
        });
        out.found+=base.found;out.saved+=base.saved;out.errors+=base.errors;out.profilesDone+=Math.max(1,base.pages);out.profilesOk+=base.sourcesOk;out.profilesFailed+=base.sourcesFailed;out.status.putAll(base.sourceStatus);
        if(out.found>=out.target)return out;

        // Deepen BiznesOferty for 200/500 mode (v3.1 base scans at most 3 pages/category).
        if(depth>3 && out.found<out.target){
            BiznesOfertyScanner.Result b=BiznesOfertyScanner.scanBiznesOnly(context,depth,(m,n,p,t)->notify(progress,out,m));
            // Do not add b.found wholesale because pages 1..3 overlap the base scan.
            out.saved+=b.saved;out.errors+=b.errors;out.profilesDone+=b.pages;
            out.found=Math.max(out.found,b.found+Math.max(0,base.found-120));
            out.status.put("BiznesOferty deep","OK · do "+depth+" stron/kategorię");
        }

        int pageBudget=out.target<=50?2:(out.target<=200?10:28);
        scanSprzedamBiznesPages(context,out,pageBudget,progress);
        scanSmergers(context,out,out.target<=50?2:(out.target<=200?10:18),progress);
        scanSimple(context,out,"SprzedamFirme.com","https://www.sprzedamfirme.com/","sprzedamfirme.com",new String[]{"/ogloszenie","/oferta","/listing"},progress);
        scanSimple(context,out,"Sprzedam.pl","https://sprzedam.pl/sprzedam_firme","sprzedam.pl",new String[]{"/ogloszenie","/oferta","sprzedam_firme"},progress);
        scanSimple(context,out,"DealStream Poland","https://dealstream.com/poland/businesses-for-sale","dealstream.com",new String[]{"/poland/","/businesses-for-sale/"},progress);
        scanSimple(context,out,"DealStream Online","https://dealstream.com/poland/online-businesses-for-sale","dealstream.com",new String[]{"/poland/","/businesses-for-sale/"},progress);
        scanCommercial(context,out,"Morizon komercyjne","https://www.morizon.pl/komercyjne/","morizon.pl",progress);
        scanCommercial(context,out,"Gratka lokale","https://gratka.pl/nieruchomosci/lokale-uzytkowe","gratka.pl",progress);
        scanCommercial(context,out,"Nieruchomosci-Online","https://www.nieruchomosci-online.pl/lokale-uzytkowe,sprzedaz/","nieruchomosci-online.pl",progress);
        scanCommercial(context,out,"Nieruchomosci-na-sprzedaz","https://nieruchomosci-na-sprzedaz.pl/oferty/sprzedaz/komercyjne","nieruchomosci-na-sprzedaz.pl",progress);

        if(out.found<out.target)scanOlxQueries(context,out,progress);
        return out;
    }

    private static void scanSprzedamBiznesPages(Context c,Result r,int pages,Progress p){
        MobileDb db=new MobileDb(c.getApplicationContext());int before=r.found;boolean ok=false;
        try{
            for(int page=1;page<=pages&&r.found<r.target;page++){
                String url="https://sprzedambiznes.pl/firmy-na-sprzedaz"+(page<=1?"":"?strona="+page);
                try{Document d=get(url,"https://sprzedambiznes.pl/");int n=parseGeneric(d,db,"SprzedamBiznes.pl","sprzedambiznes.pl",new String[]{"/firmy-na-sprzedaz/"},"business_sale");r.found+=n;r.saved+=n;ok=true;}catch(Exception e){r.errors++;}
                r.profilesDone++;notify(p,r,"SprzedamBiznes · strona "+page);
            }
        }finally{db.close();}
        mark(r,"SprzedamBiznes strony",ok,before);
    }

    private static void scanSmergers(Context c,Result r,int pages,Progress p){
        MobileDb db=new MobileDb(c.getApplicationContext());int before=r.found;boolean ok=false;
        try{
            for(int page=1;page<=pages&&r.found<r.target;page++){
                String url="https://www.smergers.com/businesses-for-sale-and-investment-in-poland/c301b/"+(page<=1?"":"?page="+page);
                try{Document d=get(url,"https://www.smergers.com/");int n=parseGeneric(d,db,"SMERGERS","smergers.com",new String[]{"/business/","/businesses/","/opportunity/"},"business_sale");if(n==0)n=parseHeadingsWithLinks(d,db,"SMERGERS","smergers.com");r.found+=n;r.saved+=n;ok=true;}catch(Exception e){r.errors++;}
                r.profilesDone++;notify(p,r,"SMERGERS · strona "+page);
            }
        }finally{db.close();}
        mark(r,"SMERGERS Poland",ok,before);
    }

    private static void scanOlxQueries(Context c,Result r,Progress p){
        String[][] qs={
                {"OLX · firma na sprzedaż","https://www.olx.pl/dla-firm/sprzedam-firme/q-firma-na-sprzeda%C5%BC/"},
                {"OLX · gotowy biznes","https://www.olx.pl/dla-firm/sprzedam-firme/q-gotowy-biznes/"},
                {"OLX · sprzedam biznes","https://www.olx.pl/dla-firm/sprzedam-firme/q-sprzedam-biznes/"},
                {"OLX · sprzedam firmę","https://www.olx.pl/dla-firm/sprzedam-firme/q-sprzedam-firme/"},
                {"OLX · gastronomia","https://www.olx.pl/dla-firm/sprzedam-firme/q-gastronomia/"},
                {"OLX · salon","https://www.olx.pl/dla-firm/sprzedam-firme/q-salon/"},
                {"OLX · warsztat","https://www.olx.pl/dla-firm/sprzedam-firme/q-warsztat/"},
                {"OLX · sklep","https://www.olx.pl/dla-firm/sprzedam-firme/q-sklep/"}
        };
        MobileDb db=new MobileDb(c.getApplicationContext());
        try{for(String[]q:qs){if(r.found>=r.target)break;int before=r.found;boolean ok=false;try{Document d=get(q[1],"https://www.olx.pl/");int n=parseGeneric(d,db,"OLX · Firmy na sprzedaż","olx.pl",new String[]{"/d/oferta/"},"business_sale");r.found+=n;r.saved+=n;ok=true;}catch(Exception e){r.errors++;}r.profilesDone++;mark(r,q[0],ok,before);notify(p,r,q[0]);}}finally{db.close();}
    }

    private static void scanSimple(Context c,Result r,String name,String url,String domain,String[] includes,Progress p){
        if(r.found>=r.target)return;MobileDb db=new MobileDb(c.getApplicationContext());int before=r.found;boolean ok=false;
        try{Document d=get(url,"https://"+domain+"/");int n=parseGeneric(d,db,name,domain,includes,"business_sale");r.found+=n;r.saved+=n;ok=true;}catch(Exception e){r.errors++;}finally{db.close();}
        r.profilesDone++;mark(r,name,ok,before);notify(p,r,name);
    }

    private static void scanCommercial(Context c,Result r,String name,String url,String domain,Progress p){
        if(r.found>=r.target)return;MobileDb db=new MobileDb(c.getApplicationContext());int before=r.found;boolean ok=false;
        try{Document d=get(url,"https://"+domain+"/");int n=parseCommercial(d,db,name,domain);r.found+=n;r.saved+=n;ok=true;}catch(Exception e){r.errors++;}finally{db.close();}
        r.profilesDone++;mark(r,name,ok,before);notify(p,r,name);
    }

    static int parseGeneric(Document doc,MobileDb db,String source,String domain,String[] includes,String defaultType){
        LinkedHashMap<String,Element> uniq=new LinkedHashMap<>();
        for(Element a:doc.select("a[href]")){
            String href=a.absUrl("href");if(href.isEmpty()||!href.contains(domain))continue;
            boolean match=includes==null||includes.length==0; if(!match)for(String s:includes)if(href.contains(s)){match=true;break;} if(!match)continue;
            String txt=clean(a.text());if(txt.length()<4){Element h=a.selectFirst("h1,h2,h3,h4,strong,[title]");if(h!=null)txt=clean(h.text().isEmpty()?h.attr("title"):h.text());}
            if(txt.length()<4)continue;Element old=uniq.get(href);if(old==null||clean(a.text()).length()>clean(old.text()).length())uniq.put(href,a);
        }
        int n=0;for(Map.Entry<String,Element>e:uniq.entrySet()){
            Element a=e.getValue();Element box=container(a);String all=clean(box==null?a.parent().text():box.text());String title=clean(a.text());
            if(title.length()<5||title.length()>240){Element h=box==null?null:box.selectFirst("h1,h2,h3,h4,strong");title=h==null?"":clean(h.text());}
            if(title.length()<5||looksNavigation(title))continue;
            Money m=money(all);String date=date(all);String loc=location(all);JSONObjectLike z=new JSONObjectLike(title,all,source,loc);
            db.upsert(e.getKey(),title,trim(all,900),loc,m.value,m.currency,source,date,DealClassifier.quickScore(z.json()),"source_listing","mobile_scan",0,false);n++;
        }return n;
    }

    static int parseCommercial(Document d,MobileDb db,String source,String domain){
        LinkedHashMap<String,Element> uniq=new LinkedHashMap<>();
        for(Element a:d.select("a[href]")){String h=a.absUrl("href");String t=clean(a.text());if(h.isEmpty()||!h.contains(domain)||t.length()<5)continue;String low=t.toLowerCase(Locale.ROOT);if(low.contains("następna")||low.contains("nastepna")||low.contains("zobacz wszystkie")||low.contains("mapa"))continue;Element parent=container(a);String all=clean(parent==null?a.parent().text():parent.text());if(!(all.contains("zł")||all.contains("PLN")||all.contains("m²")||all.contains("m2")))continue;Element old=uniq.get(h);if(old==null||t.length()>clean(old.text()).length())uniq.put(h,a);}
        int n=0;for(Map.Entry<String,Element>e:uniq.entrySet()){String title=clean(e.getValue().text());if(title.length()>220)title=title.substring(0,220);Element b=container(e.getValue());String all=clean(b==null?e.getValue().parent().text():b.text());Money m=money(all);String loc=location(all);JSONObjectLike z=new JSONObjectLike(title,all,source,loc);db.upsert(e.getKey(),title,trim(all,900),loc,m.value,m.currency,source,date(all),DealClassifier.quickScore(z.json()),"source_listing","mobile_scan",0,false);n++;}return n;
    }

    static int parseHeadingsWithLinks(Document d,MobileDb db,String source,String domain){int n=0;Set<String>seen=new HashSet<>();for(Element h:d.select("h1,h2,h3,h4")){Element a=h.selectFirst("a[href]");if(a==null&&h.parent()!=null)a=h.parent().selectFirst("a[href]");if(a==null)continue;String url=a.absUrl("href"),title=clean(h.text());if(url.isEmpty()||!url.contains(domain)||title.length()<6||!seen.add(url))continue;Element box=container(h);String all=clean(box==null?h.parent().text():box.text());Money m=money(all);String loc=location(all);JSONObjectLike z=new JSONObjectLike(title,all,source,loc);db.upsert(url,title,trim(all,900),loc,m.value,m.currency,source,date(all),DealClassifier.quickScore(z.json()),"source_listing","mobile_scan",0,false);n++;}return n;}

    private static Element container(Element a){Element e=a;for(int i=0;i<7&&e!=null;i++,e=e.parent()){String t=clean(e.text());if(t.length()>=70&&(t.contains("zł")||t.contains("PLN")||t.contains("EUR")||t.contains("USD")||t.contains("€")||t.contains("Revenue")||t.contains("Price")||t.contains("Cena")))return e;}return a.parent();}
    private static Document get(String url,String ref)throws Exception{return Jsoup.connect(url).userAgent(UA).referrer(ref).timeout(14000).followRedirects(true).maxBodySize(4_000_000).get();}
    private static void mark(Result r,String name,boolean ok,int before){if(ok){r.profilesOk++;r.status.put(name,"OK · "+Math.max(0,r.found-before)+" rekordów");}else{r.profilesFailed++;r.status.put(name,"brak odpowiedzi / pominięty");}}
    private static void notify(Progress p,Result r,String m){if(p!=null)p.onProgress(m,r.found,r.profilesDone,logicalProfiles());}
    private static int normalizeTarget(int n){if(n<=50)return 50;if(n<=200)return 200;return 500;}
    private static boolean looksNavigation(String t){String x=t.toLowerCase(Locale.ROOT);return x.equals("zobacz opis")||x.equals("wyświetl")||x.equals("wyswietl")||x.equals("szczegóły")||x.equals("szczegoly")||x.equals("contact business")||x.equals("view")||x.equals("więcej")||x.equals("wiecej");}
    private static String clean(String s){return s==null?"":s.replace('\u00a0',' ').replaceAll("\\s+"," ").trim();}
    private static String trim(String s,int n){String x=clean(s);return x.length()>n?x.substring(0,n):x;}
    private static String date(String s){Matcher m=DATE.matcher(s);return m.find()?m.group(1):"";}
    private static String location(String s){String[]v={"Warszawa","Kraków","Krakow","Wrocław","Wroclaw","Poznań","Poznan","Gdańsk","Gdansk","Gdynia","Katowice","Łódź","Lodz","Szczecin","Lublin","Rzeszów","Rzeszow","Białystok","Bialystok","Bydgoszcz","Częstochowa","Czestochowa","Gliwice","Opole","Poland","Polska","mazowieckie","śląskie","slaskie","małopolskie","malopolskie","dolnośląskie","dolnoslaskie","wielkopolskie","pomorskie"};String l=s.toLowerCase(Locale.ROOT);for(String x:v)if(l.contains(x.toLowerCase(Locale.ROOT)))return x;return "";}
    private static Money money(String s){Matcher m=MONEY.matcher(s);if(!m.find())return new Money(null,"");String raw=m.group(1);String cur=m.group(2).toUpperCase(Locale.ROOT).replace("ZŁ","PLN").replace("€","EUR").replace("$","USD");try{String x=raw.replace(" ","").replace("'","").replace("’","");if(x.contains(",")&&!x.contains("."))x=x.replace(",",".");else{x=x.replace(",","");}return new Money(Double.parseDouble(x),cur);}catch(Exception e){return new Money(null,cur);}}
    private static final class Money{final Double value;final String currency;Money(Double v,String c){value=v;currency=c;}}
    private static final class JSONObjectLike{private final org.json.JSONObject j=new org.json.JSONObject();JSONObjectLike(String t,String d,String s,String l){try{j.put("title",t);j.put("description",d);j.put("source_name",s);j.put("location",l);}catch(Exception ignored){}}org.json.JSONObject json(){return j;}}
}
