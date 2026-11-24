package com.blackempire.kekenow.fragment;

import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;


import android.app.Fragment;

import com.blackempire.kekenow.R;
import com.blackempire.kekenow.model.FareBand;
import com.blackempire.kekenow.model.RideRecord;
import com.blackempire.kekenow.model.UserProfile;
import com.blackempire.kekenow.service.VoiceService;
import com.blackempire.kekenow.store.ProfileStore;
import com.blackempire.kekenow.store.RideStore;
import com.blackempire.kekenow.util.MapBridge;
import android.widget.Button;
import android.widget.Toast;

import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.util.Locale;

/**
 * Shows the details of a single ride and lets the rider accept, decline, or complete it.
 */
public class RideDetailFragment extends Fragment {

    private static final String TAG = "KekeNow";
    private static final String ARG_RIDE_ID = "rideId";

    public static RideDetailFragment newInstance(String rideId) {
        RideDetailFragment f = new RideDetailFragment();
        Bundle args = new Bundle();
        args.putString(ARG_RIDE_ID, rideId);
        f.setArguments(args);
        return f;
    }

    // Views
    private WebView wvMap;
    private TextView tvMapError;
    private TextView tvDestination;
    private TextView tvStatusBadge;
    private TextView tvDistance;
    private TextView tvFare;
    private Button btnAccept;
    private Button btnDecline;
    private Button btnComplete;
    private View layoutActions;

    // Services / store
    private RideStore rideStore;
    private VoiceService voiceService;

    private RideRecord currentRecord;
    private boolean mapReady = false;

    // ------------------------------------------------------------------ //
    //  Lifecycle
    // ------------------------------------------------------------------ //

    @Override
    public View onCreateView(LayoutInflater inflater,
                             ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_ride_detail, container, false);

        bindViews(view);

        rideStore    = new RideStore(getActivity());
        voiceService = new VoiceService();
        voiceService.init(getActivity());

        view.findViewById(R.id.btn_back).setOnClickListener(v -> {
            android.app.Activity a = getActivity();
            if (a != null) a.getFragmentManager().popBackStack();
        });

        String rideId = getArguments() != null ? getArguments().getString(ARG_RIDE_ID, "") : "";
        if (!rideId.isEmpty()) {
            loadRide(rideId);
        }
        return view;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        voiceService.shutdown();
        rideStore.shutdown();
    }

    // ------------------------------------------------------------------ //
    //  View binding
    // ------------------------------------------------------------------ //

    private void bindViews(View v) {
        wvMap         = v.findViewById(R.id.wv_map);
        tvMapError    = v.findViewById(R.id.tv_map_error);
        tvDestination = v.findViewById(R.id.tv_detail_destination);
        tvStatusBadge = v.findViewById(R.id.tv_detail_status_badge);
        tvDistance    = v.findViewById(R.id.tv_detail_distance);
        tvFare        = v.findViewById(R.id.tv_detail_fare);
        btnAccept     = v.findViewById(R.id.btn_accept_job);
        btnDecline    = v.findViewById(R.id.btn_decline_job);
        btnComplete   = v.findViewById(R.id.btn_complete_job);
        layoutActions = v.findViewById(R.id.layout_action_buttons);
    }

    // ------------------------------------------------------------------ //
    //  Load ride from store
    // ------------------------------------------------------------------ //

    private void loadRide(String rideId) {
        rideStore.get(rideId, record -> {
            if (record == null) {
                Log.e(TAG, "Ride not found: " + rideId);
                return;
            }
            currentRecord = record;
            populateViews(record);
            setupMap(record);
            setupButtons(record);
        });
    }

    private void populateViews(RideRecord r) {
        String dest = (r.destName != null && !r.destName.isEmpty())
            ? r.destName
            : String.format(Locale.US, "%.4f, %.4f", r.destLat, r.destLon);
        tvDestination.setText(dest);

        double km = r.distanceMetres / 1000.0;
        tvDistance.setText(String.format(Locale.US, "%.1f km", km));

        tvFare.setText(new FareBand(r.fareMin, r.fareMax).toDisplayString());

        tvStatusBadge.setText(statusLabel(r.status));
        applyStatusBadgeColor(r.status);
    }

    private void setupButtons(RideRecord r) {
        boolean isPending  = RideRecord.STATUS_PENDING.equals(r.status);
        boolean isAccepted = RideRecord.STATUS_ACCEPTED.equals(r.status);

        layoutActions.setVisibility(isPending  ? View.VISIBLE : View.GONE);
        btnComplete.setVisibility(isAccepted ? View.VISIBLE : View.GONE);

        btnAccept.setOnClickListener(v -> acceptJob(r));
        btnDecline.setOnClickListener(v -> declineJob(r));
        btnComplete.setOnClickListener(v -> completeJob(r));
    }

    // ------------------------------------------------------------------ //
    //  Actions
    // ------------------------------------------------------------------ //

    private void acceptJob(RideRecord r) {
        // Attach this rider's profile so the passenger can see who is
        // coming: photo, name, phone, FRSC plate and keke code / union tag.
        UserProfile me = new ProfileStore(getActivity()).getCurrent();
        if (me != null && me.isRider()) {
            r.riderName        = me.name;
            r.riderPhone       = me.phone;
            r.riderPhotoPath   = me.photoPath;
            r.riderPlateNumber = me.plateNumber;
            r.riderStateTag    = me.stateTag;
            if (!me.hasRiderIdentity()) {
                Toast.makeText(getActivity(),
                    R.string.rider_profile_missing, Toast.LENGTH_LONG).show();
            }
        } else {
            Toast.makeText(getActivity(),
                R.string.rider_profile_missing, Toast.LENGTH_LONG).show();
        }
        r.status = RideRecord.STATUS_ACCEPTED;
        rideStore.update(r, new RideStore.WriteCallback() {
            @Override
            public void onSuccess(RideRecord updated) {
                voiceService.speak(getString(R.string.rider_accepted_speech));
                // Refresh UI: show Complete button, hide Accept/Decline
                layoutActions.setVisibility(View.GONE);
                btnComplete.setVisibility(View.VISIBLE);
                tvStatusBadge.setText(statusLabel(RideRecord.STATUS_ACCEPTED));
                applyStatusBadgeColor(RideRecord.STATUS_ACCEPTED);
                currentRecord = updated;
            }

            @Override
            public void onError(Exception e) {
                Log.e(TAG, "Failed to accept job " + r.id, e);
            }
        });
    }

    private void declineJob(RideRecord r) {
        rideStore.updateStatus(r.id, RideRecord.STATUS_DECLINED, new RideStore.WriteCallback() {
            @Override
            public void onSuccess(RideRecord updated) {
                getActivity().getFragmentManager().popBackStack();
            }

            @Override
            public void onError(Exception e) {
                Log.e(TAG, "Failed to decline job " + r.id, e);
                getActivity().getFragmentManager().popBackStack();
            }
        });
    }

    private void completeJob(RideRecord r) {
        rideStore.updateStatus(r.id, RideRecord.STATUS_COMPLETED, new RideStore.WriteCallback() {
            @Override
            public void onSuccess(RideRecord updated) {
                voiceService.speak(getString(R.string.rider_complete_speech));
                getActivity().getFragmentManager().popBackStack();
            }

            @Override
            public void onError(Exception e) {
                Log.e(TAG, "Failed to complete job " + r.id, e);
                getActivity().getFragmentManager().popBackStack();
            }
        });
    }

    // ------------------------------------------------------------------ //
    //  Map
    // ------------------------------------------------------------------ //

    private void setupMap(RideRecord r) {
        WebSettings ws = wvMap.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setDomStorageEnabled(true);
        ws.setBuiltInZoomControls(true);
        ws.setDisplayZoomControls(false);

        wvMap.addJavascriptInterface(new MapBridge(() -> {
            mapReady = true;
            pushRouteToMap(r);
        }), "Android");

        wvMap.setWebViewClient(new WebViewClient() {
            @Override
            public void onReceivedError(WebView view, WebResourceRequest request,
                                        WebResourceError error) {
                if (request.isForMainFrame()) {
                    Log.e(TAG, "RideDetail map error: " + error.getDescription());
                    wvMap.setVisibility(View.GONE);
                    tvMapError.setVisibility(View.VISIBLE);
                }
            }
        });

        wvMap.loadUrl("file:///android_asset/map.html");
    }

    private void pushRouteToMap(RideRecord r) {
        if (!mapReady) return;
        String geoJson = (r.routeGeoJson != null && !r.routeGeoJson.isEmpty())
            ? r.routeGeoJson : "null";
        String js = String.format(
            "javascript:showRoute(%s, %f, %f, %f, %f)",
            geoJson, r.originLat, r.originLon, r.destLat, r.destLon);
        wvMap.post(() -> wvMap.evaluateJavascript(js, null));
    }

    // ------------------------------------------------------------------ //
    //  Status badge helpers
    // ------------------------------------------------------------------ //

    private String statusLabel(String status) {
        if (status == null) return "?";
        switch (status) {
            case RideRecord.STATUS_ACCEPTED:  return getString(R.string.status_accepted);
            case RideRecord.STATUS_COMPLETED: return getString(R.string.status_completed);
            case RideRecord.STATUS_DECLINED:  return getString(R.string.status_declined);
            default:                          return getString(R.string.status_pending);
        }
    }

    private void applyStatusBadgeColor(String status) {
        int colorRes;
        switch (status != null ? status : "") {
            case RideRecord.STATUS_ACCEPTED:  colorRes = R.color.status_accepted;  break;
            case RideRecord.STATUS_COMPLETED: colorRes = R.color.status_completed; break;
            case RideRecord.STATUS_DECLINED:  colorRes = R.color.status_declined;  break;
            default:                          colorRes = R.color.status_pending;   break;
        }
        GradientDrawable bg = (GradientDrawable) getActivity().getDrawable(R.drawable.bg_status_badge);
        if (bg != null) {
            bg = (GradientDrawable) bg.mutate();
            bg.setColor(getActivity().getColor(colorRes));
            tvStatusBadge.setBackground(bg);
        }
    }
}
