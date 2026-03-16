package uk.nhs.prm.repo.re_registration.listener;

import com.amazon.sqs.javamessaging.message.SQSTextMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.nhs.prm.repo.re_registration.config.Tracer;
import uk.nhs.prm.repo.re_registration.handlers.ActiveSuspensionsHandler;
import uk.nhs.prm.repo.re_registration.model.ActiveSuspensionsMessage;
import uk.nhs.prm.repo.re_registration.parser.ActiveSuspensionsParser;

import jakarta.jms.JMSException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static uk.nhs.prm.repo.re_registration.logging.TestLogAppender.addTestLogAppender;

@ExtendWith(MockitoExtension.class)
class ActiveSuspensionsMessageListenerTest {

    @Mock
    Tracer tracer;

    @Mock
    ActiveSuspensionsHandler handler;

    @Mock
    ActiveSuspensionsParser parser;

    @InjectMocks
    private ActiveSuspensionsMessageListener messageListener;

    private String payload = "payload";

    @Test
    void shouldStartTracingWhenReceivesAMessage() throws JMSException {
        var message = getTextMessage();

        messageListener.onMessage(message);
        verify(tracer).setMDCContext(message);
    }

    @Test
    void shouldHandleMessageWhenActiveSuspensionsEventReceivedAndAcknowledge() throws JMSException {
        var message = getTextMessage();
        var activeSuspensionsMessage = getActiveSuspensionsMessage();

        when(parser.parse(any())).thenReturn(activeSuspensionsMessage);

        messageListener.onMessage(message);

        verify(handler, times(1)).handle(activeSuspensionsMessage);
        verify(message).acknowledge();
    }

    @Test
    void shouldThrowExceptionAndNotAcknowledgeMessageWhenErrorOccurs() throws JMSException {
        var logged = addTestLogAppender();
        var exception = new IllegalStateException("some exception");
        var message = getTextMessage();

        doThrow(exception).when(handler).handle(any());

        messageListener.onMessage(message);

        assertThat(logged.findLoggedEvent("Failure to handle message").getThrowableProxy().getMessage()).isEqualTo("some exception");
        verify(message, never()).acknowledge();
    }

    private SQSTextMessage getTextMessage() throws JMSException {
        return spy(new SQSTextMessage(payload));
    }

    private ActiveSuspensionsMessage getActiveSuspensionsMessage() {
        return new ActiveSuspensionsMessage("some-nhs-number", "some-ods-code", "last-updated");
    }

}