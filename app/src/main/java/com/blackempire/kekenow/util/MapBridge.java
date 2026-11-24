package com.blackempire.kekenow.util;

import android.util.Log;
import android.webkit.JavascriptInterface;

/**
 * JavaScript-to-Java bridge injected into the WebView as "Android".
 *
 * The Leaflet page can call Android.onMapReady() to signal that the map has
 * finished loading and is ready to receive route data.
 *
 * All communication Java→JS goes the other way via WebView.evaluateJavascript().
 */
public class MapBridge {

    private static final String TAG = "KekeNow";

    /** Callback fired when the Leaflet page reports itself ready. */
    public interface MapReadyListener {
        void onMapReady();
    }

    private MapReadyListener listener;

    public MapBridge(MapReadyListener listener) {
        this.listener = listener;
    }

    @JavascriptInterface
    public void onMapReady() {
        Log.d(TAG, "MapBridge: Leaflet map ready");
        if (listener != null) {
            listener.onMapReady();
        }
    }

    @JavascriptInterface
    public void logError(String message) {
        Log.e(TAG, "MapBridge JS error: " + message);
    }
}
