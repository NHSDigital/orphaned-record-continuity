package uk.nhs.prm.repo.ehrtransferservice.repo_incoming;

import com.amazon.sqs.javamessaging.message.SQSTextMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.nhs.prm.repo.ehrtransferservice.exceptions.ConversationAlreadyInProgressException;
import uk.nhs.prm.repo.ehrtransferservice.exceptions.ConversationIneligibleForRetryException;
import uk.nhs.prm.repo.ehrtransferservice.exceptions.FailedToPersistException;
import uk.nhs.prm.repo.ehrtransferservice.exceptions.acknowledgement.EhrCompleteAcknowledgementFailedException;
import uk.nhs.prm.repo.ehrtransferservice.exceptions.database.ConversationAlreadyPresentException;
import uk.nhs.prm.repo.ehrtransferservice.exceptions.database.ConversationNotPresentException;
import uk.nhs.prm.repo.ehrtransferservice.exceptions.database.ConversationUpdateException;
import uk.nhs.prm.repo.ehrtransferservice.exceptions.database.QueryReturnedNoItemsException;
import uk.nhs.prm.repo.ehrtransferservice.exceptions.timeout.TimeoutExceededException;
import uk.nhs.prm.repo.ehrtransferservice.logging.Tracer;

import javax.jms.Message;

import java.util.UUID;
import java.util.stream.Stream;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RepoIncomingEventListenerTest {

    @Mock
    Tracer tracer;
    @Mock
    RepoIncomingService repoIncomingService;
    @Mock
    RepoIncomingEventParser incomingEventParser;
    @InjectMocks
    RepoIncomingEventListener repoIncomingEventListener;

    @Test
    void onMessage_ValidIncomingEvent_ShouldCallTracerWithMessageAndConversation() throws Exception {
        // given
        String payload = "payload";
        RepoIncomingEvent incomingEvent = getIncomingEvent();
        SQSTextMessage message = spy(new SQSTextMessage(payload));

        // when
        when(incomingEventParser.parse(payload)).thenReturn(incomingEvent);
        repoIncomingEventListener.onMessage(message);

        // then
        verify(incomingEventParser).parse(payload);
        verify(tracer).setMDCContextFromSqs(message);
        verify(repoIncomingService).processIncomingEvent(incomingEvent);
    }

    @Test
    void onMessage_ValidIncomingEvent_ShouldAcknowledgeTheMessageWhenRequestIsSuccessful() throws Exception {
        // given
        RepoIncomingEvent incomingEvent = getIncomingEvent();
        String payload = "payload";
        Message message = spy(new SQSTextMessage(payload));

        // when
        when(incomingEventParser.parse(payload)).thenReturn(incomingEvent);
        repoIncomingEventListener.onMessage(message);

        // then
        verify(repoIncomingService).processIncomingEvent(incomingEvent);
        verify(message).acknowledge();
    }

    @ParameterizedTest
    @MethodSource({"ArgumentsOfExceptionsThatShouldAcknowledge"})
    void onMessage_ProcessIncomingEventThrowsIneligibleForRetryOrEhrCompleteAcknowledgementFailedException_ShouldAcknowledgeTheMessage(Exception exception) throws Exception {
        // given
        RepoIncomingEvent incomingEvent = getIncomingEvent();
        String payload = "payload";
        Message message = spy(new SQSTextMessage(payload));

        // when
        when(incomingEventParser.parse(payload)).thenReturn(incomingEvent);
        doThrow(exception).when(repoIncomingService).processIncomingEvent(incomingEvent);

        repoIncomingEventListener.onMessage(message);

        // then
        verify(repoIncomingService).processIncomingEvent(incomingEvent);
        verify(message).acknowledge();
    }

    private static Stream<Arguments> ArgumentsOfExceptionsThatShouldAcknowledge() {
        UUID inboundConversationId = UUID.randomUUID();
        return Stream.of(
                Arguments.of(new ConversationIneligibleForRetryException(inboundConversationId)),
                Arguments.of(new EhrCompleteAcknowledgementFailedException(inboundConversationId, new Throwable()))
        );
    }

    @ParameterizedTest
    @MethodSource({"ArgumentsOfExceptionsThatShouldNotAcknowledge"})
    void onMessage_ProcessIncomingEventThrowsAnyOtherException_ShouldNotAcknowledgeTheMessage(Exception exception) throws Exception {
        // given
        RepoIncomingEvent incomingEvent = getIncomingEvent();
        String payload = "payload";
        SQSTextMessage message = spy(new SQSTextMessage(payload));

        // when
        when(incomingEventParser.parse(payload)).thenReturn(incomingEvent);
        doThrow(exception).when(repoIncomingService).processIncomingEvent(incomingEvent);

        repoIncomingEventListener.onMessage(message);

        // then
        verify(repoIncomingService).processIncomingEvent(incomingEvent);
        verify(message, never()).acknowledge();
    }

    private static Stream<Arguments> ArgumentsOfExceptionsThatShouldNotAcknowledge() {
        UUID inboundConversationId = UUID.randomUUID();
        return Stream.of(
                Arguments.of(new TimeoutExceededException(inboundConversationId)),
                Arguments.of(new ConversationAlreadyInProgressException(inboundConversationId)),
                Arguments.of(new ConversationAlreadyPresentException(inboundConversationId)),
                Arguments.of(new ConversationNotPresentException(inboundConversationId)),
                Arguments.of(new FailedToPersistException(inboundConversationId, new Throwable())),
                Arguments.of(new ConversationUpdateException(inboundConversationId, new Throwable())),
                Arguments.of(new QueryReturnedNoItemsException(inboundConversationId))
        );
    }

    private RepoIncomingEvent getIncomingEvent() {
        return new RepoIncomingEvent("111111111", "source-gp", "nem-message-id", "unique-uuid");
    }
}
