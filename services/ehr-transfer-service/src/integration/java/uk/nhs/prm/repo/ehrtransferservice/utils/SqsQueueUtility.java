package uk.nhs.prm.repo.ehrtransferservice.utils;

import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.PurgeQueueRequest;
import software.amazon.awssdk.services.sqs.model.GetQueueUrlRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public final class SqsQueueUtility {
    private final SqsClient sqsClient;
    private static final Logger LOGGER = LogManager.getLogger(SqsQueueUtility.class);

    @Autowired
    public SqsQueueUtility(SqsClient sqsClient) {
        this.sqsClient = sqsClient;
    }

    public void purgeQueue(String queueName) {
        final String queueUrl = getQueueUrl(queueName);
        final PurgeQueueRequest request = PurgeQueueRequest.builder()
                .queueUrl(queueUrl)
                .build();
        sqsClient.purgeQueue(request);
        LOGGER.info("Successfully purged queue - {}", queueUrl);
    }

    public void sendSqsMessage(String message, String queueName) {
        final String queueUrl = getQueueUrl(queueName);
        SendMessageRequest request = SendMessageRequest.builder()
                .queueUrl(queueUrl)
                .messageBody(message)
                .build();
        sqsClient.sendMessage(request);
        LOGGER.info("Message sent successfully to {} SQS queue", queueName);
    }

    private String getQueueUrl(String queueName) {
        GetQueueUrlRequest queueUrlRequest = GetQueueUrlRequest.builder()
                .queueName(queueName)
                .build();
        return sqsClient.getQueueUrl(queueUrlRequest).queueUrl();
    }
}