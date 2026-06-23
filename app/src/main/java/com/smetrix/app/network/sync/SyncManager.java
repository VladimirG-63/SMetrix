
package com.smetrix.app.network.sync;

import androidx.work.BackoffPolicy;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import java.util.concurrent.TimeUnit;





































public class SyncManager {






    private static final String WORK_NAME_ONE_TIME = "SYNC_ONE_TIME";





    private static final String WORK_NAME_PERIODIC = "SYNC_PERIODIC";






    private static final String TAG_ONE_TIME = "SYNC_ONE_TIME";




    private static final String TAG_PERIODIC = "SYNC_PERIODIC";






    private static final long BACKOFF_DELAY_SECONDS = 30L;





    private static final long PERIODIC_INTERVAL_MINUTES = 15L;






    private final WorkManager workManager;

















    public SyncManager(WorkManager workManager) {
        if (workManager == null) {
            throw new IllegalArgumentException("SyncManager: workManager не может быть null.");
        }
        this.workManager = workManager;
    }




















    public void scheduleSync() {

        Constraints constraints = buildNetworkConstraints();


        OneTimeWorkRequest syncRequest = new OneTimeWorkRequest.Builder(SyncWorker.class)
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_DELAY_SECONDS, TimeUnit.SECONDS)
                .addTag(TAG_ONE_TIME)
                .build();


        workManager.enqueueUniqueWork(
                WORK_NAME_ONE_TIME,
                ExistingWorkPolicy.KEEP,
                syncRequest
        );
    }



















    public void scheduleSyncForce() {

        Constraints constraints = buildNetworkConstraints();


        OneTimeWorkRequest syncRequest = new OneTimeWorkRequest.Builder(SyncWorker.class)
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_DELAY_SECONDS, TimeUnit.SECONDS)
                .addTag(TAG_ONE_TIME)
                .build();


        workManager.enqueueUniqueWork(
                WORK_NAME_ONE_TIME,
                ExistingWorkPolicy.REPLACE,
                syncRequest
        );
    }























    public void schedulePeriodicSync() {

        Constraints constraints = buildNetworkConstraints();


        PeriodicWorkRequest periodicRequest =
                new PeriodicWorkRequest.Builder(SyncWorker.class, PERIODIC_INTERVAL_MINUTES, TimeUnit.MINUTES)
                        .setConstraints(constraints)
                        .addTag(TAG_PERIODIC)
                        .build();


        workManager.enqueueUniquePeriodicWork(
                WORK_NAME_PERIODIC,
                ExistingPeriodicWorkPolicy.KEEP,
                periodicRequest
        );
    }































    private Constraints buildNetworkConstraints() {
        return new Constraints.Builder().build();
    }
}
