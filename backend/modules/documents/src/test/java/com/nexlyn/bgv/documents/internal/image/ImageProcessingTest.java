package com.nexlyn.bgv.documents.internal.image;

import com.nexlyn.bgv.common.error.ApiException;
import com.nexlyn.bgv.common.error.ErrorCode;
import com.nexlyn.bgv.documents.TestImages;
import com.nexlyn.bgv.documents.internal.domain.Crop;
import com.nexlyn.bgv.documents.internal.domain.ImageQuality;
import com.nexlyn.bgv.documents.internal.image.FileTypeSniffer.FileType;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ImageProcessingTest {

    // ---- what a file really is ----------------------------------------------------------------------------

    @Test
    void recognisesFilesByTheirFirstBytesNotByName() {
        assertThat(FileTypeSniffer.detect(TestImages.jpeg(20, 20))).contains(FileType.JPEG);
        assertThat(FileTypeSniffer.detect(TestImages.png(20, 20))).contains(FileType.PNG);
        assertThat(FileTypeSniffer.detect(TestImages.pdf())).contains(FileType.PDF);
    }

    @Test
    void rejectsEverythingElse() {
        assertThat(FileTypeSniffer.detect("<html><script>alert(1)</script></html>".getBytes(StandardCharsets.UTF_8))).isEqualTo(Optional.empty());
        assertThat(FileTypeSniffer.detect("GIF89a....".getBytes(StandardCharsets.US_ASCII))).isEmpty();
        assertThat(FileTypeSniffer.detect(new byte[]{(byte) 0xFF, (byte) 0xD8})).as("too short").isEmpty();
        assertThat(FileTypeSniffer.detect(new byte[0])).isEmpty();
        assertThat(FileTypeSniffer.detect(null)).isEmpty();
    }

    // ---- quality badge -------------------------------------------------------------------------------------

    @Test
    void qualityFollowsTheLongerSide() {
        assertThat(ImageQuality.of(1500, 200)).isEqualTo(ImageQuality.HIGH);
        assertThat(ImageQuality.of(200, 3000)).isEqualTo(ImageQuality.HIGH);
        assertThat(ImageQuality.of(1499, 1000)).isEqualTo(ImageQuality.MEDIUM);
        assertThat(ImageQuality.of(800, 100)).isEqualTo(ImageQuality.MEDIUM);
        assertThat(ImageQuality.of(799, 799)).isEqualTo(ImageQuality.LOW);
    }

    // ---- crop rules ----------------------------------------------------------------------------------------

    @Test
    void cropMustStayInsideThePictureAndNotBeASliver() {
        assertThat(new Crop(0, 0, 1, 1).fitsInPicture()).isTrue();
        assertThat(new Crop(0.25, 0.1, 0.5, 0.8).fitsInPicture()).isTrue();
        assertThat(new Crop(0.5, 0, 0.6, 1).fitsInPicture()).as("goes past the right edge").isFalse();
        assertThat(new Crop(-0.1, 0, 0.5, 0.5).fitsInPicture()).isFalse();
        assertThat(new Crop(0, 0, 0.04, 0.5).fitsInPicture()).as("too narrow").isFalse();
        assertThat(new Crop(0, 0, Double.NaN, 0.5).fitsInPicture()).isFalse();
    }

    // ---- turning pictures upright ----------------------------------------------------------------------

    private static boolean isRed(BufferedImage image, int x, int y) {
        int rgb = image.getRGB(x, y);
        return ((rgb >> 16) & 0xFF) > 200 && ((rgb >> 8) & 0xFF) < 60 && (rgb & 0xFF) < 60;
    }

    /** Where the red square (which starts in the source's top-left corner) ends up, for each EXIF orientation. */
    @Test
    void everyOrientationPutsTheTopLeftWhereTheExifStandardSays() {
        BufferedImage source = TestImages.picture(40, 20); // wider than tall, so a quarter turn is visible
        int[][] expected = {
                // orientation, then the corner where the source's top-left ends up: 0 TL, 1 TR, 2 BR, 3 BL
                {1, 0}, {2, 1}, {3, 2}, {4, 3}, {5, 0}, {6, 1}, {7, 2}, {8, 3}};
        for (int[] row : expected) {
            BufferedImage turned = ExifOrientation.apply(source, row[0]);
            boolean swapped = row[0] >= 5;
            assertThat(turned.getWidth()).as("width for %d", row[0]).isEqualTo(swapped ? 20 : 40);
            assertThat(turned.getHeight()).as("height for %d", row[0]).isEqualTo(swapped ? 40 : 20);
            int w = turned.getWidth();
            int h = turned.getHeight();
            int[][] corners = {{1, 1}, {w - 2, 1}, {w - 2, h - 2}, {1, h - 2}};
            for (int corner = 0; corner < 4; corner++) {
                assertThat(isRed(turned, corners[corner][0], corners[corner][1]))
                        .as("orientation %d, corner %d", row[0], corner)
                        .isEqualTo(corner == row[1]);
            }
        }
    }

    @Test
    void readsTheOrientationFromAJpegAndIgnoresJunk() {
        byte[] plain = TestImages.jpeg(30, 10);
        assertThat(ExifOrientation.read(plain)).isEqualTo(1);
        for (int orientation = 1; orientation <= 8; orientation++) {
            assertThat(ExifOrientation.read(TestImages.withExifOrientation(plain, orientation))).isEqualTo(orientation);
        }
        assertThat(ExifOrientation.read(TestImages.withExifOrientation(plain, 9))).as("out of range").isEqualTo(1);
        assertThat(ExifOrientation.read(new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE1, 0, 2})).isEqualTo(1);
    }

    // ---- cleaning ------------------------------------------------------------------------------------------

    private final ImageProcessor processor = new ImageProcessor(1_000_000);

    @Test
    void reEncodesAndReportsTheSize() {
        var result = processor.process(TestImages.jpeg(64, 48), FileType.JPEG);
        assertThat(result.mimeType()).isEqualTo("image/jpeg");
        assertThat(result.width()).isEqualTo(64);
        assertThat(result.height()).isEqualTo(48);
        assertThat(TestImages.read(result.bytes()).getWidth()).isEqualTo(64);
    }

    @Test
    void appliesTheOrientationBeforeDroppingTheExifBlock() {
        byte[] sideways = TestImages.withExifOrientation(TestImages.jpeg(60, 20), 6);
        var result = processor.process(sideways, FileType.JPEG);
        assertThat(result.width()).isEqualTo(20);
        assertThat(result.height()).isEqualTo(60);
        assertThat(ExifOrientation.read(result.bytes())).as("no EXIF left").isEqualTo(1);
        assertThat(new String(result.bytes(), StandardCharsets.ISO_8859_1)).doesNotContain("Exif");
    }

    @Test
    void keepsPngTransparencyAndFormat() {
        BufferedImage clear = new BufferedImage(10, 10, BufferedImage.TYPE_INT_ARGB);
        var bytes = new java.io.ByteArrayOutputStream();
        try {
            javax.imageio.ImageIO.write(clear, "png", bytes);
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
        var result = processor.process(bytes.toByteArray(), FileType.PNG);
        assertThat(result.mimeType()).isEqualTo("image/png");
        assertThat(TestImages.read(result.bytes()).getColorModel().hasAlpha()).isTrue();
    }

    @Test
    void refusesAPictureWithTooManyPixelsBeforeDecodingIt() {
        byte[] big = TestImages.png(1200, 1000); // 1.2 million pixels, limit here is 1 million
        assertThatThrownBy(() -> processor.process(big, FileType.PNG))
                .isInstanceOfSatisfying(ApiException.class, e -> {
                    assertThat(e.code()).isEqualTo(ErrorCode.UNSUPPORTED_FILE);
                    assertThat(e.getMessage()).contains("too many pixels");
                });
    }

    @Test
    void refusesADamagedPicture() {
        byte[] damaged = TestImages.jpeg(50, 50);
        for (int i = 20; i < damaged.length - 2; i++) {
            damaged[i] = (byte) 0x13;
        }
        assertThatThrownBy(() -> processor.process(damaged, FileType.JPEG))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.UNSUPPORTED_FILE));
    }
}
