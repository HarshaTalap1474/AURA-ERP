package com.aura.attendix;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.appcompat.app.AppCompatDelegate;

/**
 * ThemeManager — persists and applies the user's theme preference.
 * Modes: 0 = System Default, 1 = Light, 2 = Dark
 */
public class ThemeManager {

    private static final String PREFS_NAME  = "AuraTheme";
    private static final String KEY_MODE    = "theme_mode";

    public static final int MODE_SYSTEM = 0;
    public static final int MODE_LIGHT  = 1;
    public static final int MODE_DARK   = 2;

    /** Call once in AuraApp.onCreate() and at every Activity.onCreate() before setContentView. */
    public static void apply(Context context) {
        int mode = getMode(context);
        switch (mode) {
            case MODE_LIGHT:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
                break;
            case MODE_DARK:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
                break;
            default:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        }
    }

    public static void setMode(Context context, int mode) {
        SharedPreferences.Editor ed = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit();
        ed.putInt(KEY_MODE, mode).apply();
        apply(context);
    }

    public static int getMode(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                      .getInt(KEY_MODE, MODE_SYSTEM);
    }
}
