package com.nexlyn.bgv.documents.internal.service;

import com.nexlyn.bgv.documents.internal.domain.Crop;
import com.nexlyn.bgv.documents.internal.domain.DocumentKind;
import com.nexlyn.bgv.documents.internal.domain.ImageQuality;

import java.time.Instant;
import java.util.UUID;

/** The shapes returned to the frontend. No entity leaves the service layer. */
public final class DocumentViews {

    private DocumentViews() {
    }

    /**
     * @param displayLabel the label shown to people: the custom one, or "Original Document" /
     *                     "Additional Document N" by position among the check's supporting documents
     */
    public record DocumentView(UUID id, UUID caseId, UUID checkId, DocumentKind kind, String label, String displayLabel,
                               String originalFilename, String mimeType, long sizeBytes, Integer width, Integer height,
                               ImageQuality quality, boolean moveToNextPage, boolean useLargerBox, Crop crop,
                               int sortOrder, long version, Instant uploadedAt) {
    }

    /** A file's bytes with what is needed to send them. */
    public record DocumentContent(byte[] bytes, String mimeType, String filename, boolean image) {
    }
}
