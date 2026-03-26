package uk.nhs.prm.repo.re_registration.pds;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import uk.nhs.prm.repo.re_registration.http.HttpClient;
import uk.nhs.prm.repo.re_registration.model.ReRegistrationEvent;
import uk.nhs.prm.repo.re_registration.pds.model.PdsAdaptorSuspensionStatusResponse;
import uk.nhs.prm.repo.re_registration.service.SsmService;

@Slf4j
@Service
public class PdsAdaptorService {

    private SsmService ssmService;
    private HttpClient httpClient;
    private String pdsAdaptorServiceUrl;
    private String authUserName;
    private String authPasswordSsmParameterName;
    private String authPassword;

    public PdsAdaptorService(
            SsmService ssmService,
            HttpClient httpClient,
            @Value("${pdsAdaptor.serviceUrl}") String pdsAdaptorServiceUrl,
            @Value("${pdsAdaptor.authUserName}") String authUserName,
            @Value("${pdsAdaptor.authPasswordSsmParameterName}") String authPasswordSsmParameterName
    ) {
        this.ssmService = ssmService;
        this.httpClient = httpClient;
        this.pdsAdaptorServiceUrl = pdsAdaptorServiceUrl;
        this.authUserName = authUserName;
        this.authPasswordSsmParameterName = authPasswordSsmParameterName;
    }

    @PostConstruct
    private void getAuthPasswordFromSsm() {
        authPassword = ssmService.getValueForParameter(authPasswordSsmParameterName);
    }

    public PdsAdaptorSuspensionStatusResponse getPatientPdsStatus(ReRegistrationEvent reRegistrationEvent) {
        var url = getPatientUrl(reRegistrationEvent.getNhsNumber());
        try {
            log.info("Making a GET suspended-patient-status to pds-adaptor");
            var pdsAdaptorResponseEntity = httpClient.get(url, authUserName, authPassword);
            return getParsedPdsAdaptorResponseBody(pdsAdaptorResponseEntity.getBody());
        } catch (Exception e) {
            log.error("Error during the performing pds adaptor request." + e.getMessage());
            throw e;
        }
    }

    private PdsAdaptorSuspensionStatusResponse getParsedPdsAdaptorResponseBody(String responseBody) {
        try {
            log.info("Trying to parse pds-adaptor response");
            return new ObjectMapper().readValue(responseBody, PdsAdaptorSuspensionStatusResponse.class);
        } catch (Exception e) {
            log.error("Encountered Exception while trying to parse pds-adaptor response");
            throw new RuntimeException(e);
        }
    }

    private String getPatientUrl(String nhsNumber) {
        return pdsAdaptorServiceUrl + "/suspended-patient-status/" + nhsNumber;
    }
}
