package com.project.beehivemonitor.fragment;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.ViewGroup;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.project.beehivemonitor.R;
import com.project.beehivemonitor.activity.BaseActivity;
import com.project.beehivemonitor.adapter.AdapterScannedDevices;
import com.project.beehivemonitor.databinding.FragmentSelectDeviceBinding;
import com.project.beehivemonitor.model.Event;
import com.project.beehivemonitor.model.ScannedDevice;
import com.project.beehivemonitor.util.BluetoothOperations;
import com.project.beehivemonitor.util.ConnectionState;
import com.project.beehivemonitor.util.Logger;
import com.project.beehivemonitor.util.PermissionUtil;
import com.project.beehivemonitor.util.PreferenceManager;
import com.project.beehivemonitor.viewmodel.ConnectionViewModel;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

public class SelectDeviceFragment extends BaseFragment<FragmentSelectDeviceBinding> {

    private static final String SCAN_DEVICE_NAME_PREFIX = "BeeHive";

    private static final String START_SCAN_TEXT = "Start Scan";
    private static final String STOP_SCAN_TEXT = "Stop Scan";

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable stopScanRunnable = this::stopScan;
    private final AtomicBoolean isScanning = new AtomicBoolean(false);

    private ConnectionViewModel connectionViewModel;
    private AdapterScannedDevices adapterScannedDevices;
    private ScannedDevice selectedDevice;
    private boolean isConnecting;
    private boolean isAwaitingConnectionSetup;

    @Override
    protected void initOnCreateView() {
        isConnecting = false;
        isAwaitingConnectionSetup = false;
        connectionViewModel = new ViewModelProvider(this).get(ConnectionViewModel.class);
        adapterScannedDevices = new AdapterScannedDevices(scannedDevice -> {
            stopScan();
            selectedDevice = scannedDevice;
            isConnecting = true;
            connectionViewModel.connect(scannedDevice.getName(), scannedDevice.getMacAddress());
        });
        binding.rvScannedDevices.setLayoutManager(new LinearLayoutManager(getContext(), LinearLayoutManager.VERTICAL, false));
        binding.rvScannedDevices.setAdapter(adapterScannedDevices);
        binding.btnScan.setOnClickListener(view -> {
            switch (binding.btnScan.getText().toString()) {
                case START_SCAN_TEXT: {
                    checkPermissionsAndScan();
                    break;
                }

                case STOP_SCAN_TEXT: {
                    stopScan();
                    break;
                }
            }
        });
        connectionViewModel.getBluetoothStateLiveData().observe(this, bluetoothStateObserver);
        connectionViewModel.getConnectionStateLiveData().observe(this, connectionStateObserver);
        connectionViewModel.getConnectionSetupStatusLiveData().observe(this, connectionSetupStatusObserver);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (BluetoothOperations.isBluetoothEnabled() && PermissionUtil.areAllPermissionsGranted(requireContext(), PermissionUtil.getScanAndConnectPermissions())) {
            startScan();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        stopScan();
    }

    private void startScan() {
        if (isScanning.compareAndSet(false, true)) {
            binding.btnScan.setText(STOP_SCAN_TEXT);
            adapterScannedDevices.clearScannedDevices();
            connectionViewModel.getScannedDeviceLiveData().observe(this, scannedDeviceObserver);
            connectionViewModel.startScan();
            handler.postDelayed(stopScanRunnable, 10000);
        }
    }

    private void stopScan() {
        if (isScanning.compareAndSet(true, false)) {
            handler.removeCallbacks(stopScanRunnable);
            connectionViewModel.stopScan();
            connectionViewModel.getScannedDeviceLiveData().removeObserver(scannedDeviceObserver);
            binding.btnScan.setText(START_SCAN_TEXT);
        }
    }

    private void checkPermissionsAndScan() {
        Activity activity = getActivity();
        if (activity == null) return;
        runWithContext(context -> {
            if (!isPermissionGranted(Manifest.permission.BLUETOOTH_CONNECT) || !isPermissionGranted(Manifest.permission.BLUETOOTH_SCAN)) {
                nearByDevicesPermissionLauncher.launch(new String[]{Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN});
            } else if (!isPermissionGranted(Manifest.permission.ACCESS_FINE_LOCATION)) {
                locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION);
            } else if (!BluetoothOperations.isBluetoothEnabled()) {
                if (!BluetoothOperations.requestTurnOnBluetooth(activity)) {
                    showToast("Turn on bluetooth to proceed");
                }
            } else {
                startScan();
            }
        });
    }

    private void navigateToDataFragment() {
        ((BaseActivity<?>) requireContext()).getNavHostFragment().getNavController().navigate(R.id.dataFragment);
    }

    @NonNull
    @Override
    protected FragmentSelectDeviceBinding getBinding(ViewGroup container) {
        return FragmentSelectDeviceBinding.inflate(getLayoutInflater(), container, false);
    }


    private final Observer<Event<ScannedDevice>> scannedDeviceObserver = scannedDeviceEvent -> {
        if (scannedDeviceEvent.hasBeenHandled()) return;
        ScannedDevice scannedDevice = scannedDeviceEvent.getContentIfNotHandled();
        if (scannedDevice != null) {
            if (scannedDevice.getName() != null && !scannedDevice.getName().isBlank() && scannedDevice.getName().toLowerCase().startsWith(SCAN_DEVICE_NAME_PREFIX.toLowerCase())) {
                adapterScannedDevices.addScannedDevice(scannedDevice);
            }
        } else {
            stopScan();
            showToast("Error Scanning for Devices");
        }
    };

    private final Observer<Event<Boolean>> bluetoothStateObserver = isBluetoothOnEvent -> {
        if (isBluetoothOnEvent.hasBeenHandled()) return;
        boolean isBluetoothOn = isBluetoothOnEvent.getContentIfNotHandled();
        Logger.info("bluetoothStateObserver - isBluetoothOn: " + isBluetoothOn);
        if (!isBluetoothOn) {
            stopScan();
        }
        checkPermissionsAndScan();
    };

    private final Observer<Event<ConnectionState>> connectionStateObserver = connectionStateEvent -> {
        if (!isConnecting || connectionStateEvent.hasBeenHandled()) return;
        ConnectionState connectionState = connectionStateEvent.getContentIfNotHandled();
        Logger.info("connectionStateObserver - connectionState: " + connectionState);
        switch (connectionState) {
            case CONNECTED: {
                isAwaitingConnectionSetup = true;
                showLoading("Getting device ready...");
                break;
            }
            case CONNECTING: {
                isAwaitingConnectionSetup = false;
                showLoading("Connecting...");
                break;
            }
            case DISCONNECTED: {
                isConnecting = false;
                isAwaitingConnectionSetup = false;
                hideLoading();
                showToast("Connection failed, try again");
                break;
            }
        }
    };

    private final Observer<Event<Boolean>> connectionSetupStatusObserver = connectionSetupStatusEvent -> {
        if (!isAwaitingConnectionSetup || connectionSetupStatusEvent.hasBeenHandled()) return;
        Boolean connectionSetupStatus = connectionSetupStatusEvent.getContentIfNotHandled();
        Logger.info("connectionSetupStatusObserver - connectionSetupStatus: " + connectionSetupStatus);
        if (connectionSetupStatus) {
            isConnecting = false;
            isAwaitingConnectionSetup = false;
            hideLoading();
            showToast("Connection Successful");
            PreferenceManager.getInstance().setSelectedDevice(selectedDevice);
            navigateToDataFragment();
        }
    };

    private final ActivityResultLauncher<String[]> nearByDevicesPermissionLauncher = registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), permissions -> {
        for (Map.Entry<String, Boolean> permission : permissions.entrySet()) {
            if (!permission.getValue()) {
                runWithActivity(activity -> {
                    if (ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.BLUETOOTH_CONNECT)) {
                        showToast("Near by devices permission required!", true);
                    } else {
                        runWithContext(context -> {
                            showToast("Grant near by devices permission to proceed!", true);
                            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                            intent.setData(Uri.fromParts("package", context.getPackageName(), null));
                            context.startActivity(intent);
                        });
                    }
                });
                return;
            }
        }
        checkPermissionsAndScan();
    });

    private final ActivityResultLauncher<String> locationPermissionLauncher = registerForActivityResult(new ActivityResultContracts.RequestPermission(), permissionGranted -> {
        if (permissionGranted) {
            checkPermissionsAndScan();
        } else {
            runWithActivity(activity -> {
                if (ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.BLUETOOTH_CONNECT)) {
                    showToast("Precise Location permission required!", true);
                } else {
                    runWithContext(context -> {
                        showToast("Grant precise location permission to proceed!", true);
                        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                        intent.setData(Uri.fromParts("package", context.getPackageName(), null));
                        context.startActivity(intent);
                    });
                }
            });
        }
    });
}