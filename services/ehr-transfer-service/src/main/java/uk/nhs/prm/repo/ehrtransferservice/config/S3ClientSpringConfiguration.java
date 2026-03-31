package uk.nhs.prm.repo.ehrtransferservice.config;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

@Configuration
@RequiredArgsConstructor
public class S3ClientSpringConfiguration {
    @Value("${aws.region}")
    private String awsRegion;

    private AwsCredentialsProvider awsCredentialsProvider;

    @Bean
    public S3Client amazonS3Client() {
        Region region = Region.of(awsRegion);
        return S3Client.builder()
                .region(region)
                .credentialsProvider(awsCredentialsProvider)
                .build();
    }

    @Bean
    public AmazonS3 amazonS3ClientForSnsExtended() {
        return AmazonS3ClientBuilder.standard().withRegion(awsRegion).build();
    }
}