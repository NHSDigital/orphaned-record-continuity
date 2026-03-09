package uk.nhs.prm.deductions.nemseventprocessor.metrics;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.cloudwatch.CloudWatchClient;
import software.amazon.awssdk.services.cloudwatch.model.Dimension;
import software.amazon.awssdk.services.cloudwatch.model.MetricDatum;
import software.amazon.awssdk.services.cloudwatch.model.PutMetricDataRequest;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;

/**
 * TODO - THIS SHOULD BE REMOVED
 *  This class is only used to publish health check metrics for the service's connection to CloudWatch.
 *  We think this is redundant, however, when we've tried to remove health checks from other services, pipelines have
 *  failed. The health checks are quite deeply rooted in the codebase. For now, we're leaving this in place.
 */

@Component
public class MetricPublisher {
    public CloudWatchClient cloudWatchClient;
    private final AppConfig config;

    @Autowired
    public MetricPublisher(CloudWatchClient cloudWatchClient, AppConfig config) {
        this.cloudWatchClient = cloudWatchClient;
        this.config = config;
    }

    public void publishMetric(String metricName, Double healthValue) {
        ArrayList<Dimension> dimensions = new ArrayList<>();
        dimensions.add(Dimension
                .builder()
                .name("Environment")
                .value(config.environment())
                .build());

        MetricDatum datum = MetricDatum
                .builder()
                .metricName(metricName)
                .value(healthValue)
                .timestamp(awsCompatibleNow())
                .dimensions(dimensions)
                .build();

        publish(datum);
    }

    private void publish(MetricDatum datum) {
        PutMetricDataRequest request =
                PutMetricDataRequest
                        .builder()
                        .namespace("NemsEventProcessor")
                        .metricData(datum)
                        .build();

        cloudWatchClient.putMetricData(request);
    }

    // why? here's why: https://forums.aws.amazon.com/thread.jspa?threadID=328321
    private Instant awsCompatibleNow() {
        return Instant.parse(ZonedDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MILLIS).format(DateTimeFormatter.ISO_INSTANT));
    }
}
