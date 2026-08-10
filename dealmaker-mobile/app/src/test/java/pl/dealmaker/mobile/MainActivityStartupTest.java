package pl.dealmaker.mobile;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import androidx.test.core.app.ApplicationProvider;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class MainActivityStartupTest {
    @Test public void freshMainActivityStartsAndStaysAlive() {
        Context context = ApplicationProvider.getApplicationContext();
        context.getSharedPreferences("dealmaker_mobile", Context.MODE_PRIVATE).edit().clear().commit();
        context.deleteDatabase("dealmaker_mobile.db");
        MainActivity activity = Robolectric.buildActivity(MainActivity.class).setup().get();
        assertNotNull(activity);
        assertFalse(activity.isFinishing());
        assertNotNull(activity.getWindow().getDecorView());
    }

    @Test public void rememberedUnavailableLaptopDoesNotCrashStartup() {
        Context context = ApplicationProvider.getApplicationContext();
        SharedPreferences prefs = context.getSharedPreferences("dealmaker_mobile", Context.MODE_PRIVATE);
        prefs.edit().clear().putString("baseUrl", "http://192.168.87.138:8765").commit();
        MainActivity activity = Robolectric.buildActivity(MainActivity.class).setup().get();
        assertNotNull(activity);
        assertFalse(activity.isFinishing());
    }
}
