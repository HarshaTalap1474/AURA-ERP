package com.aura.attendix;

import com.android.volley.AuthFailureError;
import com.android.volley.DefaultRetryPolicy;
import com.android.volley.Request;
import com.android.volley.Response;
import com.android.volley.toolbox.JsonObjectRequest;

import org.json.JSONObject;

import java.util.Map;

/**
 * AuraRequest — convenience factory for Volley requests with proper timeout config.
 *
 * Default Volley timeout is 2500ms with 1 retry = 5s total (explains the 4-5s lag).
 * We set a tight 6s socket timeout with 0 retries so failures are reported quickly,
 * and all requests are dispatched through VolleySingleton for connection reuse.
 */
public class AuraRequest {

    // 6 seconds — enough for Cloudflare tunnel overhead, fails fast on error
    private static final int TIMEOUT_MS     = 6000;
    private static final int MAX_RETRIES    = 0;       // Fail fast; don't double-wait
    private static final float BACKOFF      = 1.0f;

    /**
     * Makes a JSON GET request through the shared queue.
     */
    public static void get(
            android.content.Context context,
            String url,
            Map<String, String> headers,
            Response.Listener<JSONObject> onSuccess,
            Response.ErrorListener onError) {

        JsonObjectRequest req = new JsonObjectRequest(Request.Method.GET, url, null, onSuccess, onError) {
            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                return headers != null ? headers : super.getHeaders();
            }
        };

        req.setRetryPolicy(new DefaultRetryPolicy(TIMEOUT_MS, MAX_RETRIES, BACKOFF));
        VolleySingleton.getInstance(context).add(req);
    }

    /**
     * Makes a JSON POST request through the shared queue.
     */
    public static void post(
            android.content.Context context,
            String url,
            JSONObject body,
            Map<String, String> headers,
            Response.Listener<JSONObject> onSuccess,
            Response.ErrorListener onError) {

        JsonObjectRequest req = new JsonObjectRequest(Request.Method.POST, url, body, onSuccess, onError) {
            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                return headers != null ? headers : super.getHeaders();
            }
        };

        req.setRetryPolicy(new DefaultRetryPolicy(TIMEOUT_MS, MAX_RETRIES, BACKOFF));
        VolleySingleton.getInstance(context).add(req);
    }
}
