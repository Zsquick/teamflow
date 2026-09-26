package com.teamflow.audit;

import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 请求调用链编号在响应、请求属性与 MDC 之间的一致性测试。 */
class RequestTraceFilterTest {

    private final RequestTraceFilter filter = new RequestTraceFilter();

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void shouldGenerateOneTraceIdForRequestResponseAndLogging()
            throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> traceSeenByChain = new AtomicReference<>();

        filter.doFilter(
                request,
                response,
                (ignoredRequest, ignoredResponse) -> traceSeenByChain.set(
                        MDC.get(RequestTraceFilter.TRACE_ID_MDC_KEY)
                )
        );

        String traceId = (String) request.getAttribute(
                RequestTraceFilter.TRACE_ID_ATTRIBUTE
        );
        assertTrue(traceId.matches("[0-9a-f]{32}"));
        assertEquals(
                traceId,
                response.getHeader(RequestTraceFilter.TRACE_ID_HEADER)
        );
        assertEquals(traceId, traceSeenByChain.get());
        assertNull(MDC.get(RequestTraceFilter.TRACE_ID_MDC_KEY));
    }

    @Test
    void shouldClearMdcWhenDownstreamRequestFails() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThrows(
                ServletException.class,
                () -> filter.doFilter(
                        request,
                        response,
                        (ignoredRequest, ignoredResponse) -> {
                            throw new ServletException("downstream failed");
                        }
                )
        );

        assertNull(MDC.get(RequestTraceFilter.TRACE_ID_MDC_KEY));
        assertEquals(
                request.getAttribute(RequestTraceFilter.TRACE_ID_ATTRIBUTE),
                response.getHeader(RequestTraceFilter.TRACE_ID_HEADER)
        );
    }
}
