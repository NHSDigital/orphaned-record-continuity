package uk.nhs.prm.repo.suspension.service.pds;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import uk.nhs.prm.repo.suspension.service.http.RateLimitHttpClient;
import uk.nhs.prm.repo.suspension.service.model.PdsAdaptorSuspensionStatusResponse;
import uk.nhs.prm.repo.suspension.service.model.UpdateManagingOrganisationRequest;
import uk.nhs.prm.repo.suspension.service.service.SsmService;

@Component
@Slf4j
public class PdsService {
    public static final String SUSPENSION_SERVICE_USERNAME = "suspension-service";

    private static final String SUSPENDED_PATIENT = "suspended-patient-status/";

    private final SsmService ssmService;

    private final PdsAdaptorSuspensionStatusResponseParser responseParser;

    private final RateLimitHttpClient httpClient;

    private String serviceUrl;

    private String suspensionServicePasswordSsmParameterName;

    private String suspensionServicePassword;

    public PdsService(
            SsmService ssmService,
            PdsAdaptorSuspensionStatusResponseParser responseParser,
            RateLimitHttpClient httpClient,
            @Value("${pdsAdaptor.serviceUrl}") String serviceUrl,
            @Value("${pdsAdaptor.suspensionService.passwordSsmParameterName}") String suspensionServicePasswordSsmParameterName
    ) {
        this.ssmService = ssmService;
        this.responseParser = responseParser;
        this.httpClient = httpClient;
        this.serviceUrl = serviceUrl;
        this.suspensionServicePasswordSsmParameterName = suspensionServicePasswordSsmParameterName;
    }

    @PostConstruct
    private void getAuthPasswordFromSsm() {
        suspensionServicePassword = ssmService.getValueForParameter(suspensionServicePasswordSsmParameterName);
    }

    public PdsAdaptorSuspensionStatusResponse isSuspended(String nhsNumber) {
        final String url = getPatientUrl(nhsNumber);
        try {
            ResponseEntity<String> response = httpClient.getWithStatusCodeNoRateLimit(url, SUSPENSION_SERVICE_USERNAME, suspensionServicePassword);
            if (response.getStatusCode().is4xxClientError()){
                throw new HttpClientErrorException(response.getStatusCode());
            }
            return responseParser.parse(response.getBody());
        } catch (HttpClientErrorException e) {
            log.error("Got client error", e);
            throw new InvalidPdsRequestException("Got client error", e);
        } catch (Exception e) {
            String error = "Got unexpected error";
            log.error(error, e);
            throw new IntermittentErrorPdsException(error, e);
        }
    }

    public PdsAdaptorSuspensionStatusResponse updateMof(String nhsNumber, String previousOdsCode, String recordETag) {
        log.info("Making request to update Managing Organization field");
        final String url = getPatientUrl(nhsNumber);
        final UpdateManagingOrganisationRequest requestPayload = new UpdateManagingOrganisationRequest(previousOdsCode, recordETag);
        try {
            ResponseEntity<String> response = httpClient.putWithStatusCodeWithTwoSecRateLimit(url, SUSPENSION_SERVICE_USERNAME, suspensionServicePassword, requestPayload);
            return responseParser.parse(response.getBody());
        } catch (HttpClientErrorException e) {
            log.error("Got client error");
            throw new InvalidPdsRequestException("Got client error", e);
        } catch (Exception e) {
            log.error("Got server error");
            throw new IntermittentErrorPdsException("Got server error", e);
        }
    }

    private String getPatientUrl(String nhsNumber) {
        return serviceUrl + "/" + SUSPENDED_PATIENT + nhsNumber;
    }
}
