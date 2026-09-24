package com.nexlyn.bgv.cases.internal.service;

import com.nexlyn.bgv.auth.AdminPrincipal;
import com.nexlyn.bgv.auth.AuditEvent;
import com.nexlyn.bgv.auth.AuthApi;
import com.nexlyn.bgv.auth.CaseAccessPolicy;
import com.nexlyn.bgv.auth.CaseAction;
import com.nexlyn.bgv.cases.internal.domain.BgvCase;
import com.nexlyn.bgv.cases.internal.domain.Candidate;
import com.nexlyn.bgv.cases.internal.domain.CaseAssignment;
import com.nexlyn.bgv.cases.internal.domain.CaseRole;
import com.nexlyn.bgv.cases.internal.domain.Client;
import com.nexlyn.bgv.cases.internal.repository.CandidateRepository;
import com.nexlyn.bgv.cases.internal.repository.CaseAssignmentRepository;
import com.nexlyn.bgv.cases.internal.repository.CaseRepository;
import com.nexlyn.bgv.cases.internal.repository.ClientRepository;
import com.nexlyn.bgv.cases.internal.service.CaseViews.AssignmentView;
import com.nexlyn.bgv.cases.internal.service.CaseViews.CaseRow;
import com.nexlyn.bgv.cases.internal.service.CaseViews.CaseView;
import com.nexlyn.bgv.common.enums.CaseLifecycle;
import com.nexlyn.bgv.common.error.ApiError;
import com.nexlyn.bgv.common.error.ApiException;
import com.nexlyn.bgv.common.error.ErrorCode;
import com.nexlyn.bgv.common.security.Permission;
import com.nexlyn.bgv.common.web.PageResponse;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Creating, finding, opening and deleting cases (CLAUDE.md {@literal §9.2}). Every operation that
 * names one case asks {@link CaseAccessPolicy} first, so an analyst can only reach cases assigned to them.
 */
@Service
public class CaseService {

    public record CreateCaseInput(UUID clientId, LocalDate issueDate, LocalDate dueDate) {
    }

    public record CaseFilter(CaseLifecycle status, UUID clientId, UUID assigneeId, String query) {
    }

    private static final int MAX_PAGE_SIZE = 100;
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Kolkata");

    private final CaseRepository cases;
    private final ClientRepository clients;
    private final CandidateRepository candidates;
    private final CaseAssignmentRepository assignments;
    private final ReportIdGenerator reportIds;
    private final CaseViewAssembler assembler;
    private final CaseAccessPolicy policy;
    private final AuthApi auth;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public CaseService(CaseRepository cases, ClientRepository clients, CandidateRepository candidates,
                       CaseAssignmentRepository assignments, ReportIdGenerator reportIds, CaseViewAssembler assembler,
                       CaseAccessPolicy policy, AuthApi auth, ApplicationEventPublisher events, Clock clock) {
        this.cases = cases;
        this.clients = clients;
        this.candidates = candidates;
        this.assignments = assignments;
        this.reportIds = reportIds;
        this.assembler = assembler;
        this.policy = policy;
        this.auth = auth;
        this.events = events;
        this.clock = clock;
    }

    /** New case with a generated Report ID and an empty candidate; the creator becomes its preparer. */
    @PreAuthorize("hasAuthority('CASE_CREATE')")
    @Transactional
    public CaseView create(CreateCaseInput input) {
        AdminPrincipal creator = auth.requireCurrentAdmin();
        Client client = clients.findById(input.clientId()).filter(Client::isActive).orElseThrow(() ->
                new ApiException(ErrorCode.VALIDATION_FAILED, "Choose an active client.",
                        List.of(new ApiError.FieldError("clientId", "is not an active client")), null));
        Instant now = Instant.now(clock);
        LocalDate issueDate = input.issueDate() != null ? input.issueDate() : LocalDate.now(clock.withZone(BUSINESS_ZONE));

        BgvCase created = new BgvCase(reportIds.next(), client.getId(), issueDate, input.dueDate(), creator.id());
        created.markSaved("report-info", now, creator.id()); // its values are already set
        created = cases.saveAndFlush(created);
        candidates.save(new Candidate(created.getId()));
        assignments.save(new CaseAssignment(created.getId(), creator.id(), CaseRole.PREPARER, now, creator.id()));

        events.publishEvent(new AuditEvent("CASE_CREATED", null, null, "CASE", created.getId().toString(),
                created.getId(), null, null, null,
                Map.of("reportId", created.getReportId(), "client", client.getName())));
        return assembler.view(created);
    }

    @PreAuthorize("hasAnyAuthority('CASE_READ_ALL', 'CASE_READ_ASSIGNED')")
    @Transactional(readOnly = true)
    public PageResponse<CaseRow> list(CaseFilter filter, int page, int size) {
        AdminPrincipal me = auth.requireCurrentAdmin();
        boolean readAll = me.hasPermission(Permission.CASE_READ_ALL.name());
        Page<BgvCase> result = cases.findAll(specification(filter, me.id(), readAll), PageRequest.of(Math.max(page, 0),
                Math.min(Math.max(size, 1), MAX_PAGE_SIZE), Sort.by(Sort.Direction.DESC, "updatedAt")));

        return new PageResponse<>(rows(result.getContent()), result.getNumber(), result.getSize(), result.getTotalElements());
    }

    /** List lines for these cases (candidate, client and assignments looked up in bulk). Reads only: the caller has already limited which cases. */
    List<CaseRow> rows(List<BgvCase> content) {
        List<UUID> ids = content.stream().map(BgvCase::getId).toList();
        Map<UUID, Candidate> candidateByCase = candidates.findAllByCaseIdIn(ids).stream()
                .collect(Collectors.toMap(Candidate::getCaseId, Function.identity()));
        Map<UUID, Client> clientById = clients.findAllById(content.stream().map(BgvCase::getClientId).distinct().toList())
                .stream().collect(Collectors.toMap(Client::getId, Function.identity()));
        Map<UUID, List<CaseAssignment>> assignedByCase = assignments.findAllByKeyCaseIdIn(ids).stream()
                .collect(Collectors.groupingBy(CaseAssignment::getCaseId));

        List<CaseRow> rows = new ArrayList<>();
        for (BgvCase c : content) {
            Candidate candidate = candidateByCase.get(c.getId());
            List<AssignmentView> people = assembler.assignmentViews(assignedByCase.getOrDefault(c.getId(), List.of()));
            rows.add(new CaseRow(c.getId(), c.getReportId(), clientById.get(c.getClientId()).getName(),
                    candidate == null ? null : candidate.getFullName(), candidate == null ? null : candidate.getEmployeeId(),
                    c.getLifecycle(), c.getIssueDate(), c.getDueDate(), people, c.getSavedSections().size(), c.getUpdatedAt()));
        }
        return rows;
    }

    @PreAuthorize("hasAnyAuthority('CASE_READ_ALL', 'CASE_READ_ASSIGNED')")
    @Transactional(readOnly = true)
    public CaseView get(UUID id) {
        policy.check(id, CaseAction.READ);
        return assembler.view(find(id));
    }

    /** Soft delete: the case disappears from every screen, its Report ID is never reused. Finalized reports stay. */
    @PreAuthorize("hasAuthority('CASE_DELETE')")
    @Transactional
    public void delete(UUID id) {
        policy.check(id, CaseAction.DELETE);
        BgvCase c = find(id);
        if (c.getLifecycle() == CaseLifecycle.FINALIZED) {
            throw new ApiException(ErrorCode.CONFLICT, "A finalized report cannot be deleted.");
        }
        c.softDelete(Instant.now(clock));
        cases.saveAndFlush(c);
        events.publishEvent(new AuditEvent("CASE_DELETED", null, null, "CASE", id.toString(), id, null, null,
                Map.of("reportId", c.getReportId()), null));
    }

    BgvCase find(UUID id) {
        return cases.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Case not found."));
    }

    // ---- list filtering ---------------------------------------------------------------------------

    private static Specification<BgvCase> specification(CaseFilter filter, UUID me, boolean readAll) {
        return (root, query, cb) -> {
            List<Predicate> all = new ArrayList<>();
            all.add(cb.isNull(root.get("deletedAt")));
            if (filter.status() != null) {
                all.add(cb.equal(root.get("lifecycle"), filter.status()));
            }
            if (filter.clientId() != null) {
                all.add(cb.equal(root.get("clientId"), filter.clientId()));
            }
            if (filter.assigneeId() != null) {
                all.add(assignedTo(root, query.subquery(UUID.class), cb, filter.assigneeId()));
            }
            if (!readAll) {
                // Data-level rule: without CASE_READ_ALL only cases assigned to me exist.
                all.add(assignedTo(root, query.subquery(UUID.class), cb, me));
            }
            if (filter.query() != null && !filter.query().isBlank()) {
                String pattern = "%" + escapeLike(filter.query().trim().toLowerCase(Locale.ROOT)) + "%";
                Subquery<UUID> byCandidate = query.subquery(UUID.class);
                Root<Candidate> candidate = byCandidate.from(Candidate.class);
                byCandidate.select(candidate.get("caseId")).where(
                        cb.equal(candidate.get("caseId"), root.get("id")),
                        cb.or(cb.like(cb.lower(candidate.get("fullName")), pattern, '\\'),
                                cb.like(cb.lower(candidate.get("employeeId")), pattern, '\\')));
                all.add(cb.or(cb.like(cb.lower(root.get("reportId")), pattern, '\\'), cb.exists(byCandidate)));
            }
            return cb.and(all.toArray(Predicate[]::new));
        };
    }

    static Predicate assignedTo(Root<BgvCase> root, Subquery<UUID> sub,
                                        jakarta.persistence.criteria.CriteriaBuilder cb, UUID adminId) {
        Root<CaseAssignment> a = sub.from(CaseAssignment.class);
        sub.select(a.get("key").get("caseId")).where(
                cb.equal(a.get("key").get("caseId"), root.get("id")),
                cb.equal(a.get("key").get("adminId"), adminId));
        return cb.exists(sub);
    }

    private static String escapeLike(String text) {
        return text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
