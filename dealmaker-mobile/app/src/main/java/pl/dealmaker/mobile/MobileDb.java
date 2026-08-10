package pl.dealmaker.mobile;

import android.content.*;
import android.database.Cursor;
import android.database.sqlite.*;
import org.json.*;
import java.util.*;

public final class MobileDb extends SQLiteOpenHelper {
    private static final String DB_NAME = "dealmaker_mobile.db";
    private static final int DB_VERSION = 1;

    public MobileDb(Context c) { super(c, DB_NAME, null, DB_VERSION); }

    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE leads (id INTEGER PRIMARY KEY AUTOINCREMENT, server_id INTEGER, source_url TEXT UNIQUE, title TEXT NOT NULL, description TEXT, location TEXT, price REAL, currency TEXT, source_name TEXT, published_at TEXT, score INTEGER DEFAULT 0, verification_status TEXT, origin TEXT, sync_state INTEGER DEFAULT 0, updated_at INTEGER NOT NULL)");
        db.execSQL("CREATE INDEX idx_leads_updated ON leads(updated_at DESC)");
        db.execSQL("CREATE INDEX idx_leads_source ON leads(source_name)");
    }

    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {}

    public synchronized long upsert(String sourceUrl, String title, String description, String location, Double price,
                                    String currency, String sourceName, String publishedAt, int score,
                                    String verificationStatus, String origin, long serverId, boolean synced) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues v = new ContentValues();
        if (serverId > 0) v.put("server_id", serverId);
        v.put("source_url", emptyToNull(sourceUrl));
        v.put("title", safe(title, "Oferta"));
        v.put("description", safe(description, ""));
        v.put("location", safe(location, ""));
        if (price != null) v.put("price", price); else v.putNull("price");
        v.put("currency", safe(currency, ""));
        v.put("source_name", safe(sourceName, ""));
        v.put("published_at", safe(publishedAt, ""));
        v.put("score", score);
        v.put("verification_status", safe(verificationStatus, "source_listing"));
        v.put("origin", safe(origin, "local"));
        v.put("sync_state", synced ? 1 : 0);
        v.put("updated_at", System.currentTimeMillis());

        String where = sourceUrl != null && !sourceUrl.trim().isEmpty() ? "source_url=?" : (serverId > 0 ? "server_id=?" : null);
        String[] args = sourceUrl != null && !sourceUrl.trim().isEmpty() ? new String[]{sourceUrl} : (serverId > 0 ? new String[]{String.valueOf(serverId)} : null);
        if (where != null) {
            Cursor c = db.query("leads", new String[]{"id"}, where, args, null, null, null, "1");
            try {
                if (c.moveToFirst()) {
                    long id = c.getLong(0);
                    db.update("leads", v, "id=?", new String[]{String.valueOf(id)});
                    return id;
                }
            } finally { c.close(); }
        }
        return db.insertOrThrow("leads", null, v);
    }

    public synchronized int importServerItems(JSONArray items) {
        if (items == null) return 0;
        int n = 0;
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            for (int i = 0; i < items.length(); i++) {
                JSONObject x = items.optJSONObject(i); if (x == null) continue;
                String url = x.optString("source_url", "");
                String title = x.optString("title", "Oferta");
                String desc = x.optString("description", "");
                String loc = x.optString("location", "");
                Double price = x.isNull("price") ? null : x.optDouble("price");
                long sid = x.optLong("id", 0);
                upsert(url, title, desc, loc, price, x.optString("currency", ""), x.optString("source_name", ""),
                        x.optString("published_at", x.optString("created_at", "")), x.optInt("score", 0),
                        x.optString("verification_status", "verified"), "server", sid, true);
                n++;
            }
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
        return n;
    }

    public synchronized JSONArray search(String q, int limit) {
        JSONArray out = new JSONArray();
        SQLiteDatabase db = getReadableDatabase();
        String sel = null; String[] args = null;
        if (q != null && !q.trim().isEmpty()) {
            String like = "%" + q.trim() + "%";
            sel = "title LIKE ? OR description LIKE ? OR location LIKE ? OR source_url LIKE ?";
            args = new String[]{like, like, like, like};
        }
        Cursor c = db.query("leads", null, sel, args, null, null, "updated_at DESC", String.valueOf(Math.max(1, Math.min(limit, 500))));
        try {
            while (c.moveToNext()) out.put(row(c));
        } finally { c.close(); }
        return out;
    }

    public synchronized JSONArray unsynced(int limit) {
        JSONArray out = new JSONArray();
        Cursor c = getReadableDatabase().query("leads", null, "sync_state=0", null, null, null, "updated_at ASC", String.valueOf(limit));
        try { while (c.moveToNext()) out.put(row(c)); } finally { c.close(); }
        return out;
    }

    public synchronized void markSynced(long id) {
        ContentValues v = new ContentValues(); v.put("sync_state", 1);
        getWritableDatabase().update("leads", v, "id=?", new String[]{String.valueOf(id)});
    }

    public synchronized int count() {
        Cursor c = getReadableDatabase().rawQuery("SELECT COUNT(*) FROM leads", null);
        try { return c.moveToFirst() ? c.getInt(0) : 0; } finally { c.close(); }
    }

    public synchronized int countUnsynced() {
        Cursor c = getReadableDatabase().rawQuery("SELECT COUNT(*) FROM leads WHERE sync_state=0", null);
        try { return c.moveToFirst() ? c.getInt(0) : 0; } finally { c.close(); }
    }

    private JSONObject row(Cursor c) {
        JSONObject j = new JSONObject();
        try {
            j.put("local_id", c.getLong(c.getColumnIndexOrThrow("id")));
            j.put("id", c.isNull(c.getColumnIndexOrThrow("server_id")) ? 0 : c.getLong(c.getColumnIndexOrThrow("server_id")));
            put(j,"source_url",str(c,"source_url")); put(j,"title",str(c,"title")); put(j,"description",str(c,"description"));
            put(j,"location",str(c,"location"));
            int pi = c.getColumnIndexOrThrow("price"); if (!c.isNull(pi)) j.put("price", c.getDouble(pi));
            put(j,"currency",str(c,"currency")); put(j,"source_name",str(c,"source_name")); put(j,"published_at",str(c,"published_at"));
            j.put("score", c.getInt(c.getColumnIndexOrThrow("score"))); put(j,"verification_status",str(c,"verification_status"));
            put(j,"origin",str(c,"origin")); j.put("sync_state", c.getInt(c.getColumnIndexOrThrow("sync_state")));
        } catch (JSONException ignored) {}
        return j;
    }

    private String str(Cursor c, String col) { int i=c.getColumnIndexOrThrow(col); return c.isNull(i)?"":c.getString(i); }
    private static void put(JSONObject j,String k,String v) throws JSONException { j.put(k,v==null?"":v); }
    private static String safe(String s,String d){ return s==null?d:s; }
    private static String emptyToNull(String s){ return s==null||s.trim().isEmpty()?null:s.trim(); }
}
