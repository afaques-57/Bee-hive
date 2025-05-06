package com.project.beehive.viewmodel;

import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.project.beehive.model.ScannedDevice;

public class ConnectionViewModel extends ViewModel {

    private final MutableLiveData<ScannedDevice> scannedDeviceLiveData = new MutableLiveData<>();

    public void connect(ConnectionCallback connectionCallback) {
        connectionCallback.onSuccess();
    }

    public void startScan() {

    }

    public void stopScan() {

    }

    public interface ConnectionCallback {
        void onSuccess();

        void onError();
    }
}
