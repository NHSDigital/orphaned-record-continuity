package uk.nhs.prm.repo.ehrtransferservice.repo_incoming;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import uk.nhs.prm.repo.ehrtransferservice.exceptions.ConversationIneligibleForRetryException;
import uk.nhs.prm.repo.ehrtransferservice.exceptions.acknowledgement.EhrCompleteAcknowledgementFailedException;
import uk.nhs.prm.repo.ehrtransferservice.logging.Tracer;

import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageListener;
import javax.jms.TextMessage;

@Slf4j
@RequiredArgsConstructor
public class RepoIncomingEventListener implements MessageListener {
    private final Tracer tracer;
    private final RepoIncomingService repoIncomingService;
    private final RepoIncomingEventParser parser;

    // time gap between our EHR requests to allow Foundation Suppliers to process other requests they might receive
    @Value("${processingPeriodMilliseconds}")
    private int processingPeriodMilliseconds;

    @Override
    public void onMessage(Message message) {
        log.info("RECEIVED: Message from RepoIncoming");

        try {
            tracer.setMDCContextFromSqs(message);
            RepoIncomingEvent parsedMessage = parseMessage(message);
            processIncomingEventAndAcknowledgeMessageOnQueue(message, parsedMessage);
        } catch (JMSException e) {
            log.error("Caught JMSException while processing incoming event. Exception message is: {}", e.getMessage());
        }
        waitForEmisProcessingPeriod();
    }

    private RepoIncomingEvent parseMessage(Message message) throws JMSException {
        String payload = ((TextMessage) message).getText();
        RepoIncomingEvent parsedMessage = parser.parse(payload);
        log.info("PARSED: message with conversationId " + parsedMessage.getConversationId());
        return parsedMessage;
    }

    private void processIncomingEventAndAcknowledgeMessageOnQueue(Message message, RepoIncomingEvent parsedMessage) {
        String inboundConversationIdUppercased = parsedMessage.getConversationId().toUpperCase();

        try {
            repoIncomingService.processIncomingEvent(parsedMessage);
            acknowledgeMessageOnQueue(message, inboundConversationIdUppercased);
        } catch (ConversationIneligibleForRetryException | EhrCompleteAcknowledgementFailedException exception) {
            log.warn("Error while attempting to process incoming event with inboundConversationId {}. " +
                    "Will acknowledge message on queue.", inboundConversationIdUppercased, exception);
            acknowledgeMessageOnQueue(message, inboundConversationIdUppercased);
        } catch (Exception exception) {
            log.error("Error while processing message with inboundConversationId {}",
                    inboundConversationIdUppercased, exception);
        }
    }

    private void acknowledgeMessageOnQueue(Message message, String inboundConversationIdUppercased) {
        try {
            message.acknowledge();
            log.info("ACKNOWLEDGED: Message from RepoIncoming with inboundConversationId {}", inboundConversationIdUppercased);
        } catch (JMSException jmsException) {
            log.warn("Error while attempting to acknowledge message on RepoIncoming queue with inboundConversationId {}",
                    inboundConversationIdUppercased, jmsException);
        }
    }

    private void waitForEmisProcessingPeriod() {
        try {
            Thread.sleep(processingPeriodMilliseconds);
        } catch (InterruptedException e) {
            log.error("Caught interruptedException waiting for EMIS processing period", e);
        }
    }
}
