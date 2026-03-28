package com.aura.attendix;

import android.content.Context;
import android.graphics.Bitmap;

import androidx.collection.LruCache;

import com.android.volley.Cache;
import com.android.volley.Network;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.BasicNetwork;
import com.android.volley.toolbox.DiskBasedCache;
import com.android.volley.toolbox.HurlStack;
import com.android.volley.toolbox.ImageLoader;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

/**
 * VolleySingleton — App-wide shared RequestQueue.
 *
 * WHY THIS MATTERS:
 * Creating Volley.newRequestQueue(context) every time you make a request
 * creates a new thread pool, a new HTTP connection, and a new DNS lookup.
 * That's the 4-5 second delay. A singleton queue reuses:
 *   • HTTP Keep-Alive connections (biggest win, ~2-3s saved)
 *   • Thread pool (already warm)
 *   • DNS cache
 *   • Disk response cache (GET responses served instantly on repeat)
 */
public class VolleySingleton {

    private static VolleySingleton instance;
    private RequestQueue requestQueue;
    private ImageLoader imageLoader;
    private static Context ctx;

    // 10 MB disk cache for GET responses
    private static final int DISK_CACHE_SIZE_BYTES = 10 * 1024 * 1024;

    private VolleySingleton(Context context) {
        ctx = context.getApplicationContext();
        requestQueue = getRequestQueue();

        imageLoader = new ImageLoader(requestQueue, new ImageLoader.ImageCache() {
            private final LruCache<String, Bitmap> cache = new LruCache<>(20);

            @Override
            public Bitmap getBitmap(String url) { return cache.get(url); }

            @Override
            public void putBitmap(String url, Bitmap bitmap) { cache.put(url, bitmap); }
        });
    }

    public static synchronized VolleySingleton getInstance(Context context) {
        if (instance == null) {
            instance = new VolleySingleton(context);
        }
        return instance;
    }

    public RequestQueue getRequestQueue() {
        if (requestQueue == null) {
            // Build a proper disk-backed cache instead of the default no-cache queue
            File cacheDir = new File(ctx.getCacheDir(), "volley");
            Cache cache = new DiskBasedCache(cacheDir, DISK_CACHE_SIZE_BYTES);
            Network network = new BasicNetwork(new HurlStack());
            requestQueue = new RequestQueue(cache, network);
            requestQueue.start();
        }
        return requestQueue;
    }

    public <T> void add(Request<T> req) {
        getRequestQueue().add(req);
    }

    public ImageLoader getImageLoader() {
        return imageLoader;
    }

    /**
     * Returns auth headers map — avoids repeating this block in every Activity.
     * Usage:  VolleySingleton.authHeaders(token)
     */
    public static Map<String, String> authHeaders(String token) {
        Map<String, String> headers = new HashMap<>();
        headers.put("Authorization", "Bearer " + token);
        headers.put("Content-Type", "application/json");
        headers.put("Accept", "application/json");
        return headers;
    }
}
