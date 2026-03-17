package uk.nhs.prm.repo.suspension.service.suspensionsevents;

import com.amazon.sqs.javamessaging.message.SQSTextMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.nhs.prm.repo.suspension.service.config.Tracer;
import uk.nhs.prm.repo.suspension.service.pds.InvalidPdsRequestException;

import jakarta.jms.JMSException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static uk.nhs.prm.repo.suspension.service.logging.TestLogAppender.addTestLogAppender;

@ExtendWith(MockitoExtension.class)
public class SuspensionsEventListenerTest {

    private static final String nemsMessageId = "A6FBE8C3-9144-4DDD-BFFE-B49A96456B29";

    @Mock
    Tracer tracer;

    @Mock
    private SuspensionMessageProcessor suspensionsEventProcessor;

    @InjectMocks
    private SuspensionsEventListener suspensionsEventListener;

    @Test
    void shouldCallNemsEventServiceWithReceivedMessage() throws JMSException {
        var payload = "payload";
        SQSTextMessage message = spy(new SQSTextMessage(payload));

        suspensionsEventListener.onMessage(message);
        verify(suspensionsEventProcessor).process(payload);
        verify(message).acknowledge();
    }

    @Test
    void shouldSwallowAndLogExceptionsAsErrorsInProcessingWithoutAcknowledgingMessage() throws JMSException {
        var logged = addTestLogAppender();
        var exception = new IllegalStateException("some exception");
        var message = spy(new SQSTextMessage("bob"));

        doThrow(exception).when(suspensionsEventProcessor).process(any());

        suspensionsEventListener.onMessage(message);

        assertThat(logged.findLoggedEvent("Failure to handle message").getThrowableProxy().getMessage()).isEqualTo("some exception");
        verify(message, never()).acknowledge();
    }

    @Test
    void shouldAcknowledgeMessageWhenInvalidPdsRequestExceptionsThrown() throws JMSException {
        var exception = new InvalidPdsRequestException("some exception", new Throwable());
        var message = spy(new SQSTextMessage("bob"));

        doThrow(exception).when(suspensionsEventProcessor).process(any());

        suspensionsEventListener.onMessage(message);

        verify(message).acknowledge();
    }

    @Test
    void shouldAcknowledgeMessageWhenInvalidSuspensionMessageExceptionThrown() throws JMSException {
        var exception = new InvalidSuspensionMessageException("some exception", new Throwable());
        var message = spy(new SQSTextMessage("bob"));

        doThrow(exception).when(suspensionsEventProcessor).process(any());

        suspensionsEventListener.onMessage(message);

        verify(message).acknowledge();
    }
}
