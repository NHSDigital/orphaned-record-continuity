package uk.nhs.prm.repo.ehrtransferservice.config;

import com.amazon.sqs.javamessaging.AmazonSQSExtendedClient;
import com.amazon.sqs.javamessaging.ExtendedClientConfiguration;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.sqs.SqsClient;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
public class SqsExtendedClient {

    @Value("${aws.sqsLargeMessageBucketName}")
    private String bucketName;

    @Bean
    public AmazonSQSExtendedClient s3SupportedSqsClient(S3Client s3) {
        var extendedClientConfiguration = new ExtendedClientConfiguration().withPayloadSupportEnabled(s3, bucketName);
        return new AmazonSQSExtendedClient(SqsClient.builder().build(), extendedClientConfiguration);
    }
}
