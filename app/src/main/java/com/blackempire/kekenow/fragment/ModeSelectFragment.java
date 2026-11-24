package com.blackempire.kekenow.fragment;

import android.app.Fragment;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.blackempire.kekenow.MainActivity;
import com.blackempire.kekenow.R;
import com.blackempire.kekenow.model.UserProfile;
import com.blackempire.kekenow.store.ProfileStore;

/**
 * Role-choice screen — the entry point for anyone who downloads the app.
 *
 * First launch: the user picks <b>Rider</b> or <b>Customer</b>, and the
 * sign-up that follows matches the choice: riders get the full profile
 * (photo, name, phone, state, FRSC plate, keke code / union tag);
 * customers give name and phone only.
 *
 * Once signed in, the same screen acts as the mode switcher, and shows
 * the signed-in profile (photo, name) with Edit / Sign out.
 * Framework android.app.Fragment (no AndroidX).
 */
public class ModeSelectFragment extends Fragment {

    @Override
    public View onCreateView(LayoutInflater inflater,
                             ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_mode_select, container, false);
        ProfileStore profileStore = new ProfileStore(getActivity());
        boolean signedIn = profileStore.isSignedIn();

        view.findViewById(R.id.card_passenger).setOnClickListener(
            v -> onRolePicked(MainActivity.MODE_PASSENGER, signedIn));
        view.findViewById(R.id.card_rider).setOnClickListener(
            v -> onRolePicked(MainActivity.MODE_RIDER, signedIn));

        // Signed-in profile row (hidden for first-launch visitors).
        View row = view.findViewById(R.id.layout_profile_row);
        UserProfile me = profileStore.getCurrent();
        if (signedIn && me != null) {
            row.setVisibility(View.VISIBLE);
            ((TextView) view.findViewById(R.id.tv_profile_name)).setText(me.name);
            ImageView iv = view.findViewById(R.id.iv_profile_photo);
            if (me.photoPath != null) {
                Bitmap bitmap = BitmapFactory.decodeFile(me.photoPath);
                if (bitmap != null) iv.setImageBitmap(bitmap);
            }
            view.findViewById(R.id.btn_edit_profile).setOnClickListener(v -> {
                if (getActivity() instanceof MainActivity) {
                    ((MainActivity) getActivity()).navigateTo(
                        ProfileRegisterFragment.newInstance(me.phone, me.role, true), true);
                }
            });
            view.findViewById(R.id.btn_sign_out).setOnClickListener(v -> {
                profileStore.signOut();
                if (getActivity() instanceof MainActivity) {
                    ((MainActivity) getActivity()).showAuth();
                }
            });
        } else {
            row.setVisibility(View.GONE);
        }
        return view;
    }

    private void onRolePicked(String mode, boolean signedIn) {
        if (!(getActivity() instanceof MainActivity)) return;
        MainActivity activity = (MainActivity) getActivity();
        if (signedIn) {
            // Returning user: the role choice is a mode switch.
            activity.setMode(mode);
        } else {
            // First launch: the role choice starts the matching sign-up.
            String role = MainActivity.MODE_RIDER.equals(mode)
                ? UserProfile.ROLE_RIDER : UserProfile.ROLE_PASSENGER;
            activity.navigateTo(ProfileRegisterFragment.newInstance("", role, false), true);
        }
    }
}
