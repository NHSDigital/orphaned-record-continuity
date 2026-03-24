package uk.nhs.prm.deductions.nemseventprocessor.metrics.healthprobes;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.GetQueueAttributesRequest;
import software.amazon.awssdk.services.sqs.model.GetQueueUrlRequest;
import uk.nhs.prm.deductions.nemseventprocessor.metrics.AppConfig;

/**
 * TODO - THIS SHOULD BE REMOVED
 *  This class is only used to publish health check metrics for the service's connection to SQS.
 *  We think this is redundant, however, when we've tried to remove health checks from other services, pipelines have
 *  failed. The health checks are quite deeply rooted in the codebase. For now, we're leaving this in place.
 */

@Component
@Slf4j
public class SqsHealthProbe implements HealthProbe {
    private final AppConfig config;
    private final SqsClient sqsClient;

    @Autowired
    public SqsHealthProbe(AppConfig config, SqsClient sqsClient) {
        this.config = config;
        this.sqsClient = sqsClient;
    }

    @Override
    public boolean isHealthy() {
        try {
            String queueUrl = sqsClient.getQueueUrl(GetQueueUrlRequest.builder().queueName(queueName()).build()).queueUrl();
            sqsClient.getQueueAttributes(GetQueueAttributesRequest.builder().queueUrl(queueUrl).build());
            return true;
        } catch (RuntimeException exception) {
            log.info("Failed to query SQS queue: " + queueName(), exception);
            return false;
        }
    }

    private String queueName() {
        return config.incomingQueueName();
    }
}
