package pl.dealmaker.mobile;

import android.app.*;
import android.content.*;
import android.graphics.Color;
import android.net.Uri;
import android.os.*;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.net.URLEncoder;
import java.util.Locale;
import java.util.concurrent.*;
import java.util.regex.*;

public class MainActivity extends Activity {
    private static final String PREFS = "dealmaker_mobile";
    private final ExecutorService io = Executors.newFixedThreadPool(4);
    private final Handler ui = new Handler(Looper.getMainLooper());
    private SharedPreferences prefs;
    private LinearLayout root, content, nav;
    private TextView status;
    private String baseUrl = "";
    private int activeScanId = -1;
    private boolean scanPolling = false;

    private final int bg = Color.rgb(11,14,19), card = Color.rgb(20,26,35), card2 = Color.rgb(26,34,48);
    private final int text = Color.rgb(245,247,250), muted = Color.rgb(155,167,183), accent = Color.rgb(91,227,154), bad = Color.rgb(255,107,107);

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        baseUrl = prefs.getString("baseUrl", "");
        buildShell();
        handleShare(getIntent());
        if (baseUrl.trim().isEmpty()) showConnect(); else testAndShowDashboard();
    }

    @Override protected void onNewIntent(Intent intent) { super.onNewIntent(intent); setIntent(intent); handleShare(intent); }
    @Override protected void onDestroy() { scanPolling=false; io.shutdownNow(); super.onDestroy(); }

    private void buildShell() {
        root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(bg);
        status = tv("● OFFLINE", 12, muted); status.setPadding(dp(16),dp(10),dp(16),dp(8)); root.addView(status);
        ScrollView scroll = new ScrollView(this); content = new LinearLayout(this); content.setOrientation(LinearLayout.VERTICAL); content.setPadding(dp(14),dp(8),dp(14),dp(110)); scroll.addView(content); root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        nav = new LinearLayout(this); nav.setOrientation(LinearLayout.HORIZONTAL); nav.setPadding(dp(8),dp(8),dp(8),dp(10)); nav.setBackgroundColor(card);
        addNav("Start", this::showDashboard); addNav("Leady", this::showLeads); addNav("Skan", this::showScan); addNav("+ Dodaj", this::showCapture); addNav("Ustaw", this::showConnect);
        root.addView(nav,new LinearLayout.LayoutParams(-1,dp(72))); setContentView(root);
    }

    private void addNav(String label, Runnable r) { Button b=button(label); b.setOnClickListener(v->r.run()); nav.addView(b,new LinearLayout.LayoutParams(0,-1,1)); }
    private TextView tv(String s,int sp,int c){ TextView v=new TextView(this);v.setText(s);v.setTextSize(sp);v.setTextColor(c);v.setPadding(dp(10),dp(8),dp(10),dp(8));v.setTextIsSelectable(true);return v; }
    private Button button(String s){ Button b=new Button(this);b.setText(s);b.setTextColor(text);b.setTextSize(12);b.setAllCaps(false);b.setBackgroundResource(R.drawable.bg_button);return b; }
    private EditText input(String hint){ EditText e=new EditText(this);e.setHint(hint);e.setHintTextColor(muted);e.setTextColor(text);e.setSingleLine(true);e.setBackgroundColor(card2);e.setPadding(dp(14),dp(12),dp(14),dp(12));return e; }
    private void clear(){ content.removeAllViews(); }
    private void title(String s,String sub){ TextView t=tv(s,26,text);t.setTypeface(null,1);content.addView(t); if(sub!=null) content.addView(tv(sub,13,muted)); }
    private LinearLayout cardBox(){ LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);l.setPadding(dp(10),dp(8),dp(10),dp(8));l.setBackgroundResource(R.drawable.bg_card); LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(0,dp(6),0,dp(6));l.setLayoutParams(p);return l; }
    private int dp(int v){ return (int)(v*getResources().getDisplayMetrics().density+.5f); }

    private ApiClient api(){ return new ApiClient(baseUrl); }
    private interface JsonOk { void done(JSONObject j); }
    private void async(Callable<JSONObject> task, JsonOk ok) { async(task,ok,e->toast("Błąd: "+shortErr(e))); }
    private void async(Callable<JSONObject> task, JsonOk ok, java.util.function.Consumer<Exception> fail) { io.submit(()->{try{JSONObject j=task.call();ui.post(()->ok.done(j));}catch(Exception e){ui.post(()->fail.accept(e));}}); }
    private String shortErr(Exception e){String s=e.getMessage();return s==null?e.getClass().getSimpleName():(s.length()>180?s.substring(0,180):s);}
    private void toast(String s){ Toast.makeText(this,s,Toast.LENGTH_LONG).show(); }

    private void showConnect(){
        scanPolling=false; clear(); title("Połącz z Dealmaker", "Laptop i telefon muszą być w tej samej sieci Wi‑Fi. Na laptopie uruchom START_LAN.bat.");
        EditText server=input("np. 192.168.1.23:8765");server.setText(baseUrl);content.addView(server);
        Button test=button("Połącz");content.addView(test);test.setOnClickListener(v->{String s=ServerValidator.normalize(server.getText().toString());if(!ServerValidator.isAllowed(s)){toast("Dla HTTP podaj lokalny adres IP laptopa, np. 192.168.1.23:8765");return;}test.setEnabled(false);async(()->new ApiClient(s).get("/api/health"),j->{test.setEnabled(true);if(j.optBoolean("ok")){baseUrl=s;prefs.edit().putString("baseUrl",s).apply();status.setText("● ONLINE  "+s);status.setTextColor(accent);flushPendingShare();showDashboard();}else toast("Backend nie jest gotowy");},e->{test.setEnabled(true);toast("Nie mogę połączyć: "+shortErr(e));});});
        Button auto=button("Znajdź automatycznie w Wi‑Fi");content.addView(auto);auto.setOnClickListener(v->{auto.setEnabled(false);auto.setText("Szukam Dealmaker...");io.submit(()->{try{String found=LocalDiscovery.find();ui.post(()->{auto.setEnabled(true);auto.setText("Znajdź automatycznie w Wi‑Fi");if(found==null)toast("Nie znaleziono. Sprawdź START_LAN.bat i Wi‑Fi.");else{server.setText(found);toast("Znaleziono: "+found);}});}catch(Exception e){ui.post(()->{auto.setEnabled(true);auto.setText("Znajdź automatycznie w Wi‑Fi");toast(shortErr(e));});}});});
        content.addView(tv("Wskazówka: adres IP laptopa znajdziesz w Windows przez ipconfig → IPv4. Aplikacja akceptuje niezabezpieczone HTTP tylko dla adresów sieci lokalnej; zdalne adresy wymagają HTTPS.",12,muted));
    }

    private void testAndShowDashboard(){ async(()->api().get("/api/health"),j->{if(j.optBoolean("ok")){status.setText("● ONLINE  "+baseUrl);status.setTextColor(accent);flushPendingShare();showDashboard();}else{status.setText("● OFFLINE");showConnect();}},e->{status.setText("● OFFLINE");status.setTextColor(bad);showConnect();}); }

    private void showDashboard(){
        scanPolling=false; clear(); title("Dealmaker Mobile", "Sterowanie lokalnym Dealmaker OS z telefonu");
        LinearLayout c=cardBox(); TextView body=tv("Ładowanie…",16,text);c.addView(body);content.addView(c);
        async(()->api().get("/api/analytics"),j->{int total=j.optInt("total");JSONObject pipe=j.optJSONObject("pipeline");int quality=pipe==null?0:pipe.optInt("quality");int contact=pipe==null?0:pipe.optInt("with_contact");int won=pipe==null?0:pipe.optInt("won");body.setText("Leady: "+total+"\nWartościowe (70+): "+quality+"\nZ kontaktem: "+contact+"\nWygrane: "+won);});
        Button scan=button("▶ Uruchom pełny skan");content.addView(scan);scan.setOnClickListener(v->startScan());
        Button web=button("Otwórz pełny panel WWW");content.addView(web);web.setOnClickListener(v->openUrl(baseUrl));
        Button refresh=button("Odśwież");content.addView(refresh);refresh.setOnClickListener(v->showDashboard());
    }

    private void showLeads(){
        scanPolling=false; clear(); title("Leady", "Szukaj po tytule, opisie, lokalizacji i URL");
        EditText q=input("np. magazyn Śląsk / catering / 259201");content.addView(q);Button search=button("Szukaj");content.addView(search);LinearLayout list=new LinearLayout(this);list.setOrientation(LinearLayout.VERTICAL);content.addView(list);
        Runnable go=()->{String query=q.getText().toString().trim();String path=query.isEmpty()?"/api/leads?limit=80":"/api/search?limit=80&q="+enc(query);search.setEnabled(false);async(()->api().get(path),j->{search.setEnabled(true);list.removeAllViews();JSONArray a=j.optJSONArray("items");if(a==null||a.length()==0){list.addView(tv("Brak wyników.",14,muted));return;}for(int i=0;i<a.length();i++){JSONObject x=a.optJSONObject(i);if(x!=null)list.addView(leadCard(x));}},e->{search.setEnabled(true);toast(shortErr(e));});}; search.setOnClickListener(v->go.run());q.setOnEditorActionListener((v,id,event)->{go.run();return true;});go.run();
    }
    private View leadCard(JSONObject x){
        LinearLayout c=cardBox();String leadTitle=x.optString("title","Oferta");double price=x.optDouble("price",Double.NaN);String cur=x.optString("currency","");String loc=x.optString("location","");String src=x.optString("source_name","");int score=x.optInt("score",0);String url=x.optString("source_url","");
        TextView t=tv(leadTitle,16,text);t.setTypeface(null,1);c.addView(t);String line=(loc.trim().isEmpty()?"":loc+" · ")+(Double.isNaN(price)?"":String.format(Locale.ROOT,"%.0f %s · ",price,cur))+"score "+score+" · "+src;c.addView(tv(line,12,score>=70?accent:muted));String desc=x.optString("description","");if(desc.length()>220)desc=desc.substring(0,220)+"…";c.addView(tv(desc,13,muted));if(!url.trim().isEmpty()){Button o=button("Otwórz źródło");o.setOnClickListener(v->openUrl(url));c.addView(o);}return c;
    }

    private void showScan(){
        clear(); title("Live Scanner", "Progres, błędy i sterowanie skanem");scanPolling=true;LinearLayout box=cardBox();TextView info=tv("Sprawdzam aktywny skan…",15,text);box.addView(info);ProgressBar pb=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);pb.setMax(1000);box.addView(pb);content.addView(box);
        Button start=button("▶ Start pełnego skanu (FAST)");Button stop=button("■ Zatrzymaj");Button resume=button("↻ Wznów");content.addView(start);content.addView(stop);content.addView(resume);start.setOnClickListener(v->startScan());stop.setOnClickListener(v->{if(activeScanId>0)async(()->api().post("/api/scans/"+activeScanId+"/stop",new JSONObject()),j->toast("Zatrzymywanie skanu"));});resume.setOnClickListener(v->{if(activeScanId>0)async(()->api().post("/api/scans/"+activeScanId+"/resume",new JSONObject()),j->{activeScanId=j.optInt("scan_id",activeScanId);toast("Wznowiono");});});
        pollActive(info,pb);
    }
    private void pollActive(TextView info,ProgressBar pb){ if(!scanPolling)return; async(()->api().get("/api/scans/active"),j->{JSONArray a=j.optJSONArray("items");if(a!=null&&a.length()>0){activeScanId=a.optJSONObject(0).optInt("id",-1);pollProgress(info,pb);}else{activeScanId=-1;info.setText("Brak aktywnego skanu.");pb.setProgress(0);ui.postDelayed(()->pollActive(info,pb),2200);}},e->{info.setText("Brak połączenia: "+shortErr(e));ui.postDelayed(()->pollActive(info,pb),3000);}); }
    private void pollProgress(TextView info,ProgressBar pb){if(!scanPolling||activeScanId<0)return;int id=activeScanId;async(()->api().get("/api/scans/"+id+"/progress?event_limit=12"),j->{JSONObject s=j.optJSONObject("scan");if(s==null)s=j;int done=s.optInt("done_sources",s.optInt("completed_sources",0));int total=s.optInt("total_sources",186);int found=s.optInt("found",s.optInt("found_count",0));int inserted=s.optInt("inserted",s.optInt("inserted_count",0));int errors=s.optInt("errors",s.optInt("error_count",0));String st=s.optString("status","running");int perm=total>0?(int)(1000.0*done/total):0;pb.setProgress(Math.max(0,Math.min(1000,perm)));info.setText("Skan #"+id+" · "+st.toUpperCase(Locale.ROOT)+"\n"+done+" / "+total+" źródeł  ·  "+String.format(Locale.ROOT,"%.1f%%",perm/10.0)+"\nZnalezione: "+found+"  ·  Nowe: "+inserted+"  ·  Błędy: "+errors);if(st.equals("running")||st.equals("stopping"))ui.postDelayed(()->pollProgress(info,pb),1300);else{activeScanId=-1;ui.postDelayed(()->pollActive(info,pb),2200);}},e->{info.setText("Błąd progresu: "+shortErr(e));ui.postDelayed(()->pollActive(info,pb),2500);}); }
    private void startScan(){JSONObject b=new JSONObject();try{b.put("source_ids",new JSONArray());b.put("power_profile","fast");b.put("limit_per_source",20);b.put("verify_per_source",20);b.put("only_new",true);}catch(Exception ignored){}async(()->api().post("/api/scans",b),j->{activeScanId=j.optInt("scan_id",-1);toast("Skan #"+activeScanId+" uruchomiony");showScan();});}

    private void showCapture(){
        scanPolling=false; clear(); title("Szybkie dodanie", "Zapisz link lub notatkę bez utraty źródła");EditText url=input("URL (opcjonalnie)");EditText ttl=input("Tytuł (opcjonalnie)");EditText note=input("Notatka / opis");note.setSingleLine(false);note.setMinLines(5);note.setGravity(Gravity.TOP);content.addView(url);content.addView(ttl);content.addView(note);Button add=button("Zapisz w Dealmaker");content.addView(add);add.setOnClickListener(v->{JSONObject j=new JSONObject();try{if(!url.getText().toString().trim().isEmpty())j.put("url",url.getText().toString().trim());if(!ttl.getText().toString().trim().isEmpty())j.put("title",ttl.getText().toString().trim());j.put("note",note.getText().toString().trim());}catch(Exception ignored){}add.setEnabled(false);async(()->api().post("/api/mobile/capture",j),r->{add.setEnabled(true);toast("Zapisano lead #"+r.optInt("id"));url.setText("");ttl.setText("");note.setText("");},e->{add.setEnabled(true);toast(shortErr(e));});});
    }

    private void handleShare(Intent intent){if(intent==null||!Intent.ACTION_SEND.equals(intent.getAction())||!"text/plain".equals(intent.getType()))return;String shared=intent.getStringExtra(Intent.EXTRA_TEXT);if(shared==null||shared.trim().isEmpty())return;if(baseUrl.trim().isEmpty()){prefs.edit().putString("pendingShare",shared).apply();toast("Zapiszę po połączeniu z laptopem");}else sendShared(shared);}
    private void flushPendingShare(){String s=prefs.getString("pendingShare","");if(!s.trim().isEmpty()){prefs.edit().remove("pendingShare").apply();sendShared(s);}}
    private void sendShared(String s){String url=firstUrl(s);String note=s;if(url!=null)note=s.replace(url,"").trim();JSONObject j=new JSONObject();try{if(url!=null)j.put("url",url);j.put("title",url!=null?"Link udostępniony z telefonu":"Notatka udostępniona z telefonu");j.put("note",note);}catch(Exception ignored){}async(()->api().post("/api/mobile/capture",j),r->toast("Dodano do Dealmaker (#"+r.optInt("id")+")"),e->{prefs.edit().putString("pendingShare",s).apply();toast("Nie wysłano — zachowano do ponowienia");});}
    private String firstUrl(String s){Matcher m=Pattern.compile("https?://[^\\s]+",Pattern.CASE_INSENSITIVE).matcher(s);return m.find()?m.group():null;}
    private String enc(String s){ try{return URLEncoder.encode(s, "UTF-8");}catch(Exception e){return s;} }
    private void openUrl(String u){try{startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(u)));}catch(Exception e){toast("Nie mogę otworzyć linku");}}
}
