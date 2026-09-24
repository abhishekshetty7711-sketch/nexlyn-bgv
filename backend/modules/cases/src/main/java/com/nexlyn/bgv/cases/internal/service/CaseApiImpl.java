package com.nexlyn.bgv.cases.internal.service;

import com.nexlyn.bgv.cases.CaseApi;
import com.nexlyn.bgv.cases.CaseReport;
import com.nexlyn.bgv.cases.CaseValidation;
import com.nexlyn.bgv.common.error.ApiException;
import com.nexlyn.bgv.common.error.ErrorCode;
import com.nexlyn.bgv.cases.internal.domain.BgvCase;
import com.nexlyn.bgv.cases.internal.domain.Candidate;
import com.nexlyn.bgv.cases.internal.domain.VerificationCheck;
import com.nexlyn.bgv.cases.internal.repository.CandidateRepository;
import com.nexlyn.bgv.cases.internal.repository.CaseRepository;
import com.nexlyn.bgv.cases.internal.repository.VerificationCheckRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/** Answers other modules' questions about cases and checks (no access checks: the caller does them). */
@Component
class CaseApiImpl implements CaseApi {

    private final CaseRepository cases;
    private final VerificationCheckRepository checks;
    private final CandidateRepository candidates;
    private final CaseViewAssembler caseViews;
    private final CheckViewAssembler checkViews;
    private final CaseInsightService insight;

    CaseApiImpl(CaseRepository cases, VerificationCheckRepository checks, CandidateRepository candidates,
                CaseViewAssembler caseViews, CheckViewAssembler checkViews, CaseInsightService insight) {
        this.cases = cases;
        this.checks = checks;
        this.candidates = candidates;
        this.caseViews = caseViews;
        this.checkViews = checkViews;
        this.insight = insight;
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

    @Override
    @Transactional(readOnly = true)
    public CaseReport reportOf(UUID caseId) {
        BgvCase c = cases.findByIdAndDeletedAtIsNull(caseId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Case not found."));
        CaseViews.CaseView view = caseViews.view(c);
        CaseViews.CandidateView k = view.candidate();
        String company = view.companyDisplayName() != null && !view.companyDisplayName().isBlank()
                ? view.companyDisplayName() : view.client().displayName();
        List<CaseReport.Check> reportChecks = checkViews.viewAll(caseId).stream().map(CaseApiImpl::reportCheck).toList();
        return new CaseReport(view.id(), view.reportId(), view.lifecycle(), view.issueDate(), company,
                new CaseReport.Candidate(k.fullName(), k.parentType() == com.nexlyn.bgv.cases.internal.domain.ParentType.GUARDIAN,
                        k.parentName(), k.employeeId(), k.dob(), k.phoneDisplay(), k.photoDocumentId()),
                new CaseReport.Period(view.period().show(), view.period().start(), view.period().end()),
                new CaseReport.Pill(view.overview().statusPreset().name(), view.overview().statusTitle(), view.overview().statusSubtitle()),
                new CaseReport.Overview(view.overview().effective().total(), view.overview().effective().completed(),
                        view.overview().effective().overallStatus()),
                view.remarks().analystRemarks(), view.remarks().finalRecommendation(),
                new CaseReport.Settings(view.settings().layoutCards(), view.settings().dateFormat().name(),
                        view.settings().watermarkEnabled(), view.settings().watermarkText()),
                reportChecks);
    }

    private static CaseReport.Check reportCheck(CheckViews.CheckView v) {
        String summary = notBlank(v.summaryDescription()) ? v.summaryDescription() : v.documentName();
        String verifies = notBlank(v.thisCardVerifies()) ? v.thisCardVerifies() : summary;
        return new CaseReport.Check(v.id(), v.type(), v.groupKey(), v.title(), summary, verifies, v.documentName(),
                v.status(), v.verificationType(), v.requestedDate(), v.completedDate(), v.remarks(), v.hasAttestation(),
                v.barCouncilNo(), v.disclaimer(),
                v.fields().stream()
                        .map(f -> new CaseReport.Field(f.label(), f.type().name().toLowerCase(Locale.ROOT), f.value(), f.verifiedTick()))
                        .toList(),
                v.details().stream().map(d -> new CaseReport.Detail(d.label(), d.value())).toList(),
                v.freeSections().stream()
                        .map(b -> new CaseReport.FreeBlock(b.kind().name(), b.text(), b.documentId()))
                        .toList());
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    @Override
    @Transactional(readOnly = true)
    public CaseValidation validationOf(UUID caseId) {
        if (!caseExists(caseId)) {
            throw new ApiException(ErrorCode.NOT_FOUND, "Case not found.");
        }
        CaseViews.ValidationResult result = insight.validationFor(caseId);
        return new CaseValidation(
                result.errors().stream().map(i -> new CaseValidation.Issue(i.section(), i.field(), i.message())).toList(),
                result.warnings().stream().map(i -> new CaseValidation.Issue(i.section(), i.field(), i.message())).toList());
    }
}
