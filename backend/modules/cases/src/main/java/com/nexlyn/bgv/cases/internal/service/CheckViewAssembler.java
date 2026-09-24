package com.nexlyn.bgv.cases.internal.service;

import com.nexlyn.bgv.cases.internal.checktype.CheckTypeDefinition;
import com.nexlyn.bgv.cases.internal.checktype.CheckTypeDefinition.FieldDefinition;
import com.nexlyn.bgv.cases.internal.checktype.CheckTypeRegistry;
import com.nexlyn.bgv.cases.internal.domain.Candidate;
import com.nexlyn.bgv.cases.internal.domain.CheckDetail;
import com.nexlyn.bgv.cases.internal.domain.CheckField;
import com.nexlyn.bgv.cases.internal.domain.CheckFreeSection;
import com.nexlyn.bgv.cases.internal.domain.ParentType;
import com.nexlyn.bgv.cases.internal.domain.VerificationCheck;
import com.nexlyn.bgv.cases.internal.repository.CandidateRepository;
import com.nexlyn.bgv.cases.internal.repository.CheckDetailRepository;
import com.nexlyn.bgv.cases.internal.repository.CheckFieldRepository;
import com.nexlyn.bgv.cases.internal.repository.CheckFreeSectionRepository;
import com.nexlyn.bgv.cases.internal.repository.VerificationCheckRepository;
import com.nexlyn.bgv.cases.internal.service.CheckViews.CheckView;
import com.nexlyn.bgv.cases.internal.service.CheckViews.DateSync;
import com.nexlyn.bgv.cases.internal.service.CheckViews.DetailView;
import com.nexlyn.bgv.cases.internal.service.CheckViews.FieldView;
import com.nexlyn.bgv.cases.internal.service.CheckViews.FreeSectionView;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/** Builds {@link CheckView}s from entities: labels follow the candidate, sensitive values stay masked. */
@Component
class CheckViewAssembler {

    private final VerificationCheckRepository checks;
    private final CheckFieldRepository fields;
    private final CheckDetailRepository details;
    private final CheckFreeSectionRepository freeSections;
    private final CandidateRepository candidates;
    private final CheckTypeRegistry registry;

    CheckViewAssembler(VerificationCheckRepository checks, CheckFieldRepository fields, CheckDetailRepository details,
                       CheckFreeSectionRepository freeSections, CandidateRepository candidates, CheckTypeRegistry registry) {
        this.checks = checks;
        this.fields = fields;
        this.details = details;
        this.freeSections = freeSections;
        this.candidates = candidates;
        this.registry = registry;
    }

    /** Every check of the case, in report order. */
    List<CheckView> viewAll(UUID caseId) {
        List<VerificationCheck> all = checks.findAllByCaseIdOrderBySortOrderAsc(caseId);
        ParentType parent = candidates.findByCaseId(caseId).map(Candidate::getParentType).orElse(ParentType.FATHER);
        return java.util.stream.IntStream.range(0, all.size()).mapToObj(i -> view(all.get(i), i, parent)).toList();
    }

    CheckView view(VerificationCheck check) {
        List<VerificationCheck> all = checks.findAllByCaseIdOrderBySortOrderAsc(check.getCaseId());
        int index = 0;
        for (int i = 0; i < all.size(); i++) {
            if (all.get(i).getId().equals(check.getId())) {
                index = i;
            }
        }
        ParentType parent = candidates.findByCaseId(check.getCaseId()).map(Candidate::getParentType).orElse(ParentType.FATHER);
        return view(check, index, parent);
    }

    private CheckView view(VerificationCheck check, int index, ParentType parent) {
        CheckTypeDefinition def = registry.find(check.getType()).orElseThrow(
                () -> new IllegalStateException("Check type " + check.getType() + " has no definition"));
        List<CheckField> stored = fields.findAllByCheckIdOrderBySortOrderAsc(check.getId());
        List<FieldView> fieldViews = def.fields().stream()
                .map(d -> fieldView(d, stored.stream().filter(f -> f.getFieldKey().equals(d.key())).findFirst().orElse(null), parent))
                .toList();
        List<DetailView> detailViews = details.findAllByCheckIdOrderBySortOrderAsc(check.getId()).stream()
                .sorted(Comparator.comparingInt(CheckDetail::getSortOrder))
                .map(d -> new DetailView(d.getLabel(), d.getValue()))
                .toList();
        List<FreeSectionView> freeViews = freeSections.findAllByCheckIdOrderBySortOrderAsc(check.getId()).stream()
                .map(CheckViewAssembler::freeView).toList();
        DateSync sync = index == 0 ? DateSync.MASTER : check.isDatesManual() ? DateSync.MANUAL : DateSync.AUTO;
        return new CheckView(check.getId(), check.getCaseId(), check.getType(), def.displayName(), def.documentName(), def.groupKey(),
                check.getTitle(), check.getSummaryDescription(), check.getThisCardVerifies(), check.getStatus(),
                check.getVerificationType(), check.getRequestedDate(), check.getCompletedDate(), sync, check.getRemarks(),
                check.isHasAttestation(), check.getBarCouncilNo(), check.getDisclaimer(), check.getSortOrder(),
                check.getVersion(), fieldViews, detailViews, freeViews, check.getUpdatedAt());
    }

    private static FieldView fieldView(FieldDefinition d, CheckField f, ParentType parent) {
        String label = d.labelByParentType() && parent == ParentType.GUARDIAN ? d.label().replace("Father's", "Guardian's") : d.label();
        if (f == null) { // a field added to the YAML after this check was created: shown empty until saved
            return new FieldView(d.key(), label, d.type(), d.sensitive(), d.required(), null, false, false, false,
                    com.nexlyn.bgv.cases.internal.domain.FieldSource.MANUAL);
        }
        boolean hasValue = d.sensitive() ? f.getValueEncrypted() != null : f.getValue() != null;
        return new FieldView(d.key(), label, d.type(), d.sensitive(), d.required(), f.getValue(), hasValue,
                f.isVerifiedTick(), f.isManual(), f.getSource());
    }

    private static FreeSectionView freeView(CheckFreeSection s) {
        return new FreeSectionView(s.getId(), s.getKind(), s.getTextValue(), s.getDocumentId(), s.getSortOrder());
    }
}
