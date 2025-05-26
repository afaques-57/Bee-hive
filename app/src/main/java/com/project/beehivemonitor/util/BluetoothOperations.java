package com.project.beehivemonitor.util;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.BluetoothStatusCodes;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import com.project.beehivemonitor.BeeHiveMonitorApp;
import com.project.beehivemonitor.model.ScannedDevice;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public final class BluetoothOperations {

    private static final UUID CCC_DESCRIPTOR_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

    private static final int GATT_MIN_MTU_SIZE = 23;
    private static final int GATT_MAX_MTU_SIZE = 517;

    private static final Handler handler = new Handler(Looper.getMainLooper());

    private static final Set<ConnectionCallback> connectionCallbacks = new HashSet<>();
    private static final Map<String, List<DataCallback>> dataCallbacksMap = new HashMap<>();

    private static final Queue<Runnable> pendingWriteDescriptors = new ConcurrentLinkedQueue<>();
    private static final AtomicBoolean isWriteDescriptorInProgress = new AtomicBoolean(false);

    private static BluetoothLeScanner bluetoothLeScanner;
    volatile private static BluetoothDevice _bluetoothDevice;
    volatile private static BluetoothGatt bluetoothGatt;

    volatile private static ConnectionState _deviceState = ConnectionState.DISCONNECTED;

    private static final BroadcastReceiver adapterStateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (Objects.equals(intent.getAction(), BluetoothAdapter.ACTION_STATE_CHANGED)) {
                int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR);
                switch (state) {
                    case BluetoothAdapter.STATE_ON: {
                        sendConnectionCallback(connectionCallback -> connectionCallback.onBluetoothStateChanged(true));
                        break;
                    }
                    case BluetoothAdapter.STATE_OFF: {
                        stopScan();
                        setDeviceConnectionState(ConnectionState.DISCONNECTED);
                        sendConnectionCallback(connectionCallback -> connectionCallback.onBluetoothStateChanged(false));
                        break;
                    }
                }
            }
        }
    };

    @SuppressLint("MissingPermission")
    private static final ScanCallback scanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            super.onScanResult(callbackType, result);
            sendConnectionCallback(connectionCallback -> {
                try {
                    String deviceName = result.getDevice().getName();
                    deviceName = deviceName != null ? deviceName : "";
                    connectionCallback.onScannedDeviceFound(new ScannedDevice(deviceName, result.getDevice().getAddress()));
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
        }

        @Override
        public void onScanFailed(int errorCode) {
            sendConnectionCallback(ConnectionCallback::onDeviceScanFailed);
        }
    };

    private static final ScanSettings scanSettings = new ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
            .setMatchMode(ScanSettings.MATCH_MODE_AGGRESSIVE)
            .setNumOfMatches(ScanSettings.MATCH_NUM_ONE_ADVERTISEMENT)
            .setReportDelay(0L)
            .build();

    static {
        BeeHiveMonitorApp.application.registerReceiver(adapterStateReceiver, new IntentFilter((BluetoothAdapter.ACTION_STATE_CHANGED)));
    }

    public static boolean isDeviceConnected() {
        return _deviceState == ConnectionState.CONNECTED;
    }

    public static void connectDevice(Context context, String macAddress) {
        Logger.info("connectDevice called for " + macAddress);
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            Logger.error("permission not granted");
            return;
        }
        BluetoothDevice bluetoothDevice = getBluetoothDevice(macAddress);
        if (!isDeviceConnected()) {
            setDeviceConnectionState(ConnectionState.CONNECTING);
            handler.post(() -> bluetoothGatt = bluetoothDevice.connectGatt(context, false, bluetoothGattCallback, BluetoothDevice.TRANSPORT_LE));
        } else {
            setDeviceConnectionState(ConnectionState.CONNECTED);
            Logger.info("connectDevice called - device already connected");
        }
    }

    public static void disconnectDevice() {
        Logger.info("disconnectDevice called");
        BluetoothGatt bluetoothGatt = BluetoothOperations.bluetoothGatt;
        if (bluetoothGatt != null) {
            handler.post(bluetoothGatt::disconnect);
        } else {
            Logger.info("disconnectDevice called - BluetoothGatt not found");
        }
        handler.postDelayed(BluetoothOperations::completeDisconnect, 50);
    }

    public static boolean startScan() {
        try {
            bluetoothLeScanner = getBluetoothAdapter().getBluetoothLeScanner();
            if (ContextCompat.checkSelfPermission(BeeHiveMonitorApp.application, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
            bluetoothLeScanner.startScan(null, scanSettings, scanCallback);
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public static void stopScan() {
        try {
            if (ContextCompat.checkSelfPermission(BeeHiveMonitorApp.application, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED) {
                bluetoothLeScanner.stopScan(scanCallback);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static boolean requestTurnOnBluetooth(Activity activity) {
        if (!isBluetoothEnabled()) {
            if (activity.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED || activity.checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            activity.startActivityForResult(enableBtIntent, 1212);
        }
        return true;
    }

    public static boolean isBluetoothEnabled() {
        return getBluetoothAdapter().isEnabled();
    }

    @SuppressLint("MissingPermission")
    private static final BluetoothGattCallback bluetoothGattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            String macAddress = gatt.getDevice().getAddress();
            Logger.info("bluetoothGattCallback onConnectionStateChange - macAddress " + macAddress + ", status: " + status + ", newState: " + newState);
            BluetoothDevice bluetoothDevice = _bluetoothDevice;
            if (bluetoothDevice == null || !Objects.equals(bluetoothDevice.getAddress(), macAddress)) {
                Logger.error("onConnectionStateChange received for different macAddress");
                gatt.close();
                return;
            }
            if (status == BluetoothGatt.GATT_SUCCESS) {
                switch (newState) {
                    case BluetoothProfile.STATE_CONNECTED: {
                        setDeviceConnectionState(ConnectionState.CONNECTED);
                        handler.post(gatt::discoverServices);
                        break;
                    }

                    case BluetoothProfile.STATE_CONNECTING: {
                        setDeviceConnectionState(ConnectionState.CONNECTING);
                        break;
                    }

                    case BluetoothProfile.STATE_DISCONNECTED: {
                        completeDisconnect();
                        break;
                    }
                }
            } else {
                Logger.info("status not successful");
                completeDisconnect();
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            String macAddress = gatt.getDevice().getAddress();
            Logger.info("onServicesDiscovered - macAddress: " + macAddress + "status: " + status);
            BluetoothDevice bluetoothDevice = _bluetoothDevice;
            if (bluetoothDevice == null || !Objects.equals(bluetoothDevice.getAddress(), macAddress)) {
                Logger.error("onServicesDiscovered received for different macAddress");
                gatt.close();
                return;
            }
            if (status != BluetoothGatt.GATT_SUCCESS) {
                completeDisconnect();
                return;
            }
            handler.post(() -> gatt.requestMtu(GATT_MAX_MTU_SIZE));
        }

        @Override
        public void onCharacteristicRead(@NonNull BluetoothGatt gatt, @NonNull BluetoothGattCharacteristic characteristic, @NonNull byte[] value, int status) {
            String uuid = characteristic.getUuid().toString();
            Logger.info("onCharacteristicRead - characteristicUUID: " + uuid + ", status: " + status + ", value: " + Arrays.toString(value));
            sendDataCallback(uuid, dataCallback -> {
                try {
                    dataCallback.onCharacteristicRead(value);
                } catch (Exception e) {
                    Logger.error("sendDataCallback onCharacteristicRead - error: " + e.getMessage());
                    e.printStackTrace();
                }
            });
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            Logger.info("onCharacteristicWrite - characteristicUUID: " + characteristic.getUuid().toString() + ", status: " + status);
        }

        @Override
        public void onCharacteristicChanged(@NonNull BluetoothGatt gatt, @NonNull BluetoothGattCharacteristic characteristic, @NonNull byte[] value) {
            String uuid = characteristic.getUuid().toString();
            Logger.info("onCharacteristicChanged - characteristicUUID: " + uuid + ", value: " + Arrays.toString(value));
            sendDataCallback(uuid, dataCallback -> {
                try {
                    dataCallback.onCharacteristicChanged(value);
                } catch (Exception e) {
                    Logger.error("sendDataCallback onCharacteristicChanged - error: " + e.getMessage());
                    e.printStackTrace();
                }
            });
        }

        @Override
        public void onDescriptorRead(@NonNull BluetoothGatt gatt, @NonNull BluetoothGattDescriptor descriptor, int status, @NonNull byte[] value) {
            Logger.info("onDescriptorRead - characteristicUUID: " + descriptor.getUuid().toString() + ", status: " + status + ", value: " + Arrays.toString(value));
        }

        @Override
        public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
            Logger.info("onDescriptorWrite - descriptorUUID: " + descriptor.getUuid().toString() + ", status: " + status);
            isWriteDescriptorInProgress.set(false);
            runPendingWriteDescriptors();
        }

        @Override
        public void onReadRemoteRssi(BluetoothGatt gatt, int rssi, int status) {
            super.onReadRemoteRssi(gatt, rssi, status);
        }

        @Override
        public void onMtuChanged(BluetoothGatt gatt, int mtu, int status) {
            String macAddress = gatt.getDevice().getAddress();
            Logger.info("onMtuChanged - macAddress: " + macAddress + ", mtu: " + mtu + ", status: " + status);
            BluetoothDevice bluetoothDevice = _bluetoothDevice;
            if (bluetoothDevice == null || !Objects.equals(bluetoothDevice.getAddress(), macAddress)) {
                Logger.error("onMtuChanged received for different macAddress");
                gatt.close();
                return;
            }
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Logger.info("onMtuChanged success");
            } else {
                Logger.warn("onMtuChanged failed");
            }
            pendingWriteDescriptors.clear();
            for (BluetoothGattService service : gatt.getServices()) {
                Logger.info("service: " + service.getUuid().toString());
                for (BluetoothGattCharacteristic characteristic : service.getCharacteristics()) {
                    Logger.info("characteristic: " + characteristic.getUuid().toString());
                    pendingWriteDescriptors.add(() -> {
                        Logger.info("Running Enable Notifications for service: " + service.getUuid() + ", characteristic: " + characteristic.getUuid());
                        boolean isWriteDescriptorCalled = enableNotifications(gatt, characteristic);
                        if (!isWriteDescriptorCalled) {
                            Logger.info("writeDescriptor not called");
                            isWriteDescriptorInProgress.set(false);
                            runPendingWriteDescriptors();
                        }
                    });
                    Logger.info("write descriptor added to queue, position: " + pendingWriteDescriptors.size());
                }
            }
            runPendingWriteDescriptors();
        }
    };

    private static void runPendingWriteDescriptors() {
        Logger.info("runPendingWriteDescriptors - pendingWriteDescriptors size " + pendingWriteDescriptors.size());
        if (pendingWriteDescriptors.isEmpty()) {
            Logger.info("runPendingWriteDescriptors - complete");
            sendConnectionCallback(ConnectionCallback::onConnectionSetupComplete);
            return;
        }
        if (isWriteDescriptorInProgress.compareAndSet(false, true)) {
            Runnable task = pendingWriteDescriptors.poll();
            if (task != null) {
                Logger.info("runPendingWriteDescriptors - running next");
                handler.post(task);
            } else {
                runPendingWriteDescriptors();
            }
        }
    }

    @SuppressLint("MissingPermission")
    private static boolean enableNotifications(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
        try {
            boolean notificationSet = gatt.setCharacteristicNotification(characteristic, true);
            if (notificationSet) {
                BluetoothGattDescriptor descriptor = characteristic.getDescriptor(CCC_DESCRIPTOR_UUID);
                Logger.info("notification set");
                if (descriptor != null) {
                    Logger.info("descriptor not null");
                    int properties = characteristic.getProperties();
                    byte[] descriptorValue;
                    if ((properties & BluetoothGattCharacteristic.PROPERTY_NOTIFY) > 0) {
                        descriptorValue = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE;
                        Logger.info("characteristic has notify property");
                    } else if ((properties & BluetoothGattCharacteristic.PROPERTY_INDICATE) > 0) {
                        descriptorValue = BluetoothGattDescriptor.ENABLE_INDICATION_VALUE;
                        Logger.info("characteristic has indicate property");
                    } else {
                        Logger.info("characteristic does not have notify or indicate property");
                        return false;
                    }
                    boolean result;
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        result = gatt.writeDescriptor(descriptor, descriptorValue) == BluetoothStatusCodes.SUCCESS;
                    } else {
                        descriptor.setValue(descriptorValue);
                        result = gatt.writeDescriptor(descriptor);
                    }
                    Logger.info("writeDescriptor call successful: " + result);
                    return true;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            Logger.error("enableNotifications exception: " + e.getMessage());
        }
        return false;
    }

    @SuppressLint("MissingPermission")
    private static void completeDisconnect() {
        Logger.info("completeDisconnect called!");
        BluetoothGatt bluetoothGatt = BluetoothOperations.bluetoothGatt;
        if (bluetoothGatt != null) {
            bluetoothGatt.close();
            BluetoothOperations.bluetoothGatt = null;
        }
        _bluetoothDevice = null;
        setDeviceConnectionState(ConnectionState.DISCONNECTED);
    }

    private static void setDeviceConnectionState(ConnectionState connectionState) {
        _deviceState = connectionState;
        Logger.info("setDeviceConnectionState - connectionState: " + connectionState);
        sendConnectionCallback(connectionCallback -> connectionCallback.onConnectionStateChanged(connectionState));
    }

    private static BluetoothAdapter getBluetoothAdapter() {
        BluetoothManager bluetoothManager = (BluetoothManager) BeeHiveMonitorApp.application.getSystemService(Context.BLUETOOTH_SERVICE);
        return bluetoothManager.getAdapter();
    }

    private static BluetoothDevice getBluetoothDevice(String macAddress) {
        BluetoothDevice bluetoothDevice = _bluetoothDevice;
        if (bluetoothDevice == null || !Objects.equals(bluetoothDevice.getAddress(), macAddress)) {
            if (bluetoothDevice != null) {
                completeDisconnect();
            }
            bluetoothDevice = getBluetoothAdapter().getRemoteDevice(macAddress);
            _deviceState = ConnectionState.DISCONNECTED;
            _bluetoothDevice = bluetoothDevice;
        }
        return bluetoothDevice;
    }

    private static void sendDataCallback(String uuid, Consumer<DataCallback> consumer) {
        List<DataCallback> dataCallbacks = dataCallbacksMap.get(uuid);
        if (dataCallbacks != null) {
            dataCallbacks.forEach(consumer);
        }
    }

    private static void sendConnectionCallback(Consumer<ConnectionCallback> consumer) {
        connectionCallbacks.forEach(consumer);
    }

    public static void addDataCallback(String uuid, DataCallback dataCallback) {
        dataCallbacksMap.putIfAbsent(uuid, new ArrayList<>());
        Objects.requireNonNull(dataCallbacksMap.get(uuid)).add(dataCallback);
    }

    public static void removeDataCallback(String uuid, DataCallback dataCallback) {
        if (dataCallbacksMap.containsKey(uuid)) {
            Objects.requireNonNull(dataCallbacksMap.get(uuid)).remove(dataCallback);
        }
    }

    public static void addConnectionCallback(ConnectionCallback connectionCallback) {
        connectionCallbacks.add(connectionCallback);
    }

    public static void removeConnectionCallback(ConnectionCallback connectionCallback) {
        connectionCallbacks.remove(connectionCallback);
    }

    public interface ConnectionCallback {
        void onBluetoothStateChanged(boolean isOn);

        void onScannedDeviceFound(ScannedDevice scannedDevice);

        void onDeviceScanFailed();

        void onConnectionStateChanged(ConnectionState connectionState);

        void onConnectionSetupComplete();
    }

    public interface DataCallback {
        void onCharacteristicChanged(byte[] value);

        void onCharacteristicRead(byte[] value);
    }
}
