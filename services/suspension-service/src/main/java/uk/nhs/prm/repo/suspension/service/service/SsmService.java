package uk.nhs.prm.repo.suspension.service.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.ssm.SsmClient;
import software.amazon.awssdk.services.ssm.model.GetParameterRequest;
import software.amazon.awssdk.services.ssm.model.Parameter;

@Slf4j
@Service
public class SsmService {

    private final SsmClient ssmClient;

    @Autowired
    public SsmService(SsmClient ssmClient) {
        this.ssmClient = ssmClient;
    }

    public String getValueForParameter(String parameterName) {
        log.info("Getting value for SSM parameter: {}", parameterName);

        GetParameterRequest parameterRequest = GetParameterRequest.builder()
                .name(parameterName)
                .withDecryption(true)
                .build();

        Parameter parameter = ssmClient.getParameter(parameterRequest).parameter();

        log.info("Value for SSM parameter {} retrieved successfully. Parameter version is: {}", parameterName, parameter.version());

        return parameter.value();
    }
}
