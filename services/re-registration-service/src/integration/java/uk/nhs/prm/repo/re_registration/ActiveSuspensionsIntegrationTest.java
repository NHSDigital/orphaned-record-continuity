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
import uk.nhs.prm.repo.re_registration.data.ActiveSuspensionsDb;
import uk.nhs.prm.repo.re_registration.infra.LocalStackAwsConfig;
import uk.nhs.prm.repo.re_registration.model.ActiveSuspensionsMessage;
import uk.nhs.prm.repo.re_registration.model.ReRegistrationEvent;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertNull;

@SpringBootTest()
@ActiveProfiles("test")
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = LocalStackAwsConfig.class)
public class ActiveSuspensionsIntegrationTest {
    @Autowired
    private SqsClient sqs;

    @Autowired
    ActiveSuspensionsDb activeSuspensionsDb;

    @Value("${aws.activeSuspensionsQueueName}")
    private String activeSuspensionsQueueName;

    @Value("${aws.reRegistrationsQueueName}")
    private String reRegistrationsQueueName;

    private static final String NHS_NUMBER = "0987654321";
    private static final String PREVIOUS_ODS_CODE = "OLD001";
    private static final String NEWLY_REGISTERED_ODS_CODE = "NEW001";
    private static final String NEMS_LAST_UPDATED_DATE = "2022-09-01T15:00:33+00:00";
    private static final String RE_REGISTRATIONS_LAST_UPDATED_DATE = "2022-09-02T15:00:33+00:00";
    private static final String NEMS_MESSAGE_ID = String.valueOf(UUID.randomUUID());


    @Test
    void shouldSaveMessageFromActiveSuspensionsQueueInDb() {
        sendMessage(activeSuspensionsQueueName, getActiveSuspensionsMessage());

        await().atMost(20, TimeUnit.SECONDS).pollInterval(1, TimeUnit.SECONDS).untilAsserted(() -> {
            var activeSuspensionsData = activeSuspensionsDb.getByNhsNumber(NHS_NUMBER);

            assertThat(activeSuspensionsData.getNhsNumber()).isEqualTo(NHS_NUMBER);
            assertThat(activeSuspensionsData.getPreviousOdsCode()).isEqualTo(PREVIOUS_ODS_CODE);
            assertThat(activeSuspensionsData.getNemsLastUpdatedDate()).isEqualTo(NEMS_LAST_UPDATED_DATE);
        });

    }

    @Test
    void shouldDeleteRecordFromActiveSuspensionsDbWhenRecordFoundByNhsNumber() {
        sendMessage(reRegistrationsQueueName, getReRegistrationEvent());

        await().atMost(20, TimeUnit.SECONDS).pollInterval(1, TimeUnit.SECONDS).untilAsserted(() -> {
            activeSuspensionsDb.deleteByNhsNumber(NHS_NUMBER);
            var activeSuspensionsData = activeSuspensionsDb.getByNhsNumber(NHS_NUMBER);

            assertNull(activeSuspensionsData);

        });
    }

    private void sendMessage(String queueName, String messageBody) {
        var queueUrl = sqs.getQueueUrl(builder -> builder.queueName(queueName)).queueUrl();
        sqs.sendMessage(builder -> builder.queueUrl(queueUrl).messageBody(messageBody));
    }

    private String getActiveSuspensionsMessage() {
        return new ActiveSuspensionsMessage(NHS_NUMBER, PREVIOUS_ODS_CODE, NEMS_LAST_UPDATED_DATE).toJsonString();
    }

    private String getReRegistrationEvent() {
        return new ReRegistrationEvent(NHS_NUMBER, NEWLY_REGISTERED_ODS_CODE, NEMS_MESSAGE_ID, RE_REGISTRATIONS_LAST_UPDATED_DATE).toJsonString();
    }

}
