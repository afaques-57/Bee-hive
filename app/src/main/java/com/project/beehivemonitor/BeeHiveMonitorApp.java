package com.project.beehivemonitor;

import android.app.Application;

public class BeeHiveMonitorApp extends Application {
    public static Application application;

    @Override
    public void onCreate() {
        super.onCreate();
        application = this;
    }
}
