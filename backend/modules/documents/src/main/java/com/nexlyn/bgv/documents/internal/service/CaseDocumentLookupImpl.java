package com.nexlyn.bgv.documents.internal.service;

import com.nexlyn.bgv.cases.CaseDocumentLookup;
import com.nexlyn.bgv.documents.internal.domain.DocumentKind;
import com.nexlyn.bgv.documents.internal.repository.StoredDocumentRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Answers the cases module's questions about documents (validation, image blocks). */
@Component
class CaseDocumentLookupImpl implements CaseDocumentLookup {

    private final StoredDocumentRepository documents;

    CaseDocumentLookupImpl(StoredDocumentRepository documents) {
        this.documents = documents;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, Long> supportingDocumentCounts(UUID caseId) {
        Map<UUID, Long> counts = new HashMap<>();
        for (Object[] row : documents.countSupportingByCheck(caseId)) {
            counts.put((UUID) row[0], (Long) row[1]);
        }
        return counts;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isFreeImageOf(UUID documentId, UUID caseId, UUID checkId) {
        return documents.findByIdAndDeletedAtIsNull(documentId)
                .filter(d -> d.getKind() == DocumentKind.FREE_IMAGE && caseId.equals(d.getCaseId()) && checkId.equals(d.getCheckId()))
                .isPresent();
    }
}
