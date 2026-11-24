package com.blackempire.kekenow.fragment;

import android.os.Bundle;
import android.os.FileObserver;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import android.app.AlertDialog;
import android.app.Fragment;

import android.widget.ListView;

import com.blackempire.kekenow.MainActivity;
import com.blackempire.kekenow.R;
import com.blackempire.kekenow.model.RideRecord;
import com.blackempire.kekenow.model.UserProfile;
import com.blackempire.kekenow.service.VoiceService;
import com.blackempire.kekenow.store.ProfileStore;
import com.blackempire.kekenow.store.RideStore;

import java.util.ArrayList;
import java.util.List;

/**
 * Main screen for the Rider mode.
 * Shows a live job list from the shared rides/ directory.
 * Uses FileObserver to detect new/updated ride files.
 */
public class RiderFragment extends Fragment {

    private static final String TAG = "KekeNow";

    private ListView rvJobs;
    private View layoutEmpty;
    private TextView tvModeLabel;

    private RideStore rideStore;
    private VoiceService voiceService;
    private JobCardAdapter adapter;

    private FileObserver fileObserver;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private int lastKnownJobCount = 0;

    // ------------------------------------------------------------------ //
    //  Lifecycle
    // ------------------------------------------------------------------ //

    @Override
    public View onCreateView(LayoutInflater inflater,
                             ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_rider, container, false);

        rvJobs      = view.findViewById(R.id.rv_jobs);
        layoutEmpty = view.findViewById(R.id.layout_empty_jobs);
        tvModeLabel = view.findViewById(R.id.tv_mode_label_rider);

        rideStore    = new RideStore(getActivity());
        voiceService = new VoiceService();
        voiceService.init(getActivity());

        adapter = new JobCardAdapter(new ArrayList<>(), record -> openDetail(record));
        rvJobs.setAdapter(adapter);

        setupModeLabel();
        setupRiderProfileHint(view);
        refreshJobs();
        return view;
    }

    /**
     * Riders must have a complete identity (photo, name, phone, state,
     * plate, keke code) or passengers cannot see who is coming. Show a
     * banner that opens the profile form until it is complete.
     */
    private void setupRiderProfileHint(View view) {
        UserProfile me = new ProfileStore(getActivity()).getCurrent();
        boolean complete = me != null && me.hasRiderIdentity();
        View hint = view.findViewById(R.id.layout_rider_profile_hint);
        hint.setVisibility(complete ? View.GONE : View.VISIBLE);
        if (!complete) {
            view.findViewById(R.id.btn_complete_profile).setOnClickListener(v -> {
                if (getActivity() instanceof MainActivity) {
                    ((MainActivity) getActivity()).navigateTo(
                        ProfileRegisterFragment.newInstance(
                            me != null ? me.phone : "", UserProfile.ROLE_RIDER, true), true);
                }
            });
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        startFileObserver();
        refreshJobs();
    }

    @Override
    public void onPause() {
        super.onPause();
        stopFileObserver();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        stopFileObserver();
        voiceService.shutdown();
        rideStore.shutdown();
    }

    // ------------------------------------------------------------------ //
    //  Long-press to switch mode
    // ------------------------------------------------------------------ //

    private void setupModeLabel() {
        if (tvModeLabel == null) return;
        GestureDetector gd = new GestureDetector(getActivity(),
            new GestureDetector.SimpleOnGestureListener() {
                @Override
                public void onLongPress(MotionEvent e) { showSwitchModeDialog(); }
            });
        tvModeLabel.setOnTouchListener((v, event) -> { gd.onTouchEvent(event); return false; });
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
    //  FileObserver
    // ------------------------------------------------------------------ //

    @SuppressWarnings("deprecation") // FileObserver(String) constructor is fine for API 24+
    private void startFileObserver() {
        String path = rideStore.getRidesDirectory();
        fileObserver = new FileObserver(path, FileObserver.CREATE | FileObserver.MODIFY) {
            @Override
            public void onEvent(int event, String fileName) {
                if (fileName != null && fileName.endsWith(".json")) {
                    mainHandler.postDelayed(() -> refreshJobs(), 300);
                }
            }
        };
        fileObserver.startWatching();
        Log.d(TAG, "FileObserver started on " + path);
    }

    private void stopFileObserver() {
        if (fileObserver != null) {
            fileObserver.stopWatching();
            fileObserver = null;
        }
    }

    // ------------------------------------------------------------------ //
    //  Job list refresh
    // ------------------------------------------------------------------ //

    public void refreshJobs() {
        rideStore.listPending(records -> {
            int newCount = records.size();
            if (newCount > lastKnownJobCount && lastKnownJobCount >= 0) {
                voiceService.speak(getString(R.string.rider_new_job));
            }
            lastKnownJobCount = newCount;

            adapter.setRecords(records);
            layoutEmpty.setVisibility(records.isEmpty() ? View.VISIBLE : View.GONE);
            rvJobs.setVisibility(records.isEmpty() ? View.GONE : View.VISIBLE);
        });
    }

    // ------------------------------------------------------------------ //
    //  Navigation to detail
    // ------------------------------------------------------------------ //

    private void openDetail(RideRecord record) {
        RideDetailFragment detail = RideDetailFragment.newInstance(record.id);
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).navigateTo(detail, true);
        }
    }
}
