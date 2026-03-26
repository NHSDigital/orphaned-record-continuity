package uk.nhs.prm.repo.suspension.service.pds;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import uk.nhs.prm.repo.suspension.service.http.RateLimitHttpClient;
import uk.nhs.prm.repo.suspension.service.model.PdsAdaptorSuspensionStatusResponse;
import uk.nhs.prm.repo.suspension.service.model.UpdateManagingOrganisationRequest;
import uk.nhs.prm.repo.suspension.service.service.SsmService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.util.ReflectionTestUtils.setField;
import static uk.nhs.prm.repo.suspension.service.logging.TestLogAppender.addTestLogAppender;

@ExtendWith(MockitoExtension.class)
class PdsServiceTest {
    @Mock
    private SsmService ssmService;

    @Mock
    private PdsAdaptorSuspensionStatusResponseParser responseParser;

    @Mock
    private RateLimitHttpClient client;

    private PdsService pdsService;

    private String serviceUrl = "http://pds-adaptor";

    private String suspensionServicePasswordSsmParameterName = "passwordSsmParameterName";

    @BeforeEach
    public void setUp() {
        pdsService = new PdsService(ssmService, responseParser, client, serviceUrl, suspensionServicePasswordSsmParameterName);
        // normally retrieved from SSM post-construction, but set directly here for testing
        setField(pdsService, "suspensionServicePassword", "PASS");
    }

    @Test
    public void shouldGetPatientStatusFromPdsServiceUrlAndParseResponse() {
        String expectedUrl = "http://pds-adaptor/suspended-patient-status/1234567890";
        var parsedStatus = aStatus();

        ResponseEntity<String> response = ResponseEntity.ok("some status");
        when(client.getWithStatusCodeNoRateLimit(expectedUrl, "suspension-service", "PASS"))
                .thenReturn(response);
        when(responseParser.parse("some status")).thenReturn(parsedStatus);

        var status = pdsService.isSuspended("1234567890");
        assertThat(status).isEqualTo(parsedStatus);
    }

    @Test
    public void shouldUpdatePatientStatusFromPdsServiceUrlAndParseResponse() {
        String expectedUrl = "http://pds-adaptor/suspended-patient-status/1234567890";
        var parsedStatus = aStatus();
        var requestPayload = new UpdateManagingOrganisationRequest("hello", "bob");

        ResponseEntity<String> response = ResponseEntity.ok("some status");
        when(client.putWithStatusCodeWithTwoSecRateLimit(expectedUrl, "suspension-service", "PASS", requestPayload)).thenReturn(response);
        when(responseParser.parse("some status")).thenReturn(parsedStatus);

        var status = pdsService.updateMof("1234567890", "hello", "bob");
        assertThat(status).isEqualTo(parsedStatus);
    }

    @Test
    public void shouldThrowExceptionWhenPdsReturn404() {
        String expectedUrl = "http://pds-adaptor/suspended-patient-status/1234567890";

        ResponseEntity<String> response = ResponseEntity.notFound().build();
        when(client.getWithStatusCodeNoRateLimit(expectedUrl, "suspension-service", "PASS")).thenReturn(response);

        assertThrows(InvalidPdsRequestException.class, () -> {
            pdsService.isSuspended("1234567890");
        });
    }

    @Test
    public void shouldThrowExceptionIncludingDetailWhenPdsReturn400() {
        String expectedUrl = "http://pds-adaptor/suspended-patient-status/1234567890";

        when(client.getWithStatusCodeNoRateLimit(expectedUrl, "suspension-service", "PASS"))
                .thenThrow(HttpClientErrorException.class);

        var thrown = assertThrows(InvalidPdsRequestException.class, () -> {
            pdsService.isSuspended("1234567890");
        });

        assertThat(thrown.getCause().getClass()).isEqualTo(HttpClientErrorException.class);
    }

    @Test
    public void shouldThrowExceptionWhenPdsReturn400ForMofUpdate() {
        String expectedUrl = "http://pds-adaptor/suspended-patient-status/1234567890";
        var requestPayload = new UpdateManagingOrganisationRequest("hello", "bob");

        when(client.putWithStatusCodeWithTwoSecRateLimit(expectedUrl, "suspension-service", "PASS", requestPayload))
                .thenThrow(HttpClientErrorException.class);

        assertThrows(InvalidPdsRequestException.class, () -> {
            pdsService.updateMof("1234567890", "hello", "bob");
        });
    }

    @Test
    public void shouldLogTheCausingExceptionIncludingDetailsOfAny4xxTypeHttpClientErrorException() {
        var logged = addTestLogAppender();

        var theCause = new HttpClientErrorException(HttpStatus.NOT_FOUND, "Some more detail");
        when(client.getWithStatusCodeNoRateLimit(anyString(), anyString(), anyString()))
                .thenThrow(theCause);

        assertThrows(InvalidPdsRequestException.class, () -> {
            pdsService.isSuspended("1234567890");
        });

        var errorLog = logged.findLoggedEvent("client error");
        assertThat(errorLog).isNotNull();
        assertThat(errorLog.getThrowableProxy().getMessage()).contains("Some more detail");
    }


    @Test
    public void shouldAssumeThisIsAnIntermittentExceptionWhenPdsReturn500() {
        String expectedUrl = "http://pds-adaptor/suspended-patient-status/1234567890";

        when(client.getWithStatusCodeNoRateLimit(expectedUrl, "suspension-service", "PASS"))
                .thenThrow(HttpServerErrorException.class);

        assertThrows(IntermittentErrorPdsException.class, () -> {
            pdsService.isSuspended("1234567890");
        });
    }

    @Test
    public void shouldThrowExceptionWhenPdsReturn500ForMofUpdate() {
        String expectedUrl = "http://pds-adaptor/suspended-patient-status/1234567890";
        var requestPayload = new UpdateManagingOrganisationRequest("hello", "bob");

        when(client.putWithStatusCodeWithTwoSecRateLimit(expectedUrl, "suspension-service", "PASS", requestPayload))
                .thenThrow(HttpServerErrorException.class);

        assertThrows(IntermittentErrorPdsException.class, () -> {
            pdsService.updateMof("1234567890", "hello", "bob");
        });
    }

    @Test
    public void shouldAssumeThisIsAnIntermittentExceptionWhenPdsConnectionFails() {
        String expectedUrl = "http://pds-adaptor/suspended-patient-status/1234567890";

        when(client.getWithStatusCodeNoRateLimit(expectedUrl, "suspension-service", "PASS"))
                .thenThrow(RuntimeException.class);

        assertThrows(IntermittentErrorPdsException.class, () -> {
            pdsService.isSuspended("1234567890");
        });
    }

    @Test
    public void shouldAssumeThatAnyOldErrorIsIntermittentIfWeHaventClassifiedIt() {
        when(client.getWithStatusCodeNoRateLimit(anyString(), anyString(), anyString()))
                .thenThrow(IllegalArgumentException.class);

        assertThrows(IntermittentErrorPdsException.class, () -> {
            pdsService.isSuspended("1234567890");
        });
    }

    @Test
    public void shouldLogTheCausingExceptionIncludingDetailsOfAnyUnexpectedExceptionClassifiedAsIntermittent() {
        var logged = addTestLogAppender();

        var theCause = new HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR, "Some more interesting detail");
        when(client.getWithStatusCodeNoRateLimit(anyString(), anyString(), anyString()))
                .thenThrow(theCause);

        assertThrows(IntermittentErrorPdsException.class, () -> {
            pdsService.isSuspended("1234567890");
        });

        var errorLog = logged.findLoggedEvent("unexpected error");
        assertThat(errorLog).isNotNull();
        assertThat(errorLog.getThrowableProxy().getMessage()).contains("Some more interesting detail");
    }

    @Test
    public void shouldThrowExceptionWhenPdsConnectionFailsForMofUpdate() {
        String expectedUrl = "http://pds-adaptor/suspended-patient-status/1234567890";
        var requestPayload = new UpdateManagingOrganisationRequest("hello", "bob");

        when(client.putWithStatusCodeWithTwoSecRateLimit(expectedUrl, "suspension-service", "PASS", requestPayload))
                .thenThrow(RuntimeException.class);

        assertThrows(IntermittentErrorPdsException.class, () -> {
            pdsService.updateMof("1234567890", "hello", "bob");
        });
    }

    private PdsAdaptorSuspensionStatusResponse aStatus() {
        return new PdsAdaptorSuspensionStatusResponse("9692294951", true, "ODS123", "ODS456", "v1", false);
    }
}