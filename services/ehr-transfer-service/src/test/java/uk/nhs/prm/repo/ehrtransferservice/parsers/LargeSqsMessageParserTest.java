package uk.nhs.prm.repo.ehrtransferservice.parsers;

import com.fasterxml.jackson.core.JsonParseException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import uk.nhs.prm.repo.ehrtransferservice.models.LargeSqsMessage;
import uk.nhs.prm.repo.ehrtransferservice.utils.TestDataLoader;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.*;

class LargeSqsMessageParserTest {

    private final LargeSqsMessageParser parser = new LargeSqsMessageParser();
    private final TestDataLoader loader = new TestDataLoader();

    private InputStream readResourceFile(String resourceFileName) throws FileNotFoundException {
        return new FileInputStream("src/test/resources/data/" + resourceFileName);
    }

    @Test
    void shouldParseLargeSQSMessage() throws IOException {
        String messageAsString = loader.getDataAsString("RCMR_IN030000UK06");

        var result = parser.parse(messageAsString);

        assertEquals(result.getClass(), LargeSqsMessage.class);
    }


    @ParameterizedTest
    @ValueSource(strings = {
            "simpleTextMessage.txt",
            "RCMR_IN030000UK06WithoutInteractionId",
            "RCMR_IN030000UK06WithoutMessageHeader",
            "RCMR_IN030000UK06WithoutSoapHeader",
            "RCMR_IN030000UK06WithIncorrectInteractionId"
    })
    void shouldThrowJsonParseExceptionWhenCannotParseMessage(String fileName) throws IOException {
        String messageAsString = loader.getDataAsString(fileName);

        Exception expected = assertThrows(JsonParseException.class, () ->
                parser.parse(messageAsString)
        );
        assertThat(expected, notNullValue());
    }
}