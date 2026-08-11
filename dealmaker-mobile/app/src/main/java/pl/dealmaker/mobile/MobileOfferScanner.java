package pl.dealmaker.mobile;

import android.content.Context;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lightweight source-isolated scanner designed to run directly on Android.
 * It only stores evidence found on public listing pages and never invents
 * missing fields. A failure in one source does not stop the other sources.
 */
public final class MobileOfferScanner {
    public interface Progress { void onProgress(String message, int found, int pagesDone, int pagesTotal); }
    public static final class Result {
        public int found, saved, pages, errors, sourcesOk, sourcesFailed;
        public final LinkedHashMap<String,String> sourceStatus = new LinkedHashMap<>();
        public String summary() {
            StringBuilder b=new StringBuilder();
            for(Map.Entry<String,String> e:sourceStatus.entrySet()) b.append(e.getKey()).append(": ").append(e.getValue()).append('\n');
            return b.toString().trim();
        }
    }

    private static final String UA="Mozilla/5.0 (Linux; Android 16; Mobile) AppleWebKit/537.36 Chrome/140 Mobile Safari/537.36 DealmakerMobile/3.2";
    private static final Pattern PLN=Pattern.compile("(?<!\\d)(\\d[\\d .]{2,})\\s*(zł|PLN)(?![A-Za-z])",Pattern.CASE_INSENSITIVE);
    private static final Pattern EUR=Pattern.compile("€\\s*([\\d,.]+(?:\\s*[mk])?)|([\\d,.]+(?:\\s*[mk])?)\\s*€",Pattern.CASE_INSENSITIVE);
    private static final Pattern USD=Pattern.compile("\\$\\s*([\\d,.]+(?:\\s*[mk])?)|([\\d,.]+(?:\\s*[mk])?)\\s*\\$",Pattern.CASE_INSENSITIVE);

    private MobileOfferScanner() {}

    public static Result scan(Context context,int depth,Progress progress){
        Result all=new Result();
        int d=Math.max(1,Math.min(depth,5));
        int estimated=10*d + 1 + 1 + d + 1;
        BiznesOfertyScanner.Result bo=BiznesOfertyScanner.scanBiznesOnly(context,d,(m,n,p,t)->{
            if(progress!=null)progress.onProgress(m,all.found+n,p,estimated);
        });
        all.found+=bo.found;all.saved+=bo.saved;all.pages+=bo.pages;all.errors+=bo.errors;
        if(bo.errors<bo.pages){all.sourcesOk++;all.sourceStatus.put("BiznesOferty","OK · "+bo.found+" ofert");}
        else{all.sourcesFailed++;all.sourceStatus.put("BiznesOferty","brak odpowiedzi");}

        scanSprzedamBiznes(context,all,progress,estimated);
        scanBusinessesForSale(context,all,progress,estimated);
        scanBusinessInPoland(context,d,all,progress,estimated);
        scanOlx(context,all,progress,estimated);
        return all;
    }

    private static void scanSprzedamBiznes(Context c,Result r,Progress p,int total){
        final String name="SprzedamBiznes";final String url="https://sprzedambiznes.pl/firmy-na-sprzedaz";int before=r.found;
        try{Document doc=get(url,"https://sprzedambiznes.pl/");LinkedHashMap<String,Element> links=new LinkedHashMap<>();for(Element a:doc.select("a[href*=/firmy-na-sprzedaz/]")){String href=a.absUrl("href");if(href.isEmpty()||href.equals(url)||href.matches(".*/firmy-na-sprzedaz/?$"))continue;Element old=links.get(href);if(old==null||clean(a.text()).length()>clean(old.text()).length())links.put(href,a);}MobileDb db=new MobileDb(c.getApplicationContext());for(Map.Entry<String,Element> e:links.entrySet()){String href=e.getKey();Element a=e.getValue();Element box=container(a,"zł","Przychody:");String txt=clean(box==null?a.text():box.text());String title=headingOrText(a,txt);if(title.length()<6||title.length()>280)continue;Money m=money(txt);String loc=extractLocationPl(txt);db.upsert(href,title,trim(txt,700),loc,m.value,m.currency,"SprzedamBiznes.pl","",0,"source_listing","mobile_scan",0,false);r.found++;r.saved++;}db.close();r.sourcesOk++;r.sourceStatus.put(name,"OK · "+(r.found-before)+" ofert");}catch(Exception ex){r.errors++;r.sourcesFailed++;r.sourceStatus.put(name,"błąd · "+shortErr(ex));}r.pages++;if(p!=null)p.onProgress(name+" · gotowe",r.found,r.pages,total);
    }

    private static void scanBusinessesForSale(Context c,Result r,Progress p,int total){
        final String name="BusinessesForSale";final String url="https://poland.businessesforsale.com/polish/search/businesses-for-sale";int before=r.found;
        try{Document doc=get(url,"https://poland.businessesforsale.com/");MobileDb db=new MobileDb(c.getApplicationContext());Set<String>seen=new HashSet<>();for(Element a:doc.select("h2 a[href], h3 a[href]")){String href=a.absUrl("href");String title=clean(a.text());if(href.isEmpty()||!href.contains("businessesforsale.com/")||title.length()<5||!seen.add(href))continue;Element box=container(a,"Asking Price","Revenue:");String txt=clean(box==null?a.parent().text():box.text());if(txt.toLowerCase(Locale.ROOT).contains("franchise")&&!txt.toLowerCase(Locale.ROOT).contains("business"))continue;Money m=money(txt);String loc=afterLabel(txt,"Location:",80);db.upsert(href,title,trim(txt,700),loc,m.value,m.currency,"BusinessesForSale.com","",0,"source_listing","mobile_scan",0,false);r.found++;r.saved++;}db.close();r.sourcesOk++;r.sourceStatus.put(name,"OK · "+(r.found-before)+" ofert");}catch(Exception ex){r.errors++;r.sourcesFailed++;r.sourceStatus.put(name,"błąd · "+shortErr(ex));}r.pages++;if(p!=null)p.onProgress(name+" · gotowe",r.found,r.pages,total);
    }

    private static void scanBusinessInPoland(Context c,int depth,Result r,Progress p,int total){
        final String name="Business-in-Poland";int before=r.found;Set<String>seen=new HashSet<>();boolean anyOk=false;MobileDb db=new MobileDb(c.getApplicationContext());for(int page=0;page<depth;page++){String url=page==0?"https://www.business-in-poland.eu/en/businesses-for-sale/":"https://www.business-in-poland.eu/en/businesses-for-sale/index/"+(page*12);try{Document doc=get(url,"https://www.business-in-poland.eu/");anyOk=true;for(Element a:doc.select("a[href*=/en/businesses-for-sale/view/]")){String href=a.absUrl("href");if(href.isEmpty()||!seen.add(href))continue;Element box=container(a,"City:","Price:");String txt=clean(box==null?a.parent().text():box.text());String title=headingOrText(a,txt);if(title.equalsIgnoreCase("View")||title.equalsIgnoreCase("View→")||title.length()<5){Element h=box==null?null:box.selectFirst("h1,h2,h3,h4");title=h==null?"":clean(h.text());}if(title.length()<5)continue;Money m=money(txt);String loc=afterLabel(txt,"City:",45);db.upsert(href,title,trim(txt,650),loc,m.value,m.currency,"Business-in-Poland.eu","",0,"source_listing","mobile_scan",0,false);r.found++;r.saved++;}}catch(Exception ex){r.errors++;r.sourceStatus.put(name+" p"+(page+1),"błąd · "+shortErr(ex));}r.pages++;if(p!=null)p.onProgress(name+" · strona "+(page+1),r.found,r.pages,total);}db.close();if(anyOk){r.sourcesOk++;r.sourceStatus.put(name,"OK · "+(r.found-before)+" ofert");}else{r.sourcesFailed++;r.sourceStatus.put(name,"brak odpowiedzi");}
    }

    private static void scanOlx(Context c,Result r,Progress p,int total){
        final String name="OLX Firmy";final String url="https://www.olx.pl/dla-firm/sprzedam-firme/q-firma-na-sprzeda%C5%BC/";int before=r.found;try{Document doc=get(url,"https://www.olx.pl/");MobileDb db=new MobileDb(c.getApplicationContext());Set<String>seen=new HashSet<>();Elements as=doc.select("a[href*=/d/oferta/], a[data-cy=listing-ad-title]");for(Element a:as){String href=a.absUrl("href");if(href.isEmpty()||!seen.add(href))continue;Element box=container(a,"zł","Odświeżono");String txt=clean(box==null?a.parent().text():box.text());String title=headingOrText(a,txt);if(title.length()<5)continue;Money m=money(txt);String loc=extractOlxLocation(txt);db.upsert(href,title,trim(txt,650),loc,m.value,m.currency,"OLX · Firmy na sprzedaż","",0,"source_listing","mobile_scan",0,false);r.found++;r.saved++;}db.close();r.sourcesOk++;r.sourceStatus.put(name,"OK · "+(r.found-before)+" ofert");}catch(Exception ex){r.errors++;r.sourcesFailed++;r.sourceStatus.put(name,"pominięty · "+shortErr(ex));}r.pages++;if(p!=null)p.onProgress(name+" · gotowe",r.found,r.pages,total);
    }

    private static Document get(String url,String ref)throws Exception{return Jsoup.connect(url).userAgent(UA).referrer(ref).timeout(14000).followRedirects(true).maxBodySize(4_000_000).get();}
    private static Element container(Element a,String marker1,String marker2){Element e=a;for(int i=0;i<8&&e!=null;i++,e=e.parent()){String t=clean(e.text());if(t.length()>40&&(t.contains(marker1)||t.contains(marker2)))return e;}return a.parent();}
    private static String headingOrText(Element a,String fallback){Element h=a.selectFirst("h1,h2,h3,h4,strong");String t=h==null?clean(a.text()):clean(h.text());if(t.length()<5)t=clean(a.attr("title"));if(t.length()<5)t=fallback;if(t.length()>180)t=t.substring(0,180);return t;}
    private static String extractLocationPl(String s){String[] voiv={"dolnośląskie","kujawsko-pomorskie","lubelskie","lubuskie","łódzkie","małopolskie","mazowieckie","opolskie","podkarpackie","podlaskie","pomorskie","śląskie","świętokrzyskie","warmińsko-mazurskie","wielkopolskie","zachodniopomorskie"};String l=s.toLowerCase(Locale.ROOT);for(String v:voiv)if(l.contains(v))return v;return "";}
    private static String extractOlxLocation(String s){int i=s.indexOf(" - ");if(i>0&&i<s.length()-3)return trim(s.substring(i+3),60);return "";}
    private static String afterLabel(String s,String label,int max){int i=s.indexOf(label);if(i<0)return "";String x=s.substring(i+label.length()).trim();int stop=x.indexOf("Details:");if(stop>0)x=x.substring(0,stop);stop=x.indexOf("Price:");if(stop>0)x=x.substring(0,stop);return trim(x,max);}
    private static Money money(String s){Matcher p=PLN.matcher(s);if(p.find())return new Money(parseNum(p.group(1)),"PLN");Matcher e=EUR.matcher(s);if(e.find())return new Money(parseScaled(firstNonNull(e.group(1),e.group(2))),"EUR");Matcher u=USD.matcher(s);if(u.find())return new Money(parseScaled(firstNonNull(u.group(1),u.group(2))),"USD");return new Money(null,"");}
    private static Double parseNum(String s){try{return Double.parseDouble(s.replace(" ","").replace(".","").replace(",","."));}catch(Exception e){return null;}}
    private static Double parseScaled(String s){if(s==null)return null;String x=s.trim().toLowerCase(Locale.ROOT);double mul=1;if(x.endsWith("m")){mul=1_000_000;x=x.substring(0,x.length()-1);}else if(x.endsWith("k")){mul=1_000;x=x.substring(0,x.length()-1);}try{return Double.parseDouble(x.replace(",",""))*mul;}catch(Exception e){return null;}}
    private static String firstNonNull(String a,String b){return a!=null?a:b;}
    private static String clean(String s){return s==null?"":s.replace('\u00a0',' ').replaceAll("\\s+"," ").trim();}
    private static String trim(String s,int max){String x=clean(s);return x.length()>max?x.substring(0,max):x;}
    private static String shortErr(Exception e){String s=e.getMessage();if(s==null||s.isEmpty())s=e.getClass().getSimpleName();return trim(s,90);}
    private static final class Money{final Double value;final String currency;Money(Double v,String c){value=v;currency=c;}}
}
