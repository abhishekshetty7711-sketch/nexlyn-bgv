package com.nexlyn.bgv.documents.internal.image;

import com.nexlyn.bgv.common.error.ApiException;
import com.nexlyn.bgv.common.error.ErrorCode;
import com.nexlyn.bgv.documents.internal.image.FileTypeSniffer.FileType;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;

/**
 * Checks that an upload really is a picture, and re-encodes it. Re-encoding drops every piece of
 * metadata (EXIF location, camera details, embedded thumbnails) and anything smuggled into the file
 * after the image data (CLAUDE.md section 11.4).
 */
public final class ImageProcessor {

    /** The cleaned picture and what is known about it. */
    public record Result(byte[] bytes, String mimeType, int width, int height) {
    }

    private static final float JPEG_QUALITY = 0.92f;

    private final long maxPixels;

    public ImageProcessor(long maxPixels) {
        this.maxPixels = maxPixels;
        ImageIO.setUseCache(false); // never write temporary files with document contents
    }

    public Result process(byte[] original, FileType type) {
        BufferedImage decoded = decode(original);
        BufferedImage upright = ExifOrientation.apply(standardise(decoded, type), type == FileType.JPEG ? ExifOrientation.read(original) : 1);
        byte[] cleaned = type == FileType.JPEG ? encodeJpeg(upright) : encodePng(upright);
        return new Result(cleaned, type.mimeType(), upright.getWidth(), upright.getHeight());
    }

    private BufferedImage decode(byte[] bytes) {
        try (ImageInputStream stream = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(stream);
            if (!readers.hasNext()) {
                throw unreadable();
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(stream, true, true);
                long width = reader.getWidth(0);
                long height = reader.getHeight(0);
                if (width <= 0 || height <= 0) {
                    throw unreadable();
                }
                // Checked before decoding: a small file can describe an enormous picture.
                if (width * height > maxPixels) {
                    throw new ApiException(ErrorCode.UNSUPPORTED_FILE,
                            "That image has too many pixels. Please resize it (at most " + maxPixels / 1_000_000 + " megapixels) and try again.");
                }
                return reader.read(0);
            } finally {
                reader.dispose();
            }
        } catch (IOException | RuntimeException e) {
            if (e instanceof ApiException api) {
                throw api;
            }
            throw unreadable();
        }
    }

    private static ApiException unreadable() {
        return new ApiException(ErrorCode.UNSUPPORTED_FILE,
                "That image could not be read. Please upload a standard JPEG or PNG (not CMYK or damaged).");
    }

    /** Converts whatever the decoder produced into plain RGB (JPEG) or RGBA (PNG) so it can be turned and re-encoded. */
    private static BufferedImage standardise(BufferedImage source, FileType type) {
        boolean wantAlpha = type == FileType.PNG && source.getColorModel().hasAlpha();
        int target = wantAlpha ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB;
        if (source.getType() == target) {
            return source;
        }
        BufferedImage copy = new BufferedImage(source.getWidth(), source.getHeight(), target);
        Graphics2D g = copy.createGraphics();
        try {
            if (!wantAlpha) {
                g.setColor(Color.WHITE); // transparent areas become white, not black
                g.fillRect(0, 0, copy.getWidth(), copy.getHeight());
            }
            g.drawImage(source, 0, 0, null);
        } finally {
            g.dispose();
        }
        return copy;
    }

    private static byte[] encodeJpeg(BufferedImage image) {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        try (ByteArrayOutputStream out = new ByteArrayOutputStream(); MemoryCacheImageOutputStream stream = new MemoryCacheImageOutputStream(out)) {
            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(JPEG_QUALITY);
            writer.setOutput(stream);
            writer.write(null, new IIOImage(image, null, null), param); // null metadata: nothing carried over
            stream.flush();
            return out.toByteArray();
        } catch (IOException e) {
            throw unreadable();
        } finally {
            writer.dispose();
        }
    }

    private static byte[] encodePng(BufferedImage image) {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("png").next();
        try (ByteArrayOutputStream out = new ByteArrayOutputStream(); MemoryCacheImageOutputStream stream = new MemoryCacheImageOutputStream(out)) {
            writer.setOutput(stream);
            writer.write(null, new IIOImage(image, null, null), writer.getDefaultWriteParam());
            stream.flush();
            return out.toByteArray();
        } catch (IOException e) {
            throw unreadable();
        } finally {
            writer.dispose();
        }
    }
}
