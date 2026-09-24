package com.nexlyn.bgv.cases.internal.service;

import com.nexlyn.bgv.cases.internal.checktype.CheckTypeDefinition;
import com.nexlyn.bgv.cases.internal.checktype.CheckTypeRegistry;
import com.nexlyn.bgv.cases.internal.domain.CheckField;
import com.nexlyn.bgv.cases.internal.domain.VerificationCheck;
import com.nexlyn.bgv.cases.internal.repository.CheckFieldRepository;
import com.nexlyn.bgv.cases.internal.repository.VerificationCheckRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
class ChecksSummaryImpl implements ChecksSummary {

    private final VerificationCheckRepository checks;
    private final CheckFieldRepository fields;
    private final CheckTypeRegistry registry;

    ChecksSummaryImpl(VerificationCheckRepository checks, CheckFieldRepository fields, CheckTypeRegistry registry) {
        this.checks = checks;
        this.fields = fields;
        this.registry = registry;
    }

    @Override
    @Transactional(readOnly = true)
    public List<CheckSummary> summariesOf(UUID caseId) {
        List<VerificationCheck> all = checks.findAllByCaseIdOrderBySortOrderAsc(caseId);
        if (all.isEmpty()) {
            return List.of();
        }
        Map<UUID, List<CheckField>> rowsByCheck = fields.findAllByCheckIdIn(all.stream().map(VerificationCheck::getId).toList())
                .stream().collect(Collectors.groupingBy(CheckField::getCheckId));
        return all.stream().map(check -> {
            CheckTypeDefinition def = registry.find(check.getType()).orElse(null);
            List<String> missing = def == null ? List.of() : missingRequired(def, rowsByCheck.getOrDefault(check.getId(), List.of()));
            return new CheckSummary(check.getId(), check.getTitle(), check.getStatus(), check.getRequestedDate(),
                    check.getCompletedDate(), missing);
        }).toList();
    }

    private static List<String> missingRequired(CheckTypeDefinition def, List<CheckField> rows) {
        Map<String, CheckField> byKey = rows.stream().collect(Collectors.toMap(CheckField::getFieldKey, Function.identity()));
        return def.fields().stream().filter(CheckTypeDefinition.FieldDefinition::required).filter(d -> {
            CheckField row = byKey.get(d.key());
            return row == null || (d.sensitive() ? row.getValueEncrypted() == null : row.getValue() == null);
        }).map(CheckTypeDefinition.FieldDefinition::label).toList();
    }
}
