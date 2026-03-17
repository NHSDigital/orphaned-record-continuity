package uk.nhs.prm.repo.suspension.service.suspensionsevents;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import uk.nhs.prm.repo.suspension.service.config.Tracer;
import uk.nhs.prm.repo.suspension.service.pds.InvalidPdsRequestException;

import jakarta.jms.JMSException;
import jakarta.jms.Message;
import jakarta.jms.MessageListener;
import jakarta.jms.TextMessage;

@Slf4j
@RequiredArgsConstructor
public class SuspensionsEventListener implements MessageListener {

    private final SuspensionMessageProcessor suspensionsEventProcessor;
    private final Tracer tracer;

    @Override
    public void onMessage(Message message) {
        try {
            tracer.setMDCContext(message);
            log.info("RECEIVED: Suspensions Event Message");
            var payload = ((TextMessage) message).getText();
            suspensionsEventProcessor.process(payload);
            deleteMessage(message);
        } catch (InvalidPdsRequestException | InvalidSuspensionMessageException e) {
            deleteMessage(message);
        } catch (Exception e) {
            log.error("Failure to handle message", e);
        }
    }

    private void deleteMessage(Message message) {
        try {
            message.acknowledge();
        } catch (JMSException e) {
            log.error("Got an error during the deletion of message from suspension queue.");
        }
    }
}
