package com.blackempire.kekenow.model;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Plain data class representing a single ride request.
 * Serialises to / from JSON for flat-file storage in files/rides/.
 */
public final class RideRecord {

    public static final String STATUS_PENDING   = "pending";
    public static final String STATUS_ACCEPTED  = "accepted";
    public static final String STATUS_COMPLETED = "completed";
    public static final String STATUS_DECLINED  = "declined";

    public String id;
    public String createdAt;      // ISO-8601 UTC
    public String passengerId;    // SHA-256 of ANDROID_ID

    public String status;

    public double originLat;
    public double originLon;

    public double destLat;
    public double destLon;
    public String destName;

    public double distanceMetres;
    public double durationSeconds;

    public int fareMin;
    public int fareMax;

    public String routeGeoJson;   // may be null when OSRM is unavailable
    public boolean isFallback;    // true when Haversine was used instead of OSRM

    // Accepted rider's identity — shown to the passenger for security.
    public String riderName;
    public String riderPhone;
    public String riderPhotoPath; // absolute path in internal storage, may be null
    public String riderPlateNumber; // FRSC plate, e.g. "ASB-991-VC"
    public String riderStateTag;    // union tag painted on the keke, e.g. "DT/CT/10550B"

    // ------------------------------------------------------------------ //

    public JSONObject toJson() throws JSONException {
        JSONObject obj = new JSONObject();
        obj.put("id", id);
        obj.put("createdAt", createdAt);
        obj.put("passengerId", passengerId);
        obj.put("status", status);

        JSONObject origin = new JSONObject();
        origin.put("lat", originLat);
        origin.put("lon", originLon);
        obj.put("origin", origin);

        JSONObject dest = new JSONObject();
        dest.put("lat", destLat);
        dest.put("lon", destLon);
        dest.put("name", destName != null ? destName : "");
        obj.put("destination", dest);

        obj.put("distanceMetres", distanceMetres);
        obj.put("durationSeconds", durationSeconds);
        obj.put("fareMin", fareMin);
        obj.put("fareMax", fareMax);
        obj.put("routeGeoJson", routeGeoJson != null ? routeGeoJson : "");
        obj.put("isFallback", isFallback);

        obj.put("riderName", riderName != null ? riderName : "");
        obj.put("riderPhone", riderPhone != null ? riderPhone : "");
        obj.put("riderPhotoPath", riderPhotoPath != null ? riderPhotoPath : "");
        obj.put("riderPlateNumber", riderPlateNumber != null ? riderPlateNumber : "");
        obj.put("riderStateTag", riderStateTag != null ? riderStateTag : "");
        return obj;
    }

    public static RideRecord fromJson(JSONObject obj) throws JSONException {
        RideRecord r = new RideRecord();
        r.id            = obj.getString("id");
        r.createdAt     = obj.getString("createdAt");
        r.passengerId   = obj.optString("passengerId", "");
        r.status        = obj.getString("status");

        JSONObject origin = obj.getJSONObject("origin");
        r.originLat = origin.getDouble("lat");
        r.originLon = origin.getDouble("lon");

        JSONObject dest = obj.getJSONObject("destination");
        r.destLat  = dest.getDouble("lat");
        r.destLon  = dest.getDouble("lon");
        r.destName = dest.optString("name", "");

        r.distanceMetres  = obj.optDouble("distanceMetres", 0);
        r.durationSeconds = obj.optDouble("durationSeconds", 0);
        r.fareMin         = obj.optInt("fareMin", 0);
        r.fareMax         = obj.optInt("fareMax", 0);
        r.routeGeoJson    = obj.optString("routeGeoJson", "");
        r.isFallback      = obj.optBoolean("isFallback", false);

        r.riderName        = obj.optString("riderName", "");
        r.riderPhone       = obj.optString("riderPhone", "");
        r.riderPhotoPath   = obj.optString("riderPhotoPath", "");
        r.riderPlateNumber = obj.optString("riderPlateNumber", "");
        r.riderStateTag    = obj.optString("riderStateTag", "");
        return r;
    }
}
