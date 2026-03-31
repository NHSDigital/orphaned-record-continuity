package uk.nhs.prm.repo.ehrtransferservice.config;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import uk.nhs.prm.repo.ehrtransferservice.logging.UpdateableTraceContext;

import javax.jms.JMSException;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static uk.nhs.prm.repo.ehrtransferservice.logging.TraceKey.CONVERSATION_ID;
import static uk.nhs.prm.repo.ehrtransferservice.logging.TraceKey.TRACE_ID;

class UpdateableTraceContextTest {

    private static UpdateableTraceContext traceContext;

    @BeforeAll
    static void setUp() {
        traceContext = new UpdateableTraceContext();
    }

    @Test
    void updateTraceIdShouldAddTraceIdToLoggingContextWhenPassed() throws JMSException {
        traceContext.updateTraceId("someTraceId");

        String mdcTraceIdValue = MDC.get(TRACE_ID.toString());

        assertThat(mdcTraceIdValue).isEqualTo("someTraceId");
    }

    @Test
    void updateTraceIdShouldCreateAndAddHyphenatedUuidTraceIdWhenNotPresent() throws JMSException {
        traceContext.updateTraceId(null);

        String mdcTraceIdValue = MDC.get(TRACE_ID.toString());

        assertThat(mdcTraceIdValue).isNotNull();
        assertThat(UUID.fromString(mdcTraceIdValue)).isNotNull();
    }

    @Test
    void updateTraceIdShouldSetTheTraceIdInTheLoggingContext() {
        MDC.clear();

        traceContext.updateTraceId("bob");

        assertThat(MDC.get(TRACE_ID.toString())).isEqualTo("bob");
    }

    @Test
    void updateTraceIdShouldOverwriteTheTraceIdInTheLoggingContext() {
        MDC.put(TRACE_ID.toString(), "foo");

        traceContext.updateTraceId("bar");

        assertThat(MDC.get(TRACE_ID.toString())).isEqualTo("bar");
    }

    @Test
    void clearShouldClearTheConversationIdAndTraceIdWhenCleared() {
        MDC.put(TRACE_ID.toString(), "whoop");
        MDC.put(CONVERSATION_ID.toString(), "cheese");

        traceContext.clear();

        assertThat(MDC.get(CONVERSATION_ID.toString())).isNull();
        assertThat(MDC.get(TRACE_ID.toString())).isNull();
    }

    @Test
    void updateConversationIdShouldUpdateTheConversationIdButNotClearTheTraceId() {
        MDC.put(TRACE_ID.toString(), "some-trace-id");
        MDC.put(CONVERSATION_ID.toString(), "old-convo");

        traceContext.updateConversationId("new-convo");

        assertThat(MDC.get(TRACE_ID.toString())).isEqualTo("some-trace-id");
        assertThat(MDC.get(CONVERSATION_ID.toString())).isEqualTo("new-convo");
    }

    @Test
    void updateConversationIdShouldLeaveTheConversationIdIfNewOneBlank() {
        MDC.put(CONVERSATION_ID.toString(), "old-convo");

        traceContext.updateConversationId("");

        assertThat(MDC.get(CONVERSATION_ID.toString())).isEqualTo("old-convo");
    }

    @Test
    void updateConversationIdShouldLeaveTheConversationIdIfNewOneNull() {
        MDC.put(CONVERSATION_ID.toString(), "old-convo");

        traceContext.updateConversationId(null);

        assertThat(MDC.get(CONVERSATION_ID.toString())).isEqualTo("old-convo");
    }
}
