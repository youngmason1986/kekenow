package com.blackempire.kekenow;

import android.app.Activity;
import android.app.Fragment;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.content.SharedPreferences;
import android.os.Bundle;

import com.blackempire.kekenow.fragment.AuthFragment;
import com.blackempire.kekenow.fragment.ModeSelectFragment;
import com.blackempire.kekenow.fragment.PassengerFragment;
import com.blackempire.kekenow.fragment.RiderFragment;
import com.blackempire.kekenow.store.ProfileStore;

/**
 * Single activity that hosts all fragments.
 *
 * Entry point by state:
 * <ul>
 *   <li>Never registered (no saved profile) — the role-choice screen
 *       (Rider / Customer); the sign-up that follows matches the role.</li>
 *   <li>Registered but signed out — the phone sign-in screen.</li>
 *   <li>Signed in — the saved mode, or the role-choice screen as a
 *       mode switcher when no mode was saved yet.</li>
 * </ul>
 * Framework-only (android.app): this machine builds offline with no
 * AndroidX artifacts, so AppCompatActivity / androidx fragments are out.
 */
public class MainActivity extends Activity {

    public static final String PREF_FILE = "kekenow_prefs";
    public static final String PREF_MODE = "mode";
    public static final String MODE_PASSENGER = "passenger";
    public static final String MODE_RIDER     = "rider";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if (savedInstanceState != null) {
            // Fragment already restored by the framework after rotation, etc.
            return;
        }

        ProfileStore profileStore = new ProfileStore(this);
        SharedPreferences prefs = getSharedPreferences(PREF_FILE, MODE_PRIVATE);
        String savedMode = prefs.getString(PREF_MODE, null);

        Fragment initial;
        if (!profileStore.isSignedIn()) {
            // First launch (no profile at all) -> role choice;
            // returning user (profile saved, signed out) -> phone sign-in.
            initial = profileStore.getCurrent() != null
                ? new AuthFragment()
                : new ModeSelectFragment();
        } else if (MODE_PASSENGER.equals(savedMode)) {
            initial = new PassengerFragment();
        } else if (MODE_RIDER.equals(savedMode)) {
            initial = new RiderFragment();
        } else {
            initial = new ModeSelectFragment();
        }

        getFragmentManager()
            .beginTransaction()
            .replace(R.id.fragment_container, initial)
            .commit();
    }

    /** Replace the current fragment, optionally adding to the back stack. */
    public void navigateTo(Fragment fragment, boolean addToBackStack) {
        FragmentTransaction tx = getFragmentManager()
            .beginTransaction()
            .replace(R.id.fragment_container, fragment);
        if (addToBackStack) {
            tx.addToBackStack(null);
        }
        tx.commit();
    }

    /** Save the chosen mode and navigate to the corresponding fragment. */
    public void setMode(String mode) {
        SharedPreferences prefs = getSharedPreferences(PREF_FILE, MODE_PRIVATE);
        prefs.edit().putString(PREF_MODE, mode).apply();

        Fragment target = MODE_RIDER.equals(mode) ? new RiderFragment() : new PassengerFragment();
        getFragmentManager()
            .beginTransaction()
            .replace(R.id.fragment_container, target)
            .commit();
    }

    /** Clear saved mode and return to the mode selector. */
    public void resetMode() {
        SharedPreferences prefs = getSharedPreferences(PREF_FILE, MODE_PRIVATE);
        prefs.edit().remove(PREF_MODE).apply();

        getFragmentManager()
            .beginTransaction()
            .replace(R.id.fragment_container, new ModeSelectFragment())
            .commit();
    }

    /** Drop back to the phone sign-in screen (after sign-out). */
    public void showAuth() {
        getFragmentManager().popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE);
        getFragmentManager()
            .beginTransaction()
            .replace(R.id.fragment_container, new AuthFragment())
            .commit();
    }
}
