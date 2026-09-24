package com.nexlyn.bgv.documents.internal.domain;

/** A rough sharpness badge from the longer side of the image (CLAUDE.md section 6.2). */
public enum ImageQuality {
    HIGH, MEDIUM, LOW;

    /** 1500 px or more is high, 800 px or more medium, anything smaller low. */
    public static ImageQuality of(int width, int height) {
        int longest = Math.max(width, height);
        return longest >= 1500 ? HIGH : longest >= 800 ? MEDIUM : LOW;
    }
}
