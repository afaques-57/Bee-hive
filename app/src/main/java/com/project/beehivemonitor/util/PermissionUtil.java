package com.project.beehivemonitor.util;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;

import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.List;

public final class PermissionUtil {

    public static boolean isPermissionGranted(Context context, String permission) {
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED;
    }

    public static boolean areAllPermissionsGranted(Context context, List<String> permissions) {
        return permissions.stream().allMatch(permission -> isPermissionGranted(context, permission));
    }

    public static List<String> getScanAndConnectPermissions() {
        List<String> permissionList = new ArrayList<>();
        permissionList.add(Manifest.permission.BLUETOOTH_CONNECT);
        permissionList.add(Manifest.permission.BLUETOOTH_SCAN);
        permissionList.add(Manifest.permission.ACCESS_FINE_LOCATION);
        return permissionList;
    }

    public static List<String> getConnectPermissions() {
        List<String> permissionList = new ArrayList<>();
        permissionList.add(Manifest.permission.BLUETOOTH_CONNECT);
        permissionList.add(Manifest.permission.BLUETOOTH_SCAN);
        return permissionList;
    }
}
