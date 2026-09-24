package com.nexlyn.bgv.common.validation;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BoldOnlyHtmlTest {

    @Test
    void keepsBoldAndPlainText() {
        assertThat(BoldOnlyHtml.sanitize("All checks <strong>passed</strong>.")).isEqualTo("All checks <strong>passed</strong>.");
        assertThat(BoldOnlyHtml.sanitize("plain text")).isEqualTo("plain text");
        assertThat(BoldOnlyHtml.sanitize("")).isEmpty();
        assertThat(BoldOnlyHtml.sanitize(null)).isNull();
    }

    @Test
    void writesBoldTagsBackInOneForm() {
        assertThat(BoldOnlyHtml.sanitize("<B>x</B> and <STRONG >y</Strong>")).isEqualTo("<strong>x</strong> and <strong>y</strong>");
    }

    @Test
    void neutralisesEveryOtherTag() {
        assertThat(BoldOnlyHtml.sanitize("<script>alert(1)</script>")).isEqualTo("&lt;script&gt;alert(1)&lt;/script&gt;");
        assertThat(BoldOnlyHtml.sanitize("<img src=x onerror=alert(1)>")).doesNotContain("<img");
        assertThat(BoldOnlyHtml.sanitize("<a href=\"javascript:alert(1)\">x</a>")).doesNotContain("<a ").doesNotContain("\"");
        assertThat(BoldOnlyHtml.sanitize("<strong onclick=\"alert(1)\">x</strong>")).startsWith("&lt;strong onclick=").doesNotContain("<strong onclick");
        assertThat(BoldOnlyHtml.sanitize("<b><script>x</script></b>")).isEqualTo("<strong>&lt;script&gt;x&lt;/script&gt;</strong>");
    }

    @Test
    void escapesQuotesAndAmpersandsInText() {
        assertThat(BoldOnlyHtml.sanitize("Tom & Jerry's \"team\"")).isEqualTo("Tom &amp; Jerry&#39;s &quot;team&quot;");
    }

    @Test
    void repairsUnbalancedTags() {
        assertThat(BoldOnlyHtml.sanitize("<strong>never closed")).isEqualTo("<strong>never closed</strong>");
        assertThat(BoldOnlyHtml.sanitize("never opened</strong> text")).isEqualTo("never opened text");
        assertThat(BoldOnlyHtml.sanitize("<b><b>x</b>")).isEqualTo("<strong><strong>x</strong></strong>");
    }

    @Test
    void isIdempotentSoRepeatedSavesDoNotPileUpEntities() {
        for (String input : new String[]{"Tom & Jerry <script>x</script> <b>bold</b>", "a &amp; b", "1 < 2 > 0", "\"q\" 'q'", "<b>x"}) {
            String once = BoldOnlyHtml.sanitize(input);
            assertThat(BoldOnlyHtml.sanitize(once)).as(input).isEqualTo(once);
        }
    }
}
