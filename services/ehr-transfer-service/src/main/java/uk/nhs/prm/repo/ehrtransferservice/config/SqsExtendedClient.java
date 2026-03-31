package uk.nhs.prm.repo.ehrtransferservice.config;

import com.amazon.sqs.javamessaging.AmazonSQSExtendedClient;
import com.amazon.sqs.javamessaging.ExtendedClientConfiguration;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.sqs.AmazonSQSAsync;
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
    public AmazonSQSExtendedClient s3SupportedSqsClient(AmazonSQSAsync sqsClient, AmazonS3 s3) {
        var extendedClientConfiguration = new ExtendedClientConfiguration().withPayloadSupportEnabled(s3, bucketName, true);
        return new AmazonSQSExtendedClient(sqsClient, extendedClientConfiguration);
    }
}