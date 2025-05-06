package com.project.beehive.activity;

import android.os.Bundle;

import androidx.annotation.NonNull;

import com.project.beehive.databinding.ActivityMainBinding;

public class DeviceActivity extends BaseActivity<ActivityMainBinding> {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

    }

    @NonNull
    @Override
    protected ActivityMainBinding getBinding() {
        return ActivityMainBinding.inflate(getLayoutInflater());
    }
}