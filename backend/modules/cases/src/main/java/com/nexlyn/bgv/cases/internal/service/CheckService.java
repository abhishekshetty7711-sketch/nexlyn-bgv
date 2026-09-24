package com.nexlyn.bgv.cases.internal.service;

import com.nexlyn.bgv.auth.AdminPrincipal;
import com.nexlyn.bgv.auth.AuditEvent;
import com.nexlyn.bgv.cases.CaseDocumentLookup;
import com.nexlyn.bgv.cases.CheckDeletedEvent;
import com.nexlyn.bgv.cases.FreeImageRemovedEvent;
import com.nexlyn.bgv.auth.AuthApi;
import com.nexlyn.bgv.auth.CaseAccessPolicy;
import com.nexlyn.bgv.auth.CaseAction;
import com.nexlyn.bgv.cases.internal.checktype.CheckTypeDefinition;
import com.nexlyn.bgv.cases.internal.checktype.CheckTypeDefinition.DetailDefault;
import com.nexlyn.bgv.cases.internal.checktype.CheckTypeDefinition.FieldDefinition;
import com.nexlyn.bgv.cases.internal.checktype.CheckTypeRegistry;
import com.nexlyn.bgv.cases.internal.checktype.FieldType;
import com.nexlyn.bgv.cases.internal.checktype.FieldValues;
import com.nexlyn.bgv.cases.internal.domain.BgvCase;
import com.nexlyn.bgv.cases.internal.domain.Candidate;
import com.nexlyn.bgv.cases.internal.domain.CheckDetail;
import com.nexlyn.bgv.cases.internal.domain.CheckField;
import com.nexlyn.bgv.cases.internal.domain.CheckFreeSection;
import com.nexlyn.bgv.cases.internal.domain.FieldSource;
import com.nexlyn.bgv.cases.internal.domain.FreeSectionKind;
import com.nexlyn.bgv.cases.internal.domain.VerificationCheck;
import com.nexlyn.bgv.cases.internal.repository.CandidateRepository;
import com.nexlyn.bgv.cases.internal.repository.CaseRepository;
import com.nexlyn.bgv.cases.internal.repository.CheckDetailRepository;
import com.nexlyn.bgv.cases.internal.repository.CheckFieldRepository;
import com.nexlyn.bgv.cases.internal.repository.CheckFreeSectionRepository;
import com.nexlyn.bgv.cases.internal.repository.VerificationCheckRepository;
import com.nexlyn.bgv.cases.internal.service.CheckViews.CheckView;
import com.nexlyn.bgv.cases.internal.service.CheckViews.RevealedValue;
import com.nexlyn.bgv.common.crypto.AesGcmEncryptor;
import com.nexlyn.bgv.common.enums.CheckStatus;
import com.nexlyn.bgv.common.error.ApiError;
import com.nexlyn.bgv.common.error.ApiException;
import com.nexlyn.bgv.common.error.ErrorCode;
import com.nexlyn.bgv.common.masking.PiiMasker;
import com.nexlyn.bgv.common.security.Permission;
import com.nexlyn.bgv.common.validation.BoldOnlyHtml;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The verification checks of a case (CLAUDE.md {@literal §8}, {@literal §9.2}). Every operation asks the
 * case access policy first, refuses to touch a locked case, and is audited without ever writing a
 * field value into the audit log. Sensitive values are validated, encrypted, and shown masked.
 */
@Service
public class CheckService {

    /** Default legal attestation wording (CLAUDE.md {@literal §6.2}). */
    static final String DEFAULT_BAR_COUNCIL_NO = "KAR/670/06";
    static final String DEFAULT_DISCLAIMER = "This report is based on information available in accessible court records and databases "
            + "at the time of verification. While due care has been taken, the completeness and accuracy of records cannot be "
            + "guaranteed due to limitations in record availability and updates. This report is issued solely for background "
            + "verification purposes.";
    private static final int MAX_CHECKS_PER_CASE = 50;
    private static final int MAX_FREE_SECTIONS = 30;
    private static final int MAX_DETAILS = 30;
    private static final LocalDate EARLIEST = LocalDate.of(1900, 1, 1);
    private static final LocalDate LATEST = LocalDate.of(2100, 12, 31);

    public record FieldInput(String key, String value, Boolean verifiedTick, Boolean manual, Boolean clear) {
    }

    public record DetailInput(String label, String value) {
    }

    public record SaveCheckInput(String title, String summaryDescription, String thisCardVerifies, CheckStatus status,
                                 String verificationType, LocalDate requestedDate, LocalDate completedDate, String remarks,
                                 boolean hasAttestation, String barCouncilNo, String disclaimer,
                                 List<FieldInput> fields, List<DetailInput> details) {
    }

    private final CaseRepository cases;
    private final VerificationCheckRepository checks;
    private final CheckFieldRepository fields;
    private final CheckDetailRepository details;
    private final CheckFreeSectionRepository freeSections;
    private final CandidateRepository candidates;
    private final CheckTypeRegistry registry;
    private final CheckViewAssembler assembler;
    private final AesGcmEncryptor encryptor;
    private final CaseAccessPolicy policy;
    private final AuthApi auth;
    private final ApplicationEventPublisher events;
    private final Clock clock;
    private final ObjectProvider<CaseDocumentLookup> documentLookup;

    public CheckService(CaseRepository cases, VerificationCheckRepository checks, CheckFieldRepository fields,
                        CheckDetailRepository details, CheckFreeSectionRepository freeSections, CandidateRepository candidates,
                        CheckTypeRegistry registry, CheckViewAssembler assembler,
                        @Qualifier("piiEncryptor") AesGcmEncryptor encryptor, CaseAccessPolicy policy, AuthApi auth,
                        ApplicationEventPublisher events, Clock clock, ObjectProvider<CaseDocumentLookup> documentLookup) {
        this.documentLookup = documentLookup;
        this.cases = cases;
        this.checks = checks;
        this.fields = fields;
        this.details = details;
        this.freeSections = freeSections;
        this.candidates = candidates;
        this.registry = registry;
        this.assembler = assembler;
        this.encryptor = encryptor;
        this.policy = policy;
        this.auth = auth;
        this.events = events;
        this.clock = clock;
    }

    // ---- reading ------------------------------------------------------------------------------------

    @PreAuthorize("hasAnyAuthority('CASE_READ_ALL', 'CASE_READ_ASSIGNED')")
    @Transactional(readOnly = true)
    public List<CheckView> list(UUID caseId) {
        policy.check(caseId, CaseAction.READ);
        findCase(caseId);
        return assembler.viewAll(caseId);
    }

    /**
     * Returns one real sensitive value. Needs {@code PII_UNMASK} and access to the case; every call is
     * written to the audit log (who, which case, which field), never the value itself.
     */
    @PreAuthorize("hasAuthority('PII_UNMASK')")
    @Transactional
    public RevealedValue reveal(UUID caseId, UUID checkId, String fieldKey) {
        policy.check(caseId, CaseAction.READ);
        findCase(caseId);
        VerificationCheck check = findCheck(caseId, checkId);
        CheckTypeDefinition def = definitionOf(check);
        FieldDefinition d = def.field(fieldKey);
        if (d == null || !d.sensitive()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "That field does not hold a sensitive value.");
        }
        CheckField row = fields.findAllByCheckIdOrderBySortOrderAsc(checkId).stream()
                .filter(f -> f.getFieldKey().equals(fieldKey)).findFirst().orElse(null);
        if (row == null || row.getValueEncrypted() == null) {
            throw new ApiException(ErrorCode.NOT_FOUND, "No value has been entered for this field.");
        }
        events.publishEvent(new AuditEvent("PII_REVEALED", null, null, "CHECK_FIELD", checkId.toString(), caseId, null, null,
                null, Map.of("field", fieldKey, "checkType", check.getType())));
        return new RevealedValue(fieldKey, encryptor.decrypt(row.getValueEncrypted()));
    }

    // ---- adding, saving, ordering, deleting ----------------------------------------------------------------

    @PreAuthorize("hasAuthority('CHECK_UPDATE')")
    @Transactional
    public CheckView add(UUID caseId, String typeCode) {
        policy.check(caseId, CaseAction.UPDATE_CHECK);
        BgvCase c = openCase(caseId);
        AdminPrincipal me = auth.requireCurrentAdmin();
        CheckTypeDefinition def = registry.find(typeCode).orElseThrow(() ->
                new ApiException(ErrorCode.VALIDATION_FAILED, "That check type does not exist.",
                        List.of(new ApiError.FieldError("type", "is not a known check type")), null));
        List<VerificationCheck> existing = checks.findAllByCaseIdOrderBySortOrderAsc(caseId);
        if (existing.size() >= MAX_CHECKS_PER_CASE) {
            throw new ApiException(ErrorCode.CONFLICT, "A case can have at most " + MAX_CHECKS_PER_CASE + " checks.");
        }
        Candidate candidate = candidates.findByCaseId(caseId).orElseThrow();

        int next = existing.isEmpty() ? 0 : existing.get(existing.size() - 1).getSortOrder() + 1;
        VerificationCheck check = new VerificationCheck(caseId, def.code(), def.displayName(), def.verificationTypeDefault(), next, me.id());
        if (!existing.isEmpty()) { // later checks start with the dates of the first (the date master)
            check.applyDates(existing.get(0).getRequestedDate(), existing.get(0).getCompletedDate(), false);
        }
        // The advocate's seal is only ever applied by someone holding ATTESTATION_APPLY.
        if (def.attestationDefault() && me.hasPermission(Permission.ATTESTATION_APPLY.name())) {
            check.applyAttestation(true, DEFAULT_BAR_COUNCIL_NO, DEFAULT_DISCLAIMER);
        }
        check = checks.saveAndFlush(check);

        int order = 0;
        for (FieldDefinition d : def.fields()) {
            CheckField row = new CheckField(check.getId(), d.key(), d.label(), order++);
            if (d.prefill() != null) {
                row.setPlain(CheckPrefillService.valueOf(candidate, d.prefill()), FieldSource.CANDIDATE, false);
            }
            fields.save(row);
        }
        int detailOrder = 0;
        for (DetailDefault d : def.details()) {
            details.save(new CheckDetail(check.getId(), d.label(), d.defaultValue().isEmpty() ? null : d.defaultValue(), detailOrder++));
        }
        touch(c, me);
        events.publishEvent(new AuditEvent("CHECK_ADDED", null, null, "CHECK", check.getId().toString(), caseId, null, null,
                null, Map.of("type", def.code(), "attestation", check.isHasAttestation())));
        return assembler.view(check);
    }

    @PreAuthorize("hasAuthority('CHECK_UPDATE')")
    @Transactional
    public CheckView save(UUID caseId, UUID checkId, long version, SaveCheckInput in) {
        policy.check(caseId, CaseAction.UPDATE_CHECK);
        BgvCase c = openCase(caseId);
        AdminPrincipal me = auth.requireCurrentAdmin();
        VerificationCheck check = findCheck(caseId, checkId);
        if (check.getVersion() != version) {
            throw new ApiException(ErrorCode.CONFLICT,
                    "This check was changed by someone else since you opened it. Reload it and try again.");
        }
        CheckTypeDefinition def = definitionOf(check);
        Candidate candidate = candidates.findByCaseId(caseId).orElseThrow();
        Map<String, Object> before = cardSnapshot(check);

        // ---- the card ----
        String title = in.title() == null ? "" : in.title().trim();
        if (title.isEmpty() || title.length() > 200) {
            throw invalid("title", "is required and at most 200 characters");
        }
        if (in.status() == null) {
            throw invalid("status", "is required");
        }
        String verificationType = in.verificationType() == null || in.verificationType().isBlank()
                ? def.verificationTypeDefault() : in.verificationType().trim();
        if (verificationType.length() > 50) {
            throw invalid("verificationType", "is too long");
        }
        String remarks = blankToNull(BoldOnlyHtml.sanitize(in.remarks()));
        if (remarks != null && remarks.length() > 20000) {
            throw invalid("remarks", "is too long");
        }
        check.applyCard(title, plainText("summaryDescription", in.summaryDescription(), 2000),
                plainText("thisCardVerifies", in.thisCardVerifies(), 2000), in.status(), verificationType, remarks, me.id());
        check.touch(Instant.now(clock)); // fields-only edits must still move the version

        // ---- dates (the first check is the master) ----
        checkDate("requestedDate", in.requestedDate());
        checkDate("completedDate", in.completedDate());
        applyDates(check, caseId, in.requestedDate(), in.completedDate());

        // ---- attestation (the advocate's seal needs its own permission) ----
        applyAttestation(check, in, me);

        // ---- fields ----
        List<String> changedKeys = applyFields(check, def, candidate, in.fields());

        // ---- extra details ----
        if (in.details() != null) {
            replaceDetails(check, in.details());
        }

        checks.saveAndFlush(check);
        touch(c, me);
        Map<String, Object> after = cardSnapshot(check);
        after.put("changedFields", changedKeys);
        events.publishEvent(new AuditEvent("CHECK_SAVED", null, null, "CHECK", checkId.toString(), caseId, null, null, before, after));
        return assembler.view(check);
    }

    @PreAuthorize("hasAuthority('CHECK_UPDATE')")
    @Transactional
    public List<CheckView> reorder(UUID caseId, List<UUID> orderedIds) {
        policy.check(caseId, CaseAction.UPDATE_CHECK);
        BgvCase c = openCase(caseId);
        List<VerificationCheck> all = checks.findAllByCaseIdOrderBySortOrderAsc(caseId);
        Set<UUID> existing = all.stream().map(VerificationCheck::getId).collect(Collectors.toSet());
        if (orderedIds == null || orderedIds.size() != existing.size() || !existing.equals(new HashSet<>(orderedIds))) {
            throw invalid("ids", "must list every check of the case exactly once");
        }
        Map<UUID, VerificationCheck> byId = all.stream().collect(Collectors.toMap(VerificationCheck::getId, Function.identity()));
        for (int i = 0; i < orderedIds.size(); i++) {
            byId.get(orderedIds.get(i)).moveTo(i);
        }
        checks.saveAllAndFlush(all);
        touch(c, auth.requireCurrentAdmin());
        events.publishEvent(new AuditEvent("CHECKS_REORDERED", null, null, "CASE", caseId.toString(), caseId, null, null,
                null, Map.of("order", orderedIds.stream().map(UUID::toString).toList())));
        return assembler.viewAll(caseId);
    }

    @PreAuthorize("hasAuthority('CHECK_UPDATE')")
    @Transactional
    public void delete(UUID caseId, UUID checkId) {
        policy.check(caseId, CaseAction.UPDATE_CHECK);
        BgvCase c = openCase(caseId);
        VerificationCheck check = findCheck(caseId, checkId);
        fields.deleteAllByCheckId(checkId);
        details.deleteAllByCheckId(checkId);
        freeSections.deleteAllByCheckId(checkId);
        checks.delete(check);
        checks.flush();
        events.publishEvent(new CheckDeletedEvent(caseId, checkId)); // the documents module retires the files of this check
        touch(c, auth.requireCurrentAdmin());
        events.publishEvent(new AuditEvent("CHECK_DELETED", null, null, "CHECK", checkId.toString(), caseId, null, null,
                Map.of("type", check.getType(), "title", check.getTitle()), null));
    }

    // ---- free sections -----------------------------------------------------------------------------------------

    @PreAuthorize("hasAuthority('CHECK_UPDATE')")
    @Transactional
    public CheckView addFreeSection(UUID caseId, UUID checkId, FreeSectionKind kind, String text, UUID documentId) {
        policy.check(caseId, CaseAction.UPDATE_CHECK);
        BgvCase c = openCase(caseId);
        VerificationCheck check = findCheck(caseId, checkId);
        List<CheckFreeSection> existing = freeSections.findAllByCheckIdOrderBySortOrderAsc(checkId);
        if (existing.size() >= MAX_FREE_SECTIONS) {
            throw new ApiException(ErrorCode.CONFLICT, "A check can have at most " + MAX_FREE_SECTIONS + " extra blocks.");
        }
        int next = existing.isEmpty() ? 0 : existing.get(existing.size() - 1).getSortOrder() + 1;
        if (kind == FreeSectionKind.IMAGE) {
            CaseDocumentLookup lookup = documentLookup.getIfAvailable();
            if (documentId == null || lookup == null || !lookup.isFreeImageOf(documentId, caseId, checkId)) {
                throw invalid("documentId", "must be an image uploaded for this check");
            }
            if (existing.stream().anyMatch(section -> documentId.equals(section.getDocumentId()))) {
                throw invalid("documentId", "is already used by another block");
            }
            freeSections.saveAndFlush(CheckFreeSection.image(checkId, documentId, next));
        } else {
            freeSections.saveAndFlush(new CheckFreeSection(checkId, FreeSectionKind.TEXT, requiredText("text", text), next));
        }
        return freeSectionsChanged(c, check, "FREE_SECTION_ADDED");
    }

    @PreAuthorize("hasAuthority('CHECK_UPDATE')")
    @Transactional
    public CheckView updateFreeSection(UUID caseId, UUID checkId, UUID sectionId, String text) {
        policy.check(caseId, CaseAction.UPDATE_CHECK);
        BgvCase c = openCase(caseId);
        VerificationCheck check = findCheck(caseId, checkId);
        CheckFreeSection section = freeSections.findByIdAndCheckId(sectionId, checkId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Section not found."));
        if (section.getKind() != FreeSectionKind.TEXT) {
            throw invalid("kind", "only text sections can be edited here");
        }
        section.setTextValue(requiredText("text", text));
        freeSections.saveAndFlush(section);
        return freeSectionsChanged(c, check, "FREE_SECTION_UPDATED");
    }

    @PreAuthorize("hasAuthority('CHECK_UPDATE')")
    @Transactional
    public CheckView deleteFreeSection(UUID caseId, UUID checkId, UUID sectionId) {
        policy.check(caseId, CaseAction.UPDATE_CHECK);
        BgvCase c = openCase(caseId);
        VerificationCheck check = findCheck(caseId, checkId);
        CheckFreeSection section = freeSections.findByIdAndCheckId(sectionId, checkId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Section not found."));
        freeSections.delete(section);
        freeSections.flush();
        if (section.getKind() == FreeSectionKind.IMAGE && section.getDocumentId() != null) {
            events.publishEvent(new FreeImageRemovedEvent(caseId, checkId, section.getDocumentId()));
        }
        return freeSectionsChanged(c, check, "FREE_SECTION_DELETED");
    }

    private CheckView freeSectionsChanged(BgvCase c, VerificationCheck check, String action) {
        check.touch(Instant.now(clock)); // moves the check's version so a stale editor is noticed
        checks.saveAndFlush(check);
        touch(c, auth.requireCurrentAdmin());
        events.publishEvent(new AuditEvent(action, null, null, "CHECK", check.getId().toString(), c.getId(), null, null, null, null));
        return assembler.view(check);
    }

    // ---- save helpers ------------------------------------------------------------------------------------------------

    private void applyDates(VerificationCheck check, UUID caseId, LocalDate requested, LocalDate completed) {
        List<VerificationCheck> all = checks.findAllByCaseIdOrderBySortOrderAsc(caseId);
        VerificationCheck master = all.get(0);
        if (master.getId().equals(check.getId())) {
            check.applyDates(requested, completed, false);
            for (VerificationCheck other : all) {
                if (!other.getId().equals(check.getId()) && !other.isDatesManual()) {
                    other.applyDates(requested, completed, false);
                    checks.save(other);
                }
            }
        } else {
            boolean sameAsMaster = Objects.equals(requested, master.getRequestedDate()) && Objects.equals(completed, master.getCompletedDate());
            check.applyDates(requested, completed, !sameAsMaster);
        }
    }

    private void applyAttestation(VerificationCheck check, SaveCheckInput in, AdminPrincipal me) {
        boolean on = in.hasAttestation();
        String bar = on ? blankToNull(in.barCouncilNo()) : null;
        String disclaimer = on ? blankToNull(in.disclaimer()) : null;
        if (on && bar == null) {
            bar = DEFAULT_BAR_COUNCIL_NO;
        }
        if (on && disclaimer == null) {
            disclaimer = DEFAULT_DISCLAIMER;
        }
        if (bar != null && bar.length() > 50) {
            throw invalid("barCouncilNo", "is too long");
        }
        if (disclaimer != null && disclaimer.length() > 5000) {
            throw invalid("disclaimer", "is too long");
        }
        boolean changed = on != check.isHasAttestation() || !Objects.equals(bar, check.getBarCouncilNo())
                || !Objects.equals(disclaimer, check.getDisclaimer());
        if (!changed) {
            return;
        }
        if (!me.hasPermission(Permission.ATTESTATION_APPLY.name())) {
            throw new ApiException(ErrorCode.FORBIDDEN, "You need permission to apply or change an attestation.");
        }
        check.applyAttestation(on, bar, disclaimer);
        events.publishEvent(new AuditEvent("ATTESTATION_CHANGED", null, null, "CHECK", check.getId().toString(), check.getCaseId(),
                null, null, null, Map.of("attestation", on)));
    }

    /** Applies the submitted field values. Returns the keys that changed (never their values) for the audit entry. */
    private List<String> applyFields(VerificationCheck check, CheckTypeDefinition def, Candidate candidate, List<FieldInput> inputs) {
        if (inputs == null || inputs.isEmpty()) {
            return List.of();
        }
        Map<String, CheckField> rows = new LinkedHashMap<>();
        fields.findAllByCheckIdOrderBySortOrderAsc(check.getId()).forEach(r -> rows.put(r.getFieldKey(), r));
        List<String> changed = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (FieldInput input : inputs) {
            FieldDefinition d = def.field(input.key());
            if (d == null) {
                throw invalid("fields", "contains '" + input.key() + "', which this check type does not have");
            }
            if (!seen.add(d.key())) {
                throw invalid("fields", "lists '" + d.key() + "' twice");
            }
            CheckField row = rows.get(d.key());
            if (row == null) { // added to the YAML after this check was made
                row = new CheckField(check.getId(), d.key(), d.label(), rows.size());
                rows.put(d.key(), row);
            }
            String beforeValue = d.sensitive() ? row.getValueEncrypted() : row.getValue();
            if (input.verifiedTick() != null) {
                row.setVerifiedTick(input.verifiedTick());
            }
            if (d.sensitive()) {
                applySensitive(row, d, input);
            } else {
                applyPlain(row, d, candidate, input);
            }
            String afterValue = d.sensitive() ? row.getValueEncrypted() : row.getValue();
            if (!Objects.equals(beforeValue, afterValue) || input.verifiedTick() != null) {
                changed.add(d.sensitive() ? d.key() + " (sensitive)" : d.key());
            }
            fields.save(row);
        }
        return changed;
    }

    private void applySensitive(CheckField row, FieldDefinition d, FieldInput input) {
        if (Boolean.TRUE.equals(input.clear())) {
            row.clear();
            return;
        }
        if (input.value() == null || input.value().isBlank()) {
            return; // unchanged: the screen only ever holds the masked form and cannot send the real value back
        }
        String normalized = normalizeOrFail(d, input.value());
        row.setSensitive(mask(d.type(), normalized), encryptor.encrypt(normalized), PiiMasker.last4(normalized));
    }

    private void applyPlain(CheckField row, FieldDefinition d, Candidate candidate, FieldInput input) {
        boolean manual = input.manual() == null || input.manual();
        if (d.prefill() != null && !manual) {
            row.setPlain(CheckPrefillService.valueOf(candidate, d.prefill()), FieldSource.CANDIDATE, false); // back to following the candidate
            return;
        }
        if (input.value() == null && !Boolean.TRUE.equals(input.clear())) {
            return; // not submitted: unchanged
        }
        String normalized = Boolean.TRUE.equals(input.clear()) ? null : normalizeOrFail(d, input.value());
        row.setPlain(normalized, FieldSource.MANUAL, d.prefill() != null);
    }

    private String normalizeOrFail(FieldDefinition d, String raw) {
        try {
            return FieldValues.normalize(d, raw);
        } catch (IllegalArgumentException e) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, d.label() + " " + e.getMessage() + ".",
                    List.of(new ApiError.FieldError("fields." + d.key(), e.getMessage())), null);
        }
    }

    private static String mask(FieldType type, String value) {
        return switch (type) {
            case AADHAAR -> PiiMasker.maskAadhaar(value);
            case PAN -> PiiMasker.maskPan(value);
            default -> PiiMasker.maskGeneric(value);
        };
    }

    private void replaceDetails(VerificationCheck check, List<DetailInput> inputs) {
        if (inputs.size() > MAX_DETAILS) {
            throw invalid("details", "can have at most " + MAX_DETAILS + " rows");
        }
        List<DetailInput> cleaned = new ArrayList<>();
        for (DetailInput input : inputs) {
            String label = input.label() == null ? "" : input.label().trim();
            String value = blankToNull(input.value());
            if (label.isEmpty() && value == null) {
                continue; // an empty row is simply dropped
            }
            if (label.isEmpty() || label.length() > 200 || (value != null && value.length() > 1000)) {
                throw invalid("details", "need a label of at most 200 characters and a value of at most 1000");
            }
            cleaned.add(new DetailInput(label, value));
        }
        details.deleteAllByCheckId(check.getId());
        details.flush();
        int order = 0;
        for (DetailInput d : cleaned) {
            details.save(new CheckDetail(check.getId(), d.label(), d.value(), order++));
        }
    }

    // ---- shared ---------------------------------------------------------------------------------------------------------

    private BgvCase findCase(UUID caseId) {
        return cases.findByIdAndDeletedAtIsNull(caseId).orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Case not found."));
    }

    /** The case must exist and still be in an editable state. */
    private BgvCase openCase(UUID caseId) {
        BgvCase c = findCase(caseId);
        if (!c.getLifecycle().isEditable()) {
            throw new ApiException(ErrorCode.CONFLICT,
                    "This case is locked while it is in review, approved or finalized. It cannot be edited now.");
        }
        return c;
    }

    private VerificationCheck findCheck(UUID caseId, UUID checkId) {
        return checks.findByIdAndCaseId(checkId, caseId).orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Check not found."));
    }

    private CheckTypeDefinition definitionOf(VerificationCheck check) {
        return registry.find(check.getType()).orElseThrow(() -> new IllegalStateException("Check type " + check.getType() + " has no definition"));
    }

    /** Moves the case's "last changed" marker without touching its version, so an open case form is not made stale. */
    private void touch(BgvCase c, AdminPrincipal me) {
        cases.touch(c.getId(), Instant.now(clock), me.id());
    }

    private static void checkDate(String field, LocalDate date) {
        if (date != null && (date.isBefore(EARLIEST) || date.isAfter(LATEST))) {
            throw invalid(field, "must be a date between 1900 and 2100");
        }
    }

    private static String plainText(String field, String value, int max) {
        String text = blankToNull(value);
        if (text != null && text.length() > max) {
            throw invalid(field, "is too long");
        }
        return text;
    }

    private static String requiredText(String field, String value) {
        String text = blankToNull(value);
        if (text == null || text.length() > 5000) {
            throw invalid(field, "is required and at most 5000 characters");
        }
        return text;
    }

    private static Map<String, Object> cardSnapshot(VerificationCheck check) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("type", check.getType());
        map.put("title", check.getTitle());
        map.put("status", check.getStatus().name());
        map.put("verificationType", check.getVerificationType());
        map.put("requestedDate", String.valueOf(check.getRequestedDate()));
        map.put("completedDate", String.valueOf(check.getCompletedDate()));
        map.put("attestation", check.isHasAttestation());
        return map;
    }

    private static ApiException invalid(String field, String problem) {
        return new ApiException(ErrorCode.VALIDATION_FAILED, "The " + field + " " + problem + ".",
                List.of(new ApiError.FieldError(field, problem)), null);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
