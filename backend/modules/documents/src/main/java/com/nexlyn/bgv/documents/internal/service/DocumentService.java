package com.nexlyn.bgv.documents.internal.service;

import com.nexlyn.bgv.auth.AuditEvent;
import com.nexlyn.bgv.auth.AuthApi;
import com.nexlyn.bgv.auth.CaseAccessPolicy;
import com.nexlyn.bgv.auth.CaseAction;
import com.nexlyn.bgv.cases.CaseApi;
import com.nexlyn.bgv.common.error.ApiError;
import com.nexlyn.bgv.common.error.ApiException;
import com.nexlyn.bgv.common.error.ErrorCode;
import com.nexlyn.bgv.documents.internal.config.UploadProperties;
import com.nexlyn.bgv.documents.internal.domain.Crop;
import com.nexlyn.bgv.documents.internal.domain.DocumentKind;
import com.nexlyn.bgv.documents.internal.domain.ImageQuality;
import com.nexlyn.bgv.documents.internal.domain.StoredDocument;
import com.nexlyn.bgv.documents.internal.image.FileTypeSniffer;
import com.nexlyn.bgv.documents.internal.image.FileTypeSniffer.FileType;
import com.nexlyn.bgv.documents.internal.image.ImageProcessor;
import com.nexlyn.bgv.documents.internal.repository.StoredDocumentRepository;
import com.nexlyn.bgv.documents.internal.service.DocumentViews.DocumentContent;
import com.nexlyn.bgv.documents.internal.service.DocumentViews.DocumentView;
import com.nexlyn.bgv.documents.internal.storage.StorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Uploading, describing, ordering, reading and removing files (CLAUDE.md section 9.3).
 *
 * <p>Every method checks the permission (method security) and then the case (data-level policy), and
 * refuses changes once the case is locked. The bytes are accepted only after they are recognised from
 * their content as JPEG, PNG or PDF; images are re-encoded to drop metadata.
 */
@Service
public class DocumentService {

    private static final Logger log = LoggerFactory.getLogger(DocumentService.class);

    static final int MAX_PER_CHECK = 30;
    private static final int MAX_LABEL = 100;

    /** What the editor sends back for one document. {@code version} guards against two people editing at once. */
    public record UpdateInput(String label, boolean moveToNextPage, boolean useLargerBox, Crop crop, Long version) {
    }

    private final StoredDocumentRepository documents;
    private final StorageService storage;
    private final CaseApi cases;
    private final CaseAccessPolicy policy;
    private final AuthApi auth;
    private final ApplicationEventPublisher events;
    private final Clock clock;
    private final UploadProperties limits;
    private final ImageProcessor images;

    public DocumentService(StoredDocumentRepository documents, StorageService storage, CaseApi cases,
                           CaseAccessPolicy policy, AuthApi auth, ApplicationEventPublisher events, Clock clock,
                           UploadProperties limits) {
        this.documents = documents;
        this.storage = storage;
        this.cases = cases;
        this.policy = policy;
        this.auth = auth;
        this.events = events;
        this.clock = clock;
        this.limits = limits;
        this.images = new ImageProcessor(limits.maxPixelsOrDefault());
    }

    // ---- uploading ----------------------------------------------------------------------------------------

    /** A supporting document, or an image for a free block, on a check. */
    @PreAuthorize("hasAuthority('DOCUMENT_UPLOAD')")
    @Transactional
    public DocumentView uploadForCheck(UUID checkId, DocumentKind kind, String filename, byte[] content) {
        if (kind == DocumentKind.PHOTO) {
            throw invalid("kind", "must be CHECK_DOC or FREE_IMAGE");
        }
        UUID caseId = cases.caseIdOfCheck(checkId).orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Check not found."));
        StoredDocument stored = store(caseId, checkId, kind, filename, content);
        return view(stored, positionAmongSupporting(stored));
    }

    /** The candidate photo. A new one replaces the old one (which is kept in storage but retired). */
    @PreAuthorize("hasAuthority('DOCUMENT_UPLOAD')")
    @Transactional
    public DocumentView uploadPhoto(UUID caseId, String filename, byte[] content) {
        return view(store(caseId, null, DocumentKind.PHOTO, filename, content), 0);
    }

    private StoredDocument store(UUID caseId, UUID checkId, DocumentKind kind, String filename, byte[] content) {
        policy.check(caseId, CaseAction.UPLOAD_DOCUMENT);
        requireEditable(caseId);
        if (content == null || content.length == 0) {
            throw invalid("file", "is empty");
        }
        if (content.length > limits.maxBytesOrDefault()) {
            throw new ApiException(ErrorCode.FILE_TOO_LARGE,
                    "That file is larger than " + limits.maxBytesOrDefault() / (1024 * 1024) + " MB.");
        }
        FileType type = FileTypeSniffer.detect(content).orElseThrow(() ->
                new ApiException(ErrorCode.UNSUPPORTED_FILE, "Only JPEG, PNG and PDF files can be uploaded."));
        if (!type.isImage() && kind != DocumentKind.CHECK_DOC) {
            throw new ApiException(ErrorCode.UNSUPPORTED_FILE, "This needs a picture (JPEG or PNG), not a PDF.");
        }
        if (checkId != null && documents.countByCheckIdAndKindAndDeletedAtIsNull(checkId, kind) >= MAX_PER_CHECK) {
            throw new ApiException(ErrorCode.CONFLICT, "A check can have at most " + MAX_PER_CHECK + " files of this kind.");
        }

        byte[] bytes = content;
        Integer width = null;
        Integer height = null;
        ImageQuality quality = null;
        if (type.isImage()) {
            ImageProcessor.Result cleaned = images.process(content, type);
            bytes = cleaned.bytes();
            width = cleaned.width();
            height = cleaned.height();
            quality = ImageQuality.of(width, height);
        }

        Instant now = Instant.now(clock);
        String key = "cases/" + caseId + "/" + UUID.randomUUID();
        UUID uploader = auth.requireCurrentAdmin().id();
        int order = checkId == null ? 0 : documents.maxSortOrder(checkId, kind).map(max -> max + 1).orElse(0);
        StoredDocument document = new StoredDocument(caseId, checkId, kind, key, cleanFilename(filename), type.mimeType(),
                bytes.length, sha256(bytes), width, height, quality, order, uploader, now);

        storage.put(key, bytes, type.mimeType());
        try {
            document = documents.saveAndFlush(document);
            if (kind == DocumentKind.PHOTO) {
                UUID photoId = document.getId();
                cases.replaceCandidatePhoto(caseId, photoId).ifPresent(previous -> retire(previous, now));
            }
        } catch (RuntimeException e) {
            deleteQuietly(key); // do not leave a file nobody knows about
            throw e;
        }
        events.publishEvent(new AuditEvent("DOCUMENT_UPLOADED", null, null, "DOCUMENT", document.getId().toString(), caseId,
                null, null, null, Map.of("kind", kind.name(), "mimeType", type.mimeType(), "sizeBytes", bytes.length,
                "sha256", document.getSha256())));
        return document;
    }

    // ---- reading ------------------------------------------------------------------------------------------

    @PreAuthorize("hasAnyAuthority('CASE_READ_ALL', 'CASE_READ_ASSIGNED')")
    @Transactional(readOnly = true)
    public List<DocumentView> listForCheck(UUID checkId) {
        UUID caseId = cases.caseIdOfCheck(checkId).orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Check not found."));
        policy.check(caseId, CaseAction.READ);
        return views(documents.findAllByCheckIdAndDeletedAtIsNullOrderBySortOrderAscCreatedAtAsc(checkId));
    }

    /** The bytes of one file. Every access is audited (who looked at which document). */
    @PreAuthorize("hasAnyAuthority('CASE_READ_ALL', 'CASE_READ_ASSIGNED')")
    @Transactional // not read-only: the audit row is written in this transaction
    public DocumentContent content(UUID documentId) {
        StoredDocument document = find(documentId);
        policy.check(document.getCaseId(), CaseAction.READ);
        byte[] bytes = storage.get(document.getStorageKey());
        events.publishEvent(new AuditEvent("DOCUMENT_VIEWED", null, null, "DOCUMENT", documentId.toString(), document.getCaseId(),
                null, null, null, null));
        return new DocumentContent(bytes, document.getMimeType(), downloadName(document), document.isImage());
    }

    // ---- changing -----------------------------------------------------------------------------------------

    @PreAuthorize("hasAuthority('DOCUMENT_UPLOAD')")
    @Transactional
    public DocumentView update(UUID documentId, UpdateInput input) {
        StoredDocument document = find(documentId);
        policy.check(document.getCaseId(), CaseAction.UPLOAD_DOCUMENT);
        requireEditable(document.getCaseId());
        if (input.version() != null && input.version() != document.getVersion()) {
            throw new ApiException(ErrorCode.CONFLICT, "This document was changed by someone else. Reload it and try again.");
        }
        String label = input.label() == null || input.label().isBlank() ? null : input.label().trim();
        if (label != null && label.length() > MAX_LABEL) {
            throw invalid("label", "must be at most " + MAX_LABEL + " characters");
        }
        if (input.crop() != null && !input.crop().fitsInPicture()) {
            throw invalid("crop", "must stay inside the picture and be at least 5% wide and tall");
        }
        boolean listed = document.getKind() != DocumentKind.PHOTO;
        if (!document.isImage() && (input.crop() != null || input.useLargerBox())) {
            throw invalid("crop", "only pictures can be cropped or shown in a larger box");
        }
        if (!listed && (input.moveToNextPage() || input.useLargerBox() || label != null)) {
            throw invalid("label", "the candidate photo has no label or page options");
        }
        // The larger box exists only on a page of its own (reference tool: switching it on also moves the document
        // to the next page), so the two are never stored apart: what is saved and returned is what is printed.
        boolean moveToNextPage = input.moveToNextPage() || input.useLargerBox();
        document.applyPresentation(label, moveToNextPage, input.useLargerBox(), input.crop(), Instant.now(clock));
        document = documents.saveAndFlush(document);
        events.publishEvent(new AuditEvent("DOCUMENT_UPDATED", null, null, "DOCUMENT", documentId.toString(), document.getCaseId(),
                null, null, null, null));
        return view(document, positionAmongSupporting(document));
    }

    @PreAuthorize("hasAuthority('DOCUMENT_UPLOAD')")
    @Transactional
    public List<DocumentView> reorder(UUID checkId, List<UUID> orderedIds) {
        UUID caseId = cases.caseIdOfCheck(checkId).orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Check not found."));
        policy.check(caseId, CaseAction.UPLOAD_DOCUMENT);
        requireEditable(caseId);
        List<StoredDocument> current = documents.findAllByCheckIdAndKindAndDeletedAtIsNullOrderBySortOrderAscCreatedAtAsc(checkId, DocumentKind.CHECK_DOC);
        if (orderedIds == null || orderedIds.size() != current.size()
                || !new HashSet<>(orderedIds).equals(current.stream().map(StoredDocument::getId).collect(Collectors.toSet()))) {
            throw invalid("ids", "must list every supporting document of the check exactly once");
        }
        Map<UUID, StoredDocument> byId = current.stream().collect(Collectors.toMap(StoredDocument::getId, d -> d));
        Instant now = Instant.now(clock);
        for (int i = 0; i < orderedIds.size(); i++) {
            byId.get(orderedIds.get(i)).moveTo(i, now);
        }
        documents.saveAllAndFlush(byId.values());
        events.publishEvent(new AuditEvent("DOCUMENTS_REORDERED", null, null, "CHECK", checkId.toString(), caseId, null, null,
                null, Map.of("order", orderedIds.stream().map(UUID::toString).toList())));
        return views(documents.findAllByCheckIdAndDeletedAtIsNullOrderBySortOrderAscCreatedAtAsc(checkId));
    }

    /** Removes a supporting document. (Free-block images are removed by removing their block instead.) */
    @PreAuthorize("hasAuthority('DOCUMENT_DELETE')")
    @Transactional
    public void delete(UUID documentId) {
        StoredDocument document = find(documentId);
        policy.check(document.getCaseId(), CaseAction.DELETE_DOCUMENT);
        requireEditable(document.getCaseId());
        switch (document.getKind()) {
            case FREE_IMAGE -> throw new ApiException(ErrorCode.CONFLICT,
                    "This picture belongs to an image block. Remove the block from the check instead.");
            case PHOTO -> cases.clearCandidatePhoto(document.getCaseId());
            case CHECK_DOC -> {
                // nothing else refers to it
            }
        }
        document.retire(Instant.now(clock));
        documents.saveAndFlush(document);
        events.publishEvent(new AuditEvent("DOCUMENT_DELETED", null, null, "DOCUMENT", documentId.toString(), document.getCaseId(),
                null, null, null, Map.of("kind", document.getKind().name())));
    }

    /** Removes the candidate photo of a case. */
    @PreAuthorize("hasAuthority('DOCUMENT_DELETE')")
    @Transactional
    public void deletePhoto(UUID caseId) {
        policy.check(caseId, CaseAction.DELETE_DOCUMENT);
        requireEditable(caseId);
        cases.clearCandidatePhoto(caseId).ifPresent(previous -> {
            retire(previous, Instant.now(clock));
            events.publishEvent(new AuditEvent("DOCUMENT_DELETED", null, null, "DOCUMENT", previous.toString(), caseId,
                    null, null, null, Map.of("kind", DocumentKind.PHOTO.name())));
        });
    }

    // ---- helpers ------------------------------------------------------------------------------------------

    private void retire(UUID documentId, Instant now) {
        documents.findByIdAndDeletedAtIsNull(documentId).ifPresent(old -> {
            old.retire(now);
            documents.save(old);
        });
    }

    private StoredDocument find(UUID documentId) {
        return documents.findByIdAndDeletedAtIsNull(documentId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Document not found."));
    }

    private void requireEditable(UUID caseId) {
        if (!cases.isEditable(caseId)) {
            throw new ApiException(ErrorCode.CONFLICT,
                    "This case is locked while it is in review, approved or finalized. Its documents cannot be changed now.");
        }
    }

    private List<DocumentView> views(List<StoredDocument> rows) {
        List<DocumentView> result = new ArrayList<>();
        int supporting = 0;
        for (StoredDocument row : rows) {
            result.add(view(row, row.getKind() == DocumentKind.CHECK_DOC ? supporting++ : 0));
        }
        return result;
    }

    private int positionAmongSupporting(StoredDocument document) {
        if (document.getKind() != DocumentKind.CHECK_DOC) {
            return 0;
        }
        List<StoredDocument> all = documents.findAllByCheckIdAndKindAndDeletedAtIsNullOrderBySortOrderAscCreatedAtAsc(
                document.getCheckId(), DocumentKind.CHECK_DOC);
        for (int i = 0; i < all.size(); i++) {
            if (all.get(i).getId().equals(document.getId())) {
                return i;
            }
        }
        return 0;
    }

    /** "Original Document" for the first supporting document, then "Additional Document 1", 2, ... unless renamed. */
    static String displayLabel(StoredDocument d, int position) {
        return d.getLabel() != null ? d.getLabel()
                : d.getKind() == DocumentKind.PHOTO ? "Candidate photo"
                : d.getKind() == DocumentKind.FREE_IMAGE ? "Image"
                : position == 0 ? "Original Document" : "Additional Document " + position;
    }

    private static DocumentView view(StoredDocument d, int position) {
        String display = displayLabel(d, position);
        return new DocumentView(d.getId(), d.getCaseId(), d.getCheckId(), d.getKind(), d.getLabel(), display,
                d.getOriginalFilename(), d.getMimeType(), d.getSizeBytes(), d.getWidth(), d.getHeight(), d.getQuality(),
                d.isMoveToNextPage(), d.isUseLargerBox(), d.getCrop(), d.getSortOrder(), d.getVersion(), d.getCreatedAt());
    }

    /** The name shown for a download: the original name without any path or odd characters. */
    private static String downloadName(StoredDocument d) {
        String name = d.getOriginalFilename();
        if (name != null && !name.isBlank()) {
            return name;
        }
        return "document" + (d.getMimeType().equals("application/pdf") ? ".pdf" : d.getMimeType().equals("image/png") ? ".png" : ".jpg");
    }

    /** Keeps only the last part of a path, drops control characters, and limits the length (display only). */
    static String cleanFilename(String raw) {
        if (raw == null) {
            return null;
        }
        String name = raw.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1).replaceAll("\\p{Cntrl}", "").trim();
        if (name.length() > 255) {
            name = name.substring(name.length() - 255);
        }
        return name.isEmpty() ? null : name;
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private void deleteQuietly(String key) {
        try {
            storage.delete(key);
        } catch (RuntimeException e) {
            log.warn("Could not remove an orphaned stored file: {}", e.toString());
        }
    }

    private static ApiException invalid(String field, String message) {
        return new ApiException(ErrorCode.VALIDATION_FAILED, "The request is not valid.",
                List.of(new ApiError.FieldError(field, message)), null);
    }
}
