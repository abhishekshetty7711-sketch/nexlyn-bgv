package com.nexlyn.bgv.cases.internal.web;

import com.nexlyn.bgv.auth.AdminDirectory.AdminSummary;
import com.nexlyn.bgv.cases.internal.domain.CaseRole;
import com.nexlyn.bgv.cases.internal.domain.DateFormat;
import com.nexlyn.bgv.cases.internal.domain.ParentType;
import com.nexlyn.bgv.cases.internal.domain.StatusPreset;
import com.nexlyn.bgv.cases.internal.service.CaseAssignmentService;
import com.nexlyn.bgv.cases.internal.service.CaseInsightService;
import com.nexlyn.bgv.cases.internal.service.CaseSectionService;
import com.nexlyn.bgv.cases.internal.service.CaseService;
import com.nexlyn.bgv.cases.internal.service.CaseViews.CaseRow;
import com.nexlyn.bgv.cases.internal.service.CaseViews.CaseView;
import com.nexlyn.bgv.cases.internal.service.CaseViews.ProgressView;
import com.nexlyn.bgv.cases.internal.service.CaseViews.ValidationResult;
import com.nexlyn.bgv.common.enums.CaseLifecycle;
import com.nexlyn.bgv.common.validation.ValidIndianPhone;
import com.nexlyn.bgv.common.validation.ValidPinCode;
import com.nexlyn.bgv.common.web.PageResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * {@code /api/cases/**}: the case workspace API (CLAUDE.md {@literal §9.2}). Thin: permissions and the
 * per-case access rule are enforced in the services, so they hold for every caller, not only this controller.
 * Every save carries the {@code version} the caller last saw and answers with the whole updated case.
 */
@RestController
@RequestMapping("/api")
public class CaseController {

    record CreateCaseRequest(@NotNull UUID clientId, LocalDate issueDate, LocalDate dueDate) {
    }

    record ReportInfoRequest(@NotNull Long version, @Size(max = 30) String reportId, LocalDate issueDate,
                             @NotNull UUID clientId, @Size(max = 1000) String companyDisplayName, LocalDate dueDate) {
    }

    record CandidateRequest(@NotNull Long version, @Size(max = 200) String fullName, ParentType parentType,
                            @Size(max = 200) String parentName, @Size(max = 50) String employeeId, LocalDate dob,
                            @Size(max = 30) @ValidIndianPhone String phone, @Size(max = 300) String street,
                            @Size(max = 100) String city, @Size(max = 100) String state,
                            @Size(max = 10) @ValidPinCode String pin, @Size(max = 100) String country) {
    }

    record PeriodRequest(@NotNull Long version, boolean show, LocalDate start, LocalDate end) {
    }

    record OverviewRequest(@NotNull Long version, @NotNull StatusPreset statusPreset, @Size(max = 100) String statusTitle,
                           @Size(max = 200) String statusSubtitle, @PositiveOrZero Integer totalOverride,
                           @PositiveOrZero Integer completedOverride, @Size(max = 100) String overallStatusOverride) {
    }

    record RemarksRequest(@NotNull Long version, @Size(max = 20000) String analystRemarks,
                          @Size(max = 20000) String finalRecommendation) {
    }

    record SettingsRequest(@NotNull Long version, int layoutCards, @NotNull DateFormat dateFormat,
                           boolean watermarkEnabled, @Size(max = 40) String watermarkText) {
    }

    record AssignRequest(@NotNull UUID adminId, @NotNull CaseRole role) {
    }

    private final CaseService cases;
    private final CaseSectionService sections;
    private final CaseInsightService insight;
    private final CaseAssignmentService assignments;

    public CaseController(CaseService cases, CaseSectionService sections, CaseInsightService insight,
                          CaseAssignmentService assignments) {
        this.cases = cases;
        this.sections = sections;
        this.insight = insight;
        this.assignments = assignments;
    }

    // ---- cases -------------------------------------------------------------------------------------

    @PostMapping("/cases")
    public ResponseEntity<CaseView> create(@Valid @RequestBody CreateCaseRequest body) {
        return ResponseEntity.status(HttpStatus.CREATED).header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(cases.create(new CaseService.CreateCaseInput(body.clientId(), body.issueDate(), body.dueDate())));
    }

    @GetMapping("/cases")
    public PageResponse<CaseRow> list(@RequestParam(required = false) CaseLifecycle status,
                                      @RequestParam(required = false) UUID client,
                                      @RequestParam(required = false) UUID assignee,
                                      @RequestParam(required = false) String q,
                                      @RequestParam(defaultValue = "0") int page,
                                      @RequestParam(defaultValue = "25") int size) {
        return cases.list(new CaseService.CaseFilter(status, client, assignee, q), page, size);
    }

    @GetMapping("/cases/{id}")
    public ResponseEntity<CaseView> get(@PathVariable UUID id) {
        return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store").body(cases.get(id));
    }

    @DeleteMapping("/cases/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        cases.delete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/cases/{id}/progress")
    public ProgressView progress(@PathVariable UUID id) {
        return insight.progress(id);
    }

    @GetMapping("/cases/{id}/validation")
    public ValidationResult validation(@PathVariable UUID id) {
        return insight.validate(id);
    }

    // ---- sections ------------------------------------------------------------------------------------

    @PutMapping("/cases/{id}/report-info")
    public CaseView reportInfo(@PathVariable UUID id, @Valid @RequestBody ReportInfoRequest b) {
        return sections.saveReportInfo(id, b.version(), new CaseSectionService.ReportInfoInput(
                b.reportId(), b.issueDate(), b.clientId(), b.companyDisplayName(), b.dueDate()));
    }

    @PutMapping("/cases/{id}/candidate")
    public CaseView candidate(@PathVariable UUID id, @Valid @RequestBody CandidateRequest b) {
        return sections.saveCandidate(id, b.version(), new CaseSectionService.CandidateInput(
                b.fullName(), b.parentType(), b.parentName(), b.employeeId(), b.dob(), b.phone(), b.street(),
                b.city(), b.state(), b.pin(), b.country()));
    }

    @PutMapping("/cases/{id}/verification-period")
    public CaseView period(@PathVariable UUID id, @Valid @RequestBody PeriodRequest b) {
        return sections.savePeriod(id, b.version(), new CaseSectionService.PeriodInput(b.show(), b.start(), b.end()));
    }

    @PutMapping("/cases/{id}/overview")
    public CaseView overview(@PathVariable UUID id, @Valid @RequestBody OverviewRequest b) {
        return sections.saveOverview(id, b.version(), new CaseSectionService.OverviewInput(
                b.statusPreset(), b.statusTitle(), b.statusSubtitle(), b.totalOverride(), b.completedOverride(),
                b.overallStatusOverride()));
    }

    @PutMapping("/cases/{id}/remarks")
    public CaseView remarks(@PathVariable UUID id, @Valid @RequestBody RemarksRequest b) {
        return sections.saveRemarks(id, b.version(), new CaseSectionService.RemarksInput(b.analystRemarks(), b.finalRecommendation()));
    }

    @PutMapping("/cases/{id}/settings")
    public CaseView settings(@PathVariable UUID id, @Valid @RequestBody SettingsRequest b) {
        return sections.saveSettings(id, b.version(), new CaseSectionService.SettingsInput(
                b.layoutCards(), b.dateFormat(), b.watermarkEnabled(), b.watermarkText()));
    }

    // ---- assignments ---------------------------------------------------------------------------------

    @GetMapping("/assignable-admins")
    public List<AdminSummary> assignableAdmins() {
        return assignments.assignableAdmins();
    }

    @PostMapping("/cases/{id}/assignments")
    public CaseView assign(@PathVariable UUID id, @Valid @RequestBody AssignRequest b) {
        return assignments.assign(id, b.adminId(), b.role());
    }

    @DeleteMapping("/cases/{id}/assignments/{adminId}")
    public CaseView unassign(@PathVariable UUID id, @PathVariable UUID adminId,
                             @RequestParam(required = false) CaseRole role) {
        return assignments.unassign(id, adminId, role);
    }
}
