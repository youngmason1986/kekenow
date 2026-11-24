package com.blackempire.kekenow.fragment;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import android.app.Fragment;

import com.blackempire.kekenow.MainActivity;
import com.blackempire.kekenow.R;
import com.blackempire.kekenow.model.FareBand;
import com.blackempire.kekenow.model.RideRecord;
import com.blackempire.kekenow.service.VoiceService;
import com.blackempire.kekenow.store.RideStore;
import com.blackempire.kekenow.util.FareCalculator;
import com.blackempire.kekenow.util.MapBridge;
import android.widget.Button;

import android.webkit.WebSettings;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;

/**
 * "Waiting for a rider" screen shown after a successful ride request.
 * Auto-cancels after 3 minutes if no rider accepts.
 */
public class PassengerWaitingFragment extends Fragment {

    private static final String TAG = "KekeNow";
    private static final long TIMEOUT_MS = 3 * 60 * 1000L; // 3 minutes

    private static final String ARG_RIDE_ID     = "rideId";
    private static final String ARG_FARE_MIN    = "fareMin";
    private static final String ARG_FARE_MAX    = "fareMax";
    private static final String ARG_GEOJSON     = "geoJson";
    private static final String ARG_ORIGIN_LAT  = "oLat";
    private static final String ARG_ORIGIN_LON  = "oLon";
    private static final String ARG_DEST_LAT    = "dLat";
    private static final String ARG_DEST_LON    = "dLon";

    public static PassengerWaitingFragment newInstance(
            String rideId, int fareMin, int fareMax,
            String geoJson,
            double oLat, double oLon,
            double dLat, double dLon) {
        PassengerWaitingFragment f = new PassengerWaitingFragment();
        Bundle args = new Bundle();
        args.putString(ARG_RIDE_ID,    rideId);
        args.putInt(ARG_FARE_MIN,      fareMin);
        args.putInt(ARG_FARE_MAX,      fareMax);
        args.putString(ARG_GEOJSON,    geoJson != null ? geoJson : "");
        args.putDouble(ARG_ORIGIN_LAT, oLat);
        args.putDouble(ARG_ORIGIN_LON, oLon);
        args.putDouble(ARG_DEST_LAT,   dLat);
        args.putDouble(ARG_DEST_LON,   dLon);
        f.setArguments(args);
        return f;
    }

    private WebView wvMap;
    private TextView tvFare;
    private Button btnCancel;

    // Accepted-rider card
    private View cardRider;
    private ImageView ivRiderPhoto;
    private TextView tvRiderName;
    private TextView tvRiderPhone;
    private TextView tvRiderPlate;
    private TextView tvRiderTag;
    private Button btnCallRider;

    private VoiceService voiceService;
    private RideStore rideStore;

    private String rideId = "";
    private Handler timeoutHandler;
    private Runnable timeoutRunnable;
    private Handler pollHandler;
    private Runnable pollRunnable;
    private boolean riderShown = false;
    private boolean mapReady = false;

    @Override
    public View onCreateView(LayoutInflater inflater,
                             ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_passenger_waiting, container, false);

        wvMap    = view.findViewById(R.id.wv_map_waiting);
        tvFare   = view.findViewById(R.id.tv_waiting_fare);
        btnCancel = view.findViewById(R.id.btn_cancel_wait);

        cardRider    = view.findViewById(R.id.card_rider_waiting);
        ivRiderPhoto = view.findViewById(R.id.iv_rider_photo);
        tvRiderName  = view.findViewById(R.id.tv_rider_name);
        tvRiderPhone = view.findViewById(R.id.tv_rider_phone);
        tvRiderPlate = view.findViewById(R.id.tv_rider_plate);
        tvRiderTag   = view.findViewById(R.id.tv_rider_tag);
        btnCallRider = view.findViewById(R.id.btn_call_rider);

        voiceService = new VoiceService();
        voiceService.init(getActivity());
        rideStore = new RideStore(getActivity());

        Bundle args = getArguments();
        if (args == null) return view;

        rideId = args.getString(ARG_RIDE_ID, "");
        int fareMin = args.getInt(ARG_FARE_MIN, 0);
        int fareMax = args.getInt(ARG_FARE_MAX, 0);
        tvFare.setText(new FareBand(fareMin, fareMax).toDisplayString());

        setupMap(args);

        voiceService.speak(getString(R.string.passenger_waiting_title));

        btnCancel.setOnClickListener(v -> cancel());

        // 3-minute auto-cancel
        timeoutHandler = new Handler(Looper.getMainLooper());
        timeoutRunnable = this::onTimeout;
        timeoutHandler.postDelayed(timeoutRunnable, TIMEOUT_MS);

        // Poll the ride file: a rider on this device accepts from Rider mode.
        startPolling();
        return view;
    }

    // ------------------------------------------------------------------ //
    //  Accepted-rider polling + card
    // ------------------------------------------------------------------ //

    private static final long POLL_INTERVAL_MS = 2000L;

    private void startPolling() {
        if (rideId.isEmpty()) return;
        pollHandler = new Handler(Looper.getMainLooper());
        pollRunnable = new Runnable() {
            @Override
            public void run() {
                rideStore.get(rideId, record -> {
                    if (record != null
                            && RideRecord.STATUS_ACCEPTED.equals(record.status)
                            && !riderShown) {
                        showRider(record);
                    }
                });
                if (!riderShown && pollHandler != null) {
                    pollHandler.postDelayed(this, POLL_INTERVAL_MS);
                }
            }
        };
        pollHandler.postDelayed(pollRunnable, POLL_INTERVAL_MS);
    }

    private void stopPolling() {
        if (pollHandler != null && pollRunnable != null) {
            pollHandler.removeCallbacks(pollRunnable);
        }
    }

    /** Show the accepted rider's photo, name, phone, plate and keke code. */
    private void showRider(RideRecord record) {
        riderShown = true;
        stopPolling();

        String name = record.riderName != null && !record.riderName.isEmpty()
            ? record.riderName : getString(R.string.rider_card_title);
        tvRiderName.setText(name);
        tvRiderPhone.setText(record.riderPhone != null ? record.riderPhone : "");
        tvRiderPlate.setText(record.riderPlateNumber != null && !record.riderPlateNumber.isEmpty()
            ? getString(R.string.rider_plate_label, record.riderPlateNumber) : "");
        tvRiderTag.setText(record.riderStateTag != null && !record.riderStateTag.isEmpty()
            ? getString(R.string.rider_tag_label, record.riderStateTag) : "");
        if (record.riderPhotoPath != null && !record.riderPhotoPath.isEmpty()) {
            Bitmap bitmap = BitmapFactory.decodeFile(record.riderPhotoPath);
            if (bitmap != null) ivRiderPhoto.setImageBitmap(bitmap);
        }
        final String phone = record.riderPhone;
        btnCallRider.setOnClickListener(v -> {
            if (phone != null && !phone.isEmpty()) {
                startActivity(new Intent(Intent.ACTION_DIAL,
                    Uri.parse("tel:" + phone)));
            }
        });
        cardRider.setVisibility(View.VISIBLE);

        // Speak the match details: name + the painted keke code (or plate).
        String spokenCode = record.riderStateTag != null && !record.riderStateTag.isEmpty()
            ? record.riderStateTag : record.riderPlateNumber;
        voiceService.speak(getString(R.string.rider_accepted_with_name, name,
            spokenCode != null ? spokenCode : ""));
    }

    private void setupMap(Bundle args) {
        WebSettings ws = wvMap.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setDomStorageEnabled(true);
        ws.setBuiltInZoomControls(true);
        ws.setDisplayZoomControls(false);

        wvMap.addJavascriptInterface(new MapBridge(() -> {
            mapReady = true;
            pushRouteToMap(args);
        }), "Android");

        wvMap.setWebViewClient(new WebViewClient() {
            @Override
            public void onReceivedError(WebView view, WebResourceRequest request,
                                        WebResourceError error) {
                if (request.isForMainFrame()) {
                    Log.e(TAG, "Waiting map error: " + error.getDescription());
                }
            }
        });

        wvMap.loadUrl("file:///android_asset/map.html");
    }

    private void pushRouteToMap(Bundle args) {
        if (!mapReady || args == null) return;
        String geoJson = args.getString(ARG_GEOJSON, "");
        double oLat = args.getDouble(ARG_ORIGIN_LAT);
        double oLon = args.getDouble(ARG_ORIGIN_LON);
        double dLat = args.getDouble(ARG_DEST_LAT);
        double dLon = args.getDouble(ARG_DEST_LON);

        String safeGeoJson = (geoJson.isEmpty()) ? "null" : geoJson;
        String js = String.format(
            "javascript:showRoute(%s, %f, %f, %f, %f)",
            safeGeoJson, oLat, oLon, dLat, dLon);
        wvMap.post(() -> wvMap.evaluateJavascript(js, null));
    }

    private void onTimeout() {
        stopPolling();
        voiceService.speak(getString(R.string.passenger_no_rider));
        returnToPassenger();
    }

    private void cancel() {
        stopPolling();
        if (timeoutRunnable != null) {
            timeoutHandler.removeCallbacks(timeoutRunnable);
        }
        Bundle args = getArguments();
        if (args != null) {
            String rideId = args.getString(ARG_RIDE_ID, "");
            if (!rideId.isEmpty()) {
                rideStore.updateStatus(rideId, "declined", new RideStore.WriteCallback() {
                    @Override public void onSuccess(com.blackempire.kekenow.model.RideRecord r) {}
                    @Override public void onError(Exception e) {
                        Log.e(TAG, "Failed to cancel ride " + rideId, e);
                    }
                });
            }
        }
        returnToPassenger();
    }

    private void returnToPassenger() {
        if (getActivity() instanceof MainActivity) {
            getActivity().getFragmentManager().popBackStack();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        stopPolling();
        if (timeoutHandler != null && timeoutRunnable != null) {
            timeoutHandler.removeCallbacks(timeoutRunnable);
        }
        voiceService.shutdown();
        rideStore.shutdown();
    }
}
