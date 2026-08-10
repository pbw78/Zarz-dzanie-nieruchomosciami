package pl.dealmaker.mobile;

import android.app.job.*;
import android.content.*;

public final class MobileJobs {
    private static final int JOB_ID = 876503;
    private MobileJobs() {}

    /**
     * Background scanning is optional. A scheduling failure must never prevent
     * the app from opening, especially on vendor-modified Android builds.
     */
    public static boolean schedule(Context context) {
        try {
            JobScheduler js = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
            if (js == null) return false;
            ComponentName service = new ComponentName(context, MobileScanJobService.class);
            JobInfo job = new JobInfo.Builder(JOB_ID, service)
                    .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                    .setPeriodic(6L * 60L * 60L * 1000L, 60L * 60L * 1000L)
                    .setPersisted(false)
                    .build();
            return js.schedule(job) == JobScheduler.RESULT_SUCCESS;
        } catch (RuntimeException | LinkageError ignored) {
            return false;
        }
    }
}
