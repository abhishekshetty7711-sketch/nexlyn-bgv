package com.nexlyn.bgv.cases.internal.service;

import com.nexlyn.bgv.cases.internal.checktype.CheckTypeDefinition;
import com.nexlyn.bgv.cases.internal.checktype.CheckTypeRegistry;
import com.nexlyn.bgv.cases.internal.domain.Candidate;
import com.nexlyn.bgv.cases.internal.domain.CheckField;
import com.nexlyn.bgv.cases.internal.domain.FieldSource;
import com.nexlyn.bgv.cases.internal.domain.VerificationCheck;
import com.nexlyn.bgv.cases.internal.repository.CheckFieldRepository;
import com.nexlyn.bgv.cases.internal.repository.VerificationCheckRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Keeps check fields in step with the candidate (CLAUDE.md {@literal §8}): a field with a
 * {@code prefill} is filled when the check is created and again whenever the candidate is saved,
 * unless someone typed a value by hand (the field is then marked manual and left alone).
 */
@Component
public class CheckPrefillService {

    private final VerificationCheckRepository checks;
    private final CheckFieldRepository fields;
    private final CheckTypeRegistry registry;

    CheckPrefillService(VerificationCheckRepository checks, CheckFieldRepository fields, CheckTypeRegistry registry) {
        this.checks = checks;
        this.fields = fields;
        this.registry = registry;
    }

    /** The candidate's value for a prefill property name, as text; null when it is empty. */
    static String valueOf(Candidate candidate, String property) {
        return switch (property) {
            case "fullName" -> candidate.getFullName();
            case "parentName" -> candidate.getParentName();
            case "dob" -> candidate.getDob() == null ? null : candidate.getDob().toString();
            case "phone" -> candidate.getPhone();
            case "street" -> candidate.getStreet();
            case "city" -> candidate.getCity();
            case "state" -> candidate.getState();
            case "pin" -> candidate.getPin();
            case "country" -> candidate.getCountry();
            case "employeeId" -> candidate.getEmployeeId();
            default -> throw new IllegalArgumentException("Unknown candidate property " + property);
        };
    }

    /** Re-fills every non-manual prefilled field of every check of the case. Returns how many values changed. */
    int refresh(UUID caseId, Candidate candidate) {
        List<VerificationCheck> all = checks.findAllByCaseIdOrderBySortOrderAsc(caseId);
        if (all.isEmpty()) {
            return 0;
        }
        Map<UUID, List<CheckField>> byCheck = fields.findAllByCheckIdIn(all.stream().map(VerificationCheck::getId).toList())
                .stream().collect(Collectors.groupingBy(CheckField::getCheckId));
        int changed = 0;
        for (VerificationCheck check : all) {
            CheckTypeDefinition def = registry.find(check.getType()).orElse(null);
            if (def == null) {
                continue;
            }
            Map<String, CheckField> rows = byCheck.getOrDefault(check.getId(), List.of()).stream()
                    .collect(Collectors.toMap(CheckField::getFieldKey, Function.identity()));
            for (CheckTypeDefinition.FieldDefinition d : def.fields()) {
                CheckField row = rows.get(d.key());
                if (d.prefill() == null || row == null || row.isManual()) {
                    continue;
                }
                String value = valueOf(candidate, d.prefill());
                if (!java.util.Objects.equals(value, row.getValue())) {
                    row.setPlain(value, FieldSource.CANDIDATE, false);
                    fields.save(row);
                    changed++;
                }
            }
        }
        return changed;
    }
}
