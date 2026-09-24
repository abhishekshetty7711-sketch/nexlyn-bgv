package com.nexlyn.bgv.reports.internal.render;

import com.nexlyn.bgv.documents.DocumentApi.Crop;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.Optional;

/**
 * Turns stored files into pictures a page can show: applies the saved crop, keeps the picture from being
 * larger than the print needs (the report would otherwise carry every multi-megabyte scan at full size),
 * and returns a data URI so the HTML is self-contained. A PDF supporting document is shown as its first page.
 */
public final class ImageEmbedder {

    private static final Logger log = LoggerFactory.getLogger(ImageEmbedder.class);
    private static final float JPEG_QUALITY = 0.9f;
    private static final float PDF_DPI = 130f;

    /** What a PDF looks like on the page: its first page as a picture, and how many pages it has. */
    public record PdfPreview(String dataUri, int pages) {
    }

    private final int maxSide;

    /** @param maxSide the longest side, in pixels, a picture is scaled down to (never scaled up) */
    public ImageEmbedder(int maxSide) {
        this.maxSide = maxSide;
        ImageIO.setUseCache(false);
    }

    /** The picture as a data URI, cropped and scaled down when needed; empty when it cannot be read. */
    public Optional<String> picture(byte[] bytes, String mimeType, Crop crop) {
        boolean png = "image/png".equals(mimeType);
        if (crop == null && !needsScaling(bytes)) {
            return Optional.of(dataUri(png ? "image/png" : "image/jpeg", bytes)); // untouched, exactly as stored
        }
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
            if (image == null) {
                return Optional.empty();
            }
            if (crop != null) {
                image = crop(image, crop);
            }
            image = scaleDown(image, maxSide);
            byte[] out = png ? encodePng(image) : encodeJpeg(image);
            return Optional.of(dataUri(png ? "image/png" : "image/jpeg", out));
        } catch (IOException | RuntimeException e) {
            log.warn("A picture could not be prepared for the report: {}", e.toString());
            return Optional.empty();
        }
    }

    /** The first page of a PDF as a picture. Empty when the PDF cannot be opened (a damaged or hostile file). */
    public Optional<PdfPreview> pdfFirstPage(byte[] pdf) {
        try (PDDocument document = Loader.loadPDF(pdf)) {
            if (document.getNumberOfPages() == 0) {
                return Optional.empty();
            }
            BufferedImage page = new PDFRenderer(document).renderImageWithDPI(0, PDF_DPI);
            return Optional.of(new PdfPreview(dataUri("image/png", encodePng(scaleDown(page, maxSide))), document.getNumberOfPages()));
        } catch (IOException | RuntimeException e) {
            log.warn("A PDF could not be shown in the report: {}", e.toString());
            return Optional.empty();
        }
    }

    // ---- steps -----------------------------------------------------------------------------------------

    private boolean needsScaling(byte[] bytes) {
        try (var stream = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            var readers = ImageIO.getImageReaders(stream);
            if (!readers.hasNext()) {
                return false;
            }
            var reader = readers.next();
            try {
                reader.setInput(stream, true, true);
                return Math.max(reader.getWidth(0), reader.getHeight(0)) > maxSide;
            } finally {
                reader.dispose();
            }
        } catch (IOException | RuntimeException e) {
            return false;
        }
    }

    static BufferedImage crop(BufferedImage image, Crop crop) {
        int x = clamp((int) Math.round(crop.x() * image.getWidth()), 0, image.getWidth() - 1);
        int y = clamp((int) Math.round(crop.y() * image.getHeight()), 0, image.getHeight() - 1);
        int w = clamp((int) Math.round(crop.width() * image.getWidth()), 1, image.getWidth() - x);
        int h = clamp((int) Math.round(crop.height() * image.getHeight()), 1, image.getHeight() - y);
        return image.getSubimage(x, y, w, h);
    }

    /** Halves in steps until near the target, then a last exact step, which keeps small text readable. */
    static BufferedImage scaleDown(BufferedImage image, int maxSide) {
        int longest = Math.max(image.getWidth(), image.getHeight());
        if (longest <= maxSide) {
            return image;
        }
        double factor = (double) maxSide / longest;
        int w = Math.max(1, (int) Math.round(image.getWidth() * factor));
        int h = Math.max(1, (int) Math.round(image.getHeight() * factor));
        BufferedImage current = image;
        while (current.getWidth() / 2 >= w && current.getHeight() / 2 >= h) {
            current = resize(current, current.getWidth() / 2, current.getHeight() / 2);
        }
        return resize(current, w, h);
    }

    private static BufferedImage resize(BufferedImage source, int w, int h) {
        boolean alpha = source.getColorModel().hasAlpha();
        BufferedImage target = new BufferedImage(w, h, alpha ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
        Graphics2D g = target.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            if (!alpha) {
                g.setColor(Color.WHITE);
                g.fillRect(0, 0, w, h);
            }
            g.drawImage(source, 0, 0, w, h, null);
        } finally {
            g.dispose();
        }
        return target;
    }

    private static byte[] encodeJpeg(BufferedImage image) throws IOException {
        BufferedImage rgb = image;
        if (image.getType() != BufferedImage.TYPE_INT_RGB && image.getType() != BufferedImage.TYPE_3BYTE_BGR) {
            rgb = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
            Graphics2D g = rgb.createGraphics();
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, rgb.getWidth(), rgb.getHeight());
            g.drawImage(image, 0, 0, null);
            g.dispose();
        }
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        try (ByteArrayOutputStream out = new ByteArrayOutputStream(); MemoryCacheImageOutputStream stream = new MemoryCacheImageOutputStream(out)) {
            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(JPEG_QUALITY);
            writer.setOutput(stream);
            writer.write(null, new IIOImage(rgb, null, null), param);
            stream.flush();
            return out.toByteArray();
        } finally {
            writer.dispose();
        }
    }

    private static byte[] encodePng(BufferedImage image) throws IOException {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", out);
            return out.toByteArray();
        }
    }

    private static String dataUri(String mime, byte[] bytes) {
        return "data:" + mime + ";base64," + Base64.getEncoder().encodeToString(bytes);
    }

    private static int clamp(int value, int low, int high) {
        return Math.max(low, Math.min(high, value));
    }
}
