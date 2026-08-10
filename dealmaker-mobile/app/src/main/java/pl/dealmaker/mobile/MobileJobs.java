package pl.dealmaker.mobile;

import android.app.job.*;
import android.content.*;

public final class MobileJobs {
    private static final int JOB_ID = 876503;
    private MobileJobs() {}
    public static void schedule(Context context) {
        JobScheduler js=(JobScheduler)context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if(js==null)return;
        ComponentName service=new ComponentName(context,MobileScanJobService.class);
        JobInfo job=new JobInfo.Builder(JOB_ID,service)
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setPeriodic(6L*60L*60L*1000L, 60L*60L*1000L)
                .setPersisted(true)
                .build();
        js.schedule(job);
    }
}
