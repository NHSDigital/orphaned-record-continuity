package uk.nhs.prm.repo.ehrtransferservice.config;

import com.amazon.sqs.javamessaging.message.SQSTextMessage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import uk.nhs.prm.repo.ehrtransferservice.logging.UpdateableTraceContext;
import uk.nhs.prm.repo.ehrtransferservice.logging.Tracer;

import javax.jms.JMSException;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.spy;
import static uk.nhs.prm.repo.ehrtransferservice.logging.TraceKey.CONVERSATION_ID;
import static uk.nhs.prm.repo.ehrtransferservice.logging.TraceKey.TRACE_ID;

class TracerTest {

    private static final String SOME_TRACE_ID = "someTraceId";
    private static final String SOME_CONVERSATION_ID = "someConversationId";
    private static Tracer tracer;

    public static final String TRACE_ID_KEY = TRACE_ID.toString();
    public static final String CONVERSATION_ID_KEY = CONVERSATION_ID.toString();

    @BeforeAll
    static void setUp() {
        tracer = new Tracer();
    }

    @Test
    void shouldAddTraceIdToMDCWhenItIsPresentInMessage() throws JMSException {
        SQSTextMessage message = spy(new SQSTextMessage("payload"));
        message.setStringProperty(TRACE_ID_KEY, SOME_TRACE_ID);
        message.setStringProperty(CONVERSATION_ID_KEY, SOME_CONVERSATION_ID);

        tracer.setMDCContextFromSqs(message);
        String mdcTraceIdValue = MDC.get(TRACE_ID_KEY);
        String mdcConversationIdValue = MDC.get(CONVERSATION_ID_KEY);
        assertThat(mdcTraceIdValue).isEqualTo(SOME_TRACE_ID);
        assertThat(mdcConversationIdValue).isEqualTo(SOME_CONVERSATION_ID);
    }

    @Test
    void shouldCreateAndAddHyphenatedUuidTraceIdToMDCWhenItIsNotPresentInMessage() throws JMSException {
        SQSTextMessage message = spy(new SQSTextMessage("payload"));

        tracer.setMDCContextFromSqs(message);

        String mdcTraceIdValue = MDC.get(TRACE_ID_KEY);
        assertThat(mdcTraceIdValue).isNotNull();
        assertThat(UUID.fromString(mdcTraceIdValue)).isNotNull();
    }

    @Test
    void createNewContextShouldReturnTraceContextAfterClearingIt() {
        MDC.put(CONVERSATION_ID_KEY, "cheese");

        UpdateableTraceContext traceContext = tracer.createNewContext();

        assertThat(traceContext).isNotNull();
        assertThat(MDC.get(CONVERSATION_ID.toString())).isNull();
    }
}
