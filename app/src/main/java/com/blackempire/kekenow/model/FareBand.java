package com.blackempire.kekenow.model;

/** Immutable fare band returned by {@link com.blackempire.kekenow.util.FareCalculator}. */
public final class FareBand {

    public final int min;
    public final int max;

    public FareBand(int min, int max) {
        this.min = min;
        this.max = max;
    }

    /** Display string shown in the fare card: ₦150 – ₦200 */
    public String toDisplayString() {
        return "\u20A6" + min + " \u2013 \u20A6" + max;
    }

    /** TTS-friendly string: "one hundred and fifty to two hundred naira" approximated as digits. */
    public String toSpeechString() {
        return "Estimated fare: " + min + " to " + max + " naira";
    }
}
