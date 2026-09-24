package com.nexlyn.bgv.cases.internal.service;

import com.nexlyn.bgv.auth.AdminPrincipal;
import com.nexlyn.bgv.auth.AuthApi;
import com.nexlyn.bgv.cases.internal.domain.BgvCase;
import com.nexlyn.bgv.cases.internal.domain.CaseAssignment;
import com.nexlyn.bgv.cases.internal.domain.CaseRole;
import com.nexlyn.bgv.cases.internal.repository.CaseRepository;
import com.nexlyn.bgv.cases.internal.service.CaseViews.CaseRow;
import com.nexlyn.bgv.common.enums.CaseLifecycle;
import com.nexlyn.bgv.common.security.Permission;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * What the dashboard shows (CLAUDE.md section 15, phase 7): how many cases are in each stage, my own cases,
 * the cases waiting for my review, and what is due soon or overdue. Everything follows the visibility rule
 * of the case list: without {@code CASE_READ_ALL} only cases assigned to the admin exist.
 */
@Service
@PreAuthorize("hasAnyAuthority('CASE_READ_ALL', 'CASE_READ_ASSIGNED')")
public class DashboardService {

    /** A case is "due soon" when its due date is today or within this many days. */
    static final int DUE_SOON_DAYS = 3;
    private static final int LIST_SIZE = 8;
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Kolkata");

    public record Dashboard(Map<String, Long> counts, List<CaseRow> mine, List<CaseRow> awaitingMyReview,
                            List<CaseRow> dueSoon, long overdue, LocalDate today) {
    }

    private final CaseRepository cases;
    private final CaseService caseService;
    private final AuthApi auth;
    private final Clock clock;

    public DashboardService(CaseRepository cases, CaseService caseService, AuthApi auth, Clock clock) {
        this.cases = cases;
        this.caseService = caseService;
        this.auth = auth;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Dashboard dashboard() {
        AdminPrincipal me = auth.requireCurrentAdmin();
        boolean readAll = me.hasPermission(Permission.CASE_READ_ALL.name());
        LocalDate today = LocalDate.now(clock.withZone(BUSINESS_ZONE));

        Map<String, Long> counts = new LinkedHashMap<>();
        for (CaseLifecycle state : CaseLifecycle.values()) {
            counts.put(state.name(), cases.count(visible(me.id(), readAll).and(lifecycleIs(state))));
        }

        List<CaseRow> mine = caseService.rows(cases.findAll(
                visible(me.id(), readAll).and(assignedToMe(me.id())).and(notFinalized()),
                page(Sort.by(Sort.Direction.DESC, "updatedAt"))).getContent());

        List<CaseRow> awaiting = me.hasPermission(Permission.REPORT_APPROVE.name())
                ? caseService.rows(cases.findAll(visible(me.id(), readAll).and(lifecycleIs(CaseLifecycle.IN_REVIEW)).and(notMadeBy(me.id())),
                        page(Sort.by(Sort.Direction.ASC, "submittedAt"))).getContent())
                : List.of();

        Specification<BgvCase> open = visible(me.id(), readAll).and(notFinalized());
        List<CaseRow> dueSoon = caseService.rows(cases.findAll(
                open.and(dueOnOrBefore(today.plusDays(DUE_SOON_DAYS))),
                page(Sort.by(Sort.Direction.ASC, "dueDate"))).getContent());
        long overdue = cases.count(open.and((root, query, cb) -> cb.lessThan(root.get("dueDate"), today)));

        return new Dashboard(counts, mine, awaiting, dueSoon, overdue, today);
    }

    private static PageRequest page(Sort sort) {
        return PageRequest.of(0, LIST_SIZE, sort);
    }

    // ---- conditions ---------------------------------------------------------------------------------------

    /** The case-list visibility rule: not deleted, and (unless the admin reads all cases) assigned to them. */
    private static Specification<BgvCase> visible(UUID me, boolean readAll) {
        return (root, query, cb) -> {
            Predicate notDeleted = cb.isNull(root.get("deletedAt"));
            return readAll ? notDeleted : cb.and(notDeleted, CaseService.assignedTo(root, query.subquery(UUID.class), cb, me));
        };
    }

    private static Specification<BgvCase> lifecycleIs(CaseLifecycle state) {
        return (root, query, cb) -> cb.equal(root.get("lifecycle"), state);
    }

    private static Specification<BgvCase> notFinalized() {
        return (root, query, cb) -> cb.notEqual(root.get("lifecycle"), CaseLifecycle.FINALIZED);
    }

    private static Specification<BgvCase> assignedToMe(UUID me) {
        return (root, query, cb) -> CaseService.assignedTo(root, query.subquery(UUID.class), cb, me);
    }

    private static Specification<BgvCase> dueOnOrBefore(LocalDate date) {
        return (root, query, cb) -> cb.and(cb.isNotNull(root.get("dueDate")), cb.lessThanOrEqualTo(root.get("dueDate"), date));
    }

    /** Leaves out cases this admin prepared, created or submitted: they are not theirs to approve (maker-checker). */
    private static Specification<BgvCase> notMadeBy(UUID me) {
        return (root, query, cb) -> {
            Subquery<UUID> preparer = query.subquery(UUID.class);
            Root<CaseAssignment> a = preparer.from(CaseAssignment.class);
            preparer.select(a.get("key").get("caseId")).where(
                    cb.equal(a.get("key").get("caseId"), root.get("id")),
                    cb.equal(a.get("key").get("adminId"), me),
                    cb.equal(a.get("key").get("role"), CaseRole.PREPARER));
            return cb.and(
                    cb.or(cb.isNull(root.get("createdBy")), cb.notEqual(root.get("createdBy"), me)),
                    cb.or(cb.isNull(root.get("submittedBy")), cb.notEqual(root.get("submittedBy"), me)),
                    cb.not(cb.exists(preparer)));
        };
    }
}
