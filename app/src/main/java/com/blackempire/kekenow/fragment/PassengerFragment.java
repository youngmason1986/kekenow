package com.blackempire.kekenow.fragment;

import android.Manifest;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import android.app.AlertDialog;

import android.app.Fragment;

import com.blackempire.kekenow.MainActivity;
import com.blackempire.kekenow.R;
import com.blackempire.kekenow.model.FareBand;
import com.blackempire.kekenow.model.RideRecord;
import com.blackempire.kekenow.service.GeoService;
import com.blackempire.kekenow.service.VoiceService;
import com.blackempire.kekenow.store.RideStore;
import com.blackempire.kekenow.util.FareCalculator;
import com.blackempire.kekenow.util.HashUtil;
import com.blackempire.kekenow.util.LocationHelper;
import com.blackempire.kekenow.util.MapBridge;
import android.widget.Button;
import android.widget.ImageButton;


import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.UUID;

/**
 * Main screen for the Passenger mode.
 *
 * Flow: location → voice/text destination → geocode → route → fare → request.
 */
public class PassengerFragment extends Fragment {

    private static final String TAG = "KekeNow";

    // Views
    private WebView wvMap;
    private TextView tvMapError;
    private TextView tvOfflineBanner;
    private EditText etDestination;
    private ImageButton fabMic;
    private Button btnConfirm;
    private View cardFare;
    private TextView tvFareBand;
    private TextView tvFareSub;
    private Button btnRequest;
    private View layoutProgress;
    private TextView tvProgressMessage;
    private TextView tvModeLabel;

    // Services
    private VoiceService voiceService;
    private GeoService geoService;
    private LocationHelper locationHelper;
    private RideStore rideStore;

    // State
    private GeoService.LatLon originLatLon;
    private GeoService.LatLon destLatLon;
    private String destName;
    private GeoService.RouteResult currentRoute;
    private FareBand currentFare;
    private boolean mapReady = false;

    // ------------------------------------------------------------------ //
    //  Lifecycle
    // ------------------------------------------------------------------ //

    @Override
    public View onCreateView(LayoutInflater inflater,
                             ViewGroup container,
                             Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_passenger, container, false);
        bindViews(root);
        initServices();
        setupModeLabel();
        setupWebView();
        setupVoiceInput();
        setupConfirmButton();
        setupRequestButton();
        checkNetworkAndShowBanner();
        return root;
    }

    @Override
    public void onResume() {
        super.onResume();
        fetchLocation();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        locationHelper.stopUpdates();
        voiceService.shutdown();
        geoService.shutdown();
        rideStore.shutdown();
    }

    // ------------------------------------------------------------------ //
    //  View binding
    // ------------------------------------------------------------------ //

    private void bindViews(View root) {
        wvMap            = root.findViewById(R.id.wv_map);
        tvMapError       = root.findViewById(R.id.tv_map_error);
        tvOfflineBanner  = root.findViewById(R.id.tv_offline_banner);
        etDestination    = root.findViewById(R.id.et_destination);
        fabMic           = root.findViewById(R.id.fab_mic);
        btnConfirm       = root.findViewById(R.id.btn_confirm_destination);
        cardFare         = root.findViewById(R.id.card_fare);
        tvFareBand       = root.findViewById(R.id.tv_fare_band);
        tvFareSub        = root.findViewById(R.id.tv_fare_sub);
        btnRequest       = root.findViewById(R.id.btn_request_keke);
        layoutProgress   = root.findViewById(R.id.layout_progress);
        tvProgressMessage = root.findViewById(R.id.tv_progress_message);
        tvModeLabel      = root.findViewById(R.id.tv_mode_label_passenger);
    }

    // ------------------------------------------------------------------ //
    //  Services
    // ------------------------------------------------------------------ //

    private void initServices() {
        voiceService = new VoiceService();
        voiceService.init(getActivity());

        geoService    = new GeoService();
        locationHelper = new LocationHelper(getActivity());
        rideStore     = new RideStore(getActivity());
    }

    // ------------------------------------------------------------------ //
    //  Long-press to switch mode
    // ------------------------------------------------------------------ //

    private void setupModeLabel() {
        if (tvModeLabel == null) return;
        GestureDetector gd = new GestureDetector(getActivity(),
            new GestureDetector.SimpleOnGestureListener() {
                @Override
                public void onLongPress(MotionEvent e) {
                    showSwitchModeDialog();
                }
            });
        tvModeLabel.setOnTouchListener((v, event) -> {
            gd.onTouchEvent(event);
            return false;
        });
    }

    private void showSwitchModeDialog() {
        new AlertDialog.Builder(getActivity())
            .setTitle(R.string.switch_mode_title)
            .setMessage(R.string.switch_mode_message)
            .setPositiveButton(R.string.switch_mode_confirm,
                (d, w) -> ((MainActivity) getActivity()).resetMode())
            .setNegativeButton(R.string.switch_mode_cancel, null)
            .show();
    }

    // ------------------------------------------------------------------ //
    //  Location
    // ------------------------------------------------------------------ //

    private void fetchLocation() {
        locationHelper.getCurrentLocation(getActivity(), new LocationHelper.LocationCallback2() {
            @Override
            public void onLocation(Location location) {
                originLatLon = new GeoService.LatLon(location.getLatitude(), location.getLongitude());
                Log.d(TAG, "Origin: " + originLatLon.lat + "," + originLatLon.lon);
            }

            @Override
            public void onPermissionDenied(boolean permanent) {
                if (permanent) {
                    android.app.Activity a = getActivity();
                    if (a != null) {
                        new AlertDialog.Builder(a)
                            .setMessage(R.string.location_denied_message)
                            .setPositiveButton(R.string.location_open_settings,
                                (d, w) -> LocationHelper.openAppSettings(a))
                            .setNegativeButton(android.R.string.cancel, null)
                            .show();
                    }
                }
            }

            @Override
            public void onError(Exception e) {
                Log.e(TAG, "Location error", e);
            }
        });
    }

    // ------------------------------------------------------------------ //
    //  WebView / Map
    // ------------------------------------------------------------------ //

    private void setupWebView() {
        WebSettings ws = wvMap.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setDomStorageEnabled(true);
        ws.setBuiltInZoomControls(true);
        ws.setDisplayZoomControls(false);

        wvMap.addJavascriptInterface(new MapBridge(() -> {
            mapReady = true;
            Log.d(TAG, "Leaflet map ready");
        }), "Android");

        wvMap.setWebViewClient(new WebViewClient() {
            @Override
            public void onReceivedError(WebView view, WebResourceRequest request,
                                        WebResourceError error) {
                // Only hide the map for the main frame; tile errors are expected offline
                if (request.isForMainFrame()) {
                    Log.e(TAG, "WebView main frame error: " + error.getDescription());
                    wvMap.setVisibility(View.GONE);
                    tvMapError.setVisibility(View.VISIBLE);
                }
            }
        });

        wvMap.loadUrl("file:///android_asset/map.html");
    }

    private void showRouteOnMap(GeoService.LatLon origin, GeoService.LatLon dest,
                                GeoService.RouteResult route) {
        if (!mapReady) return;

        String geoJson = (route.geoJsonGeometry != null && !route.geoJsonGeometry.isEmpty())
            ? route.geoJsonGeometry : "null";

        // Escape the geoJson string for injection — wrap in JSON.stringify result
        String js = String.format(
            "javascript:showRoute(%s, %f, %f, %f, %f)",
            geoJson,
            origin.lat, origin.lon,
            dest.lat, dest.lon
        );
        wvMap.post(() -> wvMap.evaluateJavascript(js, null));
    }

    // ------------------------------------------------------------------ //
    //  Voice input
    // ------------------------------------------------------------------ //

    private void setupVoiceInput() {
        if (!voiceService.isRecognitionAvailable()) {
            fabMic.setVisibility(View.GONE);
            etDestination.setVisibility(View.VISIBLE);
            return;
        }

        fabMic.setOnClickListener(v -> {
            if (getActivity().checkSelfPermission(
                    Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, 2001);
                return;
            }
            startListening();
        });

        // Also allow text entry directly
        etDestination.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                onDestinationConfirmed(etDestination.getText().toString().trim());
                return true;
            }
            return false;
        });
    }

    private void startListening() {
        fabMic.setEnabled(false);
        voiceService.startListening(new VoiceService.VoiceListener() {
            @Override
            public void onResult(String text) {
                etDestination.setText(text);
                voiceService.speak(text);
                fabMic.setEnabled(true);
                btnConfirm.setVisibility(View.VISIBLE);
            }

            @Override
            public void onTimeout() {
                Toast.makeText(getActivity(), R.string.voice_no_result, Toast.LENGTH_SHORT).show();
                fabMic.setEnabled(true);
            }

            @Override
            public void onError(int errorCode) {
                Toast.makeText(getActivity(), R.string.voice_no_result, Toast.LENGTH_SHORT).show();
                fabMic.setEnabled(true);
            }
        });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions,
                                           int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 2001) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startListening();
            } else {
                fabMic.setVisibility(View.GONE);
            }
        } else if (requestCode == LocationHelper.REQUEST_CODE_LOCATION) {
            fetchLocation();
        }
    }

    // ------------------------------------------------------------------ //
    //  Destination confirmation → geocode → route → fare
    // ------------------------------------------------------------------ //

    private void setupConfirmButton() {
        btnConfirm.setOnClickListener(v -> {
            String text = etDestination.getText() == null
                ? "" : etDestination.getText().toString().trim();
            if (!TextUtils.isEmpty(text)) {
                onDestinationConfirmed(text);
            }
        });
    }

    private void onDestinationConfirmed(String query) {
        if (TextUtils.isEmpty(query)) return;

        showProgress("Searching for " + query + "…");
        btnConfirm.setVisibility(View.GONE);
        cardFare.setVisibility(View.GONE);
        btnRequest.setVisibility(View.GONE);

        geoService.geocode(query, new GeoService.GeoCallback() {
            @Override
            public void onResult(GeoService.LatLon location) {
                hideProgress();
                if (location == null) {
                    voiceService.speak(getString(R.string.geo_not_found));
                    Toast.makeText(getActivity(), R.string.geo_not_found, Toast.LENGTH_LONG).show();
                    btnConfirm.setVisibility(View.VISIBLE);
                    return;
                }
                destLatLon = location;
                destName   = query;
                fetchRoute();
            }

            @Override
            public void onNetworkError(java.io.IOException e) {
                hideProgress();
                android.app.Activity a = getActivity();
                if (a != null) {
                    Toast.makeText(a, R.string.geo_no_internet, Toast.LENGTH_LONG).show();
                }
                tvOfflineBanner.setVisibility(View.VISIBLE);
                btnConfirm.setVisibility(View.VISIBLE);
            }
        });
    }

    private void fetchRoute() {
        if (originLatLon == null) {
            // No location yet — show fare using 0 distance (graceful degradation)
            originLatLon = new GeoService.LatLon(6.1944, 6.7357); // Asaba city centre fallback
        }
        showProgress("Getting route…");

        geoService.route(originLatLon, destLatLon, new GeoService.RouteCallback() {
            @Override
            public void onResult(GeoService.RouteResult result) {
                hideProgress();
                currentRoute = result;
                showFare(result);
                showRouteOnMap(originLatLon, destLatLon, result);
            }

            @Override
            public void onError(Exception e) {
                hideProgress();
                Log.e(TAG, "Route error", e);
            }
        });
    }

    private void showFare(GeoService.RouteResult route) {
        currentFare = FareCalculator.estimate(route.distanceMetres);
        tvFareBand.setText(currentFare.toDisplayString());

        if (route.fallback) {
            tvFareSub.setText(getString(R.string.fare_approximate));
        } else {
            tvFareSub.setText(R.string.fare_disclaimer);
        }

        cardFare.setVisibility(View.VISIBLE);
        btnRequest.setVisibility(View.VISIBLE);
        voiceService.speak(currentFare.toSpeechString());
    }

    // ------------------------------------------------------------------ //
    //  Request
    // ------------------------------------------------------------------ //

    private void setupRequestButton() {
        btnRequest.setOnClickListener(v -> requestKeke());
    }

    private void requestKeke() {
        if (destLatLon == null || currentFare == null) return;

        String androidId = Settings.Secure.getString(
            getActivity().getContentResolver(), Settings.Secure.ANDROID_ID);
        String passengerId = HashUtil.sha256(androidId);

        RideRecord record = new RideRecord();
        record.id              = UUID.randomUUID().toString();
        record.createdAt       = isoNow();
        record.passengerId     = passengerId;
        record.status          = RideRecord.STATUS_PENDING;
        record.originLat       = originLatLon != null ? originLatLon.lat : 0;
        record.originLon       = originLatLon != null ? originLatLon.lon : 0;
        record.destLat         = destLatLon.lat;
        record.destLon         = destLatLon.lon;
        record.destName        = destName != null ? destName : "";
        record.distanceMetres  = currentRoute != null ? currentRoute.distanceMetres : 0;
        record.durationSeconds = currentRoute != null ? currentRoute.durationSeconds : 0;
        record.fareMin         = currentFare.min;
        record.fareMax         = currentFare.max;
        record.routeGeoJson    = currentRoute != null ? currentRoute.geoJsonGeometry : null;
        record.isFallback      = currentRoute != null && currentRoute.fallback;

        rideStore.create(record, new RideStore.WriteCallback() {
            @Override
            public void onSuccess(RideRecord r) {
                voiceService.speak(getString(R.string.passenger_waiting_title));
                navigateToWaiting(r);
            }

            @Override
            public void onError(Exception e) {
                Log.e(TAG, "Failed to save ride request", e);
                Toast.makeText(getActivity(), "Could not save request", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void navigateToWaiting(RideRecord record) {
        PassengerWaitingFragment waitFrag = PassengerWaitingFragment.newInstance(
            record.id,
            record.fareMin,
            record.fareMax,
            record.routeGeoJson,
            record.originLat, record.originLon,
            record.destLat, record.destLon
        );
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).navigateTo(waitFrag, true);
        }
    }

    // ------------------------------------------------------------------ //
    //  Progress overlay
    // ------------------------------------------------------------------ //

    /** ISO-8601 UTC timestamp (API 24-safe; java.time needs API 26). */
    private static String isoNow() {
        SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
        f.setTimeZone(TimeZone.getTimeZone("UTC"));
        return f.format(new Date());
    }

    private void showProgress(String message) {
        tvProgressMessage.setText(message);
        layoutProgress.setVisibility(View.VISIBLE);
    }

    private void hideProgress() {
        layoutProgress.setVisibility(View.GONE);
    }

    // ------------------------------------------------------------------ //
    //  Offline banner
    // ------------------------------------------------------------------ //

    private void checkNetworkAndShowBanner() {
        android.net.ConnectivityManager cm = (android.net.ConnectivityManager)
            getActivity().getSystemService(android.content.Context.CONNECTIVITY_SERVICE);
        if (cm == null) return;
        android.net.NetworkInfo info = cm.getActiveNetworkInfo();
        if (info == null || !info.isConnected()) {
            tvOfflineBanner.setVisibility(View.VISIBLE);
        }
    }
}
