package pl.dealmaker.mobile;

import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.io.PrintWriter;
import java.io.StringWriter;

/**
 * Launcher safety boundary. MainActivity remains the actual application UI,
 * while this class prevents a vendor/runtime-specific startup exception from
 * turning into a silent close. The exception is still logged and rendered so
 * it can be fixed rather than hidden.
 */
public final class SafeMainActivity extends MainActivity {
    private static final String TAG = "DealmakerStartup";

    @Override public void onCreate(Bundle state) {
        try {
            super.onCreate(state);
        } catch (Throwable error) {
            Log.e(TAG, "Startup failed", error);
            showRecovery(error);
        }
    }

    private void showRecovery(Throwable error) {
        try {
            ScrollView scroll = new ScrollView(this);
            LinearLayout box = new LinearLayout(this);
            box.setOrientation(LinearLayout.VERTICAL);
            box.setPadding(36, 60, 36, 60);
            box.setBackgroundColor(Color.rgb(10, 14, 19));

            TextView title = new TextView(this);
            title.setText("Dealmaker — błąd startu");
            title.setTextSize(25);
            title.setTextColor(Color.WHITE);
            box.addView(title);

            TextView help = new TextView(this);
            help.setText("Aplikacja przechwyciła błąd zamiast się zamknąć. Zrób zrzut tego ekranu — zawiera dokładną przyczynę.");
            help.setTextSize(15);
            help.setTextColor(Color.rgb(155, 167, 183));
            help.setPadding(0, 24, 0, 24);
            box.addView(help);

            StringWriter sw = new StringWriter();
            error.printStackTrace(new PrintWriter(sw));
            TextView trace = new TextView(this);
            trace.setText(sw.toString());
            trace.setTextSize(12);
            trace.setTextColor(Color.rgb(255, 130, 130));
            trace.setTextIsSelectable(true);
            box.addView(trace);

            scroll.addView(box);
            setContentView(scroll);
        } catch (Throwable ignored) {
            // AndroidRuntime still receives the original exception in logcat.
        }
    }
}
