package com.nexlyn.bgv.documents.internal.service;

import com.nexlyn.bgv.documents.DocumentApi;
import com.nexlyn.bgv.documents.internal.domain.DocumentKind;
import com.nexlyn.bgv.documents.internal.domain.StoredDocument;
import com.nexlyn.bgv.documents.internal.repository.StoredDocumentRepository;
import com.nexlyn.bgv.documents.internal.storage.StorageService;
import com.nexlyn.bgv.common.error.ApiException;
import com.nexlyn.bgv.common.error.ErrorCode;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Answers other modules' questions about documents (no access checks: the caller does them). */
@Component
class DocumentApiImpl implements DocumentApi {

    private final StoredDocumentRepository documents;
    private final StorageService storage;

    DocumentApiImpl(StoredDocumentRepository documents, StorageService storage) {
        this.documents = documents;
        this.storage = storage;
    }

    @Override
    @Transactional(readOnly = true)
    public List<DocumentInfo> supportingDocuments(UUID checkId) {
        List<DocumentInfo> result = new ArrayList<>();
        List<StoredDocument> rows = documents.findAllByCheckIdAndKindAndDeletedAtIsNullOrderBySortOrderAscCreatedAtAsc(checkId, DocumentKind.CHECK_DOC);
        for (int i = 0; i < rows.size(); i++) {
            result.add(info(rows.get(i), i));
        }
        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<DocumentInfo> find(UUID documentId) {
        return documents.findByIdAndDeletedAtIsNull(documentId).map(d -> info(d, 0));
    }

    @Override
    @Transactional(readOnly = true)
    public byte[] content(UUID documentId) {
        StoredDocument document = documents.findByIdAndDeletedAtIsNull(documentId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Document not found."));
        return storage.get(document.getStorageKey());
    }

    private static DocumentInfo info(StoredDocument d, int position) {
        com.nexlyn.bgv.documents.internal.domain.Crop crop = d.getCrop();
        return new DocumentInfo(d.getId(), d.getCaseId(), d.getCheckId(), d.getKind().name(), DocumentService.displayLabel(d, position),
                d.getMimeType(), d.getWidth(), d.getHeight(), d.isMoveToNextPage(), d.isUseLargerBox(),
                crop == null ? null : new DocumentApi.Crop(crop.x(), crop.y(), crop.width(), crop.height()));
    }
}
