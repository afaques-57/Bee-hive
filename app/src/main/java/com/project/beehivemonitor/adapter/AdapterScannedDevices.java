package com.project.beehivemonitor.adapter;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewbinding.ViewBinding;

import com.project.beehivemonitor.databinding.ItemSelectDeviceBinding;
import com.project.beehivemonitor.model.ScannedDevice;
import com.project.beehivemonitor.util.Logger;
import com.project.beehivemonitor.util.ViewHolderBinding;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class AdapterScannedDevices extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private final List<ScannedDevice> scannedDeviceList = new ArrayList<>();
    @NonNull
    private final OnItemClickListener onItemClickListener;
    public AdapterScannedDevices(@NonNull OnItemClickListener onItemClickListener) {
        this.onItemClickListener = onItemClickListener;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ViewBinding viewBinding = ItemSelectDeviceBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
        return new ViewHolderBinding(viewBinding);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        ItemSelectDeviceBinding binding = (ItemSelectDeviceBinding) ((ViewHolderBinding) holder).binding;
        ScannedDevice scannedDevice = scannedDeviceList.get(position);
        binding.getRoot().setOnClickListener(view -> onItemClickListener.onClick(scannedDevice));

        String deviceName = scannedDevice.getName();
        binding.tvDeviceName.setText(deviceName != null && !deviceName.isBlank() ? deviceName : "- - - - -");
        binding.tvDeviceMacAddress.setText(scannedDevice.getMacAddress());
    }

    @Override
    public int getItemCount() {
        return scannedDeviceList.size();
    }

    public void addScannedDevice(ScannedDevice scannedDevice) {
        if(scannedDeviceList.stream().noneMatch(device -> Objects.equals(scannedDevice.getMacAddress(), device.getMacAddress()))) {
            Logger.info("AdapterScannedDevices - addScannedDevice: " + scannedDevice);
            scannedDeviceList.add(scannedDevice);
            notifyItemInserted(scannedDeviceList.indexOf(scannedDevice));
        }
    }

    public void clearScannedDevices() {
        scannedDeviceList.clear();
        notifyDataSetChanged();
    }

    @FunctionalInterface
    public interface OnItemClickListener {
        void onClick(ScannedDevice scannedDevice);
    }
}
