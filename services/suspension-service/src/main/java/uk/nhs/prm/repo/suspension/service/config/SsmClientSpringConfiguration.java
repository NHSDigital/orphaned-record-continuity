package uk.nhs.prm.repo.suspension.service.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ssm.SsmClient;

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
