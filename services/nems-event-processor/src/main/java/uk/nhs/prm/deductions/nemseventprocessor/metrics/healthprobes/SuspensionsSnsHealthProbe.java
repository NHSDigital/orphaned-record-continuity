package uk.nhs.prm.deductions.nemseventprocessor.metrics.healthprobes;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.GetTopicAttributesRequest;
import uk.nhs.prm.deductions.nemseventprocessor.metrics.AppConfig;

/**
 * TODO - THIS SHOULD BE REMOVED
 *  This class is only used to publish health check metrics for the service's connection to SNS.
 *  We think this is redundant, however, when we've tried to remove health checks from other services, pipelines have
 *  failed. The health checks are quite deeply rooted in the codebase. For now, we're leaving this in place.
 */

@Slf4j
@Component
public class SuspensionsSnsHealthProbe implements HealthProbe {
    private final AppConfig config;
    private final SnsClient snsClient;

    @Autowired
    public SuspensionsSnsHealthProbe(AppConfig config, SnsClient snsClient) {
        this.config = config;
        this.snsClient = snsClient;
    }

    @Override
    public boolean isHealthy() {
        try {
            snsClient.getTopicAttributes(GetTopicAttributesRequest.builder().topicArn(config.suspensionsSnsTopicArn()).build());
            return true;
        } catch (RuntimeException exception) {
            log.info("Failed to query SNS topic: " + config.suspensionsSnsTopicArn(), exception);
            return false;
        }
    }
}
