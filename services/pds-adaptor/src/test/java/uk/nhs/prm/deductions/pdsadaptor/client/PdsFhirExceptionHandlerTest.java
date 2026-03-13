package uk.nhs.prm.deductions.pdsadaptor.client;

import ch.qos.logback.classic.spi.ILoggingEvent;
import net.logstash.logback.marker.RawJsonAppendingMarker;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.UnknownContentTypeException;
import uk.nhs.prm.deductions.pdsadaptor.client.exceptions.*;
import uk.nhs.prm.deductions.pdsadaptor.model.pdsresponse.PdsFhirPatient;
import uk.nhs.prm.deductions.pdsadaptor.testing.TestLogAppender;

import java.util.HashMap;
import java.util.Map;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static uk.nhs.prm.deductions.pdsadaptor.testing.TestLogAppender.addTestLogAppender;

class PdsFhirExceptionHandlerTest {

    private PdsFhirExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new PdsFhirExceptionHandler();
    }

    @Test
    public void shouldStructurallyLogTheResponseBodyOnNotFound() {
        TestLogAppender testLogAppender = addTestLogAppender();

        Map<String, String> responseBody = new HashMap<>() {
            {
                put("some_code", "example code");
                put("some_detail", "example detail");
            }
        };
        assertThrows(RuntimeException.class, () ->
                handler.handleCommonExceptions("some description", createErrorResponse(404, asJson(responseBody))));

        ILoggingEvent logged = testLogAppender.getLastLoggedEvent();
        assertThat(logged.getMarker()).isInstanceOf(RawJsonAppendingMarker.class);

        RawJsonAppendingMarker jsonMarker = (RawJsonAppendingMarker) logged.getMarker();
        assertThat(jsonMarker.getFieldName()).isEqualTo("error_response");
        assertThat(logged.getFormattedMessage()).isEqualTo("PDS FHIR Request failed - Patient not found 404");

        String markerOutput = jsonMarker.toString();
        assertThat(markerOutput).contains("some_code");
        assertThat(markerOutput).contains("example code");
        assertThat(markerOutput).contains("some_detail");
        assertThat(markerOutput).contains("example detail");
    }

    @Test
    void shouldThrowBadRequestExceptionWhenPdsResourceInvalid() {
        HttpClientErrorException badRequest400 = new HttpClientErrorException(BAD_REQUEST, "bad-request-error");

        BadRequestException exception = assertThrows(BadRequestException.class, () ->
                handler.handleCommonExceptions("context", badRequest400));

        assertThat(exception.getMessage())
                .isEqualTo("Received 400 error from PDS FHIR: error: 400 bad-request-error");
    }

    @Test
    void shouldThrowNotFoundExceptionIfPatientNotFoundInPds() {
        HttpClientErrorException notFound404 = new HttpClientErrorException(HttpStatus.NOT_FOUND, "error");

        NotFoundException exception = assertThrows(NotFoundException.class, () ->
                handler.handleCommonExceptions("context", notFound404));

        assertThat(exception.getMessage()).isEqualTo("PDS FHIR Request failed - Patient not found 404");
    }

    @Test
    void shouldThrowAccessTokenRequestExceptionOnForbiddenError() {
        HttpClientErrorException forbiddenError403 = new HttpClientErrorException(HttpStatus.FORBIDDEN, "error");

        AccessTokenRequestException exception = assertThrows(AccessTokenRequestException.class, () ->
                handler.handleCommonExceptions("context", forbiddenError403));

        assertThat(exception.getMessage()).contains("Access token request failed");
        assertThat(exception.getCause()).isEqualTo(forbiddenError403);
    }

    @Test
    void shouldThrowAccessTokenRequestExceptionOnUnauthorizedError() {
        HttpClientErrorException unauthorizedError401 = new HttpClientErrorException(HttpStatus.UNAUTHORIZED, "error");

        AccessTokenRequestException exception = assertThrows(AccessTokenRequestException.class, () ->
                handler.handleCommonExceptions("context", unauthorizedError401));

        assertThat(exception.getMessage()).contains("Access token request failed");
        assertThat(exception.getCause()).isEqualTo(unauthorizedError401);
    }

    @Test
    void shouldThrowTooManyRequestsExceptionWhenExceedingPdsFhirRateLimit() {
        HttpClientErrorException tooManyRequests429 = new HttpClientErrorException(HttpStatus.TOO_MANY_REQUESTS, "error");

        TooManyRequestsException exception = assertThrows(TooManyRequestsException.class, () ->
                handler.handleCommonExceptions("context", tooManyRequests429));

        assertThat(exception.getMessage()).isEqualTo("Rate limit exceeded for PDS FHIR - too many requests");
    }

    @Test
    void shouldThrowRetryableRequestExceptionOnServiceUnavailable() {
        HttpServerErrorException pdsServiceIsUnavailable503 = new HttpServerErrorException(HttpStatus.SERVICE_UNAVAILABLE, "error");

        RetryableRequestException exception = assertThrows(RetryableRequestException.class, () ->
                handler.handleCommonExceptions("context", pdsServiceIsUnavailable503));

        assertThat(exception.getMessage()).isEqualTo("PDS FHIR request failed status code: 503. reason 503 error");
    }

    @Test
    void shouldThrowRetryableRequestExceptionOnNetworkFailure() {
        ResourceAccessException networkFailure = new ResourceAccessException("something like a socket timeout");

        RetryableRequestException exception = assertThrows(RetryableRequestException.class, () ->
                handler.handleCommonExceptions("context", networkFailure));

        assertThat(exception.getCause()).isEqualTo(networkFailure);
        assertThat(exception.getMessage()).contains("something like a socket timeout");
    }

    @Test
    void shouldThrowRuntimeExceptionWhenResponseCannotBeParsed() {
        UnknownContentTypeException unparseableResponseException = new UnknownContentTypeException(PdsFhirPatient.class, APPLICATION_JSON, 200,
                "ok", new HttpHeaders(), new byte[0]);

        RuntimeException exception = assertThrows(RuntimeException.class, () ->
                handler.handleCommonExceptions("requesting", unparseableResponseException));

        assertThat(exception.getMessage()).contains("PDS FHIR returned unexpected response body when requesting PDS Record");
    }

    @Test
    void shouldRethrowInitialAccessTokenRequestException() {
        AccessTokenRequestException exceptionFromAuthenticationStack = new AccessTokenRequestException(new HttpServerErrorException(HttpStatus.SERVICE_UNAVAILABLE));

        RuntimeException exception = assertThrows(RuntimeException.class, () ->
                handler.handleCommonExceptions("requesting", exceptionFromAuthenticationStack));

        assertThat(exception).isEqualTo(exceptionFromAuthenticationStack);
    }

    @Test
    void shouldRethrowInitialRuntimeExceptionWhenNotSpecificallyHandled() {
        IllegalArgumentException unexpectedException = new IllegalArgumentException("not anticipated");

        RuntimeException exception = assertThrows(RuntimeException.class, () ->
                handler.handleCommonExceptions("requesting", unexpectedException));

        assertThat(exception).isEqualTo(unexpectedException);
    }

    private HttpClientErrorException createErrorResponse(int statusCode, String responseBodyJson) {
        return new HttpClientErrorException(HttpStatus.resolve(statusCode), "error", responseBodyJson.getBytes(UTF_8), UTF_8);
    }

    private String asJson(Map<String, String> errorResponseBodyContent) {
        return new JSONObject(errorResponseBodyContent).toString();
    }
}
