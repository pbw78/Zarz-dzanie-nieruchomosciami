package pl.dealmaker.mobile;

import android.content.Context;
import org.json.*;
import java.util.Locale;

public final class MobileSync {
    public static final class Result { public int pulled, pushed, errors; public String lastError=""; }
    private MobileSync() {}

    public static Result sync(Context context, String baseUrl) {
        Result r = new Result();
        if (baseUrl == null || baseUrl.trim().isEmpty() || !ServerValidator.isAllowed(baseUrl)) return r;
        MobileDb db = new MobileDb(context.getApplicationContext());
        ApiClient api = new ApiClient(baseUrl);
        try {
            int offset = 0; final int pageSize = 1000;
            while (true) {
                JSONObject response = api.get("/api/leads?limit="+pageSize+"&offset="+offset);
                JSONArray items = response.optJSONArray("items");
                if (items == null || items.length() == 0) break;
                r.pulled += db.importServerItems(items); offset += items.length();
                int total = response.optInt("total", offset);
                if (items.length() < pageSize || offset >= total || offset >= 20000) break;
            }
        } catch (Exception e) { r.errors++; r.lastError = String.valueOf(e.getMessage()); }

        try {
            JSONArray pending = db.unsynced(250);
            for (int i=0;i<pending.length();i++) {
                JSONObject x = pending.optJSONObject(i); if (x==null) continue;
                try {
                    String origin=x.optString("origin",""); String url=x.optString("source_url","");
                    if ("mobile_scan".equals(origin) && !url.isEmpty()) {
                        JSONObject lead=new JSONObject();
                        String sourceName=x.optString("source_name","Mobile Scanner");
                        lead.put("source_id",sourceId(sourceName)); lead.put("source_name",sourceName);
                        lead.put("source_url",url); lead.put("evidence_url",url); lead.put("title",x.optString("title","Oferta"));
                        lead.put("description",x.optString("description","")); lead.put("category","business_sale");
                        String date=x.optString("published_at",""); if(!date.isEmpty())lead.put("published_at",date);
                        String loc=x.optString("location",""); if(!loc.isEmpty())lead.put("location",loc);
                        if(x.has("price"))lead.put("price",x.optDouble("price")); String cur=x.optString("currency","");if(!cur.isEmpty())lead.put("currency",cur);
                        JSONObject raw=new JSONObject();raw.put("origin","dealmaker_mobile");raw.put("verification_status","source_listing");raw.put("mobile_source",sourceName);lead.put("raw",raw);
                        api.post("/api/leads",lead);
                    } else {
                        JSONObject body = new JSONObject(); if(!url.isEmpty()) body.put("url",url);
                        body.put("title",x.optString("title","Oferta z telefonu")); body.put("note",x.optString("description",""));
                        String loc=x.optString("location",""); if(!loc.isEmpty()) body.put("location",loc);
                        api.post("/api/mobile/capture",body);
                    }
                    db.markSynced(x.optLong("local_id")); r.pushed++;
                } catch(Exception one) { r.errors++; r.lastError=String.valueOf(one.getMessage()); }
            }
        } catch(Exception e) { r.errors++; r.lastError=String.valueOf(e.getMessage()); }
        db.close(); return r;
    }

    static String sourceId(String sourceName){
        String s=sourceName==null?"mobile":sourceName.toLowerCase(Locale.ROOT);
        if(s.contains("biznesoferty"))return "mobile_biznesoferty";
        if(s.contains("sprzedambiznes"))return "mobile_sprzedambiznes";
        if(s.contains("businessesforsale"))return "mobile_businessesforsale";
        if(s.contains("business-in-poland"))return "mobile_business_in_poland";
        if(s.contains("olx"))return "mobile_olx_business";
        return "mobile_scanner";
    }
}
