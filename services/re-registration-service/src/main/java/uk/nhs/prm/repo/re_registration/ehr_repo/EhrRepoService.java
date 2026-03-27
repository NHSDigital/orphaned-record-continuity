package uk.nhs.prm.repo.re_registration.ehr_repo;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import uk.nhs.prm.repo.re_registration.config.Tracer;
import uk.nhs.prm.repo.re_registration.http.HttpClient;
import uk.nhs.prm.repo.re_registration.message_publishers.ReRegistrationAuditPublisher;
import uk.nhs.prm.repo.re_registration.model.ReRegistrationEvent;
import uk.nhs.prm.repo.re_registration.service.SsmService;

@Slf4j
@Service
public class EhrRepoService {
    private final Tracer tracer;
    private final ReRegistrationAuditPublisher auditPublisher;
    private SsmService ssmService;
    private HttpClient httpClient;
    private final String ehrRepoUrl;
    private final String ehrRepoAuthKeySsmParameterName;
    private String ehrRepoAuthKey;

    public EhrRepoService(
            Tracer tracer,
            ReRegistrationAuditPublisher auditPublisher,
            SsmService ssmService,
            HttpClient httpClient,
            @Value("${ehrRepoUrl}") String ehrRepoUrl,
            @Value("${ehrRepoAuthKeySsmParameterName}") String ehrRepoAuthKeySsmParameterName
    ) {
        this.tracer = tracer;
        this.auditPublisher = auditPublisher;
        this.ssmService = ssmService;
        this.httpClient = httpClient;
        this.ehrRepoUrl = ehrRepoUrl;
        this.ehrRepoAuthKeySsmParameterName = ehrRepoAuthKeySsmParameterName;
    }

    @PostConstruct
    private void getEhrRepoAuthKeyFromSsm() {
        ehrRepoAuthKey = ssmService.getValueForParameter(ehrRepoAuthKeySsmParameterName);
    }

    public EhrDeleteResponseContent deletePatientEhr(ReRegistrationEvent reRegistrationEvent) throws JsonProcessingException {
        var url = getPatientDeleteEhrUrl(reRegistrationEvent.getNhsNumber());
        log.info("Making a DELETE EHR Request to ehr-repo");
        var ehrRepoResponse = httpClient.delete(url, ehrRepoAuthKey);
        return getParsedDeleteEhrResponseBody(ehrRepoResponse.getBody());
    }

    private EhrDeleteResponseContent getParsedDeleteEhrResponseBody(String responseBody) throws JsonProcessingException {
        log.info("Trying to parse ehr-repo response");
        return new ObjectMapper().readValue(responseBody, EhrDeleteResponse.class).getEhrDeleteResponseContent();
    }

    private String getPatientDeleteEhrUrl(String nhsNumber) {
        return ehrRepoUrl + "/patients/" + nhsNumber;
    }
}
