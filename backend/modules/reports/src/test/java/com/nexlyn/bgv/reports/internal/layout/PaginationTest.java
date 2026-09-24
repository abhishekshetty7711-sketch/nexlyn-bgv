package com.nexlyn.bgv.reports.internal.layout;

import com.nexlyn.bgv.reports.internal.layout.Pagination.Plan;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The page rules of CLAUDE.md sections 6.2 and 6.3, ported from the reference tool. */
class PaginationTest {

    private static List<Integer> noMoves(int n) {
        return Collections.nCopies(n, 0);
    }

    private static List<String> distinct(int n) {
        return IntStream.range(0, n).mapToObj(i -> "type" + i).toList();
    }

    @Test
    void oneOrTwoGroupsKeepTheRemarksOnPageOne() {
        Plan one = Pagination.plan(List.of("identity"), noMoves(1), 4);
        assertThat(one.remarksInline()).isTrue();
        assertThat(one.dedicatedRemarks()).isFalse();
        assertThat(one.overflow()).isEmpty();
        assertThat(one.totalPages()).as("cover, one detail page, services").isEqualTo(3);
        assertThat(one.firstDetailPage()).isEqualTo(2);

        Plan two = Pagination.plan(List.of("identity", "court"), noMoves(2), 4);
        assertThat(two.remarksInline()).isTrue();
        assertThat(two.totalPages()).isEqualTo(4);
    }

    @Test
    void checksOfTheSameKindShareOneSummaryCardSoTheyDoNotCountAsMoreGroups() {
        // Two identity + two court checks are two groups: the remarks stay on page 1 (the reference tool's own example).
        Plan plan = Pagination.plan(List.of("identity", "identity", "court", "court"), noMoves(4), 4);
        assertThat(plan.groups()).hasSize(2);
        assertThat(plan.groups().get(0).checkIndexes()).containsExactly(0, 1);
        assertThat(plan.groups().get(1).checkIndexes()).containsExactly(2, 3);
        assertThat(plan.remarksInline()).isTrue();
        assertThat(plan.totalPages()).as("cover + 4 detail + services").isEqualTo(6);
    }

    @Test
    void groupsAreInOrderOfFirstAppearance() {
        Plan plan = Pagination.plan(List.of("court", "identity", "court", "address"), noMoves(4), 4);
        assertThat(plan.groups()).extracting(Pagination.Group::key).containsExactly("court", "identity", "address");
    }

    @Test
    void threeUpToTheLayoutLimitGivesTheRemarksTheirOwnPageTwo() {
        Plan three = Pagination.plan(distinct(3), noMoves(3), 4);
        assertThat(three.remarksInline()).isFalse();
        assertThat(three.dedicatedRemarks()).isTrue();
        assertThat(three.overflow()).isEmpty();
        assertThat(three.firstDetailPage()).as("page 2 is the remarks").isEqualTo(3);
        assertThat(three.totalPages()).as("cover, remarks, 3 details, services").isEqualTo(6);

        Plan four = Pagination.plan(distinct(4), noMoves(4), 4);
        assertThat(four.dedicatedRemarks()).isTrue();
        assertThat(four.coverGroups()).containsExactly(0, 1, 2, 3);
    }

    @Test
    void moreGroupsThanTheLayoutOverflowAndTheRemarksJoinTheLastOverflowPage() {
        Plan plan = Pagination.plan(distinct(5), noMoves(5), 4);
        assertThat(plan.dedicatedRemarks()).isFalse();
        assertThat(plan.remarksInline()).isFalse();
        assertThat(plan.coverGroups()).containsExactly(0, 1, 2, 3);
        assertThat(plan.overflow()).hasSize(1);
        assertThat(plan.overflow().get(0).groupIndexes()).containsExactly(4);
        assertThat(plan.overflow().get(0).withRemarks()).isTrue();
        assertThat(plan.firstDetailPage()).isEqualTo(3);
        assertThat(plan.totalPages()).as("cover, overflow, 5 details, services").isEqualTo(8);
    }

    @Test
    void sixCardLayoutFitsSixOnPageOne() {
        Plan six = Pagination.plan(distinct(6), noMoves(6), 6);
        assertThat(six.dedicatedRemarks()).isTrue();
        assertThat(six.coverGroups()).hasSize(6);
        assertThat(six.overflow()).isEmpty();

        Plan seven = Pagination.plan(distinct(7), noMoves(7), 6);
        assertThat(seven.overflow()).hasSize(1);
        assertThat(seven.overflow().get(0).groupIndexes()).containsExactly(6);
    }

    @Test
    void aBigOverflowIsSpreadOverPagesKeepingAtLeastOneGroupForTheLastPage() {
        // 4-card layout: 4 on the cover, then 17 groups to place; a page with the remarks takes 12, one without takes 14.
        Plan plan = Pagination.plan(distinct(21), noMoves(21), 4);
        assertThat(plan.overflow()).hasSize(2);
        assertThat(plan.overflow().get(0).groupIndexes()).hasSize(14);
        assertThat(plan.overflow().get(0).withRemarks()).isFalse();
        assertThat(plan.overflow().get(1).groupIndexes()).hasSize(3);
        assertThat(plan.overflow().get(1).withRemarks()).isTrue();
        // every group appears exactly once across cover and overflow
        List<Integer> all = new java.util.ArrayList<>(plan.coverGroups());
        plan.overflow().forEach(p -> all.addAll(p.groupIndexes()));
        assertThat(all).containsExactlyElementsOf(IntStream.range(0, 21).boxed().toList());
    }

    @Test
    void exactlyTheCapacityStillFitsOnOneOverflowPage() {
        Plan plan = Pagination.plan(distinct(4 + 12), noMoves(16), 4);
        assertThat(plan.overflow()).hasSize(1);
        assertThat(plan.overflow().get(0).groupIndexes()).hasSize(12);

        Plan onePast = Pagination.plan(distinct(4 + 13), noMoves(17), 4);
        assertThat(onePast.overflow()).hasSize(2);
        assertThat(onePast.overflow().get(0).groupIndexes()).as("leaves one for the last page").hasSize(12);
        assertThat(onePast.overflow().get(1).groupIndexes()).hasSize(1);
    }

    @Test
    void documentsMovedToTheirOwnPageAddPages() {
        Plan plan = Pagination.plan(List.of("identity", "court"), List.of(2, 0), 4);
        assertThat(plan.totalPages()).as("cover, identity + 2 moved, court, services").isEqualTo(1 + 3 + 1 + 1);
    }

    @Test
    void noChecksIsCoverAndServices() {
        Plan plan = Pagination.plan(List.of(), List.of(), 4);
        assertThat(plan.totalPages()).isEqualTo(2);
        assertThat(plan.remarksInline()).isTrue();
    }

    @Test
    void onlyFourOrSixCardLayoutsExist() {
        assertThatThrownBy(() -> Pagination.plan(List.of("identity"), noMoves(1), 5)).isInstanceOf(IllegalArgumentException.class);
    }
}
