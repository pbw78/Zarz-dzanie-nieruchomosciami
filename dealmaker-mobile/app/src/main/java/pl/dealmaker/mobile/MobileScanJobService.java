package pl.dealmaker.mobile;

import android.app.job.*;
import android.content.SharedPreferences;
import java.util.concurrent.*;

public class MobileScanJobService extends JobService {
    private final ExecutorService pool = Executors.newSingleThreadExecutor();
    @Override public boolean onStartJob(JobParameters params) {
        pool.submit(() -> {
            try {
                BiznesOfertyScanner.scan(getApplicationContext(), 1, null);
                SharedPreferences p=getSharedPreferences("dealmaker_mobile",MODE_PRIVATE);
                String base=p.getString("baseUrl","");
                if(!base.isEmpty()) MobileSync.sync(getApplicationContext(),base);
                p.edit().putLong("lastMobileScan",System.currentTimeMillis()).apply();
            } finally { jobFinished(params,false); }
        });
        return true;
    }
    @Override public boolean onStopJob(JobParameters params) { return true; }
    @Override public void onDestroy(){ pool.shutdownNow(); super.onDestroy(); }
}
