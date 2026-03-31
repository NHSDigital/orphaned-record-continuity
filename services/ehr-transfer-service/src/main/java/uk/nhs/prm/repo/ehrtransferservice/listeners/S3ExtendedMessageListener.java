package uk.nhs.prm.repo.ehrtransferservice.listeners;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import uk.nhs.prm.repo.ehrtransferservice.logging.Tracer;
import uk.nhs.prm.repo.ehrtransferservice.exceptions.DuplicateMessageException;
import uk.nhs.prm.repo.ehrtransferservice.gp2gp_message_models.ParsedMessage;
import uk.nhs.prm.repo.ehrtransferservice.handlers.MessageHandler;
import uk.nhs.prm.repo.ehrtransferservice.parsers.S3ExtendedMessageFetcher;

import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageListener;

@Slf4j
@RequiredArgsConstructor
public class S3ExtendedMessageListener implements MessageListener {

    private final String messageTypeDescription;
    private final Tracer tracer;
    private final S3ExtendedMessageFetcher extendedMessageFetcher;
    private final MessageHandler<ParsedMessage> parsedMessageHandler;

    @Override
    public void onMessage(Message message) {
        try {
            tracer.setMDCContextFromSqs(message);
            log.info("RECEIVED: Message from " + queueDescription());
            var parsedMessage = extendedMessageFetcher.fetchAndParse(message);
            parsedMessageHandler.handleMessage(parsedMessage);
            message.acknowledge();

            log.info("ACKNOWLEDGED: Message from " + queueDescription());
        } catch (Exception e) {
            handleException(e, message);
        }
    }

    private void handleException(Exception e, Message message) {
        if (e instanceof DuplicateMessageException) {
            log.error("Received duplicate message - message not stored.");
            try {
                message.acknowledge();
            } catch (JMSException ex) {
                log.error("Error while acknowledging a duplicate message: ", ex);
            }
        } else {
            log.error("Error while processing message from " + queueDescription(), e);
        }

    }

    private String queueDescription() {
        return messageTypeDescription + " queue";
    }

}
