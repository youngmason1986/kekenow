package com.blackempire.kekenow.util;

import java.util.Locale;

/**
 * Nigerian tricycle (keke) registration codes — all 36 states + FCT.
 *
 * Source: {@code docs/nigeria-tricycle-codes.md} (compiled Nov 24, 2026).
 *
 * A commercial keke normally carries TWO numbers:
 * <ol>
 *   <li><b>FRSC plate</b> — national format {@code ABC-123-XY}: 3 letters
 *       (LGA code), 3 digits, 2 letters. Older keke may still carry the
 *       pre-2011 format {@code AB123-CDE}. Kano has a dedicated tricycle
 *       series {@code KNT-123-A}.</li>
 *   <li><b>Union / state tag</b> — the number painted on the keke body,
 *       issued by the state tricycle union or transport authority. Its
 *       format differs by state and most states have never published
 *       theirs: Delta {@code DT/CT/10550B}, Enugu {@code ENS/104/1/6653},
 *       Lagos NURTW tag, Anambra {@code ATMC 420525}.</li>
 * </ol>
 *
 * <b>Honest boundary:</b> there is NO public government database of
 * individual keke numbers. This class only checks a number's SHAPE and
 * supplies per-state placeholders/examples. It can never verify a number
 * against government records — the app must never claim it does.
 */
public final class TricycleCodes {

    private TricycleCodes() {}

    /** All 36 states + FCT, alphabetical. */
    public static final String[] STATES = {
        "Abia", "Adamawa", "Akwa Ibom", "Anambra", "Bauchi", "Bayelsa",
        "Benue", "Borno", "Cross River", "Delta", "Ebonyi", "Edo",
        "Ekiti", "Enugu", "FCT Abuja", "Gombe", "Imo", "Jigawa",
        "Kaduna", "Kano", "Katsina", "Kebbi", "Kogi", "Kwara",
        "Lagos", "Nasarawa", "Niger", "Ogun", "Ondo", "Osun",
        "Oyo", "Plateau", "Rivers", "Sokoto", "Taraba", "Yobe", "Zamfara"
    };

    /** Default state for the picker (George rides in Asaba, Delta State). */
    public static final String DEFAULT_STATE = "Delta";

    /**
     * Plate example for the state, from docs/nigeria-tricycle-codes.md.
     * Real keke plates where the research found them (Delta ASB 991 VC,
     * Anambra NKK 170 UR, Enugu UVW 936 VS, Lagos KJA 364 QN); the rest
     * are pattern illustrations using the capital city's LGA code —
     * they show the SHAPE, they are not real registered plates.
     */
    private static final String[] PLATE_EXAMPLES = {
        "UMA 482 VC", "YLA 317 QD", "UYY 205 AB", "NKK 170 UR",
        "BAU 156 XY", "YEN 743 VC", "MKD 628 QD", "MAG 391 AB",
        "CAL 274 VC", "ASB 991 VC", "AKL 519 XY", "BEN 863 QD",
        "ADK 142 VC", "UVW 936 VS", "ABC 307 XY", "GME 658 AB",
        "WER 294 VC", "DUT 731 QD", "DKA 816 XY", "KNT-914-B",
        "KTN 573 VC", "BRK 208 AB", "LKJ 645 XY", "LRN 139 VC",
        "KJA 364 QN", "LFA 482 QD", "MNA 926 XY", "AAB 371 VC",
        "AKR 254 AB", "SGB 698 QD", "BDJ 417 XY", "JJN 583 VC",
        "PHC 129 QD", "SKK 764 AB", "JAL 835 XY", "DTR 492 VC",
        "GUS 268 QD"
    };

    /** Index of a state in {@link #STATES}, or -1 if unknown. */
    public static int indexOfState(String state) {
        if (state == null) return -1;
        for (int i = 0; i < STATES.length; i++) {
            if (STATES[i].equalsIgnoreCase(state.trim())) return i;
        }
        return -1;
    }

    /** Plate example for the state ("e.g. ASB 991 VC" for Delta). */
    public static String plateExampleFor(String state) {
        int i = indexOfState(state);
        return i >= 0 ? PLATE_EXAMPLES[i] : "ABC-123-XY";
    }

    /**
     * Placeholder text for the union/state tag field of the state.
     * Returns null for Kano — Kano keke use the KNT plate series and
     * have no separate union tag.
     */
    public static String tagHintFor(String state) {
        if (state == null) return "Union tag number on the keke (if any)";
        switch (state.trim()) {
            case "Delta":   return "e.g. DT/CT/10550B";
            case "Enugu":   return "e.g. ENS/104/1/6653";
            case "Lagos":   return "e.g. NURTW tag number on the keke";
            case "Anambra": return "e.g. ATMC 420525";
            case "Kano":    return null; // KNT plate series is the identifier
            default:        return "Union tag number on the keke (if any)";
        }
    }

    /** True when the state has no separate union tag (Kano only). */
    public static boolean hasNoSeparateTag(String state) {
        return "Kano".equalsIgnoreCase(state != null ? state.trim() : "");
    }

    /**
     * True when a rider in this state is expected to have a union tag
     * (states with a known published format). Elsewhere the tag is
     * optional — many states never published a format.
     */
    public static boolean tagRequiredFor(String state) {
        if (state == null) return false;
        switch (state.trim()) {
            case "Delta":
            case "Enugu":
            case "Lagos":
            case "Anambra":
                return true;
            default:
                return false;
        }
    }

    // ------------------------------------------------------------------ //
    //  Plate shape check (NOT verification — see class doc)
    // ------------------------------------------------------------------ //

    /** Strip spaces/dashes and uppercase, for shape checking. */
    public static String normalizePlate(String plate) {
        if (plate == null) return "";
        return plate.replaceAll("[\\s\\-]", "").toUpperCase(Locale.US);
    }

    /**
     * Shape check only: current {@code ABC123XY}, old pre-2011
     * {@code AB123CDE}, or Kano tricycle series {@code KNT123A}.
     * Says nothing about whether the plate is real or registered.
     */
    public static boolean isValidPlate(String plate) {
        String n = normalizePlate(plate);
        return n.matches("[A-Z]{3}[0-9]{3}[A-Z]{2}")   // current ABC-123-XY
            || n.matches("[A-Z]{2}[0-9]{3}[A-Z]{3}")   // old AB123-CDE
            || n.matches("KNT[0-9]{3}[A-Z]");          // Kano KNT-123-A
    }

    /** Display form with dashes: {@code ASB-991-VC}, {@code KNT-914-B}. */
    public static String formatPlate(String plate) {
        String n = normalizePlate(plate);
        if (n.matches("[A-Z]{3}[0-9]{3}[A-Z]{2}")) {
            return n.substring(0, 3) + "-" + n.substring(3, 6) + "-" + n.substring(6, 8);
        }
        if (n.matches("[A-Z]{2}[0-9]{3}[A-Z]{3}")) {
            return n.substring(0, 2) + n.substring(2, 5) + "-" + n.substring(5, 8);
        }
        if (n.matches("KNT[0-9]{3}[A-Z]")) {
            return n.substring(0, 3) + "-" + n.substring(3, 6) + "-" + n.substring(6, 7);
        }
        return plate != null ? plate.trim().toUpperCase(Locale.US) : "";
    }
}
