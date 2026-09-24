package com.nexlyn.bgv.documents.internal.service;

import com.nexlyn.bgv.cases.CheckDeletedEvent;
import com.nexlyn.bgv.cases.FreeImageRemovedEvent;
import com.nexlyn.bgv.documents.internal.repository.StoredDocumentRepository;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;

/**
 * Retires the files of things the cases module removes. It runs in the same transaction as the removal,
 * so a check never disappears while its documents stay behind (or the other way round). The stored
 * bytes are kept: a purge job with a retention rule comes with the hardening phase.
 */
@Component
class DocumentCleanupListener {

    private final StoredDocumentRepository documents;
    private final Clock clock;

    DocumentCleanupListener(StoredDocumentRepository documents, Clock clock) {
        this.documents = documents;
        this.clock = clock;
    }

    @EventListener
    void onCheckDeleted(CheckDeletedEvent event) {
        documents.retireAllOfCheck(event.checkId(), Instant.now(clock));
    }

    @EventListener
    void onFreeImageRemoved(FreeImageRemovedEvent event) {
        documents.findByIdAndDeletedAtIsNull(event.documentId()).ifPresent(document -> {
            document.retire(Instant.now(clock));
            documents.save(document);
        });
    }
}
