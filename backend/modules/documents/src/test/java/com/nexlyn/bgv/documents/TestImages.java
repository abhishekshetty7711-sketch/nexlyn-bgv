package com.nexlyn.bgv.documents;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/** Makes small pictures and files for tests. */
public final class TestImages {

    private TestImages() {
    }

    /** A picture with a red square in the top-left corner and blue everywhere else (so turning it is visible). */
    public static BufferedImage picture(int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(Color.BLUE);
        g.fillRect(0, 0, width, height);
        g.setColor(Color.RED);
        g.fillRect(0, 0, Math.max(4, width / 4), Math.max(4, height / 4));
        g.dispose();
        return image;
    }

    public static byte[] jpeg(int width, int height) {
        return write(picture(width, height), "jpg");
    }

    public static byte[] png(int width, int height) {
        return write(picture(width, height), "png");
    }

    public static byte[] pdf() {
        return "%PDF-1.4\n1 0 obj\n<< /Type /Catalog >>\nendobj\ntrailer\n<< /Root 1 0 R >>\n%%EOF\n".getBytes(StandardCharsets.US_ASCII);
    }

    public static BufferedImage read(byte[] bytes) {
        try {
            return ImageIO.read(new ByteArrayInputStream(bytes));
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Inserts an EXIF block that carries only an orientation (and a fake GPS marker string) right after the JPEG start. */
    public static byte[] withExifOrientation(byte[] jpeg, int orientation) {
        byte[] exif = {
                'E', 'x', 'i', 'f', 0, 0,
                'I', 'I', 0x2A, 0x00, 0x08, 0x00, 0x00, 0x00,      // TIFF header, first IFD at offset 8
                0x01, 0x00,                                        // one entry
                0x12, 0x01, 0x03, 0x00, 0x01, 0x00, 0x00, 0x00,    // tag 0x0112, type SHORT, count 1
                (byte) orientation, 0x00, 0x00, 0x00,              // value
                0x00, 0x00, 0x00, 0x00                             // no next IFD
        };
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(jpeg[0]);
        out.write(jpeg[1]);
        out.write(0xFF);
        out.write(0xE1);
        int length = exif.length + 2;
        out.write(length >> 8);
        out.write(length & 0xFF);
        out.writeBytes(exif);
        out.write(jpeg, 2, jpeg.length - 2);
        return out.toByteArray();
    }

    private static byte[] write(BufferedImage image, String format) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            ImageIO.write(image, format, out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
