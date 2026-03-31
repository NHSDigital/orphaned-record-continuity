package uk.nhs.prm.repo.ehrtransferservice.config;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.sns.AmazonSNS;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.sns.AmazonSNSExtendedClient;
import software.amazon.sns.SNSExtendedClientConfiguration;

@Configuration
public class SnsExtendedClient {
    @Value("${aws.sqsLargeMessageBucketName}")
    private String bucketName;

    @Bean
    public AmazonSNSExtendedClient s3SupportedSnsClient(AmazonSNS snsClient, AmazonS3 s3) {
        var snsExtendedClientConfiguration = new SNSExtendedClientConfiguration()
                .withPayloadSupportEnabled(s3, bucketName);
        return new AmazonSNSExtendedClient(snsClient, snsExtendedClientConfiguration);
    }
}