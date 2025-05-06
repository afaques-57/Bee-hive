package com.project.beehive.adapter;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewbinding.ViewBinding;

import com.project.beehive.databinding.ItemSelectDeviceBinding;
import com.project.beehive.model.ScannedDevice;
import com.project.beehive.util.ViewHolderBinding;

import java.util.ArrayList;
import java.util.List;

public class AdapterScannedDevices extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private final List<ScannedDevice> scannedDeviceList = new ArrayList<>();
    @NonNull
    private final OnItemClickListener onItemClickListener;
    public AdapterScannedDevices(OnItemClickListener onItemClickListener) {
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

        binding.tvDeviceName.setText(scannedDevice.getName());
        binding.tvDeviceMacAddress.setText(scannedDevice.getMacAddress());
    }

    @Override
    public int getItemCount() {
        return scannedDeviceList.size();
    }

    public void addScannedDevice(ScannedDevice scannedDevice) {
        scannedDeviceList.add(scannedDevice);
    }

    public void clearScannedDevices() {
        scannedDeviceList.clear();
    }

    @FunctionalInterface
    public interface OnItemClickListener {
        void onClick(ScannedDevice scannedDevice);
    }
}
