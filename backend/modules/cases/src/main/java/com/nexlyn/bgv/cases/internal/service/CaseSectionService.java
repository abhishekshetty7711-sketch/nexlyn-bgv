package com.nexlyn.bgv.cases.internal.service;

import com.nexlyn.bgv.auth.AdminPrincipal;
import com.nexlyn.bgv.auth.AuditEvent;
import com.nexlyn.bgv.auth.AuthApi;
import com.nexlyn.bgv.auth.CaseAccessPolicy;
import com.nexlyn.bgv.auth.CaseAction;
import com.nexlyn.bgv.cases.internal.domain.BgvCase;
import com.nexlyn.bgv.cases.internal.domain.Candidate;
import com.nexlyn.bgv.cases.internal.domain.Client;
import com.nexlyn.bgv.cases.internal.domain.DateFormat;
import com.nexlyn.bgv.cases.internal.domain.ParentType;
import com.nexlyn.bgv.cases.internal.domain.StatusPreset;
import com.nexlyn.bgv.cases.internal.repository.CandidateRepository;
import com.nexlyn.bgv.cases.internal.repository.CaseRepository;
import com.nexlyn.bgv.cases.internal.repository.ClientRepository;
import com.nexlyn.bgv.cases.internal.service.CaseViews.CaseView;
import com.nexlyn.bgv.common.error.ApiError;
import com.nexlyn.bgv.common.error.ApiException;
import com.nexlyn.bgv.common.error.ErrorCode;
import com.nexlyn.bgv.common.validation.BoldOnlyHtml;
import com.nexlyn.bgv.common.validation.IndianPhone;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Saving one section of the case workspace (CLAUDE.md {@literal §7}). Every save checks, in this order:
 * the admin may edit this case (policy), the case is in an editable state, and the caller saw the
 * latest version (so nobody silently overwrites a colleague's change). Each save is audited with the
 * values before and after, and answers with the whole updated workspace.
 */
@Service
@PreAuthorize("hasAuthority('CASE_UPDATE')")
public class CaseSectionService {

    public record ReportInfoInput(String reportId, LocalDate issueDate, UUID clientId, String companyDisplayName,
                                  LocalDate dueDate) {
    }

    public record CandidateInput(String fullName, ParentType parentType, String parentName, String employeeId,
                                 LocalDate dob, String phone, String street, String city, String state, String pin,
                                 String country) {
    }

    public record PeriodInput(boolean show, LocalDate start, LocalDate end) {
    }

    public record OverviewInput(StatusPreset statusPreset, String statusTitle, String statusSubtitle,
                                Integer totalOverride, Integer completedOverride, String overallStatusOverride) {
    }

    public record RemarksInput(String analystRemarks, String finalRecommendation) {
    }

    public record SettingsInput(int layoutCards, DateFormat dateFormat, boolean watermarkEnabled, String watermarkText) {
    }

    private static final Pattern REPORT_ID = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9/_-]{2,29}$");
    private static final LocalDate EARLIEST_DOB = LocalDate.of(1900, 1, 1);

    private final CaseRepository cases;
    private final CandidateRepository candidates;
    private final ClientRepository clients;
    private final CaseViewAssembler assembler;
    private final CheckPrefillService prefill;
    private final CaseAccessPolicy policy;
    private final AuthApi auth;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public CaseSectionService(CaseRepository cases, CandidateRepository candidates, ClientRepository clients,
                              CaseViewAssembler assembler, CheckPrefillService prefill, CaseAccessPolicy policy, AuthApi auth,
                              ApplicationEventPublisher events, Clock clock) {
        this.cases = cases;
        this.candidates = candidates;
        this.clients = clients;
        this.assembler = assembler;
        this.prefill = prefill;
        this.policy = policy;
        this.auth = auth;
        this.events = events;
        this.clock = clock;
    }

    // ---- Section 1: report info ----------------------------------------------------------------

    @Transactional
    public CaseView saveReportInfo(UUID id, long version, ReportInfoInput in) {
        BgvCase c = loadForEdit(id, version);
        String reportId = in.reportId() == null ? "" : in.reportId().trim();
        if (!REPORT_ID.matcher(reportId).matches()) {
            throw invalid("reportId", "use 3-30 letters, digits, dashes, slashes or underscores");
        }
        cases.findByReportIdIgnoreCase(reportId).filter(other -> !other.getId().equals(id)).ifPresent(other -> {
            throw new ApiException(ErrorCode.CONFLICT, "This Report ID is already used by another case.");
        });
        if (in.issueDate() == null) {
            throw invalid("issueDate", "is required");
        }
        Client client = clients.findById(in.clientId()).orElseThrow(() -> invalid("clientId", "does not exist"));
        if (!client.isActive() && !client.getId().equals(c.getClientId())) {
            throw invalid("clientId", "is not an active client");
        }
        Map<String, Object> before = new LinkedHashMap<>();
        before.put("reportId", c.getReportId());
        before.put("issueDate", String.valueOf(c.getIssueDate()));
        before.put("clientId", String.valueOf(c.getClientId()));
        before.put("companyDisplayName", c.getCompanyDisplayName());
        before.put("dueDate", String.valueOf(c.getDueDate()));

        c.applyReportInfo(reportId, in.issueDate(), client.getId(), blankToNull(in.companyDisplayName()), in.dueDate());
        Map<String, Object> after = new LinkedHashMap<>();
        after.put("reportId", reportId);
        after.put("issueDate", String.valueOf(in.issueDate()));
        after.put("clientId", String.valueOf(client.getId()));
        after.put("companyDisplayName", c.getCompanyDisplayName());
        after.put("dueDate", String.valueOf(in.dueDate()));
        return finish(c, "report-info", before, after);
    }

    // ---- Section 2: candidate ------------------------------------------------------------------

    @Transactional
    public CaseView saveCandidate(UUID id, long version, CandidateInput in) {
        BgvCase c = loadForEdit(id, version);
        Candidate candidate = candidates.findByCaseId(id)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Candidate not found."));
        if (in.dob() != null && (in.dob().isAfter(LocalDate.now(clock)) || in.dob().isBefore(EARLIEST_DOB))) {
            throw invalid("dob", "must be a date of birth between 1900 and today");
        }
        String phone = null;
        if (in.phone() != null && !in.phone().isBlank()) {
            phone = IndianPhone.normalize(in.phone());
            if (phone == null) {
                throw invalid("phone", "must be a valid Indian mobile number");
            }
        }
        Map<String, Object> before = candidateSnapshot(candidate);
        candidate.apply(blankToNull(in.fullName()), in.parentType() == null ? ParentType.FATHER : in.parentType(),
                blankToNull(in.parentName()), blankToNull(in.employeeId()), in.dob(), phone, blankToNull(in.street()),
                blankToNull(in.city()), blankToNull(in.state()), blankToNull(in.pin()),
                in.country() == null || in.country().isBlank() ? "India" : in.country().trim());
        candidates.saveAndFlush(candidate);
        prefill.refresh(id, candidate); // checks follow the candidate unless someone typed their own value
        return finish(c, "candidate", before, candidateSnapshot(candidate));
    }

    // ---- Section 3: verification period ----------------------------------------------------------

    @Transactional
    public CaseView savePeriod(UUID id, long version, PeriodInput in) {
        BgvCase c = loadForEdit(id, version);
        if (in.start() != null && in.end() != null && in.end().isBefore(in.start())) {
            throw invalid("end", "must not be before the start date");
        }
        Map<String, Object> before = periodSnapshot(c);
        c.applyPeriod(in.show(), in.start(), in.end());
        return finish(c, "verification-period", before, periodSnapshot(c));
    }

    // ---- Section 5: overview and status -----------------------------------------------------------

    @Transactional
    public CaseView saveOverview(UUID id, long version, OverviewInput in) {
        BgvCase c = loadForEdit(id, version);
        if (in.statusPreset() == null) {
            throw invalid("statusPreset", "is required");
        }
        String title = in.statusTitle() == null || in.statusTitle().isBlank() ? in.statusPreset().title() : in.statusTitle().trim();
        String subtitle = in.statusSubtitle() == null || in.statusSubtitle().isBlank() ? in.statusPreset().subtitle() : in.statusSubtitle().trim();
        Map<String, Object> before = overviewSnapshot(c);
        c.applyOverview(in.statusPreset(), title, subtitle, in.totalOverride(), in.completedOverride(),
                blankToNull(in.overallStatusOverride()));
        return finish(c, "overview", before, overviewSnapshot(c));
    }

    // ---- Section 6: remarks --------------------------------------------------------------------------

    @Transactional
    public CaseView saveRemarks(UUID id, long version, RemarksInput in) {
        BgvCase c = loadForEdit(id, version);
        Map<String, Object> before = remarksSnapshot(c);
        // Bold text only: anything else is neutralised on the server, whatever the browser sent.
        c.applyRemarks(blankToNull(BoldOnlyHtml.sanitize(in.analystRemarks())),
                blankToNull(BoldOnlyHtml.sanitize(in.finalRecommendation())));
        return finish(c, "remarks", before, remarksSnapshot(c));
    }

    // ---- Section 7: report settings --------------------------------------------------------------------

    @Transactional
    public CaseView saveSettings(UUID id, long version, SettingsInput in) {
        BgvCase c = loadForEdit(id, version);
        if (in.layoutCards() != 4 && in.layoutCards() != 6) {
            throw invalid("layoutCards", "must be 4 or 6");
        }
        if (in.dateFormat() == null) {
            throw invalid("dateFormat", "is required");
        }
        String watermark = in.watermarkText() == null ? "" : in.watermarkText().trim();
        if (in.watermarkEnabled() && watermark.isEmpty()) {
            throw invalid("watermarkText", "is required when the watermark is on");
        }
        Map<String, Object> before = settingsSnapshot(c);
        c.applySettings((short) in.layoutCards(), in.dateFormat(), in.watermarkEnabled(),
                watermark.isEmpty() ? c.getWatermarkText() : watermark);
        return finish(c, "settings", before, settingsSnapshot(c));
    }

    // ---- shared ---------------------------------------------------------------------------------------

    /** Access, state and version checks common to every section save. */
    private BgvCase loadForEdit(UUID id, long version) {
        policy.check(id, CaseAction.UPDATE);
        BgvCase c = cases.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Case not found."));
        if (!c.getLifecycle().isEditable()) {
            throw new ApiException(ErrorCode.CONFLICT,
                    "This case is locked while it is in review, approved or finalized. It cannot be edited now.");
        }
        if (c.getVersion() != version) {
            throw new ApiException(ErrorCode.CONFLICT,
                    "This case was changed by someone else since you opened it. Reload it and try again.");
        }
        return c;
    }

    private CaseView finish(BgvCase c, String section, Map<String, Object> before, Map<String, Object> after) {
        AdminPrincipal me = auth.requireCurrentAdmin();
        c.markSaved(section, Instant.now(clock), me.id());
        cases.saveAndFlush(c);
        events.publishEvent(new AuditEvent("CASE_SECTION_SAVED:" + section, null, null, "CASE", c.getId().toString(),
                c.getId(), null, null, before, after));
        return assembler.view(c);
    }

    private static ApiException invalid(String field, String problem) {
        return new ApiException(ErrorCode.VALIDATION_FAILED, "The " + field + " " + problem + ".",
                List.of(new ApiError.FieldError(field, problem)), null);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static Map<String, Object> candidateSnapshot(Candidate k) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("fullName", k.getFullName());
        map.put("parentType", String.valueOf(k.getParentType()));
        map.put("parentName", k.getParentName());
        map.put("employeeId", k.getEmployeeId());
        map.put("dob", String.valueOf(k.getDob()));
        map.put("phoneSet", k.getPhone() != null);
        map.put("pin", k.getPin());
        map.put("city", k.getCity());
        map.put("state", k.getState());
        map.put("country", k.getCountry());
        return map;
    }

    private static Map<String, Object> periodSnapshot(BgvCase c) {
        return Map.of("show", c.isPeriodShow(), "start", String.valueOf(c.getPeriodStart()), "end", String.valueOf(c.getPeriodEnd()));
    }

    private static Map<String, Object> overviewSnapshot(BgvCase c) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("statusPreset", c.getStatusPreset().name());
        map.put("statusTitle", c.getStatusTitle());
        map.put("statusSubtitle", c.getStatusSubtitle());
        map.put("totalOverride", c.getTotalOverride());
        map.put("completedOverride", c.getCompletedOverride());
        map.put("overallStatusOverride", c.getOverallStatusOverride());
        return map;
    }

    private static Map<String, Object> remarksSnapshot(BgvCase c) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("analystRemarksLength", c.getAnalystRemarks() == null ? 0 : c.getAnalystRemarks().length());
        map.put("finalRecommendationLength", c.getFinalRecommendation() == null ? 0 : c.getFinalRecommendation().length());
        return map;
    }

    private static Map<String, Object> settingsSnapshot(BgvCase c) {
        return Map.of("layoutCards", (int) c.getLayoutCards(), "dateFormat", c.getDateFormat().name(),
                "watermarkEnabled", c.isWatermarkEnabled(), "watermarkText", c.getWatermarkText());
    }
}
