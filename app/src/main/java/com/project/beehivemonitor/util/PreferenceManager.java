package com.project.beehivemonitor.util;

import android.content.Context;
import android.content.SharedPreferences;

import com.google.gson.Gson;
import com.project.beehivemonitor.BeeHiveMonitorApp;
import com.project.beehivemonitor.model.ScannedDevice;

public class PreferenceManager {

    private static final String SELECTED_DEVICE = "selected_device";

    private static PreferenceManager instance;
    private final SharedPreferences prefs;
    private final Gson gson = new Gson();

    private PreferenceManager(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences("app_prefs", Context.MODE_PRIVATE);
    }

    public static synchronized PreferenceManager getInstance() {
        if (instance == null) {
            instance = new PreferenceManager(BeeHiveMonitorApp.application);
        }
        return instance;
    }

    public ScannedDevice getSelectedDevice() {
        return gson.fromJson(prefs.getString(SELECTED_DEVICE, null), ScannedDevice.class);
    }

    public void setSelectedDevice(ScannedDevice scannedDevice) {
        if(scannedDevice == null) {
            prefs.edit().remove(SELECTED_DEVICE).apply();
        } else {
            prefs.edit().putString(SELECTED_DEVICE, gson.toJson(scannedDevice)).apply();
        }
    }

    public void clearAll() {
        prefs.edit().clear().apply();
    }
}
