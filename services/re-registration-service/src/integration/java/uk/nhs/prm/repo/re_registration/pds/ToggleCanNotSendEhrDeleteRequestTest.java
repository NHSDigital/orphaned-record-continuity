package uk.nhs.prm.repo.re_registration.pds;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.PurgeQueueRequest;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import uk.nhs.prm.repo.re_registration.data.ActiveSuspensionsDb;
import uk.nhs.prm.repo.re_registration.infra.LocalStackAwsConfig;
import uk.nhs.prm.repo.re_registration.model.ActiveSuspensionsMessage;
import uk.nhs.prm.repo.re_registration.model.ReRegistrationEvent;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest(properties = "toggle.canSendDeleteEhrRequest=false")
@ActiveProfiles("test")
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = LocalStackAwsConfig.class)
@DirtiesContext
public class ToggleCanNotSendEhrDeleteRequestTest {

    public static final String NHS_NUMBER = "1234567890";
    public static final String STATUS_FOR_RECEIVED_REGISTRATION_EVENT = "NO_ACTION:RE_REGISTRATION_EVENT_RECEIVED";

    @Autowired
    private SqsClient sqs;

    @Autowired
    private ActiveSuspensionsDb activeSuspensionsDb;

    @Value("${aws.reRegistrationsQueueName}")
    private String reRegistrationsQueueName;

    @Value("${aws.reRegistrationsAuditQueueName}")
    private String reRegistrationsAuditQueueName;

    private String reRegistrationsQueueUrl;
    private String reRegistrationsAuditUrl;
    private String nemsMessageId = "nemsMessageId";

    @BeforeEach
    public void setUp() {
        reRegistrationsQueueUrl = sqs.getQueueUrl(builder -> builder.queueName(reRegistrationsQueueName)).queueUrl();
        reRegistrationsAuditUrl = sqs.getQueueUrl(builder -> builder.queueName(reRegistrationsAuditQueueName)).queueUrl();
    }

    @AfterEach
    public void tearDown() {
        sqs.purgeQueue(PurgeQueueRequest.builder().queueUrl(reRegistrationsAuditUrl).build());
    }

    @Test
    void shouldSendToAuditQueueAndNotProcessMessageWhenToggleIsFalseAndActiveSuspensionIsFound() {
        var activeSuspensionsMessage = new ActiveSuspensionsMessage(NHS_NUMBER, "previous-ods-code", "2017-11-01T15:00:33+00:00");
        activeSuspensionsDb.save(activeSuspensionsMessage);
        sqs.sendMessage(builder -> builder.queueUrl(reRegistrationsQueueUrl).messageBody(getReRegistrationEvent().toJsonString()));

        await().atMost(20, TimeUnit.SECONDS).untilAsserted(() -> {
            String messageBody = checkMessageInRelatedQueue(reRegistrationsAuditUrl).get(0).body();
            assertThat(messageBody).contains(STATUS_FOR_RECEIVED_REGISTRATION_EVENT);
            assertThat(messageBody).contains(nemsMessageId);
        });
    }

    @Test
    void shouldSendUnknownMessageToAuditQueueAndNotProcessMessageWhenToggleIsFalseAndActiveSuspensionNotIsFound() {
        sqs.sendMessage(builder -> builder.queueUrl(reRegistrationsQueueUrl).messageBody(getReRegistrationEvent().toJsonString()));

        await().atMost(20, TimeUnit.SECONDS).untilAsserted(() -> {
            String messageBody = checkMessageInRelatedQueue(reRegistrationsAuditUrl).get(0).body();
            assertThat(messageBody).contains("NO_ACTION:UNKNOWN_REGISTRATION_EVENT_RECEIVED");
        });
    }

    private ReRegistrationEvent getReRegistrationEvent() {
        return new ReRegistrationEvent(NHS_NUMBER, "ABC123", nemsMessageId, "2017-11-01T15:00:33+00:00");
    }

    private List<Message> checkMessageInRelatedQueue(String queueUrl) {
        System.out.println("checking sqs queue: " + queueUrl);

        ReceiveMessageRequest requestForMessagesWithAttributes = ReceiveMessageRequest.builder()
                .queueUrl(queueUrl)
                .messageAttributeNames("traceId")
                .build();

        List<Message> messages = sqs.receiveMessage(requestForMessagesWithAttributes).messages();
        System.out.printf("Found %s messages on queue: %s%n", messages.size(), queueUrl);
        assertThat(messages).hasSize(1);
        return messages;
    }
}
