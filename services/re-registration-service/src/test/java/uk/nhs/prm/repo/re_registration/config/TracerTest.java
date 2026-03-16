package uk.nhs.prm.repo.re_registration.config;

import com.amazon.sqs.javamessaging.message.SQSTextMessage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import jakarta.jms.JMSException;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.spy;
import static uk.nhs.prm.repo.re_registration.config.Tracer.*;

class TracerTest {

    private static final String SOME_NEMS_ID = "someNemsId";
    private static final String SOME_TRACE_ID = "someTraceId";
    private static Tracer tracer;

    @BeforeAll
    static void setUp() {
        tracer = new Tracer();
    }

    @Test
    void shouldAddTraceIdToMDCWhenItIsPresentInMessage() throws JMSException {
        SQSTextMessage message = spy(new SQSTextMessage("payload"));
        message.setStringProperty(TRACE_ID, SOME_TRACE_ID);

        tracer.setMDCContext(message);
        String mdcTraceIdValue = MDC.get(TRACE_ID);
        assertThat(mdcTraceIdValue).isEqualTo(SOME_TRACE_ID);
    }

    @Test
    void shouldCreateAndAddHyphenatedUuidTraceIdToMDCWhenItIsNotPresentInMessage() throws JMSException {
        SQSTextMessage message = spy(new SQSTextMessage("payload"));

        tracer.setMDCContext(message);
        String mdcTraceIdValue = MDC.get(TRACE_ID);
        assertThat(mdcTraceIdValue).isNotNull();
        assertThat(UUID.fromString(mdcTraceIdValue)).isNotNull();
    }

    @Test
    void shouldAddNemsMessageIdToMDC() throws JMSException {
        SQSTextMessage message = spy(new SQSTextMessage("payload"));
        message.setStringProperty(NEMS_MESSAGE_ID, SOME_NEMS_ID);

        tracer.setMDCContext(message);
        String mdcValue = MDC.get(NEMS_MESSAGE_ID);
        assertThat(mdcValue).isEqualTo(SOME_NEMS_ID);
    }

    @Test
    void shouldAddTraceIdToMDC() throws JMSException {
        SQSTextMessage message = spy(new SQSTextMessage("payload"));
        message.setStringProperty(NEMS_MESSAGE_ID, SOME_NEMS_ID);

        String threadName = Thread.currentThread().getName();

        tracer.setMDCContext(message);
        String threadIdNameMdc = MDC.get(THREAD_NAME);
        assertThat(threadIdNameMdc).isEqualTo(threadName);
    }
}
