package com.nexlyn.bgv.common.logging;

import com.nexlyn.bgv.common.error.ApiError;
import com.nexlyn.bgv.common.error.ErrorCode;
import com.nexlyn.bgv.common.web.ApiErrors;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class CorrelationIdFilterTest {

    private final CorrelationIdFilter filter = new CorrelationIdFilter();

    private String run(String supplied, AtomicReference<String> seenInside, MockHttpServletResponse response) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        if (supplied != null) {
            request.addHeader(CorrelationIdFilter.HEADER, supplied);
        }
        filter.doFilter(request, response, new MockFilterChain() {
            @Override
            public void doFilter(jakarta.servlet.ServletRequest req, jakarta.servlet.ServletResponse res) {
                seenInside.set(CorrelationIdFilter.current());
            }
        });
        return response.getHeader(CorrelationIdFilter.HEADER);
    }

    @Test
    void makesAnIdWhenNoneIsSuppliedAndReturnsItInTheResponse() throws Exception {
        AtomicReference<String> inside = new AtomicReference<>();
        MockHttpServletResponse response = new MockHttpServletResponse();
        String id = run(null, inside, response);
        assertThat(id).matches("[0-9a-f-]{36}");
        assertThat(inside.get()).isEqualTo(id);
    }

    @Test
    void keepsAPlainSuppliedId() throws Exception {
        AtomicReference<String> inside = new AtomicReference<>();
        String id = run("req-2026.09.25_ABCDEF", inside, new MockHttpServletResponse());
        assertThat(id).isEqualTo("req-2026.09.25_ABCDEF");
        assertThat(inside.get()).isEqualTo("req-2026.09.25_ABCDEF");
    }

    @Test
    void replacesAnythingThatCouldSpoilALogLine() throws Exception {
        for (String hostile : new String[]{"short", "has space in it here", "line\\nbreak-injection-attempt", "<script>alert(1)</script>", "x".repeat(200)}) {
            AtomicReference<String> inside = new AtomicReference<>();
            String id = run(hostile, inside, new MockHttpServletResponse());
            assertThat(id).as(hostile).isNotEqualTo(hostile).matches("[0-9a-f-]{36}");
        }
    }

    @Test
    void theIdIsGoneAfterTheRequestAndAppearsInErrorBodies() throws Exception {
        AtomicReference<String> inside = new AtomicReference<>();
        String id = run("abcdef123456", inside, new MockHttpServletResponse());
        assertThat(CorrelationIdFilter.current()).isNull();

        // an error built while a request is being handled carries the id
        org.slf4j.MDC.put(CorrelationIdFilter.MDC_KEY, id);
        try {
            ApiError body = ApiErrors.response(ErrorCode.NOT_FOUND, "Nothing here.", java.util.List.of(), null).getBody();
            assertThat(body.correlationId()).isEqualTo("abcdef123456");
            assertThat(ApiError.of(ErrorCode.FORBIDDEN, "No.").correlationId()).isEqualTo("abcdef123456");
        } finally {
            org.slf4j.MDC.remove(CorrelationIdFilter.MDC_KEY);
        }
        assertThat(ApiError.of(ErrorCode.FORBIDDEN, "No.").correlationId()).isNull();
    }
}
