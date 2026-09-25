package com.nexlyn.bgv.reports.internal.service;

import com.nexlyn.bgv.reports.internal.assemble.ReportModelAssembler.Assembly;
import com.nexlyn.bgv.reports.internal.render.PdfRenderer.PageOverflow;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Which document is moved off a page that does not fit: the last one still on it, and only on pages that overflow. */
class ReportJobRunnerLayoutTest {

    private final UUID a = UUID.randomUUID();
    private final UUID b = UUID.randomUUID();
    private final UUID c = UUID.randomUUID();

    private Assembly assembly() {
        // page 3 holds two documents, page 5 holds one, page 4 none
        return new Assembly(null, Map.of(3, List.of(a, b), 4, List.of(), 5, List.of(c)));
    }

    @Test
    void theLastDocumentOfEachOverflowingPageIsChosen() {
        assertThat(ReportJobRunner.documentsToMove(assembly(), List.of(new PageOverflow(3, 40), new PageOverflow(5, 10))))
                .containsExactlyInAnyOrder(b, c);
    }

    @Test
    void pagesThatFitAreLeftAlone() {
        assertThat(ReportJobRunner.documentsToMove(assembly(), List.of(new PageOverflow(5, 12)))).containsExactly(c);
        assertThat(ReportJobRunner.documentsToMove(assembly(), List.of())).isEmpty();
    }

    @Test
    void aPageWithoutDocumentsCannotBeHelpedThisWay() {
        assertThat(ReportJobRunner.documentsToMove(assembly(), List.of(new PageOverflow(4, 90), new PageOverflow(1, 5)))).isEmpty();
    }
}
