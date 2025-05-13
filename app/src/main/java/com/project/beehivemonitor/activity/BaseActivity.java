package com.project.beehivemonitor.activity;

import android.annotation.SuppressLint;
import android.content.pm.ActivityInfo;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.navigation.fragment.NavHostFragment;
import androidx.viewbinding.ViewBinding;

import kotlin.UninitializedPropertyAccessException;

public abstract class BaseActivity<T extends ViewBinding> extends AppCompatActivity {

    protected T binding;
    volatile protected NavHostFragment mNavHostFragment;

    @SuppressLint("SourceLockedOrientationActivity")
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        binding = getBinding();
        setContentView(binding.getRoot());
    }

    @NonNull
    abstract protected T getBinding();

    public NavHostFragment getNavHostFragment() {
        throw new UninitializedPropertyAccessException("NavHostFragment not initialized by activity");
    }
}


