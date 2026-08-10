package pl.dealmaker.mobile;

import android.content.Context;
import android.content.SharedPreferences;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class MainActivityStartupTest {
    @Test public void freshMainActivityStartsAndStaysAlive() {
        Context context = RuntimeEnvironment.getApplication();
        context.getSharedPreferences("dealmaker_mobile", Context.MODE_PRIVATE).edit().clear().commit();
        context.deleteDatabase("dealmaker_mobile.db");
        ActivityController<MainActivity> controller = Robolectric.buildActivity(MainActivity.class).setup();
        try {
            MainActivity activity = controller.get();
            assertNotNull(activity);
            assertFalse(activity.isFinishing());
            assertNotNull(activity.getWindow().getDecorView());
        } finally {
            controller.pause().stop().destroy();
        }
    }

    @Test public void rememberedUnavailableLaptopDoesNotCrashStartup() {
        Context context = RuntimeEnvironment.getApplication();
        SharedPreferences prefs = context.getSharedPreferences("dealmaker_mobile", Context.MODE_PRIVATE);
        prefs.edit().clear().putString("baseUrl", "http://192.168.87.138:8765").commit();
        ActivityController<MainActivity> controller = Robolectric.buildActivity(MainActivity.class).setup();
        try {
            MainActivity activity = controller.get();
            assertNotNull(activity);
            assertFalse(activity.isFinishing());
            assertNotNull(activity.getWindow().getDecorView());
        } finally {
            controller.pause().stop().destroy();
        }
    }
}
