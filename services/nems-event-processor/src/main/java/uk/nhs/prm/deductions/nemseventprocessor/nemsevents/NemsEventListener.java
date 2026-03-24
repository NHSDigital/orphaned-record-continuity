package uk.nhs.prm.deductions.nemseventprocessor.nemsevents;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import uk.nhs.prm.deductions.nemseventprocessor.config.Tracer;

import jakarta.jms.Message;
import jakarta.jms.MessageListener;
import jakarta.jms.TextMessage;

@Slf4j
@RequiredArgsConstructor
public class NemsEventListener implements MessageListener {

    private final NemsEventHandler nemsEventService;
    private final Tracer tracer;

    @Override
    public void onMessage(Message message) {
        try {
            tracer.startMessageTrace(message.getStringProperty("meshMessageId"));
            log.info("RECEIVED: Nems Event Message");
            String payload = ((TextMessage) message).getText();
            nemsEventService.processNemsEvent(payload);
            message.acknowledge();
            log.info("ACKNOWLEDGED: Nems Event Message");
        } catch (Exception e) {
            log.error("Error while processing message: {}", e.getMessage());
        }
    }
}
