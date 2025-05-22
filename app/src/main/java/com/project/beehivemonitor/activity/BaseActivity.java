package com.project.beehivemonitor.activity;

import android.annotation.SuppressLint;
import android.content.pm.ActivityInfo;
import android.os.Build;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            getWindow().setDecorFitsSystemWindows(false);
            ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (v, insets) -> {
                Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
                return WindowInsetsCompat.CONSUMED;
            });
        }

        setContentView(binding.getRoot());
    }

    @NonNull
    abstract protected T getBinding();

    public NavHostFragment getNavHostFragment() {
        throw new UninitializedPropertyAccessException("NavHostFragment not initialized by activity");
    }
}


