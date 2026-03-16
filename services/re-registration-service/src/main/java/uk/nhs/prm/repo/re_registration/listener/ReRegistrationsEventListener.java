package uk.nhs.prm.repo.re_registration.listener;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import uk.nhs.prm.repo.re_registration.config.Tracer;
import uk.nhs.prm.repo.re_registration.handlers.ReRegistrationsRetryHandler;

import jakarta.jms.Message;
import jakarta.jms.MessageListener;
import jakarta.jms.TextMessage;

@Slf4j
@RequiredArgsConstructor
public class ReRegistrationsEventListener implements MessageListener {

    private final Tracer tracer;
    private final ReRegistrationsRetryHandler reRegistrationsRetryHandler;

    @Override
    public void onMessage(Message message) {
        try {
            tracer.setMDCContext(message);
            var payload = ((TextMessage) message).getText();
            reRegistrationsRetryHandler.handle(payload);
            message.acknowledge();
            log.info("ACKNOWLEDGED: Re-registrations Event Message");
        } catch (Exception e) {
            log.error("Failure to handle message", e);
        }
    }
}
