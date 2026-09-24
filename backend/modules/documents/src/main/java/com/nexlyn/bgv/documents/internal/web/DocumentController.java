package com.nexlyn.bgv.documents.internal.web;

import com.nexlyn.bgv.common.error.ApiError;
import com.nexlyn.bgv.common.error.ApiException;
import com.nexlyn.bgv.common.error.ErrorCode;
import com.nexlyn.bgv.documents.internal.domain.Crop;
import com.nexlyn.bgv.documents.internal.domain.DocumentKind;
import com.nexlyn.bgv.documents.internal.service.DocumentService;
import com.nexlyn.bgv.documents.internal.service.DocumentService.UpdateInput;
import com.nexlyn.bgv.documents.internal.service.DocumentViews.DocumentContent;
import com.nexlyn.bgv.documents.internal.service.DocumentViews.DocumentView;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

/** Files (CLAUDE.md section 9.3). Thin: the rules live in {@link DocumentService}. */
@RestController
@RequestMapping("/api")
public class DocumentController {

    record UpdateRequest(@Size(max = 100) String label, boolean moveToNextPage, boolean useLargerBox, @Valid CropRequest crop, Long version) {
    }

    record CropRequest(@NotNull Double x, @NotNull Double y, @NotNull Double width, @NotNull Double height) {
        Crop toCrop() {
            return new Crop(x, y, width, height);
        }
    }

    record OrderRequest(@NotNull List<UUID> ids) {
    }

    private final DocumentService documents;

    public DocumentController(DocumentService documents) {
        this.documents = documents;
    }

    @PostMapping("/cases/{id}/candidate/photo")
    public ResponseEntity<DocumentView> uploadPhoto(@PathVariable UUID id, @RequestParam("file") MultipartFile file) {
        return ResponseEntity.status(HttpStatus.CREATED).body(documents.uploadPhoto(id, file.getOriginalFilename(), bytesOf(file)));
    }

    @DeleteMapping("/cases/{id}/candidate/photo")
    public ResponseEntity<Void> deletePhoto(@PathVariable UUID id) {
        documents.deletePhoto(id);
        return ResponseEntity.noContent().build();
    }

    /** {@code kind} is CHECK_DOC (default, a supporting document) or FREE_IMAGE (a picture for an image block). */
    @PostMapping("/checks/{checkId}/documents")
    public ResponseEntity<DocumentView> upload(@PathVariable UUID checkId, @RequestParam("file") MultipartFile file,
                                               @RequestParam(value = "kind", defaultValue = "CHECK_DOC") String kind) {
        DocumentKind parsed;
        try {
            parsed = DocumentKind.valueOf(kind);
        } catch (IllegalArgumentException e) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "The request is not valid.",
                    List.of(new ApiError.FieldError("kind", "must be CHECK_DOC or FREE_IMAGE")), null);
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(documents.uploadForCheck(checkId, parsed, file.getOriginalFilename(), bytesOf(file)));
    }

    @GetMapping("/checks/{checkId}/documents")
    public List<DocumentView> list(@PathVariable UUID checkId) {
        return documents.listForCheck(checkId);
    }

    @PatchMapping("/checks/{checkId}/documents/order")
    public List<DocumentView> reorder(@PathVariable UUID checkId, @Valid @RequestBody OrderRequest body) {
        return documents.reorder(checkId, body.ids());
    }

    @PutMapping("/documents/{docId}")
    public DocumentView update(@PathVariable UUID docId, @Valid @RequestBody UpdateRequest body) {
        return documents.update(docId, new UpdateInput(body.label(), body.moveToNextPage(), body.useLargerBox(),
                body.crop() == null ? null : body.crop().toCrop(), body.version()));
    }

    @DeleteMapping("/documents/{docId}")
    public ResponseEntity<Void> delete(@PathVariable UUID docId) {
        documents.delete(docId);
        return ResponseEntity.noContent().build();
    }

    /**
     * The file itself, streamed after the permission and case checks (the store is never exposed).
     * Pictures show inline; PDFs are always a download, and nothing is ever run by the browser.
     */
    @GetMapping("/documents/{docId}/content")
    public ResponseEntity<byte[]> content(@PathVariable UUID docId) {
        DocumentContent file = documents.content(docId);
        ContentDisposition disposition = (file.image() ? ContentDisposition.inline() : ContentDisposition.attachment())
                .filename(file.filename(), StandardCharsets.UTF_8).build();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.mimeType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .header("X-Content-Type-Options", "nosniff")
                .header("Content-Security-Policy", "default-src 'none'; sandbox")
                .cacheControl(CacheControl.noStore().cachePrivate())
                .body(file.bytes());
    }

    private static byte[] bytesOf(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "The file could not be read.");
        }
    }
}
