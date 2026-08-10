package pl.dealmaker.mobile;

import android.app.Activity;
import android.content.*;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.*;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import org.json.*;
import java.net.URLEncoder;
import java.text.DateFormat;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.*;

public class MainActivity extends Activity {
    private static final String PREFS="dealmaker_mobile";
    private final ExecutorService io=Executors.newFixedThreadPool(4);
    private final Handler ui=new Handler(Looper.getMainLooper());
    private SharedPreferences prefs;
    private MobileDb db;
    private LinearLayout root,content,nav;
    private TextView status;
    private String baseUrl="";
    private boolean remoteOnline=false, scanPolling=false;
    private int activeScanId=-1;
    private final int bg=Color.rgb(10,14,19), card=Color.rgb(20,27,36), card2=Color.rgb(28,37,50);
    private final int text=Color.rgb(245,247,250), muted=Color.rgb(155,167,183), accent=Color.rgb(91,227,154), bad=Color.rgb(255,107,107);

    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        prefs=getSharedPreferences(PREFS,MODE_PRIVATE); db=new MobileDb(this); baseUrl=prefs.getString("baseUrl","");
        buildShell(); handleShare(getIntent()); MobileJobs.schedule(this);
        if(baseUrl.isEmpty()) showPair(); else { showDashboard(); syncInBackground(false); }
    }
    @Override protected void onNewIntent(Intent i){super.onNewIntent(i);setIntent(i);handleShare(i);}
    @Override protected void onResume(){super.onResume();if(!baseUrl.isEmpty())syncInBackground(false);}
    @Override protected void onDestroy(){scanPolling=false;io.shutdownNow();db.close();super.onDestroy();}

    private void buildShell(){
        root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(bg);
        status=tv("LOCAL",12,accent);status.setPadding(dp(16),dp(8),dp(16),dp(8));root.addView(status);
        ScrollView s=new ScrollView(this);content=new LinearLayout(this);content.setOrientation(LinearLayout.VERTICAL);content.setPadding(dp(14),dp(6),dp(14),dp(100));s.addView(content);root.addView(s,new LinearLayout.LayoutParams(-1,0,1));
        nav=new LinearLayout(this);nav.setOrientation(LinearLayout.HORIZONTAL);nav.setBackgroundColor(card);nav.setPadding(dp(5),dp(6),dp(5),dp(8));
        addNav("Start",this::showDashboard);addNav("Leady",this::showLeads);addNav("Szukaj",this::showMobileScan);addNav("Skan PC",this::showPcScan);addNav("Ustaw",this::showSettings);
        root.addView(nav,new LinearLayout.LayoutParams(-1,dp(70)));setContentView(root);refreshStatus();
    }
    private void addNav(String t,Runnable r){Button b=button(t);b.setOnClickListener(v->r.run());nav.addView(b,new LinearLayout.LayoutParams(0,-1,1));}
    private TextView tv(String s,int sp,int color){TextView v=new TextView(this);v.setText(s);v.setTextSize(sp);v.setTextColor(color);v.setPadding(dp(10),dp(8),dp(10),dp(8));v.setTextIsSelectable(true);return v;}
    private Button button(String s){Button b=new Button(this);b.setText(s);b.setAllCaps(false);b.setTextColor(text);b.setTextSize(12);b.setBackgroundResource(R.drawable.bg_button);return b;}
    private EditText input(String h){EditText e=new EditText(this);e.setHint(h);e.setHintTextColor(muted);e.setTextColor(text);e.setBackgroundColor(card2);e.setPadding(dp(14),dp(12),dp(14),dp(12));e.setSingleLine(true);return e;}
    private LinearLayout box(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);l.setPadding(dp(10),dp(8),dp(10),dp(8));l.setBackgroundResource(R.drawable.bg_card);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(0,dp(5),0,dp(5));l.setLayoutParams(p);return l;}
    private void clear(){scanPolling=false;content.removeAllViews();}
    private void title(String a,String b){TextView t=tv(a,26,text);t.setTypeface(null,Typeface.BOLD);content.addView(t);if(b!=null)content.addView(tv(b,13,muted));}
    private int dp(int x){return(int)(x*getResources().getDisplayMetrics().density+.5f);}
    private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();}
    private String enc(String s){try{return URLEncoder.encode(s,"UTF-8");}catch(Exception e){return s;}}
    private ApiClient api(){return new ApiClient(baseUrl);}

    private void refreshStatus(){int count=db.count();int pending=db.countUnsynced();String s="● LOCAL · "+count+" leadów"+(pending>0?" · "+pending+" do sync":"");if(remoteOnline)s+=" · PC ONLINE";status.setText(s);status.setTextColor(remoteOnline?accent:muted);}

    private void showPair(){
        clear();title("Pierwsze sparowanie","Robisz to raz. Potem aplikacja działa na własnej lokalnej bazie nawet bez laptopa.");
        EditText server=input("IP laptopa, np. 192.168.87.138");content.addView(server);
        Button auto=button("Znajdź Dealmaker automatycznie");content.addView(auto);
        Button pair=button("Sparuj");content.addView(pair);
        TextView help=tv("Na laptopie uruchom START_LAN.bat. Po sparowaniu telefon pobierze leady i zapamięta komputer.",13,muted);content.addView(help);
        auto.setOnClickListener(v->{auto.setEnabled(false);auto.setText("Szukam w Wi‑Fi…");io.submit(()->{try{String f=LocalDiscovery.find();ui.post(()->{auto.setEnabled(true);auto.setText("Znajdź Dealmaker automatycznie");if(f==null)toast("Nie znalazłem serwera. Sprawdź START_LAN.bat / firewall.");else{server.setText(f);pairServer(f);}});}catch(Exception e){ui.post(()->{auto.setEnabled(true);auto.setText("Znajdź Dealmaker automatycznie");toast("Błąd wyszukiwania: "+e.getMessage());});}});});
        pair.setOnClickListener(v->pairServer(server.getText().toString()));
    }
    private void pairServer(String raw){
        String s=ServerValidator.normalize(raw);if(!ServerValidator.isAllowed(s)){toast("Podaj lokalne IP laptopa. Port 8765 dopiszę automatycznie.");return;}
        io.submit(()->{try{JSONObject h=new ApiClient(s).get("/api/health");if(!h.optBoolean("ok"))throw new Exception("health=false");baseUrl=s;prefs.edit().putString("baseUrl",s).apply();remoteOnline=true;MobileSync.Result r=MobileSync.sync(this,s);ui.post(()->{toast("Sparowano. Pobrano "+r.pulled+" rekordów.");showDashboard();refreshStatus();});}catch(Exception e){ui.post(()->toast("Nie mogę połączyć z "+s+". Sprawdź START_LAN.bat i port 8765."));}});
    }

    private void showDashboard(){
        clear();title("Dealmaker Mobile","Działa lokalnie. Laptop jest potrzebny tylko do pełnego skanu i synchronizacji.");
        LinearLayout c=box();TextView stats=tv("Leady lokalnie: "+db.count()+"\nDo synchronizacji: "+db.countUnsynced(),17,text);c.addView(stats);content.addView(c);
        long last=prefs.getLong("lastMobileScan",0);String when=last==0?"jeszcze nie wykonano":DateFormat.getDateTimeInstance(DateFormat.SHORT,DateFormat.SHORT).format(new Date(last));
        LinearLayout q=box();q.addView(tv("Skan telefonu",15,accent));q.addView(tv("BiznesOferty + publiczne listy ofert\nOstatni skan: "+when+"\nAutomatyczny lekki skan: co ok. 6 h, gdy Android przydzieli okno pracy.",13,muted));Button now=button("Szukaj teraz na telefonie");q.addView(now);now.setOnClickListener(v->showMobileScan());content.addView(q);
        Button sync=button("Synchronizuj z laptopem teraz");sync.setOnClickListener(v->syncInBackground(true));content.addView(sync);
        Button add=button("+ Dodaj link / notatkę");add.setOnClickListener(v->showCapture());content.addView(add);
    }

    private void showLeads(){
        clear();title("Leady offline","Lista i wyszukiwarka działają bez laptopa.");EditText q=input("Szukaj: firma, miasto, URL, branża…");content.addView(q);Button go=button("Szukaj");content.addView(go);LinearLayout list=new LinearLayout(this);list.setOrientation(LinearLayout.VERTICAL);content.addView(list);
        Runnable run=()->renderLocal(list,db.search(q.getText().toString(),120));go.setOnClickListener(v->run.run());q.setOnEditorActionListener((v,a,e)->{run.run();return true;});run.run();
    }
    private void renderLocal(LinearLayout list,JSONArray a){list.removeAllViews();if(a.length()==0){list.addView(tv("Brak wyników.",14,muted));return;}for(int i=0;i<a.length();i++){JSONObject x=a.optJSONObject(i);if(x!=null)list.addView(leadCard(x));}}
    private View leadCard(JSONObject x){
        LinearLayout c=box();TextView t=tv(x.optString("title","Oferta"),16,text);t.setTypeface(null,Typeface.BOLD);c.addView(t);
        String meta=x.optString("source_name","");String loc=x.optString("location","");if(!loc.isEmpty())meta+=" · "+loc;if(x.has("price"))meta+=" · "+String.format(Locale.ROOT,"%.0f",x.optDouble("price"))+" "+x.optString("currency","");String date=x.optString("published_at","");if(!date.isEmpty())meta+=" · "+date;c.addView(tv(meta,12,accent));
        String d=x.optString("description","");if(d.length()>380)d=d.substring(0,380)+"…";if(!d.isEmpty())c.addView(tv(d,13,muted));String url=x.optString("source_url","");if(url.startsWith("http")){Button b=button("Otwórz dokładny URL");b.setOnClickListener(v->openUrl(url));c.addView(b);}return c;
    }

    private void showMobileScan(){
        clear();title("Szukaj na telefonie","Lekki crawler niezależny od laptopa. Nie wymyśla ofert — zapisuje tylko karty znalezione na publicznych stronach.");
        TextView prog=tv("Gotowy.",14,text);content.addView(prog);ProgressBar bar=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);bar.setMax(1000);content.addView(bar);
        Spinner depth=new Spinner(this);ArrayAdapter<String> ad=new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"Szybki · 1 strona / kategoria","Normalny · 2 strony / kategoria","Głębszy · 3 strony / kategoria"});depth.setAdapter(ad);content.addView(depth);
        Button start=button("▶ SZUKAJ TERAZ");content.addView(start);LinearLayout found=new LinearLayout(this);found.setOrientation(LinearLayout.VERTICAL);content.addView(found);
        start.setOnClickListener(v->{start.setEnabled(false);int pages=depth.getSelectedItemPosition()+1;io.submit(()->{BiznesOfertyScanner.Result r=BiznesOfertyScanner.scan(this,pages,(m,n,p,t)->ui.post(()->{prog.setText(m+"\nZnalezione: "+n+" · strony "+p+"/"+t);bar.setProgress(t>0?(int)(1000.0*p/t):0);}));prefs.edit().putLong("lastMobileScan",System.currentTimeMillis()).apply();ui.post(()->{start.setEnabled(true);prog.setText("Gotowe. Znaleziono: "+r.found+", zapisano/odświeżono: "+r.saved+", błędy stron: "+r.errors);refreshStatus();renderLocal(found,db.search("",40));});if(!baseUrl.isEmpty())syncInBackground(false);});});
    }

    private void showPcScan(){
        clear();title("Pełny skan laptopa","368 profili, Collector FB, cięższa weryfikacja. Telefon tylko steruje tym silnikiem.");scanPolling=true;TextView p=tv("Sprawdzam laptop…",14,text);content.addView(p);ProgressBar bar=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);bar.setMax(1000);content.addView(bar);Button start=button("▶ Start pełnego skanu FAST");content.addView(start);start.setOnClickListener(v->startPcScan());pollActive(p,bar);
    }
    private void startPcScan(){if(baseUrl.isEmpty()){toast("Najpierw sparuj laptop.");return;}io.submit(()->{try{JSONObject b=new JSONObject();b.put("source_ids",new JSONArray());b.put("power_profile","fast");b.put("only_new",true);JSONObject r=api().post("/api/scans",b);activeScanId=r.optInt("scan_id",-1);remoteOnline=true;ui.post(()->{toast("Uruchomiono skan #"+activeScanId);showPcScan();});}catch(Exception e){remoteOnline=false;ui.post(()->toast("Laptop jest teraz niedostępny. Skan telefonu nadal działa."));}});}
    private void pollActive(TextView p,ProgressBar bar){if(!scanPolling||baseUrl.isEmpty())return;io.submit(()->{try{JSONObject j=api().get("/api/scans/active");remoteOnline=true;JSONArray a=j.optJSONArray("items");if(a!=null&&a.length()>0){activeScanId=a.optJSONObject(0).optInt("id",-1);ui.post(()->pollProgress(p,bar));}else ui.post(()->{p.setText("Laptop online · brak aktywnego skanu");bar.setProgress(0);ui.postDelayed(()->pollActive(p,bar),2500);});}catch(Exception e){remoteOnline=false;ui.post(()->{p.setText("Laptop offline. Możesz nadal korzystać z lokalnych leadów i skanu telefonu.");bar.setProgress(0);});}refreshStatus();});}
    private void pollProgress(TextView p,ProgressBar bar){if(!scanPolling||activeScanId<0)return;int id=activeScanId;io.submit(()->{try{JSONObject j=api().get("/api/scans/"+id+"/progress?event_limit=5");JSONObject s=j.optJSONObject("scan");if(s==null)s=j;int done=s.optInt("done_sources",0),total=s.optInt("total_sources",368),found=s.optInt("found",0),fail=s.optInt("failed_sources",0);String st=s.optString("status","running");int f=total>0?(int)(1000.0*done/total):0;ui.post(()->{bar.setProgress(f);p.setText("Skan #"+id+" · "+st+"\n"+done+"/"+total+" · znalezione "+found+" · błędy "+fail);if(st.equals("running")||st.equals("stopping"))ui.postDelayed(()->pollProgress(p,bar),1500);else pollActive(p,bar);});}catch(Exception e){remoteOnline=false;ui.post(()->p.setText("Utracono połączenie z laptopem. Dane lokalne są bezpieczne."));}});}

    private void showCapture(){
        clear();title("Dodaj lokalnie","Zapis następuje od razu na telefonie. Synchronizacja może nastąpić później.");EditText url=input("URL (opcjonalnie)");EditText ttl=input("Tytuł");EditText note=input("Notatka / opis");note.setSingleLine(false);note.setMinLines(5);note.setGravity(Gravity.TOP);content.addView(url);content.addView(ttl);content.addView(note);Button save=button("Zapisz");content.addView(save);save.setOnClickListener(v->{db.upsert(url.getText().toString().trim(),ttl.getText().toString().trim(),note.getText().toString().trim(),"",null,"","Telefon","",0,"local","manual",0,false);refreshStatus();toast("Zapisano lokalnie.");showDashboard();syncInBackground(false);});
    }

    private void showSettings(){
        clear();title("Ustawienia","Sparowanie jest zapamiętane. Nie musisz łączyć się przy każdym uruchomieniu.");content.addView(tv("Laptop: "+(baseUrl.isEmpty()?"nie sparowany":baseUrl)+"\nLeady lokalne: "+db.count()+"\nDo synchronizacji: "+db.countUnsynced(),14,text));Button sync=button("Synchronizuj teraz");sync.setOnClickListener(v->syncInBackground(true));content.addView(sync);Button rep=button("Zmień / sparuj inny laptop");rep.setOnClickListener(v->showPair());content.addView(rep);Button web=button("Otwórz panel laptopa w Chrome");web.setOnClickListener(v->{if(!baseUrl.isEmpty())openUrl(baseUrl);});content.addView(web);
    }

    private void syncInBackground(boolean announce){
        if(baseUrl.isEmpty())return;io.submit(()->{try{JSONObject h=api().get("/api/health");if(!h.optBoolean("ok"))throw new Exception("offline");remoteOnline=true;MobileSync.Result r=MobileSync.sync(this,baseUrl);prefs.edit().putLong("lastSync",System.currentTimeMillis()).apply();ui.post(()->{refreshStatus();if(announce)toast("Sync OK · pobrano "+r.pulled+" · wysłano "+r.pushed);});}catch(Exception e){remoteOnline=false;ui.post(()->{refreshStatus();if(announce)toast("Laptop offline. Aplikacja nadal działa lokalnie.");});}});
    }

    private void handleShare(Intent i){if(i==null||!Intent.ACTION_SEND.equals(i.getAction())||!"text/plain".equals(i.getType()))return;String s=i.getStringExtra(Intent.EXTRA_TEXT);if(s==null||s.trim().isEmpty())return;String u=firstUrl(s);String n=u==null?s:s.replace(u,"").trim();db.upsert(u,"Link udostępniony z telefonu",n,"",null,"","Telefon","",0,"local","share",0,false);refreshStatus();toast("Zapisano lokalnie w Dealmaker.");syncInBackground(false);}
    private String firstUrl(String s){Matcher m=Pattern.compile("https?://[^\\s]+",Pattern.CASE_INSENSITIVE).matcher(s);return m.find()?m.group():null;}
    private void openUrl(String u){try{startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(u)));}catch(Exception e){toast("Nie mogę otworzyć linku.");}}
}
