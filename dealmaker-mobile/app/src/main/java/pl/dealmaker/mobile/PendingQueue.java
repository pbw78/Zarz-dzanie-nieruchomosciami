package pl.dealmaker.mobile;

import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

public final class PendingQueue {
    private static final String KEY = "pendingQueueV2";
    private static final int MAX = 100;
    private PendingQueue() {}

    public static synchronized void enqueue(SharedPreferences prefs, JSONObject item) {
        JSONArray arr = readArray(prefs);
        JSONArray out = new JSONArray();
        int start = Math.max(0, arr.length() - (MAX - 1));
        for (int i = start; i < arr.length(); i++) out.put(arr.opt(i));
        out.put(item);
        prefs.edit().putString(KEY, out.toString()).apply();
    }

    public static synchronized List<JSONObject> all(SharedPreferences prefs) {
        JSONArray arr = readArray(prefs);
        ArrayList<JSONObject> out = new ArrayList<>();
        for (int i = 0; i < arr.length(); i++) {
            JSONObject j = arr.optJSONObject(i);
            if (j != null) out.add(j);
        }
        return out;
    }

    public static synchronized int size(SharedPreferences prefs) { return readArray(prefs).length(); }

    public static synchronized void replace(SharedPreferences prefs, List<JSONObject> items) {
        JSONArray arr = new JSONArray();
        for (JSONObject j : items) arr.put(j);
        prefs.edit().putString(KEY, arr.toString()).apply();
    }

    private static JSONArray readArray(SharedPreferences prefs) {
        try { return new JSONArray(prefs.getString(KEY, "[]")); }
        catch (Exception e) { return new JSONArray(); }
    }
}
