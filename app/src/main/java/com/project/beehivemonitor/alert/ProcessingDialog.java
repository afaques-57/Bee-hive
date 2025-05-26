package com.project.beehivemonitor.alert;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.project.beehivemonitor.databinding.DialogProcessingBinding;

public class ProcessingDialog extends BottomSheetDialogFragment {

    private DialogProcessingBinding binding;
    private String infoText;

    public static ProcessingDialog newInstance() {
        return new ProcessingDialog();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = DialogProcessingBinding.inflate(inflater, container, false);
        setCancelable(false);
        binding.tvInfo.setText(infoText);
        return binding.getRoot();
    }

    public String getInfoText() {
        if (binding != null) {
            return binding.tvInfo.getText().toString();
        }
        return infoText;
    }

    public void setInfoText(String infoText) {
        if (binding != null) {
            binding.tvInfo.setText(infoText);
        } else {
            this.infoText = infoText;
        }
    }
}
