package com.nexlyn.bgv.cases.internal.service;

import com.nexlyn.bgv.cases.CaseApi;
import com.nexlyn.bgv.cases.internal.domain.BgvCase;
import com.nexlyn.bgv.cases.internal.domain.Candidate;
import com.nexlyn.bgv.cases.internal.domain.VerificationCheck;
import com.nexlyn.bgv.cases.internal.repository.CandidateRepository;
import com.nexlyn.bgv.cases.internal.repository.CaseRepository;
import com.nexlyn.bgv.cases.internal.repository.VerificationCheckRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/** Answers other modules' questions about cases and checks (no access checks: the caller does them). */
@Component
class CaseApiImpl implements CaseApi {

    private final CaseRepository cases;
    private final VerificationCheckRepository checks;
    private final CandidateRepository candidates;

    CaseApiImpl(CaseRepository cases, VerificationCheckRepository checks, CandidateRepository candidates) {
        this.cases = cases;
        this.checks = checks;
        this.candidates = candidates;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<UUID> caseIdOfCheck(UUID checkId) {
        return checks.findById(checkId)
                .map(VerificationCheck::getCaseId)
                .filter(this::caseExists);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean caseExists(UUID caseId) {
        return cases.findByIdAndDeletedAtIsNull(caseId).isPresent();
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isEditable(UUID caseId) {
        return cases.findByIdAndDeletedAtIsNull(caseId).map(BgvCase::getLifecycle).map(l -> l.isEditable()).orElse(false);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean checkBelongsToCase(UUID caseId, UUID checkId) {
        return caseExists(caseId) && checks.findByIdAndCaseId(checkId, caseId).isPresent();
    }

    @Override
    @Transactional
    public Optional<UUID> replaceCandidatePhoto(UUID caseId, UUID documentId) {
        Candidate candidate = candidates.findByCaseId(caseId).orElseThrow();
        Optional<UUID> previous = Optional.ofNullable(candidate.getPhotoDocumentId());
        candidates.setPhoto(caseId, documentId);
        return previous;
    }

    @Override
    @Transactional
    public Optional<UUID> clearCandidatePhoto(UUID caseId) {
        Candidate candidate = candidates.findByCaseId(caseId).orElseThrow();
        Optional<UUID> previous = Optional.ofNullable(candidate.getPhotoDocumentId());
        candidates.setPhoto(caseId, null);
        return previous;
    }
}
