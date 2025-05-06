package com.project.beehive.fragment;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.viewbinding.ViewBinding;

public abstract class BaseFragment<T extends ViewBinding> extends Fragment {

    T binding;

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

    protected void showToast(String msg) {
        showToast(msg, Toast.LENGTH_SHORT);
    }

    protected void showToast(String msg, int length) {
        Context context = getContext();
        if (context != null) {
            Toast.makeText(context, msg, length).show();
        }
    }
}


