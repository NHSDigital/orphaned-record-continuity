package uk.nhs.prm.repo.ehrtransferservice.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.sns.AmazonSNSExtendedClient;
import software.amazon.sns.SNSExtendedClientConfiguration;

@Configuration
public class SnsExtendedClientConfiguration {
    @Value("${aws.sqsLargeMessageBucketName}")
    private String bucketName;

    @Bean
    public AmazonSNSExtendedClient snsExtendedClient(SnsClient snsClient, S3Client s3) {
        var snsExtendedClientConfiguration = new SNSExtendedClientConfiguration()
                .withPayloadSupportEnabled(s3, bucketName);
        return new AmazonSNSExtendedClient(snsClient, snsExtendedClientConfiguration);
    }
}
