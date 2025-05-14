package com.project.beehivemonitor.viewmodel;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.project.beehivemonitor.BeeHiveMonitorApp;
import com.project.beehivemonitor.model.ScannedDevice;
import com.project.beehivemonitor.util.BluetoothOperations;
import com.project.beehivemonitor.util.ConnectionState;
import com.project.beehivemonitor.util.Event;

public class ConnectionViewModel extends ViewModel {

    private final MutableLiveData<Event<ScannedDevice>> scannedDeviceLiveData = new MutableLiveData<>();
    private final MutableLiveData<Event<Boolean>> bluetoothStateLiveData = new MutableLiveData<>();
    private final MutableLiveData<Event<ConnectionState>> connectionStateLiveData = new MutableLiveData<>();

    private final BluetoothOperations.ConnectionCallback connectionCallback = new BluetoothOperations.ConnectionCallback() {
        @Override
        public void onBluetoothStateChanged(boolean isOn) {
            bluetoothStateLiveData.postValue(new Event<>(isOn));
        }

        @Override
        public void onScannedDeviceFound(ScannedDevice scannedDevice) {
            scannedDeviceLiveData.postValue(new Event<>(scannedDevice));
        }

        @Override
        public void onDeviceScanFailed() {
            scannedDeviceLiveData.postValue(new Event<>(null));
        }

        @Override
        public void onConnectionStateChanged(ConnectionState connectionState) {
            connectionStateLiveData.postValue(new Event<>(connectionState));
        }
    };

    public ConnectionViewModel() {
        BluetoothOperations.addConnectionCallback(connectionCallback);
    }

    public LiveData<Event<ScannedDevice>> getScannedDeviceLiveData() {
        return scannedDeviceLiveData;
    }

    public LiveData<Event<Boolean>> getBluetoothStateLiveData() {
        return bluetoothStateLiveData;
    }

    public LiveData<Event<ConnectionState>> getConnectionStateLiveData() {
        return connectionStateLiveData;
    }

    public void connect(String name, String macAddress) {
        BluetoothOperations.connectDevice(BeeHiveMonitorApp.application, macAddress);
    }

    public void disconnect() {
        BluetoothOperations.disconnectDevice();
    }

    public void startScan() {
        BluetoothOperations.startScan();
    }

    public void stopScan() {
        BluetoothOperations.stopScan();
    }

    public interface ConnectionCallback {
        void onSuccess();

        void onError();
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        BluetoothOperations.removeConnectionCallback(connectionCallback);
    }
}
