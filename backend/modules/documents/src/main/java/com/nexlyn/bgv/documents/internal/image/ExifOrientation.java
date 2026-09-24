package com.nexlyn.bgv.documents.internal.image;

import java.awt.geom.AffineTransform;
import java.awt.image.AffineTransformOp;
import java.awt.image.BufferedImage;

/**
 * Phone cameras often store a photo sideways and add an "orientation" note in its EXIF data. We remove
 * all EXIF data when re-encoding (it can hold location and device details), so the note must be
 * applied to the pixels first, or the picture would show up rotated.
 */
final class ExifOrientation {

    private ExifOrientation() {
    }

    /** The EXIF orientation of a JPEG, 1 (upright) to 8; 1 when there is none or it cannot be read. */
    static int read(byte[] jpeg) {
        try {
            int offset = 2;
            while (offset + 4 < jpeg.length) {
                if ((jpeg[offset] & 0xFF) != 0xFF) {
                    return 1;
                }
                int marker = jpeg[offset + 1] & 0xFF;
                if (marker == 0xDA || marker == 0xD9) { // start of pixel data / end: no more headers
                    return 1;
                }
                int length = ((jpeg[offset + 2] & 0xFF) << 8) | (jpeg[offset + 3] & 0xFF);
                if (marker == 0xE1 && length >= 16 && matches(jpeg, offset + 4, 'E', 'x', 'i', 'f', 0, 0)) {
                    return orientationInTiff(jpeg, offset + 10, offset + 2 + length);
                }
                offset += 2 + length;
            }
        } catch (RuntimeException e) {
            return 1;
        }
        return 1;
    }

    private static int orientationInTiff(byte[] data, int tiff, int end) {
        boolean little = data[tiff] == 'I';
        if (!little && data[tiff] != 'M') {
            return 1;
        }
        int ifd = tiff + (int) readInt(data, tiff + 4, 4, little);
        int entries = (int) readInt(data, ifd, 2, little);
        for (int i = 0; i < entries; i++) {
            int entry = ifd + 2 + i * 12;
            if (entry + 12 > end) {
                return 1;
            }
            if (readInt(data, entry, 2, little) == 0x0112) {
                int value = (int) readInt(data, entry + 8, 2, little);
                return value >= 1 && value <= 8 ? value : 1;
            }
        }
        return 1;
    }

    private static long readInt(byte[] data, int at, int bytes, boolean little) {
        long value = 0;
        for (int i = 0; i < bytes; i++) {
            int b = data[at + (little ? bytes - 1 - i : i)] & 0xFF;
            value = (value << 8) | b;
        }
        return value;
    }

    private static boolean matches(byte[] data, int at, int... expected) {
        for (int i = 0; i < expected.length; i++) {
            if ((data[at + i] & 0xFF) != expected[i]) {
                return false;
            }
        }
        return true;
    }

    /** Returns the picture turned the right way up. Orientation 1 (or anything unknown) returns it unchanged. */
    static BufferedImage apply(BufferedImage image, int orientation) {
        if (orientation < 2 || orientation > 8) {
            return image;
        }
        int w = image.getWidth();
        int h = image.getHeight();
        AffineTransform t = new AffineTransform();
        boolean swap = orientation >= 5;
        switch (orientation) {
            case 2 -> { // mirrored left-right
                t.scale(-1, 1);
                t.translate(-w, 0);
            }
            case 3 -> { // upside down
                t.translate(w, h);
                t.rotate(Math.PI);
            }
            case 4 -> { // mirrored top-bottom
                t.scale(1, -1);
                t.translate(0, -h);
            }
            case 5 -> { // transposed
                t.rotate(-Math.PI / 2);
                t.scale(-1, 1);
            }
            case 6 -> { // turned 90 degrees clockwise to be upright
                t.translate(h, 0);
                t.rotate(Math.PI / 2);
            }
            case 7 -> {
                t.scale(-1, 1);
                t.translate(-h, 0);
                t.translate(0, w);
                t.rotate(3 * Math.PI / 2);
            }
            default -> { // 8: turned 90 degrees counter-clockwise to be upright
                t.translate(0, w);
                t.rotate(3 * Math.PI / 2);
            }
        }
        int type = image.getType() == BufferedImage.TYPE_CUSTOM ? BufferedImage.TYPE_INT_ARGB : image.getType();
        BufferedImage target = new BufferedImage(swap ? h : w, swap ? w : h, type);
        return new AffineTransformOp(t, AffineTransformOp.TYPE_NEAREST_NEIGHBOR).filter(image, target);
    }
}
