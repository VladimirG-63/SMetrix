package com.smetrix.app;

import android.app.Application;

import androidx.work.WorkManager;

import com.smetrix.app.network.sync.SyncManager;


public class SMetrixApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        new SyncManager(WorkManager.getInstance(this)).schedulePeriodicSync();
    }
}
