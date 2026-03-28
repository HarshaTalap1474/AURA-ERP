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
        // Pre-warm the singleton so the first request doesn't pay
        // the thread-pool + DNS initialisation cost.
        VolleySingleton.getInstance(this);
    }
}
