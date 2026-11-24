package com.blackempire.kekenow.fragment;

import android.app.Fragment;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.blackempire.kekenow.MainActivity;
import com.blackempire.kekenow.R;
import com.blackempire.kekenow.model.UserProfile;
import com.blackempire.kekenow.store.ProfileStore;

/**
 * Sign-up / sign-in screen (local only, no server, no password).
 *
 * The phone number IS the account: entering the number of a profile
 * already saved on this device signs straight back in; any other
 * number starts registration ({@link ProfileRegisterFragment}).
 * Shown first on launch until the user is signed in.
 */
public class AuthFragment extends Fragment {

    private EditText etPhone;
    private TextView tvWelcomeBack;
    private ProfileStore profileStore;

    @Override
    public View onCreateView(LayoutInflater inflater,
                             ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_auth, container, false);

        profileStore = new ProfileStore(getActivity());
        etPhone = view.findViewById(R.id.et_auth_phone);
        tvWelcomeBack = view.findViewById(R.id.tv_auth_welcome_back);

        UserProfile saved = profileStore.getCurrent();
        if (saved != null && saved.phone != null && !saved.phone.isEmpty()) {
            etPhone.setText(saved.phone);
            tvWelcomeBack.setVisibility(View.VISIBLE);
            tvWelcomeBack.setText(getString(R.string.auth_welcome_back, saved.name));
        }

        view.findViewById(R.id.btn_auth_continue).setOnClickListener(v -> onContinue());
        return view;
    }

    private void onContinue() {
        String phone = etPhone.getText() != null
            ? etPhone.getText().toString().trim() : "";
        if (phone.isEmpty()) {
            Toast.makeText(getActivity(), R.string.auth_need_phone, Toast.LENGTH_LONG).show();
            return;
        }

        UserProfile saved = profileStore.getCurrent();
        if (saved != null && normalize(saved.phone).equals(normalize(phone))) {
            // Same number as the saved profile: sign straight back in.
            profileStore.markSignedIn();
            goToModeSelect();
            return;
        }

        // New number (or first run): register a profile.
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).navigateTo(
                ProfileRegisterFragment.newInstance(phone, null, false), true);
        }
    }

    private void goToModeSelect() {
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).navigateTo(new ModeSelectFragment(), false);
        }
    }

    /** Compare phone numbers ignoring spaces, dashes and a leading +. */
    private static String normalize(String phone) {
        if (phone == null) return "";
        return phone.replaceAll("[\\s\\-()]", "").replaceFirst("^\\+", "");
    }
}
