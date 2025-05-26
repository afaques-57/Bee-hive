package com.project.beehivemonitor.fragment;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewbinding.ViewBinding;

import com.project.beehivemonitor.alert.ProcessingDialog;
import com.project.beehivemonitor.util.PermissionUtil;

import java.util.Objects;
import java.util.function.Consumer;

public abstract class BaseFragment<T extends ViewBinding> extends Fragment {

    protected T binding;
    private ProcessingDialog processingDialog;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        super.onCreateView(inflater, container, savedInstanceState);
        binding = getBinding(container);
        initOnCreateView();
        return binding.getRoot();
    }

    protected void initOnCreateView() {
    }

    @NonNull
    abstract protected T getBinding(@Nullable ViewGroup container);

    protected void onBackPressed() {
        runWithActivity(activity -> activity.getOnBackPressedDispatcher().onBackPressed());
    }

    protected void continueOnBackPress(OnBackPressedCallback onBackPressedCallback) {
        if (onBackPressedCallback.isEnabled()) {
            onBackPressedCallback.setEnabled(false);
            onBackPressed();
            onBackPressedCallback.setEnabled(true);
        } else {
            onBackPressed();
        }
    }

    protected void runWithContext(Consumer<Context> contextConsumer) {
        Context context = getContext();
        if (context != null) {
            contextConsumer.accept(context);
        }
    }

    protected void runWithActivity(Consumer<FragmentActivity> activityConsumer) {
        FragmentActivity activity = getActivity();
        if (activity != null) {
            activityConsumer.accept(activity);
        }
    }

    protected boolean isPermissionGranted(String permission) {
        return PermissionUtil.isPermissionGranted(requireContext(), permission);
    }

    protected void showLoading(String infoText) {
        if (processingDialog == null) {
            processingDialog = ProcessingDialog.newInstance();
            processingDialog.setInfoText(infoText);
            processingDialog.show(requireActivity().getSupportFragmentManager(), "loading");
        } else if (infoText != null && !Objects.equals(processingDialog.getInfoText(), infoText)) {
            processingDialog.setInfoText(infoText);
        }
    }

    protected void hideLoading() {
        if (processingDialog != null) {
            processingDialog.dismiss();
            processingDialog = null;
        }
    }

    protected void showToast(String msg) {
        showToast(msg, false);
    }

    protected void showToast(String msg, boolean isLengthLong) {
        runWithContext(context -> Toast.makeText(context, msg, isLengthLong ? Toast.LENGTH_LONG : Toast.LENGTH_SHORT).show());
    }

    protected float dpToPx(float dp) {
        return dp * requireContext().getResources().getDisplayMetrics().density;
    }
}


