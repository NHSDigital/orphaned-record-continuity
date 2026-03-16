package uk.nhs.prm.repo.re_registration.config;

import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.context.annotation.Configuration;

import jakarta.jms.JMSException;
import jakarta.jms.Message;
import java.util.UUID;

@Slf4j
@Configuration
@NoArgsConstructor
public class Tracer {

    public static final String TRACE_ID = "traceId";
    public static final String NEMS_MESSAGE_ID = "nemsMessageId";
    public static final String THREAD_NAME = "threadName";

    public void setMDCContext(Message message) throws JMSException {
        clearMDCContext();
        handleTraceId(message);
        setThreadId();
        handleNemsMessageId(message);
    }

    private void handleTraceId(Message message) throws JMSException {
        if (message.getStringProperty(TRACE_ID) == null) {
            log.info("The message has no trace ID attribute, we'll create and assign one.");
            setTraceId(createTraceId());
        } else {
            setTraceId(message.getStringProperty(TRACE_ID));
        }
    }

    private String createTraceId() {
        return UUID.randomUUID().toString();
    }

    private void setTraceId(String traceId) {
        MDC.put(TRACE_ID, traceId);
    }

    public String getTraceId() {
        return MDC.get(TRACE_ID);
    }

    private void handleNemsMessageId(Message message) throws JMSException {
        if (message.getStringProperty(NEMS_MESSAGE_ID) == null) {
            log.error("The message has no NEMS message ID attribute");
        } else {
            setNemsMessageId(message.getStringProperty(NEMS_MESSAGE_ID));
        }
    }

    private void setThreadId() {
        MDC.put(THREAD_NAME, Thread.currentThread().getName());
    }

    private void setNemsMessageId(String nemsMessageId) {
        MDC.put(NEMS_MESSAGE_ID, nemsMessageId);
    }

    public String getNemsMessageId() {
        return MDC.get(NEMS_MESSAGE_ID);
    }

    private void clearMDCContext() {
        MDC.remove(TRACE_ID);
        MDC.remove(NEMS_MESSAGE_ID);
        MDC.remove(THREAD_NAME);
    }
}
