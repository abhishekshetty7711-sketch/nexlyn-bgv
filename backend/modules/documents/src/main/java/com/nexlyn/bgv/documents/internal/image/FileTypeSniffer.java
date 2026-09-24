package com.nexlyn.bgv.documents.internal.image;

import java.util.Optional;

/**
 * Decides what a file really is from its first bytes. The name and the content type the browser sent
 * are never trusted (CLAUDE.md section 11.4). Only JPEG, PNG and PDF are accepted.
 */
public final class FileTypeSniffer {

    public enum FileType {
        JPEG("image/jpeg"), PNG("image/png"), PDF("application/pdf");

        private final String mimeType;

        FileType(String mimeType) {
            this.mimeType = mimeType;
        }

        public String mimeType() {
            return mimeType;
        }

        public boolean isImage() {
            return this != PDF;
        }
    }

    private FileTypeSniffer() {
    }

    public static Optional<FileType> detect(byte[] content) {
        if (startsWith(content, 0xFF, 0xD8, 0xFF)) {
            return Optional.of(FileType.JPEG);
        }
        if (startsWith(content, 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)) {
            return Optional.of(FileType.PNG);
        }
        if (startsWith(content, '%', 'P', 'D', 'F', '-')) {
            return Optional.of(FileType.PDF);
        }
        return Optional.empty();
    }

    private static boolean startsWith(byte[] content, int... prefix) {
        if (content == null || content.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if ((content[i] & 0xFF) != prefix[i]) {
                return false;
            }
        }
        return true;
    }
}
