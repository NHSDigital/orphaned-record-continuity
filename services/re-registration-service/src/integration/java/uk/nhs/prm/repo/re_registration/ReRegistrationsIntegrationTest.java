package uk.nhs.prm.repo.re_registration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.CreateQueueRequest;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import uk.nhs.prm.repo.re_registration.infra.LocalStackAwsConfig;
import uk.nhs.prm.repo.re_registration.logging.TestLogAppender;
import uk.nhs.prm.repo.re_registration.message_publishers.ReRegistrationAuditPublisher;
import uk.nhs.prm.repo.re_registration.model.ReRegistrationEvent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest()
@ActiveProfiles("test")
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = LocalStackAwsConfig.class)
public class ReRegistrationsIntegrationTest {

    @Autowired
    private SqsClient sqs;

    @Autowired
    ReRegistrationAuditPublisher publisher;

    @Value("${aws.reRegistrationsQueueName}")
    private String reRegistrationsQueueName;

    private String getReRegistrationsEvent() {
        return new ReRegistrationEvent("1234567890", "ABC123", UUID.randomUUID().toString(), "2017-11-01T15:00:33+00:00").toJsonString();
    }

    private void createQueue(String queueName) {
        sqs.createQueue(CreateQueueRequest.builder()
                .queueName(queueName)
                .build());
    }

    @Test
    void shouldReceiveAndLogAndAcknowledgeReRegistrationsEvent() {
        createQueue(reRegistrationsQueueName);

        var logAppender = TestLogAppender.addTestLogAppender();
        var eventMessage = getReRegistrationsEvent();

        sendMessage(reRegistrationsQueueName, eventMessage);

        await().atMost(10, TimeUnit.SECONDS).pollInterval(1, TimeUnit.SECONDS).untilAsserted(() -> {
            var receiveLog = logAppender.findLoggedEvent("RECEIVED");
            assertThat(receiveLog).isNotNull();
        });

        var messages = receiveMessages(reRegistrationsQueueName);
        assertThat(messages).isEmpty();
    }

    private void sendMessage(String queueName, String messageBody) {
        var queueUrl = getQueueUrl(queueName);
        sqs.sendMessage(builder -> builder.queueUrl(queueUrl).messageBody(messageBody));
    }

    private String getQueueUrl(String queueName) {
        return sqs.getQueueUrl(builder -> builder.queueName(queueName)).queueUrl();
    }

    private List<Message> receiveMessages(String queueName) {
        String queueUrl = getQueueUrl(queueName);
        var receiveMessageRequest = ReceiveMessageRequest.builder()
                .queueUrl(queueUrl)
                .build();
        return sqs.receiveMessage(receiveMessageRequest).messages();
    }
}
