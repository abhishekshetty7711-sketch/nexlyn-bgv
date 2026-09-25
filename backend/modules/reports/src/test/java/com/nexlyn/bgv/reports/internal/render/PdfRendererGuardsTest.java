package com.nexlyn.bgv.reports.internal.render;

import com.nexlyn.bgv.common.error.ApiException;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The two protections around the browser that need no browser to test: it may never download anything at run time
 * (that hung the first real Docker run: Playwright tried to fetch Firefox and WebKit), and a render that hangs is
 * stopped instead of holding its slot, and its case, forever.
 */
class PdfRendererGuardsTest {

    @Test
    void theBrowserDriverIsToldNeverToDownloadAnything() {
        assertThat(PdfRenderer.driverEnvironment()).containsEntry("PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD", "1");
    }

    @Test
    void aRenderThatHangsIsStoppedWithAFriendlyError() throws Exception {
        Thread[] stuck = new Thread[1];
        assertThatThrownBy(() -> PdfRenderer.within(Duration.ofMillis(200), () -> {
            stuck[0] = Thread.currentThread();
            try {
                Thread.sleep(60_000); // a browser start that never returns
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return "never used";
        })).isInstanceOf(ApiException.class).hasMessageContaining("took too long");
        stuck[0].join(2_000);
        assertThat(stuck[0].isAlive()).as("the stuck work was interrupted").isFalse();
    }

    @Test
    void workThatFinishesInTimeReturnsItsResultAndItsErrorsPassThrough() {
        assertThat(PdfRenderer.within(Duration.ofSeconds(5), () -> "done")).isEqualTo("done");
        assertThatThrownBy(() -> PdfRenderer.within(Duration.ofSeconds(5), () -> {
            throw new IllegalStateException("boom");
        })).isInstanceOf(IllegalStateException.class).hasMessage("boom");
    }
}
