package uk.nhs.prm.deductions.nemseventprocessor.config;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import com.amazon.sqs.javamessaging.message.SQSTextMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import uk.nhs.prm.deductions.nemseventprocessor.nemsevents.NemsEventListener;
import uk.nhs.prm.deductions.nemseventprocessor.nemsevents.NemsEventService;

import jakarta.jms.JMSException;
import java.util.ArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MockitoExtension.class)
class NemsEventListenerMessageTracingTest {

    @Mock
    private NemsEventService nemsEventService;

    @Test
    void shouldAddOriginalMessageIdAndTraceIdToLoggingContextWhenReceivesMessage() throws JMSException {
        var nemsEventListener = new NemsEventListener(nemsEventService, new Tracer());
        var testLogAppender = addTestLogAppender();

        SQSTextMessage meshOriginatedMessage = new SQSTextMessage("payload");
        meshOriginatedMessage.setObjectProperty("meshMessageId", "foo");
        nemsEventListener.onMessage(meshOriginatedMessage);

        var receivedLogEventProperties = testLogAppender.findLoggedEvent("RECEIVED").getMDCPropertyMap();
        assertTrue(receivedLogEventProperties.containsKey("traceId"));
        assertThat(receivedLogEventProperties.get("meshMessageId")).isEqualTo("foo");
    }

    @Test
    void shouldNotThrowIfOriginalMessageIdIsNotProvided() throws JMSException {
        var nemsEventListener = new NemsEventListener(nemsEventService, new Tracer());
        nemsEventListener.onMessage(new SQSTextMessage("payload"));
    }

    private TestLogAppender addTestLogAppender() {
        var testLogAppender = new TestLogAppender();
        var logger = (Logger)LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        logger.addAppender(testLogAppender);

        testLogAppender.start();
        return testLogAppender;
    }
}

class TestLogAppender extends AppenderBase<ILoggingEvent> {
    ArrayList<ILoggingEvent> loggingEvents = new ArrayList<>();

    @Override
    protected void append(ILoggingEvent eventObject) {
        loggingEvents.add(eventObject);
    }

    ILoggingEvent findLoggedEvent(String subString) {
        for (ILoggingEvent event: loggingEvents) {
           if (event.getMessage().contains(subString)) {
               return event;
           }
        }
        return null;
    }
}