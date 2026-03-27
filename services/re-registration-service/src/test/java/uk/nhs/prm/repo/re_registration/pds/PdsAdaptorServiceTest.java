package uk.nhs.prm.repo.re_registration.pds;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import uk.nhs.prm.repo.re_registration.http.HttpClient;
import uk.nhs.prm.repo.re_registration.model.ReRegistrationEvent;
import uk.nhs.prm.repo.re_registration.pds.model.PdsAdaptorSuspensionStatusResponse;
import uk.nhs.prm.repo.re_registration.service.SsmService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.util.ReflectionTestUtils.setField;

@ExtendWith(MockitoExtension.class)
class PdsAdaptorServiceTest {

    @Mock
    SsmService ssmService;

    @Mock
    HttpClient httpClient;

    @Captor
    ArgumentCaptor<String> url;

    @Captor
    ArgumentCaptor<String> username;

    @Captor
    ArgumentCaptor<String> password;

    PdsAdaptorService pdsAdaptorService;

    private String pdsAdaptorServiceUrl = "pds-service-url";
    private String authUserName = "username";
    private String authPasswordSsmParameterName = "passwordSsmParameterName";

    @BeforeEach
    void init() {
        pdsAdaptorService = new PdsAdaptorService(ssmService, httpClient, pdsAdaptorServiceUrl, authUserName, authPasswordSsmParameterName);
        // normally retrieved from SSM post-construction, but set directly here for testing
        setField(pdsAdaptorService, "authPassword", "password");
    }

    @Test
    void shouldCallHttpClientWithCorrectUriAndUserNameAndPassword() {
        when(httpClient.get(any(), any(), any())).thenReturn(getPdsResponseStringWithSuspendedStatus(false));
        pdsAdaptorService.getPatientPdsStatus(getReRegistrationEvent());
        verify(httpClient).get(url.capture(), username.capture(), password.capture());
        assertThat("pds-service-url/suspended-patient-status/1234567890").isEqualTo(url.getValue());
        assertThat("username").isEqualTo(username.getValue());
        assertThat("password").isEqualTo(password.getValue());
    }

    @Test
    void shouldReturnParsedPdsAdaptorResponseIfSuccessful(){
        when(httpClient.get(any(), any(), any())).thenReturn(getPdsResponseStringWithSuspendedStatus(true));
        var actualResponse = pdsAdaptorService.getPatientPdsStatus(getReRegistrationEvent());
        var expectedResponse = new PdsAdaptorSuspensionStatusResponse("0000000000",true ,"currentOdsCode","managingOrganisation","etag",false);
        assertEquals(expectedResponse, actualResponse);
    }

    @Test
    void shouldReturnParsedPdsAdaptorResponseWhenManagingOrgNull(){
        when(httpClient.get(any(), any(), any())).thenReturn(getPdsResponseStringWithSuspendedStatusAndNullManagingOrg(true));
        var actualResponse = pdsAdaptorService.getPatientPdsStatus(getReRegistrationEvent());
        var expectedResponse = new PdsAdaptorSuspensionStatusResponse("0000000000",true ,"currentOdsCode",null,"etag",false);
        assertEquals(expectedResponse, actualResponse);
    }

    private ResponseEntity<String> getPdsResponseStringWithSuspendedStatus(boolean isSuspended) {
        var pdsResponseString = "{\"nhsNumber\":\"0000000000\",\"isSuspended\":" + isSuspended + ",\"currentOdsCode\":\"currentOdsCode\",\"managingOrganisation\":\"managingOrganisation\",\"recordETag\":\"etag\",\"isDeceased\":false}";
        return new ResponseEntity<>(pdsResponseString, HttpStatus.OK);
    }

    private ResponseEntity<String> getPdsResponseStringWithSuspendedStatusAndNullManagingOrg(boolean isSuspended) {
        var pdsResponseString = "{\"nhsNumber\":\"0000000000\",\"isSuspended\":" + isSuspended + ",\"currentOdsCode\":\"currentOdsCode\",\"managingOrganisation\":null,\"recordETag\":\"etag\",\"isDeceased\":false}";
        return new ResponseEntity<>(pdsResponseString, HttpStatus.OK);
    }

    private ReRegistrationEvent getReRegistrationEvent() {
        return new ReRegistrationEvent("1234567890", "ABC123", "nemsMessageId", "2017-11-01T15:00:33+00:00");
    }
}