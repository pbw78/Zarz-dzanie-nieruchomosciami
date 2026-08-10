package pl.dealmaker.mobile;

import android.content.Context;
import org.json.*;

public final class MobileSync {
    public static final class Result { public int pulled, pushed, errors; public String lastError=""; }
    private MobileSync() {}

    public static Result sync(Context context, String baseUrl) {
        Result r = new Result();
        if (baseUrl == null || baseUrl.trim().isEmpty() || !ServerValidator.isAllowed(baseUrl)) return r;
        MobileDb db = new MobileDb(context.getApplicationContext());
        ApiClient api = new ApiClient(baseUrl);
        try {
            int offset = 0;
            final int pageSize = 1000;
            while (true) {
                JSONObject response = api.get("/api/leads?limit="+pageSize+"&offset="+offset);
                JSONArray items = response.optJSONArray("items");
                if (items == null || items.length() == 0) break;
                r.pulled += db.importServerItems(items);
                offset += items.length();
                int total = response.optInt("total", offset);
                if (items.length() < pageSize || offset >= total) break;
                if (offset >= 20000) break;
            }
        } catch (Exception e) { r.errors++; r.lastError = String.valueOf(e.getMessage()); }

        try {
            JSONArray pending = db.unsynced(250);
            for (int i=0;i<pending.length();i++) {
                JSONObject x = pending.optJSONObject(i); if (x==null) continue;
                try {
                    JSONObject body = new JSONObject();
                    String url=x.optString("source_url",""); if(!url.isEmpty()) body.put("url",url);
                    body.put("title",x.optString("title","Oferta z telefonu"));
                    String note=x.optString("description","");
                    String date=x.optString("published_at",""); if(!date.isEmpty()) note += "\nData: "+date;
                    if(x.has("price")) note += "\nCena: "+x.optDouble("price")+" "+x.optString("currency","");
                    String loc=x.optString("location",""); if(!loc.isEmpty()) body.put("location",loc);
                    body.put("note",note.trim());
                    api.post("/api/mobile/capture",body);
                    db.markSynced(x.optLong("local_id")); r.pushed++;
                } catch(Exception one) { r.errors++; r.lastError=String.valueOf(one.getMessage()); }
            }
        } catch(Exception e) { r.errors++; r.lastError=String.valueOf(e.getMessage()); }
        db.close(); return r;
    }
}
