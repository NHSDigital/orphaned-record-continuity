package uk.nhs.prm.repo.re_registration.config;

import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ssm.SsmClient;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
public class SsmClientSpringConfiguration {
    @Bean
    public SsmClient ssmClient() {
        return SsmClient.builder()
                .region(Region.EU_WEST_2)
                .build();
    }
}
