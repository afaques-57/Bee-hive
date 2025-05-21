package com.project.beehivemonitor.fragment;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.ViewGroup;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.res.ResourcesCompat;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.project.beehivemonitor.R;
import com.project.beehivemonitor.databinding.FragmentDataBinding;
import com.project.beehivemonitor.model.ScannedDevice;
import com.project.beehivemonitor.util.BluetoothOperations;
import com.project.beehivemonitor.util.ConnectionState;
import com.project.beehivemonitor.util.Event;
import com.project.beehivemonitor.util.Logger;
import com.project.beehivemonitor.util.PermissionUtil;
import com.project.beehivemonitor.util.PreferenceManager;
import com.project.beehivemonitor.viewmodel.BeeMonitorDataViewModel;
import com.project.beehivemonitor.viewmodel.ConnectionViewModel;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

public class DataFragment extends BaseFragment<FragmentDataBinding> {

    private static final String CONNECT_TEXT = "Connect";
    private static final String DISCONNECT_TEXT = "Disconnect";
    private static final String CONNECTING_TEXT = "Connecting...";

    private static final String NORMAL_TEXT = "MATICA";
    private static final String NO_QUEEN_TEXT = "NIMATICE";
    private static final String SWARMING_TEXT = "ROJENJE";

    private final Handler handler = new Handler(Looper.getMainLooper());

    private ConnectionViewModel connectionViewModel;
    private BeeMonitorDataViewModel beeMonitorDataViewModel;
    private final ScannedDevice selectedDevice = PreferenceManager.getInstance().getSelectedDevice();

    private final List<Float> temperatureValues = new CopyOnWriteArrayList<>();
    private final List<Float> humidityValues = new CopyOnWriteArrayList<>();
    private static final int GRAPH_VALUES_LIMIT = 10;

    @Override
    protected void initOnCreateView() {
        connectionViewModel = new ViewModelProvider(this).get(ConnectionViewModel.class);
        beeMonitorDataViewModel = new ViewModelProvider(this).get(BeeMonitorDataViewModel.class);
        binding.btnConnection.setText(BluetoothOperations.isDeviceConnected() ? DISCONNECT_TEXT : CONNECT_TEXT);
        binding.btnConnection.setOnClickListener(view -> {
            switch (binding.btnConnection.getText().toString()) {
                case CONNECT_TEXT: {
                    checkPermissionsAndConnect();
                    break;
                }

                case DISCONNECT_TEXT: {
                    connectionViewModel.disconnect();
                    break;
                }
            }
        });
        initLineChart(binding.lcTemperature);
        initLineChart(binding.lcHumidity);
        runWithActivity(activity -> activity.getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                BluetoothOperations.disconnectDevice();
                PreferenceManager.getInstance().setSelectedDevice(null);
                continueOnBackPress(this);
            }
        }));
        binding.btnBack.setOnClickListener(view -> onBackPressed());
        connectionViewModel.getBluetoothStateLiveData().observe(this, bluetoothStateObserver);
        connectionViewModel.getConnectionStateLiveData().observe(this, connectionStateObserver);
        handleBeeMonitoringData();
    }

    Runnable addDataRunnable = () -> {
        beeMonitorDataViewModel.postSampleData();
        addData();
    };

    private void addData() {
        handler.postDelayed(addDataRunnable, 2000);
    }

    @Override
    public void onResume() {
        super.onResume();
//        addData();
    }

    @Override
    public void onPause() {
        super.onPause();
        handler.removeCallbacks(addDataRunnable);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        connectionViewModel.getBluetoothStateLiveData().removeObserver(bluetoothStateObserver);
        connectionViewModel.getConnectionStateLiveData().removeObserver(connectionStateObserver);
    }

    private void handleBeeMonitoringData() {
        beeMonitorDataViewModel.getEventLiveData().observe(this, event -> {
            String eventName = event.peekContent();
            Logger.info("eventLiveData - event: " + eventName);
            setEventName(eventName);
        });
        beeMonitorDataViewModel.getTemperatureLiveData().observe(this, temperatureEvent -> {
            Float temperature = temperatureEvent.getContentIfNotHandled();
            if (temperature != null) {
                Logger.info("temperatureLiveData - temperature: " + temperature);
                temperatureValues.add(temperature);
                handler.post(() -> populateLineChart(binding.lcTemperature, "Temperature", temperatureValues));
            }
        });
        beeMonitorDataViewModel.getHumidityLiveData().observe(this, humidityEvent -> {
            Float humidity = humidityEvent.getContentIfNotHandled();
            if (humidity != null) {
                Logger.info("humidityLiveData - humidity: " + humidity);
                humidityValues.add(humidity);
                handler.post(() -> populateLineChart(binding.lcHumidity, "Humidity", humidityValues));
            }
        });
    }

    private void setEventName(String eventName) {
        runWithContext(context -> {
            int eventColor;
            String eventText;
            switch (eventName.toUpperCase()) {
                case NORMAL_TEXT: {
                    eventColor = ContextCompat.getColor(context, R.color.greenNormal);
                    eventText = "Normal";
                    break;
                }

                case NO_QUEEN_TEXT: {
                    eventColor = ContextCompat.getColor(context, R.color.yellowWarning);
                    eventText = "No Queen";
                    break;
                }

                case SWARMING_TEXT: {
                    eventColor = ContextCompat.getColor(context, R.color.redDanger);
                    eventText = "Swarming";
                    break;
                }

                default: {
                    eventColor = ContextCompat.getColor(context, R.color.greyUnknown);
                    eventText = "- - - - - -";
                    break;
                }
            }
            binding.tvEvent.setText(eventText);
            binding.tvEvent.setTextColor(eventColor);
            binding.cvEventContainer.setStrokeColor(eventColor);
        });
    }

    private void checkPermissionsAndConnect() {
        Activity activity = getActivity();
        if (activity == null) return;
        runWithContext(context -> {
            List<String> requiredPermissions = PermissionUtil.getConnectPermissions();
            if ((requiredPermissions.contains(Manifest.permission.BLUETOOTH_CONNECT) && !isPermissionGranted(Manifest.permission.BLUETOOTH_CONNECT)) || (requiredPermissions.contains(Manifest.permission.BLUETOOTH_SCAN) && !isPermissionGranted(Manifest.permission.BLUETOOTH_SCAN))) {
                nearByDevicesPermissionLauncher.launch(new String[]{Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN});
            } else if (requiredPermissions.contains(Manifest.permission.ACCESS_FINE_LOCATION) && !isPermissionGranted(Manifest.permission.ACCESS_FINE_LOCATION)) {
                locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION);
            } else if (!BluetoothOperations.isBluetoothEnabled()) {
                if (!BluetoothOperations.requestTurnOnBluetooth(activity)) {
                    showToast("Turn on bluetooth to proceed");
                }
            } else {
                connectionViewModel.connect(selectedDevice.getName(), selectedDevice.getMacAddress());
            }
        });
    }

    private void initLineDataSet(LineDataSet lineDataSet) {
        runWithContext(context -> {
            lineDataSet.setMode(LineDataSet.Mode.CUBIC_BEZIER);
            lineDataSet.setDrawValues(false);
            lineDataSet.setDrawFilled(true);
            lineDataSet.setLineWidth(3);
            lineDataSet.setColor(ContextCompat.getColor(context, R.color.orange));
            lineDataSet.setCircleColor(ContextCompat.getColor(context, R.color.black));
            lineDataSet.setFillColor(R.color.grey);
        });
    }

    private void initLineChart(LineChart lineChart) {
        runWithContext(context -> {
            lineChart.getAxisRight().setEnabled(false);
            lineChart.setTouchEnabled(true);
            lineChart.setPinchZoom(true);
            lineChart.getDescription().setEnabled(false);
            lineChart.setNoDataText("No data yet!");

            XAxis xAxis = lineChart.getXAxis();
            xAxis.setLabelRotationAngle(0);
            xAxis.setGranularity(1);
            xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
            xAxis.setTextColor(ContextCompat.getColor(context, R.color.orange));
            xAxis.setTypeface(ResourcesCompat.getFont(context, R.font.poppins_medium));
            xAxis.setTextSize(12);

            YAxis yAxis = lineChart.getAxisLeft();
            yAxis.setTextColor(ContextCompat.getColor(context, R.color.orange));
            yAxis.setTextColor(ContextCompat.getColor(context, R.color.orange));
            yAxis.setTypeface(ResourcesCompat.getFont(context, R.font.poppins_medium));
        });
    }

    private void populateLineChart(LineChart lineChart, String legendName, List<Float> values) {
        int size = values.size();
        int fromIndex = Math.max(size - GRAPH_VALUES_LIMIT, 0);
        List<Float> updatedValues = new ArrayList<>(values.subList(fromIndex, size));

        List<Entry> result = new ArrayList<>();
        for (int i = 0; i < updatedValues.size(); i++) {
            result.add(new Entry(i + 1, updatedValues.get(i)));
        }

        LineDataSet lineDataSet = new LineDataSet(result, legendName);
        initLineDataSet(lineDataSet);
        lineChart.setData(new LineData(lineDataSet));
        lineChart.postInvalidate();
    }

    @NonNull
    @Override
    protected FragmentDataBinding getBinding(ViewGroup container) {
        return FragmentDataBinding.inflate(getLayoutInflater(), container, false);
    }

    private final Observer<Event<Boolean>> bluetoothStateObserver = isBluetoothOnEvent -> {
        if (isBluetoothOnEvent.hasBeenHandled()) return;
        boolean isBluetoothOn = isBluetoothOnEvent.getContentIfNotHandled();
        Logger.info("bluetoothStateObserver - isBluetoothOn: " + isBluetoothOn);
        checkPermissionsAndConnect();
    };

    private final Observer<Event<ConnectionState>> connectionStateObserver = connectionStateEvent -> {
        if (connectionStateEvent.hasBeenHandled()) return;
        ConnectionState connectionState = connectionStateEvent.getContentIfNotHandled();
        switch (connectionState) {
            case CONNECTED: {
                showToast("Connected");
                binding.btnConnection.setClickable(true);
                binding.btnConnection.setText(DISCONNECT_TEXT);
                break;
            }
            case CONNECTING: {
                binding.btnConnection.setClickable(false);
                binding.btnConnection.setText(CONNECTING_TEXT);
                break;
            }
            case DISCONNECTED: {
                showToast("Disconnected");
                binding.btnConnection.setClickable(true);
                binding.btnConnection.setText(CONNECT_TEXT);
                break;
            }
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
        checkPermissionsAndConnect();
    });

    private final ActivityResultLauncher<String> locationPermissionLauncher = registerForActivityResult(new ActivityResultContracts.RequestPermission(), permissionGranted -> {
        if (permissionGranted) {
            checkPermissionsAndConnect();
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