package uk.nhs.prm.deductions.pdsadaptor.logging;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import net.logstash.logback.marker.RawJsonAppendingMarker;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import uk.nhs.prm.deductions.pdsadaptor.testing.TestLogAppender;

import java.lang.reflect.Field;
import java.util.HashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.slf4j.LoggerFactory.getLogger;
import static uk.nhs.prm.deductions.pdsadaptor.testing.TestLogAppender.addTestLogAppender;

class JsonLoggerTest {

    private Logger log;

    @BeforeEach
    public void createLogger() {
        log = getLogger(JsonLoggerTest.class);
    }

    @Test
    public void shouldStructurallyLogMessageAsInfoWithNamedJsonField() {
        TestLogAppender testLogAppender = addTestLogAppender();

        String json = asJson(new HashMap<>() {
            {
                put("some_code", "example code");
                put("some_detail", "example detail");
            }
        });

        JsonLogger.logInfoWithJson(log, "the message", "json_fieldname", json);

        ILoggingEvent logged = testLogAppender.getLastLoggedEvent();
        assertThat(logged.getMessage()).isEqualTo("the message");
        assertThat(logged.getLevel()).isEqualTo(Level.INFO);
        assertThat(logged.getMarker()).isInstanceOf(RawJsonAppendingMarker.class);

        RawJsonAppendingMarker jsonMarker = (RawJsonAppendingMarker) logged.getMarker();
        assertThat(jsonMarker.getFieldName()).isEqualTo("json_fieldname");

        String loggedJson = getMarkerFieldValue(jsonMarker);
        assertThat(loggedJson.contains("some_code")).isTrue();
        assertThat(loggedJson.contains("some_detail")).isTrue();
    }

    @Test
    public void shouldRemoveLineBreaksWithinJsonWhenStructurallyLoggingWithJson() {
        TestLogAppender testLogAppender = addTestLogAppender();

        String jsonWithLineBreak = "{\n" +
                "\"a_field\": \"some value\"}";

        JsonLogger.logInfoWithJson(log, "msg", "json_name", jsonWithLineBreak);

        ILoggingEvent logged = testLogAppender.getLastLoggedEvent();
        RawJsonAppendingMarker jsonMarker = (RawJsonAppendingMarker) logged.getMarker();
        String loggedJson = getMarkerFieldValue(jsonMarker);
        assertThat(loggedJson.contains("{\"a_field")).isTrue();
    }

    @Test
    public void shouldLogRawResponseWhenNotValidJsonAndLoggingWithJson() {
        TestLogAppender testLogAppender = addTestLogAppender();

        JsonLogger.logInfoWithJson(log, "some message", "it's invalid you know", "invalid-json");

        ILoggingEvent logged = testLogAppender.getLastLoggedEvent();

        RawJsonAppendingMarker jsonMarker = (RawJsonAppendingMarker) logged.getMarker();
        assertThat(jsonMarker.getFieldName()).isEqualTo("it's invalid you know");

        String loggedJson = getMarkerFieldValue(jsonMarker);
        assertThat(loggedJson).isEqualTo("invalid-json");
    }

    private String asJson(HashMap<Object, Object> errorResponseBodyContent) {
        return new JSONObject(errorResponseBodyContent).toString();
    }

    private String getMarkerFieldValue(RawJsonAppendingMarker marker) {
        try {
            Field field = RawJsonAppendingMarker.class.getDeclaredField("rawJson");
            field.setAccessible(true);
            return (String) field.get(marker);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException("Could not access rawJson on RawJsonAppendingMarker", e);
        }
    }

}