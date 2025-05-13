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

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import kotlin.Suppress;
import kotlin.jvm.Volatile;

public final class BluetoothOperations {

    //    private static final UUID SERVICE_UUID = UUID.fromString("");
//    private static final UUID NOTIFICATION_UUID = UUID.fromString("");
    private static final UUID CCC_DESCRIPTOR_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

    private static final Handler handler = new Handler(Looper.getMainLooper());

    private static final Set<BluetoothOperationsCallback> bluetoothOperationsCallbacks = new HashSet<>();

    private static final Queue<Runnable> pendingWriteDescriptors = new ConcurrentLinkedQueue<>();
    private static final AtomicBoolean isWriteDescriptorInProgress = new AtomicBoolean(false);
//    private static final Object writeDescriptorLock = new Object();

    private static BluetoothLeScanner bluetoothLeScanner;
    volatile private static BluetoothDevice _bluetoothDevice;
    volatile private static BluetoothGatt bluetoothGatt;

    volatile private static ConnectionState _deviceState = ConnectionState.DISCONNECTED;
//    private final static AtomicBoolean isConnectionInProgress = new AtomicBoolean(false);

    private static final BroadcastReceiver adapterStateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (Objects.equals(intent.getAction(), BluetoothAdapter.ACTION_STATE_CHANGED)) {
                int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR);
                switch (state) {
                    case BluetoothAdapter.STATE_ON: {
                        sendBluetoothOperationCallback(bluetoothOperationsCallback -> bluetoothOperationsCallback.onBluetoothStateChanged(true));
                        break;
                    }
                    case BluetoothAdapter.STATE_OFF: {
                        stopScan();
                        setDeviceConnectionState(ConnectionState.DISCONNECTED);
                        sendBluetoothOperationCallback(bluetoothOperationsCallback -> bluetoothOperationsCallback.onBluetoothStateChanged(false));
                        break;
                    }
                }
            }
        }
    };

    /*private static final BroadcastReceiver bondStateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (BluetoothDevice.ACTION_BOND_STATE_CHANGED.equals(action)) {
                BluetoothDevice device = _bluetoothDevice;
                BluetoothDevice extraDevice = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                if (device == null || extraDevice == null || !Objects.equals(device.getAddress(), extraDevice.getAddress())) {
                    Logger.info("bondStateReceiver proceed conditions failed!");
                    return;
                }
                int bondState = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.ERROR);
                int prevBondState = intent.getIntExtra(BluetoothDevice.EXTRA_PREVIOUS_BOND_STATE, BluetoothDevice.ERROR);

                Logger.info("Bond state changed: " + bondState + " (from " + prevBondState + ")");

                switch (bondState) {
                    case BluetoothDevice.BOND_BONDED:
                        Logger.info("Device bonded successfully!");
                        connectDevice(BeeHiveMonitorApp.application, device.getAddress());
                        break;
                    case BluetoothDevice.BOND_NONE:
                        Logger.warn("Bonding failed or was revoked.");
                        completeDisconnect();
                        break;
                    case BluetoothDevice.BOND_BONDING:
                        Logger.info("Bonding in progress...");
                        break;
                }
            }
        }
    };*/

    private static final ScanCallback scanCallback = new ScanCallback() {
        @SuppressLint("MissingPermission")
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            super.onScanResult(callbackType, result);
            sendBluetoothOperationCallback(bluetoothOperationsCallback -> {
                try {
                    String deviceName = result.getDevice().getName();
                    deviceName = deviceName == null ? "" : deviceName;
                    bluetoothOperationsCallback.onScannedDeviceFound(new ScannedDevice(deviceName, result.getDevice().getAddress()));
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
        }

        @Override
        public void onScanFailed(int errorCode) {
            super.onScanFailed(errorCode);
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
//            context.registerReceiver(bondStateReceiver, new IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED));
//            if (bluetoothDevice.getBondState() == BluetoothDevice.BOND_BONDED) {
//                handler.post(() -> bluetoothGatt = bluetoothDevice.connectGatt(context, false, bluetoothGattCallback, BluetoothDevice.TRANSPORT_LE));
//            } else {
//                handler.post(bluetoothDevice::createBond);
//            }
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
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                    && (activity.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
                    || activity.checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED
            )) {
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
                Logger.error("onConnectionStateChange received for different macAddress");
                gatt.close();
                return;
            }
            pendingWriteDescriptors.clear();
            if (status != BluetoothGatt.GATT_SUCCESS) {
                completeDisconnect();
                return;
            }
            List<BluetoothGattService> bluetoothGattServices = gatt.getServices();
            bluetoothGattServices.forEach(service -> {
                Logger.info("service: " + service.getUuid().toString());
                service.getCharacteristics().forEach(characteristic -> {
                    Logger.info("characteristic: " + characteristic.getUuid().toString());
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
                                return;
                            }
                            pendingWriteDescriptors.add(() -> {
                                descriptor.setValue(descriptorValue);
                                gatt.writeDescriptor(descriptor);
                            });
                            runPendingWriteDescriptors();
                        }
                    }
                });
            });
        }

        @Override
        public void onCharacteristicRead(@NonNull BluetoothGatt gatt, @NonNull BluetoothGattCharacteristic characteristic, @NonNull byte[] value, int status) {
            super.onCharacteristicRead(gatt, characteristic, value, status);
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            super.onCharacteristicWrite(gatt, characteristic, status);
        }

        @Override
        public void onCharacteristicChanged(@NonNull BluetoothGatt gatt, @NonNull BluetoothGattCharacteristic characteristic, @NonNull byte[] value) {
            Logger.info("onCharacteristicChanged - characteristicUUID: " + characteristic.getUuid() + ", value: " + Arrays.toString(value));
            try {
                Logger.info("onCharacteristicChanged - value: " + new String(value, Charset.defaultCharset()));
            } catch (Exception e) {
                Logger.error("onCharacteristicChanged - error: " + e.getMessage());
                e.printStackTrace();
            }
        }

        @Override
        public void onDescriptorRead(@NonNull BluetoothGatt gatt, @NonNull BluetoothGattDescriptor descriptor, int status, @NonNull byte[] value) {
            super.onDescriptorRead(gatt, descriptor, status, value);
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
            super.onMtuChanged(gatt, mtu, status);
        }
    };

    private static void runPendingWriteDescriptors() {
        if (!pendingWriteDescriptors.isEmpty() && isWriteDescriptorInProgress.compareAndSet(false, true)) {
            Runnable task = pendingWriteDescriptors.poll();
            if (task != null) {
                Logger.info("runPendingWriteDescriptors - running next");
                task.run();
            } else {
                runPendingWriteDescriptors();
            }
        }
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
        sendBluetoothOperationCallback(bluetoothOperationsCallback -> bluetoothOperationsCallback.onConnectionStateChanged(connectionState));
    }

    private static BluetoothAdapter getBluetoothAdapter() {
        BluetoothManager bluetoothManager = (BluetoothManager) BeeHiveMonitorApp.application.getSystemService(Context.BLUETOOTH_SERVICE);
        return bluetoothManager.getAdapter();
    }

    private static BluetoothDevice getBluetoothDevice(String macAddress) {
        BluetoothDevice bluetoothDevice = _bluetoothDevice;
        if (bluetoothDevice == null || !Objects.equals(bluetoothDevice.getAddress(), macAddress)) {
            if (bluetoothDevice != null) {
//                if(isConnectionInProgress.get()) throw new RuntimeException("Connection is Still in Progress!");
                completeDisconnect();
            }
            bluetoothDevice = getBluetoothAdapter().getRemoteDevice(macAddress);
            _deviceState = ConnectionState.DISCONNECTED;
            _bluetoothDevice = bluetoothDevice;
        }
        return bluetoothDevice;
    }

    private static void sendBluetoothOperationCallback(Consumer<BluetoothOperationsCallback> consumer) {
        bluetoothOperationsCallbacks.forEach(consumer);
    }

    public static void addCallback(BluetoothOperationsCallback bluetoothOperationsCallback) {
        bluetoothOperationsCallbacks.add(bluetoothOperationsCallback);
    }

    public static void removeCallback(BluetoothOperationsCallback bluetoothOperationsCallback) {
        bluetoothOperationsCallbacks.remove(bluetoothOperationsCallback);
    }

    public interface BluetoothOperationsCallback {
        void onBluetoothStateChanged(boolean isOn);

        void onScannedDeviceFound(ScannedDevice scannedDevice);

        void onDeviceScanFailed();

        void onConnectionStateChanged(ConnectionState connectionState);
    }
}
