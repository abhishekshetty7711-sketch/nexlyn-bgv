package com.nexlyn.bgv.reports.internal.layout;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Decides which page holds what (CLAUDE.md sections 6.2 and 6.3), ported from the reference tool's
 * {@code renderSummaryGrid}, {@code applyRemarksPaginationRule}, {@code renderDetailPages} and
 * {@code countTotalPages}. It works on <b>icon groups</b> (checks of the same kind share one summary
 * card), never on the number of checks, and never on the words in a title.
 *
 * <p>Page order: cover (page 1); then either nothing, a page for the remarks alone, or overflow pages
 * of summary cards (the last one carrying the remarks); then, for each check, its detail page followed
 * by one page per document moved to its own page; the services page is always last.
 */
public final class Pagination {

    /** Overflow capacities of a page of summary cards (with / without the remarks block at the bottom). */
    private static final int CAP_WITH_REMARKS_4 = 12;
    private static final int CAP_WITHOUT_REMARKS_4 = 14;
    private static final int CAP_WITH_REMARKS_6 = 14;
    private static final int CAP_WITHOUT_REMARKS_6 = 16;

    /** Checks that share a summary card: their indexes in report order. The first one represents the group. */
    public record Group(String key, List<Integer> checkIndexes) {
    }

    /** One overflow page of summary cards, by group index. */
    public record OverflowPage(List<Integer> groupIndexes, boolean withRemarks) {
    }

    /**
     * @param coverGroups       group indexes shown on page 1
     * @param remarksInline     the remarks sit on page 1 (two groups or fewer)
     * @param dedicatedRemarks  the remarks have a page of their own (page 2)
     * @param overflow          extra pages of summary cards, in order
     * @param firstDetailPage   the page number of the first check's detail page
     * @param totalPages        every page including the services page
     */
    public record Plan(List<Group> groups, int layout, List<Integer> coverGroups, boolean remarksInline,
                       boolean dedicatedRemarks, List<OverflowPage> overflow, int firstDetailPage, int totalPages) {
    }

    private Pagination() {
    }

    /**
     * @param iconGroups     the icon group key of each check, in report order
     * @param movedDocuments how many documents of each check are placed on their own page
     * @param layout         summary cards on page 1: 4 (spacious) or 6 (compact)
     */
    public static Plan plan(List<String> iconGroups, List<Integer> movedDocuments, int layout) {
        return plan(iconGroups, movedDocuments, iconGroups.stream().map(group -> false).toList(), layout);
    }

    /**
     * @param commentsPages whether each check's comments (and attestation) have a page of their own after its main page
     */
    public static Plan plan(List<String> iconGroups, List<Integer> movedDocuments, List<Boolean> commentsPages, int layout) {
        if (layout != 4 && layout != 6) {
            throw new IllegalArgumentException("layout must be 4 or 6");
        }
        Map<String, List<Integer>> byKey = new LinkedHashMap<>();
        for (int i = 0; i < iconGroups.size(); i++) {
            byKey.computeIfAbsent(iconGroups.get(i), key -> new ArrayList<>()).add(i);
        }
        List<Group> groups = byKey.entrySet().stream().map(e -> new Group(e.getKey(), List.copyOf(e.getValue()))).toList();
        int groupCount = groups.size();

        boolean overflows = groupCount > layout;
        boolean remarksInline = groupCount <= 2;
        boolean dedicatedRemarks = groupCount >= 3 && !overflows;

        List<Integer> cover = new ArrayList<>();
        for (int g = 0; g < Math.min(groupCount, layout); g++) {
            cover.add(g);
        }

        List<OverflowPage> overflow = overflows ? overflowPages(groupCount, layout) : List.of();

        int page = 1 + (dedicatedRemarks ? 1 : 0) + overflow.size();
        int firstDetail = page + 1;
        int total = page;
        for (int i = 0; i < iconGroups.size(); i++) {
            total += 1 + (commentsPages.get(i) ? 1 : 0) + Math.max(0, movedDocuments.get(i));
        }
        total += 1; // services page
        return new Plan(groups, layout, List.copyOf(cover), remarksInline, dedicatedRemarks, overflow, firstDetail, total);
    }

    /**
     * Spreads the groups that do not fit on page 1 over overflow pages. The remarks always sit on the last
     * one. Earlier pages take as many as they can while leaving at least one group for the last page.
     */
    private static List<OverflowPage> overflowPages(int groupCount, int layout) {
        boolean compact = layout == 6;
        int capWith = compact ? CAP_WITH_REMARKS_6 : CAP_WITH_REMARKS_4;
        int capWithout = compact ? CAP_WITHOUT_REMARKS_6 : CAP_WITHOUT_REMARKS_4;

        List<Integer> remaining = new ArrayList<>();
        for (int g = layout; g < groupCount; g++) {
            remaining.add(g);
        }
        List<OverflowPage> pages = new ArrayList<>();
        if (remaining.size() <= capWith) {
            pages.add(new OverflowPage(List.copyOf(remaining), true));
            return pages;
        }
        while (remaining.size() > capWith) {
            int take = Math.min(capWithout, remaining.size() - 1);
            if (take <= 0) {
                break;
            }
            pages.add(new OverflowPage(List.copyOf(remaining.subList(0, take)), false));
            remaining = new ArrayList<>(remaining.subList(take, remaining.size()));
        }
        pages.add(new OverflowPage(List.copyOf(remaining), true));
        return pages;
    }
}
