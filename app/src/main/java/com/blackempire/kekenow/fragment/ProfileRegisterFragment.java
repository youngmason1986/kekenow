package com.blackempire.kekenow.fragment;

import android.app.Activity;
import android.app.Fragment;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.blackempire.kekenow.MainActivity;
import com.blackempire.kekenow.R;
import com.blackempire.kekenow.model.UserProfile;
import com.blackempire.kekenow.store.ProfileStore;
import com.blackempire.kekenow.util.TricycleCodes;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.UUID;

/**
 * Sign-up / profile screen, branched by the role chosen on the
 * first-launch role-choice screen.
 *
 * <ul>
 *   <li><b>Rider</b> — full profile: photo upload, name, phone, state
 *       picker, FRSC plate number and keke code / union tag. The state
 *       picker sets the expected tag format and plate example for that
 *       state (see {@link TricycleCodes} / docs/nigeria-tricycle-codes.md).
 *       The plate shape is checked (ABC-123-XY, old AB123-CDE or Kano
 *       KNT-123-A); the tag is required in states with a known format
 *       (Delta, Enugu, Lagos, Anambra), optional elsewhere, and not
 *       applicable in Kano (the KNT plate series is the identifier).</li>
 *   <li><b>Customer</b> — name and phone number only.</li>
 * </ul>
 *
 * The numbers are only shape-checked — the app never claims to verify
 * them against government records (no public register exists), and the
 * screen says so.
 *
 * Demo scope: local storage only ({@link ProfileStore}); no server.
 */
public class ProfileRegisterFragment extends Fragment {

    private static final String ARG_PHONE = "phone";
    private static final String ARG_ROLE  = "role";
    private static final String ARG_EDIT  = "edit";
    private static final int    PICK_PHOTO = 1001;

    public static ProfileRegisterFragment newInstance(String phone, String role, boolean edit) {
        ProfileRegisterFragment f = new ProfileRegisterFragment();
        Bundle args = new Bundle();
        args.putString(ARG_PHONE, phone != null ? phone : "");
        args.putString(ARG_ROLE, role != null ? role : UserProfile.ROLE_PASSENGER);
        args.putBoolean(ARG_EDIT, edit);
        f.setArguments(args);
        return f;
    }

    private View layoutPhotoSection;
    private View layoutRiderFields;
    private ImageView ivPhoto;
    private EditText etName;
    private EditText etPhone;
    private Spinner spState;
    private EditText etPlateNumber;
    private EditText etStateTag;
    private TextView tvTagNote;
    private TextView tvNotVerifiedNote;
    private ProfileStore profileStore;

    private UserProfile editing;   // non-null when editing an existing profile
    private String photoPath;      // internal-storage path of the picked photo
    private String selectedState = TricycleCodes.DEFAULT_STATE;

    @Override
    public View onCreateView(LayoutInflater inflater,
                             ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_profile_register, container, false);
        profileStore = new ProfileStore(getActivity());

        layoutPhotoSection = view.findViewById(R.id.layout_photo_section);
        layoutRiderFields  = view.findViewById(R.id.layout_rider_fields);
        ivPhoto       = view.findViewById(R.id.iv_profile_photo);
        etName        = view.findViewById(R.id.et_profile_name);
        etPhone       = view.findViewById(R.id.et_profile_phone);
        spState       = view.findViewById(R.id.sp_state);
        etPlateNumber = view.findViewById(R.id.et_plate_number);
        etStateTag    = view.findViewById(R.id.et_state_tag);
        tvTagNote     = view.findViewById(R.id.tv_tag_note);
        tvNotVerifiedNote = view.findViewById(R.id.tv_not_verified_note);

        Bundle args = getArguments();
        boolean edit = args != null && args.getBoolean(ARG_EDIT, false);
        String role = args != null
            ? args.getString(ARG_ROLE, UserProfile.ROLE_PASSENGER)
            : UserProfile.ROLE_PASSENGER;
        final boolean isRider = UserProfile.ROLE_RIDER.equals(role);

        if (edit) {
            editing = profileStore.getCurrent();
        }
        if (editing != null) {
            etName.setText(editing.name);
            etPhone.setText(editing.phone);
            photoPath = editing.photoPath;
            showPhotoPreview();
            if (editing.state != null && !editing.state.isEmpty()) {
                selectedState = editing.state;
            }
            etPlateNumber.setText(editing.plateNumber != null ? editing.plateNumber : "");
            etStateTag.setText(editing.stateTag != null ? editing.stateTag : "");
        } else if (args != null) {
            etPhone.setText(args.getString(ARG_PHONE, ""));
        }

        // Rider-only sections: photo + state/plate/tag. Customers get name + phone only.
        layoutPhotoSection.setVisibility(isRider ? View.VISIBLE : View.GONE);
        layoutRiderFields.setVisibility(isRider ? View.VISIBLE : View.GONE);

        if (isRider) {
            ArrayAdapter<String> adapter = new ArrayAdapter<>(
                getActivity(), android.R.layout.simple_spinner_item, TricycleCodes.STATES);
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            spState.setAdapter(adapter);
            int sel = TricycleCodes.indexOfState(selectedState);
            spState.setSelection(sel >= 0 ? sel : TricycleCodes.indexOfState(TricycleCodes.DEFAULT_STATE));
            spState.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parent, View v, int position, long id) {
                    selectedState = TricycleCodes.STATES[position];
                    updateStateHints();
                }

                @Override
                public void onNothingSelected(AdapterView<?> parent) {}
            });
            updateStateHints();
        }

        ivPhoto.setOnClickListener(v -> pickPhoto());
        view.findViewById(R.id.btn_save_profile).setOnClickListener(v -> onSave(isRider));
        return view;
    }

    /** Refresh plate/tag hints for the picked state. */
    private void updateStateHints() {
        etPlateNumber.setHint(getString(R.string.profile_plate_hint,
            TricycleCodes.plateExampleFor(selectedState)));
        String tagHint = TricycleCodes.tagHintFor(selectedState);
        if (TricycleCodes.hasNoSeparateTag(selectedState)) {
            etStateTag.setVisibility(View.GONE);
            tvTagNote.setVisibility(View.VISIBLE);
            tvTagNote.setText(R.string.profile_tag_kano_note);
        } else {
            etStateTag.setVisibility(View.VISIBLE);
            etStateTag.setHint(tagHint);
            tvTagNote.setVisibility(View.VISIBLE);
            tvTagNote.setText(TricycleCodes.tagRequiredFor(selectedState)
                ? getString(R.string.profile_tag_required_note, selectedState)
                : getString(R.string.profile_tag_optional_note, selectedState));
        }
    }

    private void pickPhoto() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("image/*");
        startActivityForResult(Intent.createChooser(intent, getString(R.string.profile_photo_hint)), PICK_PHOTO);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_PHOTO && resultCode == Activity.RESULT_OK
                && data != null && data.getData() != null) {
            Uri uri = data.getData();
            String path = profileStore.savePhoto(uri);
            if (path != null) {
                photoPath = path;
                showPhotoPreview();
            } else {
                Toast.makeText(getActivity(), R.string.profile_photo_failed, Toast.LENGTH_LONG).show();
            }
        }
    }

    private void showPhotoPreview() {
        if (photoPath == null) return;
        Bitmap bitmap = BitmapFactory.decodeFile(photoPath);
        if (bitmap != null) {
            ivPhoto.setImageBitmap(bitmap);
        }
    }

    private void onSave(boolean isRider) {
        String name = etName.getText() != null ? etName.getText().toString().trim() : "";
        String phone = etPhone.getText() != null ? etPhone.getText().toString().trim() : "";

        if (name.isEmpty()) {
            Toast.makeText(getActivity(), R.string.profile_need_name, Toast.LENGTH_LONG).show();
            return;
        }
        if (phone.isEmpty()) {
            Toast.makeText(getActivity(), R.string.profile_need_phone, Toast.LENGTH_LONG).show();
            return;
        }

        String plate = null;
        String tag = null;
        if (isRider) {
            String rawPlate = etPlateNumber.getText() != null
                ? etPlateNumber.getText().toString().trim() : "";
            if (rawPlate.isEmpty()) {
                Toast.makeText(getActivity(), R.string.profile_need_plate, Toast.LENGTH_LONG).show();
                return;
            }
            if (!TricycleCodes.isValidPlate(rawPlate)) {
                Toast.makeText(getActivity(),
                    getString(R.string.profile_bad_plate,
                        TricycleCodes.plateExampleFor(selectedState)),
                    Toast.LENGTH_LONG).show();
                return;
            }
            plate = TricycleCodes.formatPlate(rawPlate);

            if (!TricycleCodes.hasNoSeparateTag(selectedState)) {
                String rawTag = etStateTag.getText() != null
                    ? etStateTag.getText().toString().trim() : "";
                if (rawTag.isEmpty() && TricycleCodes.tagRequiredFor(selectedState)) {
                    Toast.makeText(getActivity(), R.string.profile_need_tag, Toast.LENGTH_LONG).show();
                    return;
                }
                tag = rawTag.isEmpty() ? null : rawTag;
            }
        }

        UserProfile profile = editing != null ? editing : new UserProfile();
        if (profile.id == null || profile.id.isEmpty()) {
            profile.id = UUID.randomUUID().toString();
        }
        if (profile.createdAt == null || profile.createdAt.isEmpty()) {
            profile.createdAt = isoNow();
        }
        profile.role       = isRider ? UserProfile.ROLE_RIDER : UserProfile.ROLE_PASSENGER;
        profile.name       = name;
        profile.phone      = phone;
        profile.photoPath  = isRider ? photoPath : null;
        profile.state      = isRider ? selectedState : null;
        profile.plateNumber = plate;
        profile.stateTag   = tag;
        profileStore.save(profile);

        boolean edit = getArguments() != null && getArguments().getBoolean(ARG_EDIT, false);
        if (edit) {
            if (getActivity() != null) {
                getActivity().getFragmentManager().popBackStack();
            }
        } else if (getActivity() instanceof MainActivity) {
            // Enter the mode that matches the role just chosen.
            ((MainActivity) getActivity()).setMode(
                isRider ? MainActivity.MODE_RIDER : MainActivity.MODE_PASSENGER);
        }
    }

    /** ISO-8601 UTC timestamp (API 24-safe; java.time needs API 26). */
    private static String isoNow() {
        SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
        f.setTimeZone(TimeZone.getTimeZone("UTC"));
        return f.format(new Date());
    }
}
