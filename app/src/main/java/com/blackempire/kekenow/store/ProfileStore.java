package com.blackempire.kekenow.store;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.util.Log;

import com.blackempire.kekenow.model.UserProfile;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Local sign-up / sign-in storage for KekeNow (demo scope: no server).
 *
 * One profile is kept in SharedPreferences. The phone number IS the
 * account: signing in with the same number restores the saved profile,
 * a different number starts registration. Signing out keeps the saved
 * profile on the device but clears the signed-in flag.
 *
 * Profile photos are copied into internal storage
 * ({@code files/profile_photo.jpg}, app-private) so the app can show
 * them later without storage permission.
 */
public class ProfileStore {

    private static final String TAG = "KekeNow";
    private static final String PREFS_FILE   = "kekenow_profile";
    private static final String KEY_PROFILE   = "profile_json";
    private static final String KEY_SIGNED_IN = "signed_in";
    private static final String PHOTO_FILE    = "profile_photo.jpg";
    private static final int    PHOTO_MAX_PX  = 512;

    private final Context context;
    private final SharedPreferences prefs;

    public ProfileStore(Context context) {
        this.context = context.getApplicationContext();
        this.prefs   = this.context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE);
    }

    /** Save the profile and mark the user signed in. */
    public void save(UserProfile profile) {
        try {
            prefs.edit()
                .putString(KEY_PROFILE, profile.toJson().toString())
                .putBoolean(KEY_SIGNED_IN, true)
                .apply();
        } catch (JSONException e) {
            Log.e(TAG, "ProfileStore.save failed", e);
        }
    }

    /** The saved profile, or null if nobody has registered on this device. */
    public UserProfile getCurrent() {
        String json = prefs.getString(KEY_PROFILE, null);
        if (json == null || json.isEmpty()) return null;
        try {
            return UserProfile.fromJson(new JSONObject(json));
        } catch (JSONException e) {
            Log.e(TAG, "ProfileStore: could not parse saved profile", e);
            return null;
        }
    }

    public boolean isSignedIn() {
        return prefs.getBoolean(KEY_SIGNED_IN, false) && getCurrent() != null;
    }

    /** Mark the saved profile signed in (used when the same number signs back in). */
    public void markSignedIn() {
        prefs.edit().putBoolean(KEY_SIGNED_IN, true).apply();
    }

    /** Sign out but keep the saved profile so the same number can sign back in. */
    public void signOut() {
        prefs.edit().putBoolean(KEY_SIGNED_IN, false).apply();
    }

    /**
     * Copy a picked image (content:// URI from the gallery picker) into
     * internal storage, downscaled to at most {@link #PHOTO_MAX_PX} px.
     * Returns the absolute file path, or null on failure.
     */
    public String savePhoto(Uri uri) {
        try (InputStream in = context.getContentResolver().openInputStream(uri)) {
            if (in == null) return null;
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeStream(in, null, bounds);
            if (bounds.outWidth <= 0) return null;

            int sample = 1;
            while (Math.max(bounds.outWidth, bounds.outHeight) / sample > PHOTO_MAX_PX) {
                sample *= 2;
            }
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inSampleSize = sample;
            Bitmap bitmap;
            try (InputStream in2 = context.getContentResolver().openInputStream(uri)) {
                if (in2 == null) return null;
                bitmap = BitmapFactory.decodeStream(in2, null, opts);
            }
            if (bitmap == null) return null;

            File out = new File(context.getFilesDir(), PHOTO_FILE);
            try (FileOutputStream fos = new FileOutputStream(out)) {
                bitmap.compress(Bitmap.CompressFormat.JPEG, 85, fos);
                fos.flush();
            }
            bitmap.recycle();
            return out.getAbsolutePath();
        } catch (IOException e) {
            Log.e(TAG, "ProfileStore.savePhoto failed", e);
            return null;
        }
    }
}
