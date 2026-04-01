package uk.nhs.prm.repo.ehrtransferservice.config;

import com.amazon.sqs.javamessaging.AmazonSQSExtendedClient;
import com.amazon.sqs.javamessaging.ExtendedClientConfiguration;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.sqs.SqsClient;

@Configuration
@RequiredArgsConstructor
public class SqsExtendedClientConfiguration {
    @Value("${aws.sqsLargeMessageBucketName}")
    private String bucketName;

    @Bean
    public AmazonSQSExtendedClient sqsExtendedClient(SqsClient sqsClient, S3Client s3) {
        var extendedClientConfiguration = new ExtendedClientConfiguration()
                .withPayloadSupportEnabled(s3, bucketName, true);
        return new AmazonSQSExtendedClient(sqsClient, extendedClientConfiguration);
    }
}