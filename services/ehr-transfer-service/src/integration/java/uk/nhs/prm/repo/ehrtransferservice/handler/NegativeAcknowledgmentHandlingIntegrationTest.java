package uk.nhs.prm.repo.ehrtransferservice.handler;

import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.PurgeQueueRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import uk.nhs.prm.repo.ehrtransferservice.activemq.ForceXercesParserExtension;
import uk.nhs.prm.repo.ehrtransferservice.activemq.SimpleAmqpQueue;
import uk.nhs.prm.repo.ehrtransferservice.configuration.LocalStackAwsConfig;
import uk.nhs.prm.repo.ehrtransferservice.database.TransferService;
import uk.nhs.prm.repo.ehrtransferservice.exceptions.ConversationIneligibleForRetryException;
import uk.nhs.prm.repo.ehrtransferservice.repo_incoming.RepoIncomingEvent;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.fail;
import static uk.nhs.prm.repo.ehrtransferservice.database.enumeration.ConversationTransferStatus.INBOUND_FAILED;
import static uk.nhs.prm.repo.ehrtransferservice.utils.TestDataLoaderUtility.getTestDataAsString;

@SpringBootTest
@ActiveProfiles("test")
@ExtendWith(SpringExtension.class)
@ExtendWith(ForceXercesParserExtension.class)
@ContextConfiguration(classes = LocalStackAwsConfig.class)
public class NegativeAcknowledgmentHandlingIntegrationTest {
    @Autowired
    TransferService transferService;

    @Autowired
    private SqsClient sqs;

    @Value("${activemq.inboundQueue}")
    private String inboundQueue;

    @Value("${aws.nackQueueName}")
    private String nackInternalQueueName;

    private static final String NHS_NUMBER = "9798547485";
    private static final String SOURCE_GP = "B45744";
    private static final String NEMS_MESSAGE_ID = "2d74a113-1076-4c63-91bc-e50d232b6a79";
    private static final String CONVERSATION_ID = "13962CB7-6D46-4986-BDB4-3201BB25F1F7";

    @BeforeEach
    public void tearDown() {
        purgeQueue(nackInternalQueueName);
    }

    @Test
    void shouldUpdateDbWithNackErrorCodeWhenReceivedOnInternalQueue() throws IOException {
        // given
        final String negativeAck = getTestDataAsString("negative-acknowledgement");
        final UUID inboundConversationId = createConversationRecord();
        final SimpleAmqpQueue inboundQueueFromMhs = new SimpleAmqpQueue(inboundQueue);

        // when
        inboundQueueFromMhs.sendMessage(negativeAck);

        // then
        await().pollInterval(200, TimeUnit.MILLISECONDS)
                .atMost(25, TimeUnit.SECONDS).untilAsserted(() ->
                assertThat(transferService.getConversationTransferStatus(inboundConversationId)).isEqualTo(INBOUND_FAILED)
        );
    }

    private UUID createConversationRecord() {
        RepoIncomingEvent event = new RepoIncomingEvent(
            NHS_NUMBER,
            SOURCE_GP,
            NEMS_MESSAGE_ID,
            CONVERSATION_ID
        );

        try {
            transferService.createConversationOrResetForRetry(event);
        } catch (ConversationIneligibleForRetryException e) {
            fail("Conversation should be new and eligible.");
        }

        return UUID.fromString(event.getConversationId());
    }

    private void purgeQueue(String queueName) {
        final String queueUrl = sqs.getQueueUrl(builder -> builder.queueName(queueName)).queueUrl();
        sqs.purgeQueue(PurgeQueueRequest.builder().queueUrl(queueUrl).build());
    }
}