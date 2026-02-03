package uk.nhs.prm.repo.ehrtransferservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.services.sqs.SqsClient;

@Configuration
public class SqsClientSpringConfiguration {


    
    @Bean
    public SqsClient sqsClient() {
        return SqsClient.create();
    }
}