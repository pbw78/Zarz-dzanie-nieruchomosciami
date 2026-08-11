package pl.dealmaker.mobile;

import android.app.Activity;
import android.content.*;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.*;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.widget.*;
import org.json.*;
import java.text.DateFormat;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.*;

public class MainActivity extends Activity {
    private static final String PREFS="dealmaker_mobile";
    private static final int EXPORT_XLSX=3201;
    private final ExecutorService io=Executors.newFixedThreadPool(5);
    private final Handler ui=new Handler(Looper.getMainLooper());
    private SharedPreferences prefs;
    private MobileDb db;
    private LinearLayout root,header,content,nav;
    private TextView status;
    private String baseUrl="";
    private boolean remoteOnline=false,scanPolling=false;
    private int activeScanId=-1;

    private final int bg=Color.rgb(10,14,19),card=Color.rgb(20,27,36),card2=Color.rgb(28,37,50);
    private final int text=Color.rgb(245,247,250),muted=Color.rgb(155,167,183),accent=Color.rgb(91,227,154),bad=Color.rgb(255,107,107),warn=Color.rgb(247,198,90);

    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        prefs=getSharedPreferences(PREFS,MODE_PRIVATE);
        db=new MobileDb(this);
        baseUrl=prefs.getString("baseUrl","");
        getWindow().setStatusBarColor(bg);
        getWindow().setNavigationBarColor(bg);
        buildShell();
        handleShare(getIntent());
        try{MobileJobs.schedule(this);}catch(Throwable ignored){}
        if(baseUrl.isEmpty()&&db.count()==0)showPair();else{showDashboard();if(!baseUrl.isEmpty())syncInBackground(false);}
    }
    @Override protected void onNewIntent(Intent i){super.onNewIntent(i);setIntent(i);handleShare(i);}
    @Override protected void onResume(){super.onResume();if(!baseUrl.isEmpty())syncInBackground(false);}
    @Override protected void onDestroy(){scanPolling=false;io.shutdownNow();if(db!=null)db.close();super.onDestroy();}

    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data){
        super.onActivityResult(requestCode,resultCode,data);
        if(requestCode==EXPORT_XLSX&&resultCode==RESULT_OK&&data!=null&&data.getData()!=null){
            Uri uri=data.getData();toast("Tworzę Excel…");io.submit(()->{try{int n=ExcelExporter.write(this,uri);ui.post(()->toast("Excel gotowy · "+n+" leadów"));}catch(Exception e){ui.post(()->toast("Nie udało się zapisać Excela: "+safe(e.getMessage())));}});
        }
    }

    private void buildShell(){
        root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(bg);
        header=new LinearLayout(this);header.setOrientation(LinearLayout.VERTICAL);header.setBackgroundColor(card);header.setPadding(dp(16),dp(7),dp(16),dp(8));
        TextView brand=tv("Dealmaker",19,text);brand.setTypeface(null,Typeface.BOLD);brand.setPadding(0,0,0,1);header.addView(brand);
        status=tv("LOCAL",12,muted);status.setPadding(0,0,0,0);header.addView(status);root.addView(header,new LinearLayout.LayoutParams(-1,-2));
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);content=new LinearLayout(this);content.setOrientation(LinearLayout.VERTICAL);content.setPadding(dp(16),dp(12),dp(16),dp(26));scroll.addView(content,new ScrollView.LayoutParams(-1,-2));root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        nav=new LinearLayout(this);nav.setOrientation(LinearLayout.HORIZONTAL);nav.setGravity(Gravity.CENTER);nav.setBackgroundColor(card);nav.setPadding(dp(8),dp(7),dp(8),dp(8));nav.setMinimumHeight(dp(64));
        addNav("Start",this::showDashboard);addNav("Leady",this::showLeads);addNav("Szukaj",this::showMobileScan);addNav("Więcej",this::showMore);root.addView(nav,new LinearLayout.LayoutParams(-1,-2));
        setContentView(root);applySystemInsets();refreshStatus();
    }

    private void applySystemInsets(){
        root.setOnApplyWindowInsetsListener((v,insets)->{int top,bottom;if(Build.VERSION.SDK_INT>=30){android.graphics.Insets bars=insets.getInsets(WindowInsets.Type.systemBars());top=bars.top;bottom=bars.bottom;}else{top=insets.getSystemWindowInsetTop();bottom=insets.getSystemWindowInsetBottom();}header.setPadding(dp(16),top+dp(7),dp(16),dp(8));nav.setPadding(dp(8),dp(7),dp(8),bottom+dp(8));return insets;});root.requestApplyInsets();
    }

    private void addNav(String label,Runnable r){TextView v=tv(label,12,text);v.setGravity(Gravity.CENTER);v.setTypeface(null,Typeface.BOLD);v.setMinHeight(dp(52));v.setBackgroundResource(R.drawable.bg_button);v.setOnClickListener(x->r.run());LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(52),1);p.setMargins(dp(3),0,dp(3),0);nav.addView(v,p);}
    private TextView tv(String s,int sp,int color){TextView v=new TextView(this);v.setText(s);v.setTextSize(sp);v.setTextColor(color);v.setPadding(dp(10),dp(8),dp(10),dp(8));v.setTextIsSelectable(true);return v;}
    private Button button(String s){Button b=new Button(this);b.setText(s);b.setAllCaps(false);b.setTextColor(text);b.setTextSize(14);b.setMinHeight(dp(54));b.setBackgroundResource(R.drawable.bg_button);return b;}
    private Button primary(String s){Button b=button(s);b.setTextColor(accent);b.setTypeface(null,Typeface.BOLD);b.setTextSize(15);return b;}
    private EditText input(String h){EditText e=new EditText(this);e.setHint(h);e.setHintTextColor(muted);e.setTextColor(text);e.setTextSize(17);e.setBackgroundColor(card2);e.setPadding(dp(14),dp(13),dp(14),dp(13));e.setSingleLine(true);return e;}
    private LinearLayout box(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);l.setPadding(dp(12),dp(10),dp(12),dp(10));l.setBackgroundResource(R.drawable.bg_card);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(0,dp(6),0,dp(6));l.setLayoutParams(p);return l;}
    private void addFull(View v){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(0,dp(5),0,dp(5));content.addView(v,p);}
    private void clear(){scanPolling=false;content.removeAllViews();}
    private void title(String a,String b){TextView t=tv(a,27,text);t.setTypeface(null,Typeface.BOLD);t.setPadding(dp(2),dp(4),dp(2),dp(5));content.addView(t);if(b!=null){TextView s=tv(b,14,muted);s.setPadding(dp(2),0,dp(2),dp(12));content.addView(s);}}
    private int dp(int x){return(int)(x*getResources().getDisplayMetrics().density+.5f);}
    private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();}
    private ApiClient api(){return new ApiClient(baseUrl);}

    private void refreshStatus(){if(db==null)return;int count=db.count(),pending=db.countUnsynced();String s=(remoteOnline?"● PC ONLINE":"● TELEFON")+"  ·  "+count+" leadów"+(pending>0?"  ·  "+pending+" do sync":"");status.setText(s);status.setTextColor(remoteOnline?accent:muted);}

    private void showPair(){
        clear();title("Pierwsze uruchomienie","Laptop jest opcjonalny. Możesz sparować go teraz albo od razu używać własnego skanera telefonu.");
        LinearLayout pairBox=box();pairBox.addView(tv("Adres laptopa",13,muted));EditText server=input("np. 192.168.87.138");server.setText(prefs.getString("lastServerInput",""));pairBox.addView(server);pairBox.addView(tv("Możesz wpisać samo IP. Aplikacja sama doda http:// i port :8765.",12,muted));addFull(pairBox);
        Button pair=primary("SPARUJ Z LAPTOPEM");addFull(pair);Button skip=primary("UŻYWAJ TERAZ BEZ LAPTOPA");addFull(skip);Button auto=button("Znajdź automatycznie w tej sieci Wi‑Fi");addFull(auto);
        LinearLayout diagBox=box();diagBox.addView(tv("Diagnostyka połączenia",16,text));TextView diag=tv(initialNetworkInfo(),13,muted);diagBox.addView(diag);Button test=button("Sprawdź IP, port 8765 i API");diagBox.addView(test);Button chrome=button("Otwórz /api/health w Chrome");diagBox.addView(chrome);Button fw=button("Kopiuj komendę naprawy Windows Firewall");diagBox.addView(fw);addFull(diagBox);
        skip.setOnClickListener(v->showDashboard());
        pair.setOnClickListener(v->{String raw=server.getText().toString();prefs.edit().putString("lastServerInput",raw).apply();pairServer(raw,diag,pair);});
        auto.setOnClickListener(v->{auto.setEnabled(false);auto.setText("Szukam Dealmaker w Wi‑Fi…");String preferred=server.getText().toString();io.submit(()->{try{String f=LocalDiscovery.find(preferred);ui.post(()->{auto.setEnabled(true);auto.setText("Znajdź automatycznie w tej sieci Wi‑Fi");if(f==null){diag.setText("Nie znalazłem Dealmaker automatycznie.\n\n"+initialNetworkInfo()+"\n\nMożesz nadal używać skanera telefonu bez PC.");diag.setTextColor(warn);}else{server.setText(f);prefs.edit().putString("lastServerInput",f).apply();pairServer(f,diag,pair);}});}catch(Exception e){ui.post(()->{auto.setEnabled(true);auto.setText("Znajdź automatycznie w tej sieci Wi‑Fi");diag.setText("Błąd wyszukiwania: "+safe(e.getMessage()));diag.setTextColor(bad);});}});});
        test.setOnClickListener(v->runDiagnostic(server.getText().toString(),diag,test));chrome.setOnClickListener(v->{String s=ServerValidator.normalize(server.getText().toString());if(ServerValidator.isAllowed(s))openUrl(s+"/api/health");else toast("Najpierw wpisz IP laptopa.");});fw.setOnClickListener(v->{copy("New-NetFirewallRule -DisplayName \"Dealmaker OS 8765\" -Direction Inbound -Protocol TCP -LocalPort 8765 -Action Allow -Profile Any");toast("Skopiowano. Wklej w PowerShell uruchomionym jako administrator.");});
    }

    private String initialNetworkInfo(){List<String> ips=ConnectionDiagnostics.localPrivateIpv4s();return "IP telefonu: "+(ips.isEmpty()?"nie wykryto":join(ips))+"\nLaptop musi mieć adres w tej samej sieci i uruchomiony START_LAN.bat.";}
    private void runDiagnostic(String raw,TextView out,Button button){button.setEnabled(false);button.setText("Sprawdzam…");io.submit(()->{ConnectionDiagnostics.Result r=ConnectionDiagnostics.test(raw);ui.post(()->{button.setEnabled(true);button.setText("Sprawdź IP, port 8765 i API");out.setText(r.summary());out.setTextColor(r.healthOk?accent:("REFUSED".equals(r.code)||"TIMEOUT".equals(r.code)?warn:bad));});});}
    private void pairServer(String raw,TextView diag,Button pairButton){String s=ServerValidator.normalize(raw);prefs.edit().putString("lastServerInput",raw).apply();if(!ServerValidator.isAllowed(s)){diag.setText("Nieprawidłowy adres. Wpisz np. 192.168.87.138");diag.setTextColor(bad);return;}pairButton.setEnabled(false);pairButton.setText("Łączę i pobieram bazę…");io.submit(()->{ConnectionDiagnostics.Result check=ConnectionDiagnostics.test(s);if(!check.healthOk){ui.post(()->{pairButton.setEnabled(true);pairButton.setText("SPARUJ Z LAPTOPEM");diag.setText(check.summary());diag.setTextColor(warn);});return;}try{baseUrl=s;prefs.edit().putString("baseUrl",s).putString("lastServerInput",s).apply();remoteOnline=true;MobileSync.Result r=MobileSync.sync(this,s);prefs.edit().putLong("lastSync",System.currentTimeMillis()).apply();ui.post(()->{pairButton.setEnabled(true);toast("Sparowano. Pobrano/odświeżono "+r.pulled+" rekordów.");showDashboard();refreshStatus();});}catch(Exception e){ui.post(()->{pairButton.setEnabled(true);pairButton.setText("SPARUJ Z LAPTOPEM");diag.setText("Połączenie działało, ale synchronizacja się nie udała: "+safe(e.getMessage()));diag.setTextColor(bad);});}});}

    private void showDashboard(){
        clear();title("Start","Podręczny Dealmaker: telefon sam szuka ofert, zapisuje je lokalnie i działa bez PC.");
        LinearLayout stats=box();stats.addView(tv("Baza telefonu",15,muted));TextView n=tv(String.valueOf(db.count()),34,text);n.setTypeface(null,Typeface.BOLD);stats.addView(n);stats.addView(tv("leadów lokalnie  ·  "+db.countUnsynced()+" do synchronizacji",13,muted));addFull(stats);
        LinearLayout quick=box();quick.addView(tv("Skan codzienny",17,text));quick.addView(tv(DailyScanner.logicalProfiles()+" lekkich profili · "+DailyScanner.sourceSummary(),12,muted));Button b50=primary("50 OFERT · SZYBKI");Button b200=primary("200 OFERT · NORMALNY");Button b500=primary("500 OFERT · GŁĘBOKI");b50.setOnClickListener(v->showMobileScan(50));b200.setOnClickListener(v->showMobileScan(200));b500.setOnClickListener(v->showMobileScan(500));quick.addView(b50);quick.addView(b200);quick.addView(b500);addFull(quick);
        LinearLayout pc=box();pc.addView(tv(remoteOnline?"● Laptop połączony":"● PC opcjonalny / offline",16,remoteOnline?accent:muted));pc.addView(tv(baseUrl.isEmpty()?"Telefon działa samodzielnie. PC możesz sparować później.":baseUrl,13,muted));long last=prefs.getLong("lastSync",0);pc.addView(tv("Ostatnia synchronizacja: "+formatTime(last),12,muted));Button sync=button("Synchronizuj teraz");sync.setOnClickListener(v->syncInBackground(true));pc.addView(sync);addFull(pc);
        Button leads=button("Przeglądaj lokalne leady");leads.setOnClickListener(v->showLeads());addFull(leads);Button export=button("Eksportuj Excel .xlsx");export.setOnClickListener(v->exportExcel());addFull(export);
    }

    private void showLeads(){clear();title("Leady","Lokalna baza działa bez laptopa. Typ i kategoria są wyliczane automatycznie.");EditText q=input("Szukaj: firma, miasto, URL, źródło, branża…");addFull(q);Button go=button("Szukaj");addFull(go);LinearLayout list=new LinearLayout(this);list.setOrientation(LinearLayout.VERTICAL);addFull(list);Runnable run=()->renderLocal(list,db.search(q.getText().toString(),300));go.setOnClickListener(v->run.run());q.setOnEditorActionListener((v,a,e)->{run.run();return true;});run.run();}
    private void renderLocal(LinearLayout list,JSONArray a){list.removeAllViews();if(a.length()==0){list.addView(tv("Brak wyników.",14,muted));return;}for(int i=0;i<a.length();i++){JSONObject x=a.optJSONObject(i);if(x!=null)list.addView(leadCard(x));}}
    private View leadCard(JSONObject x){LinearLayout c=box();TextView t=tv(x.optString("title","Oferta"),16,text);t.setTypeface(null,Typeface.BOLD);c.addView(t);c.addView(tv(DealClassifier.type(x)+" · "+DealClassifier.category(x),12,accent));String meta=x.optString("source_name","");String loc=x.optString("location","");if(!loc.isEmpty())meta+=" · "+loc;if(x.has("price"))meta+=" · "+String.format(Locale.ROOT,"%.0f",x.optDouble("price"))+" "+x.optString("currency","");String date=x.optString("published_at","");if(!date.isEmpty())meta+=" · "+date;int score=x.optInt("score",0);if(score<=0)score=DealClassifier.quickScore(x);meta+=" · score "+score;c.addView(tv(meta,12,muted));String d=x.optString("description","");if(d.length()>420)d=d.substring(0,420)+"…";if(!d.isEmpty())c.addView(tv(d,13,muted));String url=x.optString("source_url","");if(url.startsWith("http")){Button b=button("Otwórz źródło");b.setOnClickListener(v->openUrl(url));c.addView(b);}return c;}

    private void showMobileScan(){showMobileScan(0);}
    private void showMobileScan(int autoTarget){
        clear();title("Szukaj ofert","Skan działa bez laptopa. Wybierz cel: aplikacja przechodzi publiczne profile aż zbierze cel albo wyczerpie dostępne strony.");
        LinearLayout sources=box();sources.addView(tv("Źródła mobilne",15,text));sources.addView(tv(DailyScanner.sourceSummary(),12,muted));sources.addView(tv("Profile logiczne: "+DailyScanner.logicalProfiles()+" · każda awaria źródła jest izolowana",12,muted));addFull(sources);
        LinearLayout state=box();TextView prog=tv("Gotowy.",14,text);state.addView(prog);ProgressBar bar=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);bar.setMax(1000);state.addView(bar);addFull(state);
        Button b50=primary("50 OFERT · szybki");Button b200=primary("200 OFERT · normalny");Button b500=primary("500 OFERT · głęboki");addFull(b50);addFull(b200);addFull(b500);Button[] controls={b50,b200,b500};
        LinearLayout found=new LinearLayout(this);found.setOrientation(LinearLayout.VERTICAL);addFull(found);
        b50.setOnClickListener(v->startDailyScan(50,prog,bar,found,controls));b200.setOnClickListener(v->startDailyScan(200,prog,bar,found,controls));b500.setOnClickListener(v->startDailyScan(500,prog,bar,found,controls));
        if(autoTarget>0)startDailyScan(autoTarget,prog,bar,found,controls);
    }

    private void startDailyScan(int target,TextView prog,ProgressBar bar,LinearLayout found,Button[] controls){
        for(Button b:controls)b.setEnabled(false);prog.setText("Start skanu · cel "+target+" ofert…");bar.setProgress(0);
        io.submit(()->{DailyScanner.Result r=DailyScanner.scan(this,target,(m,n,p,t)->ui.post(()->{prog.setText(m+"\nZnalezione: "+n+" / cel "+target+" · profile "+p+"/"+t);int byTarget=(int)(1000.0*Math.min(n,target)/target);bar.setProgress(Math.max(0,Math.min(1000,byTarget)));}));prefs.edit().putLong("lastMobileScan",System.currentTimeMillis()).putInt("lastMobileTarget",target).putInt("lastMobileFound",r.found).putString("lastMobileStatus",r.summary()).apply();ui.post(()->{for(Button b:controls)b.setEnabled(true);bar.setProgress(Math.min(1000,(int)(1000.0*Math.min(r.found,target)/target)));prog.setText("Gotowe · znalezione "+r.found+" · zapisane/odświeżone "+r.saved+"\nProfile OK: "+r.profilesOk+" · pominięte: "+r.profilesFailed+" · błędy stron: "+r.errors+"\n\n"+r.summary());refreshStatus();renderLocal(found,db.search("",60));});if(!baseUrl.isEmpty())syncInBackground(false);});
    }

    private void showMore(){
        clear();title("Więcej","Eksport, PC i narzędzia dodatkowe.");
        Button export=primary("EKSPORTUJ EXCEL .XLSX");export.setOnClickListener(v->exportExcel());addFull(export);
        Button pc=button("Pełny skan na laptopie");pc.setOnClickListener(v->showPcScan());addFull(pc);Button add=button("+ Dodaj link / notatkę");add.setOnClickListener(v->showCapture());addFull(add);Button sync=button("Synchronizuj z laptopem");sync.setOnClickListener(v->syncInBackground(true));addFull(sync);Button settings=button("Połączenie, diagnostyka i ustawienia");settings.setOnClickListener(v->showConnectionSettings());addFull(settings);
        long last=prefs.getLong("lastMobileScan",0);LinearLayout h=box();h.addView(tv("Ostatni skan telefonu",15,text));h.addView(tv(formatTime(last)+" · cel "+prefs.getInt("lastMobileTarget",0)+" · znalezione "+prefs.getInt("lastMobileFound",0),13,muted));String st=prefs.getString("lastMobileStatus","");if(!st.isEmpty())h.addView(tv(st,12,muted));addFull(h);
    }

    private void exportExcel(){Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");i.putExtra(Intent.EXTRA_TITLE,"Dealmaker-Leady-"+new java.text.SimpleDateFormat("yyyy-MM-dd",Locale.ROOT).format(new Date())+".xlsx");try{startActivityForResult(i,EXPORT_XLSX);}catch(Exception e){toast("Brak aplikacji do wyboru miejsca zapisu.");}}

    private void showPcScan(){clear();title("Skan laptopa","Pełny silnik Dealmaker OS: źródła desktopowe, Collector i cięższa weryfikacja.");scanPolling=true;LinearLayout b=box();TextView p=tv("Sprawdzam laptop…",14,text);b.addView(p);ProgressBar bar=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);bar.setMax(1000);b.addView(bar);addFull(b);Button start=primary("▶ START FAST");addFull(start);Button stop=button("■ Zatrzymaj aktywny skan");addFull(stop);Button resume=button("↻ Wznów skan");addFull(resume);start.setOnClickListener(v->startPcScan());stop.setOnClickListener(v->{if(activeScanId>0)postPc("/api/scans/"+activeScanId+"/stop","Zatrzymywanie skanu…");});resume.setOnClickListener(v->{if(activeScanId>0)postPc("/api/scans/"+activeScanId+"/resume","Wznowiono skan.");});pollActive(p,bar);}
    private void startPcScan(){if(baseUrl.isEmpty()){toast("Najpierw sparuj laptop.");showConnectionSettings();return;}io.submit(()->{try{JSONObject b=new JSONObject();b.put("source_ids",new JSONArray());b.put("power_profile","fast");b.put("only_new",true);JSONObject r=api().post("/api/scans",b);activeScanId=r.optInt("scan_id",-1);remoteOnline=true;ui.post(()->{toast("Uruchomiono skan #"+activeScanId);showPcScan();});}catch(Exception e){remoteOnline=false;ui.post(()->toast("Laptop jest niedostępny. Skan telefonu nadal działa."));}});}
    private void postPc(String path,String message){io.submit(()->{try{JSONObject r=api().post(path,new JSONObject());if(r.has("scan_id"))activeScanId=r.optInt("scan_id",activeScanId);ui.post(()->toast(message));}catch(Exception e){ui.post(()->toast("Nie udało się wysłać polecenia do laptopa."));}});}
    private void pollActive(TextView p,ProgressBar bar){if(!scanPolling||baseUrl.isEmpty()){if(baseUrl.isEmpty())p.setText("Laptop nie jest sparowany.");return;}io.submit(()->{try{JSONObject j=api().get("/api/scans/active");remoteOnline=true;JSONArray a=j.optJSONArray("items");if(a!=null&&a.length()>0){activeScanId=a.optJSONObject(0).optInt("id",-1);ui.post(()->pollProgress(p,bar));}else ui.post(()->{p.setText("Laptop online · brak aktywnego skanu");bar.setProgress(0);ui.postDelayed(()->pollActive(p,bar),2500);});}catch(Exception e){remoteOnline=false;ui.post(()->{p.setText("Laptop offline. Lokalna baza i skan telefonu nadal działają.");bar.setProgress(0);});}ui.post(this::refreshStatus);});}
    private void pollProgress(TextView p,ProgressBar bar){if(!scanPolling||activeScanId<0)return;int id=activeScanId;io.submit(()->{try{JSONObject j=api().get("/api/scans/"+id+"/progress?event_limit=5");JSONObject s=j.optJSONObject("scan");if(s==null)s=j;int done=s.optInt("done_sources",0),total=s.optInt("total_sources",368),found=s.optInt("found",0),fail=s.optInt("failed_sources",0);String st=s.optString("status","running");int f=total>0?(int)(1000.0*done/total):0;ui.post(()->{bar.setProgress(f);p.setText("Skan #"+id+" · "+st.toUpperCase(Locale.ROOT)+"\n"+done+" / "+total+" źródeł · znalezione "+found+" · błędy "+fail);if(st.equals("running")||st.equals("stopping"))ui.postDelayed(()->pollProgress(p,bar),1500);else pollActive(p,bar);});}catch(Exception e){remoteOnline=false;ui.post(()->p.setText("Utracono połączenie z laptopem. Dane lokalne są bezpieczne."));}});}

    private void showCapture(){clear();title("Dodaj","Zapis jest natychmiast w telefonie. Jeśli laptop jest offline, rekord zsynchronizuje się później.");EditText url=input("URL (opcjonalnie)");EditText ttl=input("Tytuł");EditText note=input("Notatka / opis");note.setSingleLine(false);note.setMinLines(5);note.setGravity(Gravity.TOP);addFull(url);addFull(ttl);addFull(note);Button save=primary("ZAPISZ LOKALNIE");addFull(save);save.setOnClickListener(v->{db.upsert(url.getText().toString().trim(),ttl.getText().toString().trim(),note.getText().toString().trim(),"",null,"","Telefon","",0,"local","manual",0,false);refreshStatus();toast("Zapisano lokalnie.");showDashboard();syncInBackground(false);});}

    private void showConnectionSettings(){clear();title("Połączenie","PC jest opcjonalny. Aplikacja pamięta laptop po pierwszym udanym sparowaniu.");LinearLayout c=box();c.addView(tv("Zapisany laptop",13,muted));EditText server=input("IP laptopa");server.setText(baseUrl.isEmpty()?prefs.getString("lastServerInput",""):baseUrl);c.addView(server);TextView diag=tv(initialNetworkInfo(),13,muted);c.addView(diag);addFull(c);Button test=button("Sprawdź połączenie");addFull(test);test.setOnClickListener(v->runDiagnostic(server.getText().toString(),diag,test));Button find=button("Znajdź laptop automatycznie");addFull(find);find.setOnClickListener(v->{find.setEnabled(false);find.setText("Szukam…");io.submit(()->{try{String f=LocalDiscovery.find(server.getText().toString());ui.post(()->{find.setEnabled(true);find.setText("Znajdź laptop automatycznie");if(f==null){diag.setText("Nie znaleziono Dealmaker w lokalnej sieci. Telefon nadal działa samodzielnie.");diag.setTextColor(warn);}else{server.setText(f);diag.setText("Znaleziono: "+f+"\nKliknij „Zapisz i sparuj ponownie”.");diag.setTextColor(accent);}});}catch(Exception e){ui.post(()->{find.setEnabled(true);find.setText("Znajdź laptop automatycznie");});}});});Button save=primary("ZAPISZ I SPARUJ PONOWNIE");addFull(save);save.setOnClickListener(v->pairServer(server.getText().toString(),diag,save));Button chrome=button("Otwórz /api/health w Chrome");addFull(chrome);chrome.setOnClickListener(v->{String s=ServerValidator.normalize(server.getText().toString());if(ServerValidator.isAllowed(s))openUrl(s+"/api/health");});Button fw=button("Kopiuj naprawę firewalla");addFull(fw);fw.setOnClickListener(v->{copy("New-NetFirewallRule -DisplayName \"Dealmaker OS 8765\" -Direction Inbound -Protocol TCP -LocalPort 8765 -Action Allow -Profile Any");toast("Skopiowano komendę PowerShell.");});}

    private void syncInBackground(boolean notify){if(baseUrl.isEmpty()){if(notify)toast("PC nie jest sparowany — telefon może działać samodzielnie.");return;}io.submit(()->{try{JSONObject h=api().get("/api/health");if(!h.optBoolean("ok"))throw new Exception("health=false");remoteOnline=true;MobileSync.Result r=MobileSync.sync(this,baseUrl);prefs.edit().putLong("lastSync",System.currentTimeMillis()).apply();ui.post(()->{refreshStatus();if(notify)toast("Synchronizacja: pobrano/odświeżono "+r.pulled+", wysłano "+r.pushed+(r.errors>0?", błędy "+r.errors:""));});}catch(Exception e){remoteOnline=false;ui.post(()->{refreshStatus();if(notify)toast("Laptop jest offline. Nadal możesz pracować i szukać lokalnie.");});}});}

    private void handleShare(Intent i){if(i==null||!Intent.ACTION_SEND.equals(i.getAction())||!"text/plain".equals(i.getType())||db==null)return;String s=i.getStringExtra(Intent.EXTRA_TEXT);if(s==null||s.trim().isEmpty())return;String url=firstUrl(s);String desc=url==null?s:s.replace(url,"").trim();String subject=i.getStringExtra(Intent.EXTRA_SUBJECT);String ttl=subject==null||subject.trim().isEmpty()?(url==null?"Notatka z telefonu":"Link udostępniony z telefonu"):subject.trim();db.upsert(url==null?"":url,ttl,desc,"",null,"","Telefon","",0,"local","share",0,false);refreshStatus();toast("Zapisano w Dealmaker lokalnie.");syncInBackground(false);}
    private String firstUrl(String s){Matcher m=Pattern.compile("https?://[^\\s]+",Pattern.CASE_INSENSITIVE).matcher(s);return m.find()?m.group():null;}
    private void openUrl(String u){try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(u)));}catch(Exception e){toast("Nie mogę otworzyć linku.");}}
    private void copy(String s){ClipboardManager cm=(ClipboardManager)getSystemService(CLIPBOARD_SERVICE);if(cm!=null)cm.setPrimaryClip(ClipData.newPlainText("Dealmaker",s));}
    private String formatTime(long t){return t<=0?"jeszcze nie wykonano":DateFormat.getDateTimeInstance(DateFormat.SHORT,DateFormat.SHORT).format(new Date(t));}
    private String join(List<String> xs){StringBuilder b=new StringBuilder();for(String x:xs){if(b.length()>0)b.append(", ");b.append(x);}return b.toString();}
    private String safe(String s){return s==null||s.trim().isEmpty()?"brak szczegółów":s.trim();}
}
