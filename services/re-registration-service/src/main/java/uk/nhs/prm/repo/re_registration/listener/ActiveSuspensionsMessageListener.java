package uk.nhs.prm.repo.re_registration.listener;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import uk.nhs.prm.repo.re_registration.config.Tracer;
import uk.nhs.prm.repo.re_registration.handlers.ActiveSuspensionsHandler;
import uk.nhs.prm.repo.re_registration.parser.ActiveSuspensionsParser;

import jakarta.jms.Message;
import jakarta.jms.MessageListener;
import jakarta.jms.TextMessage;

@Slf4j
@RequiredArgsConstructor
public class ActiveSuspensionsMessageListener implements MessageListener {

    private final Tracer tracer;
    private final ActiveSuspensionsHandler handler;
    private final ActiveSuspensionsParser parser;

    @Override
    public void onMessage(Message message) {
        try {
            tracer.setMDCContext(message);
            var payload = ((TextMessage) message).getText();
            var parsedMessage = parser.parse(payload);
            handler.handle(parsedMessage);
            message.acknowledge();
            log.info("ACKNOWLEDGED: Active Suspensions Message");
        } catch (Exception e) {
            log.error("Failure to handle message", e);
        }
    }
}
