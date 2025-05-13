package com.project.beehivemonitor.util;

import android.util.Log;

public class Logger {

    public static void info(String msg) {
        info("BeeHiveMonitor", msg);
    }

    public static void info(String tag, String msg) {
        Log.i(tag, msg);
    }

    public static void debug(String msg) {
        debug("BeeHiveMonitor", msg);
    }

    public static void debug(String tag, String msg) {
        Log.d(tag, msg);
    }

    public static void warn(String msg) {
        warn("BeeHiveMonitor", msg);
    }

    public static void warn(String tag, String msg) {
        Log.w(tag, msg);
    }

    public static void error(String msg) {
        error("BeeHiveMonitor", msg);
    }

    public static void error(String tag, String msg) {
        Log.e(tag, msg);
    }
}
