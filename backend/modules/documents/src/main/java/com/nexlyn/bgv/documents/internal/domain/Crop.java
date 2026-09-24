package com.nexlyn.bgv.documents.internal.domain;

/**
 * The part of an image to show, as fractions (0 to 1) of the original's width and height, so it does
 * not depend on the pixel size. The original file is never changed.
 */
public record Crop(double x, double y, double width, double height) {

    /** Smallest allowed side, so a crop cannot be a sliver. */
    public static final double MIN_SIDE = 0.05;
    private static final double EPSILON = 1e-6;

    /** Deliberately not a bean-style name (isValid), so JSON storage does not treat it as a property. */
    public boolean fitsInPicture() {
        return finite(x) && finite(y) && finite(width) && finite(height)
                && x >= 0 && y >= 0
                && width >= MIN_SIDE && height >= MIN_SIDE
                && x + width <= 1 + EPSILON && y + height <= 1 + EPSILON;
    }

    private static boolean finite(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }
}
