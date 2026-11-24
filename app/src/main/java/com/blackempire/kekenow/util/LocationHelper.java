package com.blackempire.kekenow.util;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;

/**
 * Location helper built on android.location.LocationManager only.
 * No Play Services: this machine builds offline and the app must run on any
 * stock Android device. Delivers a single location fix and then stops.
 * (Replaces the FusedLocationProviderClient draft — Play Services is not
 * available in the offline build repo.)
 */
public class LocationHelper {

    public static final int REQUEST_CODE_LOCATION = 1001;
    private static final String TAG = "KekeNow";
    private static final long STALE_THRESHOLD_MS = 60_000L;

    public interface LocationCallback2 {
        void onLocation(Location location);
        void onPermissionDenied(boolean permanent);
        void onError(Exception e);
    }

    private final Context context;
    private final LocationManager locationManager;
    private LocationListener activeListener;

    public LocationHelper(Context context) {
        this.context = context.getApplicationContext();
        this.locationManager = (LocationManager) this.context.getSystemService(Context.LOCATION_SERVICE);
    }

    /**
     * Returns the best available location.
     * Uses last-known if fresh enough; otherwise requests a single update.
     */
    public void getCurrentLocation(Activity activity, LocationCallback2 cb) {
        if (!hasAnyLocationPermission(activity)) {
            activity.requestPermissions(
                new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                },
                REQUEST_CODE_LOCATION
            );
            // Result handled in onRequestPermissionsResult → call getCurrentLocation again
            return;
        }

        try {
            Location last = null;
            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                last = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            }
            if (last == null && locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                last = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
            }
            if (last != null && isFresh(last)) {
                cb.onLocation(last);
                return;
            }
            requestSingleUpdate(cb);
        } catch (SecurityException e) {
            Log.e(TAG, "Location permission missing", e);
            cb.onError(e);
        }
    }

    private void requestSingleUpdate(final LocationCallback2 cb) {
        final String provider = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
                ? LocationManager.GPS_PROVIDER : LocationManager.NETWORK_PROVIDER;
        activeListener = new LocationListener() {
            @Override
            public void onLocationChanged(Location location) {
                stopUpdates();
                cb.onLocation(location);
            }

            @Override
            public void onStatusChanged(String provider, int status, Bundle extras) {
            }

            @Override
            public void onProviderEnabled(String provider) {
            }

            @Override
            public void onProviderDisabled(String provider) {
                stopUpdates();
                cb.onError(new Exception("Location provider disabled: " + provider));
            }
        };
        try {
            locationManager.requestSingleUpdate(provider, activeListener, Looper.getMainLooper());
        } catch (SecurityException e) {
            Log.e(TAG, "Location permission missing at requestSingleUpdate", e);
            cb.onError(e);
        } catch (IllegalArgumentException e) {
            Log.e(TAG, "No such provider: " + provider, e);
            cb.onError(e);
        }
    }

    /** Stop any pending location update. Call from onPause/onDestroy. */
    public void stopUpdates() {
        if (activeListener != null) {
            try {
                locationManager.removeUpdates(activeListener);
            } catch (Exception ignored) {
            }
            activeListener = null;
        }
    }

    // ------------------------------------------------------------------ //

    public static boolean hasAnyLocationPermission(Context ctx) {
        return ctx.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED
            || ctx.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    /**
     * Opens the system app-settings screen so the user can manually grant
     * location permission after a permanent denial.
     */
    public static void openAppSettings(Activity activity) {
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        intent.setData(Uri.fromParts("package", activity.getPackageName(), null));
        activity.startActivity(intent);
    }

    private static boolean isFresh(Location loc) {
        return (System.currentTimeMillis() - loc.getTime()) < STALE_THRESHOLD_MS;
    }
}
