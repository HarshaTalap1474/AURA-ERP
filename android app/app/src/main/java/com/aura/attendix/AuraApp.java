package com.aura.attendix;

import android.app.Application;

/**
 * AuraApp — Application entry point.
 * Initialises VolleySingleton once so the HTTP connection pool is warm
 * before the user even taps Login.
 */
public class AuraApp extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        // Apply saved theme (Light / Dark / System) before any UI renders
        ThemeManager.apply(this);
        // Pre-warm Volley connection pool
        VolleySingleton.getInstance(this);
    }
}
