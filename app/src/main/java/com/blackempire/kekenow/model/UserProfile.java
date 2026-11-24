package com.blackempire.kekenow.model;

import com.blackempire.kekenow.util.TricycleCodes;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * A KekeNow user profile (rider or passenger), stored locally on the
 * device — no account server, no password (demo scope for the hackathon).
 *
 * A rider's profile carries TWO keke numbers (see
 * docs/nigeria-tricycle-codes.md):
 * <ol>
 *   <li><b>plateNumber</b> — the FRSC plate, {@code ABC-123-XY}
 *       (3 letters = LGA code, 3 digits, 2 letters); older keke may
 *       carry the pre-2011 {@code AB123-CDE}; Kano uses the dedicated
 *       tricycle series {@code KNT-123-A}.</li>
 *   <li><b>stateTag</b> — the union/state tag painted on the keke body,
 *       e.g. Delta {@code DT/CT/10550B}, Enugu {@code ENS/104/1/6653}.
 *       Its format differs by state; many states never published one.</li>
 * </ol>
 * Both numbers are shown to the passenger on the waiting screen once a
 * ride is accepted, so the passenger can match the keke that arrives.
 * The app only checks a number's SHAPE — it never claims to verify a
 * number against government records (no public register exists).
 */
public final class UserProfile {

    public static final String ROLE_RIDER     = "rider";
    public static final String ROLE_PASSENGER = "passenger";

    public String id;
    public String role;        // ROLE_RIDER or ROLE_PASSENGER
    public String name;
    public String phone;
    public String photoPath;   // absolute path in internal storage, may be null
    public String state;       // rider's state of registration, e.g. "Delta"
    public String plateNumber; // rider only: FRSC plate, e.g. "ASB-991-VC"
    public String stateTag;    // rider only: union tag painted on the keke
    public String createdAt;   // ISO-8601 UTC

    public JSONObject toJson() throws JSONException {
        JSONObject obj = new JSONObject();
        obj.put("id",          id != null ? id : "");
        obj.put("role",        role != null ? role : ROLE_PASSENGER);
        obj.put("name",        name != null ? name : "");
        obj.put("phone",       phone != null ? phone : "");
        obj.put("photoPath",   photoPath != null ? photoPath : "");
        obj.put("state",       state != null ? state : "");
        obj.put("plateNumber", plateNumber != null ? plateNumber : "");
        obj.put("stateTag",    stateTag != null ? stateTag : "");
        obj.put("createdAt",   createdAt != null ? createdAt : "");
        return obj;
    }

    public static UserProfile fromJson(JSONObject obj) throws JSONException {
        UserProfile p = new UserProfile();
        p.id          = obj.optString("id", "");
        p.role        = obj.optString("role", ROLE_PASSENGER);
        p.name        = obj.optString("name", "");
        p.phone       = obj.optString("phone", "");
        p.photoPath   = optOrNull(obj, "photoPath");
        p.state       = optOrNull(obj, "state");
        p.plateNumber = optOrNull(obj, "plateNumber");
        // Backwards compat: profiles saved before the two-field change
        // kept a single "kekeCode" — treat it as the state tag.
        p.stateTag    = optOrNull(obj, "stateTag");
        if (p.stateTag == null) {
            p.stateTag = optOrNull(obj, "kekeCode");
        }
        p.createdAt   = obj.optString("createdAt", "");
        return p;
    }

    private static String optOrNull(JSONObject obj, String key) {
        String v = obj.optString(key, "");
        return v.isEmpty() ? null : v;
    }

    public boolean isRider() {
        return ROLE_RIDER.equals(role);
    }

    /** True when the profile has everything a passenger needs to see. */
    public boolean hasRiderIdentity() {
        if (!isRider()) return false;
        if (name == null || name.isEmpty()) return false;
        if (phone == null || phone.isEmpty()) return false;
        if (plateNumber == null || !TricycleCodes.isValidPlate(plateNumber)) return false;
        if (TricycleCodes.hasNoSeparateTag(state)) return true; // Kano: KNT plate is enough
        if (TricycleCodes.tagRequiredFor(state)) {
            return stateTag != null && !stateTag.isEmpty();
        }
        return true; // other states: tag optional
    }
}
