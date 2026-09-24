package com.nexlyn.bgv.documents;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * What other modules may ask the documents module (CLAUDE.md {@literal §4.2} rule 2). Read only, and
 * it does NOT check who is asking: the calling module must check access to the case first.
 */
public interface DocumentApi {

    /**
     * @param kind         PHOTO, CHECK_DOC or FREE_IMAGE
     * @param displayLabel "Original Document", "Additional Document N", or the custom label
     * @param crop         the part to show, as fractions of the picture, or null for all of it
     */
    record DocumentInfo(UUID id, UUID caseId, UUID checkId, String kind, String displayLabel, String mimeType,
                        Integer width, Integer height, boolean moveToNextPage, boolean useLargerBox, Crop crop) {

        public boolean isImage() {
            return mimeType.startsWith("image/");
        }

        public boolean isPdf() {
            return "application/pdf".equals(mimeType);
        }
    }

    /** A crop as fractions (0 to 1) of the picture's width and height. */
    record Crop(double x, double y, double width, double height) {
    }

    /** The supporting documents of a check, in report order. */
    List<DocumentInfo> supportingDocuments(UUID checkId);

    Optional<DocumentInfo> find(UUID documentId);

    /** The stored bytes (already cleaned when the picture was uploaded). Throws when the file is missing. */
    byte[] content(UUID documentId);
}
