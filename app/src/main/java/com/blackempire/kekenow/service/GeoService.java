package com.blackempire.kekenow.service;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Handles Nominatim geocoding, OSRM routing, and Haversine straight-line fallback.
 *
 * All network calls run on a background thread; callbacks are posted to the main thread.
 */
public class GeoService {

    private static final String TAG = "KekeNow";

    // Nominatim settings
    private static final String NOMINATIM_BASE =
        "https://nominatim.openstreetmap.org/search";
    private static final String NOMINATIM_VIEWBOX  = "6.65,6.15,6.80,6.00"; // Asaba bbox lon_min,lat_min,lon_max,lat_max
    private static final String NOMINATIM_UA =
        "KekeNow-App/1.0 (Android; contact@blackempire.com)";

    // OSRM demo server
    private static final String OSRM_BASE =
        "https://router.project-osrm.org/route/v1/driving/";

    // Earth radius in metres for Haversine
    private static final double EARTH_RADIUS_M = 6_371_000.0;

    // ------------------------------------------------------------------ //
    //  Public data types
    // ------------------------------------------------------------------ //

    public static class LatLon {
        public final double lat;
        public final double lon;

        public LatLon(double lat, double lon) {
            this.lat = lat;
            this.lon = lon;
        }
    }

    public static class RouteResult {
        public final double distanceMetres;
        public final double durationSeconds;
        public final String geoJsonGeometry; // may be null if fallback
        public final boolean fallback;       // true when Haversine was used

        public RouteResult(double distanceMetres, double durationSeconds,
                           String geoJsonGeometry, boolean fallback) {
            this.distanceMetres  = distanceMetres;
            this.durationSeconds = durationSeconds;
            this.geoJsonGeometry = geoJsonGeometry;
            this.fallback        = fallback;
        }
    }

    public interface GeoCallback {
        void onResult(LatLon location);            // null if not found
        void onNetworkError(IOException e);
    }

    public interface RouteCallback {
        void onResult(RouteResult result);
        void onError(Exception e);
    }

    // ------------------------------------------------------------------ //
    //  Executor lifecycle
    // ------------------------------------------------------------------ //

    private final ExecutorService executor = Executors.newFixedThreadPool(2);
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public void shutdown() {
        executor.shutdownNow();
    }

    // ------------------------------------------------------------------ //
    //  Geocoding (Nominatim)
    // ------------------------------------------------------------------ //

    public void geocode(String query, GeoCallback callback) {
        executor.execute(() -> {
            try {
                String encoded = URLEncoder.encode(query, StandardCharsets.UTF_8.name());
                String urlStr = NOMINATIM_BASE
                    + "?q=" + encoded
                    + "&format=json"
                    + "&countrycodes=ng"
                    + "&viewbox=" + NOMINATIM_VIEWBOX
                    + "&bounded=1"
                    + "&limit=1";

                String body = httpGet(urlStr);
                JSONArray arr = new JSONArray(body);

                if (arr.length() == 0) {
                    mainHandler.post(() -> callback.onResult(null));
                } else {
                    JSONObject first = arr.getJSONObject(0);
                    double lat = Double.parseDouble(first.getString("lat"));
                    double lon = Double.parseDouble(first.getString("lon"));
                    LatLon loc = new LatLon(lat, lon);
                    mainHandler.post(() -> callback.onResult(loc));
                }

            } catch (IOException e) {
                Log.e(TAG, "Nominatim network error", e);
                mainHandler.post(() -> callback.onNetworkError(e));
            } catch (JSONException | NumberFormatException e) {
                Log.e(TAG, "Nominatim parse error", e);
                mainHandler.post(() -> callback.onNetworkError(
                    new IOException("Parse error: " + e.getMessage())));
            }
        });
    }

    // ------------------------------------------------------------------ //
    //  Routing (OSRM)
    // ------------------------------------------------------------------ //

    public void route(LatLon origin, LatLon dest, RouteCallback callback) {
        executor.execute(() -> {
            // Format: lon,lat (OSRM uses lon-first)
            String coords = origin.lon + "," + origin.lat
                + ";" + dest.lon + "," + dest.lat;
            String urlStr = OSRM_BASE + coords
                + "?overview=full&geometries=geojson";

            try {
                String body = httpGet(urlStr);
                JSONObject json = new JSONObject(body);
                String code = json.optString("code", "");

                if (!"Ok".equals(code)) {
                    throw new IOException("OSRM returned code: " + code);
                }

                JSONObject routeObj  = json.getJSONArray("routes").getJSONObject(0);
                double distance  = routeObj.getDouble("distance");
                double duration  = routeObj.getDouble("duration");
                String geometry  = routeObj.getJSONObject("geometry").toString();

                RouteResult result = new RouteResult(distance, duration, geometry, false);
                mainHandler.post(() -> callback.onResult(result));

            } catch (IOException e) {
                Log.e(TAG, "OSRM network error – using Haversine fallback", e);
                double dist = haversineMetres(origin, dest);
                RouteResult fallback = new RouteResult(dist, 0, null, true);
                mainHandler.post(() -> callback.onResult(fallback));

            } catch (JSONException e) {
                Log.e(TAG, "OSRM parse error – using Haversine fallback", e);
                double dist = haversineMetres(origin, dest);
                RouteResult fallback = new RouteResult(dist, 0, null, true);
                mainHandler.post(() -> callback.onResult(fallback));
            }
        });
    }

    // ------------------------------------------------------------------ //
    //  Haversine straight-line distance
    // ------------------------------------------------------------------ //

    public static double haversineMetres(LatLon a, LatLon b) {
        double dLat = Math.toRadians(b.lat - a.lat);
        double dLon = Math.toRadians(b.lon - a.lon);
        double sinDLat = Math.sin(dLat / 2);
        double sinDLon = Math.sin(dLon / 2);
        double c = sinDLat * sinDLat
            + Math.cos(Math.toRadians(a.lat))
            * Math.cos(Math.toRadians(b.lat))
            * sinDLon * sinDLon;
        return EARTH_RADIUS_M * 2 * Math.atan2(Math.sqrt(c), Math.sqrt(1 - c));
    }

    // ------------------------------------------------------------------ //
    //  Private HTTP helper
    // ------------------------------------------------------------------ //

    private String httpGet(String urlStr) throws IOException {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(urlStr);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent", NOMINATIM_UA);
            conn.setRequestProperty("Accept", "application/json");
            conn.setConnectTimeout(10_000);
            conn.setReadTimeout(10_000);

            int code = conn.getResponseCode();
            if (code < 200 || code >= 300) {
                throw new IOException("HTTP " + code + " from " + urlStr);
            }

            BufferedReader reader = new BufferedReader(
                new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            return sb.toString();
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }
}
