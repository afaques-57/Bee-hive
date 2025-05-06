package com.project.beehive.fragment;

import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.project.beehive.adapter.AdapterScannedDevices;
import com.project.beehive.databinding.FragmentSelectDeviceBinding;
import com.project.beehive.model.ScannedDevice;
import com.project.beehive.viewmodel.ConnectionViewModel;

public class SelectDeviceFragment extends BaseFragment<FragmentSelectDeviceBinding> {

    private ConnectionViewModel connectionViewModel;
    private AdapterScannedDevices adapterScannedDevices;

    @Override
    protected void initOnCreateView() {
        connectionViewModel = new ViewModelProvider(this).get(ConnectionViewModel.class);
        adapterScannedDevices = new AdapterScannedDevices(scannedDevice -> connectionViewModel.connect(new ConnectionViewModel.ConnectionCallback() {
            @Override
            public void onSuccess() {
                showToast("Pairing Success!");
            }

            @Override
            public void onError() {
                showToast("Pairing Failed!");
            }
        }));
        binding.rvScannedDevices.setLayoutManager(new LinearLayoutManager(getContext(), LinearLayoutManager.VERTICAL, false));
        binding.rvScannedDevices.setAdapter(adapterScannedDevices);
        adapterScannedDevices.addScannedDevice(new ScannedDevice("Test", "00:00:00:00:00"));
    }

    @NonNull
    @Override
    protected FragmentSelectDeviceBinding getBinding(ViewGroup container) {
        return FragmentSelectDeviceBinding.inflate(getLayoutInflater(), container, false);
    }
}