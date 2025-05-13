package com.project.beehivemonitor.activity;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.navigation.fragment.NavHostFragment;

import com.project.beehivemonitor.R;
import com.project.beehivemonitor.databinding.ActivityMainBinding;
import com.project.beehivemonitor.util.BluetoothOperations;

public class MainActivity extends BaseActivity<ActivityMainBinding> {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        BluetoothOperations.disconnectDevice();
    }

    @Override
    public NavHostFragment getNavHostFragment() {
        NavHostFragment navHostFragment = mNavHostFragment;
        if(navHostFragment == null) {
            navHostFragment = (NavHostFragment) getSupportFragmentManager().findFragmentById(R.id.fragment_container);
            mNavHostFragment = navHostFragment;
        }
        return navHostFragment;
    }

    @NonNull
    @Override
    protected ActivityMainBinding getBinding() {
        return ActivityMainBinding.inflate(getLayoutInflater());
    }
}