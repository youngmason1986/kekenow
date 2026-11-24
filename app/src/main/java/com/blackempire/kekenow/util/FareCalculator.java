package com.blackempire.kekenow.util;

import com.blackempire.kekenow.model.FareBand;

/**
 * Converts a route distance in metres to an Asaba keke fare band.
 *
 * Fare table (Asaba 2024-2025 reference):
 *
 *  0  –  1 000 m  →  ₦100 – ₦150
 *  1 001 – 2 000 m  →  ₦150 – ₦200
 *  2 001 – 3 000 m  →  ₦200 – ₦250
 *  3 001 – 5 000 m  →  ₦250 – ₦350
 *  5 001 – 8 000 m  →  ₦350 – ₦500
 *  8 001 – 12 000 m →  ₦500 – ₦700
 *  > 12 000 m       →  ₦700 – ₦1 000
 */
public final class FareCalculator {

    private FareCalculator() { /* static utility — no instances */ }

    public static FareBand estimate(double distanceMetres) {
        if (distanceMetres <= 1000) {
            return new FareBand(100, 150);
        } else if (distanceMetres <= 2000) {
            return new FareBand(150, 200);
        } else if (distanceMetres <= 3000) {
            return new FareBand(200, 250);
        } else if (distanceMetres <= 5000) {
            return new FareBand(250, 350);
        } else if (distanceMetres <= 8000) {
            return new FareBand(350, 500);
        } else if (distanceMetres <= 12000) {
            return new FareBand(500, 700);
        } else {
            return new FareBand(700, 1000);
        }
    }
}
