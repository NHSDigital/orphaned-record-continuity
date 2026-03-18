package uk.nhs.prm.repo.suspension.service.suspensionsevents;

import org.junit.jupiter.api.Test;
import tools.jackson.core.JacksonException;

import static org.junit.jupiter.api.Assertions.assertThrows;

class SuspensionEventParserTest {

    @Test
    void parseShouldThrowAnExceptionWhenMessageIsInvalid() {
        var parser = new SuspensionEventParser();
        assertThrows(JacksonException.class, () -> parser.parse("invalid message"));
    }
}