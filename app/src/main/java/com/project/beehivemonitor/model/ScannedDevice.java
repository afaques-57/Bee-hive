package com.project.beehivemonitor.model;

import androidx.annotation.NonNull;

import java.io.Serializable;

public class ScannedDevice implements Serializable {
    private final String name;
    private final String macAddress;

    public ScannedDevice(String name, String macAddress) {
        this.name = name;
        this.macAddress = macAddress;
    }

    public String getName() {
        return name;
    }

    public String getMacAddress() {
        return macAddress;
    }

    @NonNull
    @Override
    public String toString() {
        return "ScannedDevice{" + "name='" + name + '\'' + ", macAddress='" + macAddress + '\'' + '}';
    }
}
