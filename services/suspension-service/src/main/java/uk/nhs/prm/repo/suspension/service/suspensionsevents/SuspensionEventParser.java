package uk.nhs.prm.repo.suspension.service.suspensionsevents;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Component
@Slf4j
public class SuspensionEventParser {
    private final ObjectMapper mapper = new ObjectMapper();

    public SuspensionEvent parse(String suspensionMessage) throws JacksonException {
        return mapper.readValue(suspensionMessage, SuspensionEvent.class);
    }
}