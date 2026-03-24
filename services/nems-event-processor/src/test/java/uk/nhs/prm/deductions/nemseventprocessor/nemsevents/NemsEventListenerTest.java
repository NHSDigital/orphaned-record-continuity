package uk.nhs.prm.deductions.nemseventprocessor.nemsevents;

import com.amazon.sqs.javamessaging.message.SQSTextMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.nhs.prm.deductions.nemseventprocessor.config.Tracer;

import jakarta.jms.JMSException;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NemsEventListenerTest {

    @Mock
    private NemsEventService nemsEventService;

    @Mock
    private Tracer tracer;

    @InjectMocks
    private NemsEventListener nemsEventListener;

    @Test
    void shouldCallNemsEventServiceWithReceivedMessage() throws JMSException {
        String payload = "payload";
        SQSTextMessage message = spy(new SQSTextMessage(payload));

        nemsEventListener.onMessage(message);
        verify(nemsEventService).processNemsEvent(payload);
        verify(message).acknowledge();
    }

    @Test
    void whenStringPropertyFailsThenItShouldBeHandledInCatch() throws JMSException {
        SQSTextMessage message = spy(new SQSTextMessage("payload"));
        doThrow(new JMSException("error")).when(message).getStringProperty(anyString());
        nemsEventListener.onMessage(message);
        verifyNoInteractions(nemsEventService);
        verify(message, never()).acknowledge();
    }

    @Test
    void shouldNotAcknowledgeOrProcessMessageIfJMSReceivingFails() throws JMSException {
        SQSTextMessage message = spy(new SQSTextMessage("payload"));

        doThrow(new JMSException("error")).when(message).getText();

        nemsEventListener.onMessage(message);

        verifyNoInteractions(nemsEventService);
        verify(message, never()).acknowledge();
    }

    @Test
    void shouldNotAcknowledgeTheMessageIfProcessingFailsForAnyUncheckedException() throws JMSException {
        String payload = "payload";
        SQSTextMessage message = spy(new SQSTextMessage(payload));

        doThrow(new RuntimeException("unchecked")).when(nemsEventService).processNemsEvent(anyString());

        nemsEventListener.onMessage(message);
        verify(message, never()).acknowledge();
    }

}
