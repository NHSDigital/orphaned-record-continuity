package uk.nhs.prm.repo.ehrtransferservice.listeners;


import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import uk.nhs.prm.repo.ehrtransferservice.logging.Tracer;
import uk.nhs.prm.repo.ehrtransferservice.handlers.NegativeAcknowledgementHandler;
import uk.nhs.prm.repo.ehrtransferservice.models.ack.Acknowledgement;
import uk.nhs.prm.repo.ehrtransferservice.parsers.Parser;

import javax.jms.Message;
import javax.jms.MessageListener;
import javax.jms.TextMessage;

@Slf4j
@RequiredArgsConstructor
public class NegativeAcknowledgementListener implements MessageListener {

    private final Tracer tracer;
    private final Parser parser;
    private final NegativeAcknowledgementHandler handler;

    @Override
    public void onMessage(Message message) {
        try {
            tracer.setMDCContextFromSqs(message);
            log.info("RECEIVED: Message from negative acknowledge queue");
            String payload = ((TextMessage) message).getText();
            var parsedMessage = (Acknowledgement) parser.parse(payload);
            handler.handleMessage(parsedMessage);
            message.acknowledge();
            log.info("ACKNOWLEDGED: Message from negative acknowledgement queue");
        } catch (Exception e) {
            log.error("Error while processing negative acknowledgement", e);
        }
    }
}
