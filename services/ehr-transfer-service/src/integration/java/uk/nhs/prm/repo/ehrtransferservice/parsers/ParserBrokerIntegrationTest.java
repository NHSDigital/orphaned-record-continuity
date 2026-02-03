package uk.nhs.prm.repo.ehrtransferservice.parsers;

import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.PurgeQueueRequest;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
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
import uk.nhs.prm.repo.ehrtransferservice.utils.TransferTrackerDbUtility;

import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.fail;
import static uk.nhs.prm.repo.ehrtransferservice.database.enumeration.ConversationTransferStatus.INBOUND_REQUEST_SENT;
import static uk.nhs.prm.repo.ehrtransferservice.database.enumeration.Layer.CONVERSATION;
import static uk.nhs.prm.repo.ehrtransferservice.utils.TestDataLoaderUtility.getTestDataAsString;

@SpringBootTest
@ActiveProfiles("test")
@ExtendWith(SpringExtension.class)
@ExtendWith(ForceXercesParserExtension.class)
@ContextConfiguration(classes = LocalStackAwsConfig.class)
public class ParserBrokerIntegrationTest {
    @Autowired
    private SqsClient sqs;

    @Autowired
    private TransferService transferService;

    @Autowired
    TransferTrackerDbUtility transferTrackerDbUtility;

    @Value("${activemq.inboundQueue}")
    private String inboundQueue;

    @Value("${aws.largeMessageFragmentsObservabilityQueueName}")
    private String largeMessageFragmentsObservabilityQueueName;

    @Value("${aws.smallEhrObservabilityQueueName}")
    private String smallEhrObservabilityQueueName;

    @Value("${aws.largeEhrQueueName}")
    private String largeEhrQueueName;

    @Value("${aws.parsingDlqQueueName}")
    private String parsingDlqQueueName;

    @Value("${aws.ehrCompleteQueueName}")
    private String ehrCompleteQueueName;

    @Value("${aws.ehrInUnhandledObservabilityQueueName}")
    private String ehrInUnhandledObservabilityQueueName;

    private static final UUID COPC_INBOUND_CONVERSATION_ID = UUID.fromString("ff1457fb-4f58-4870-8d90-24d9c3ef8b91");
    private static final UUID EHR_CORE_INBOUND_CONVERSATION_ID = UUID.fromString("ff27abc3-9730-40f7-ba82-382152e6b90a");
    private static final String SOURCE_GP = "A74154";
    private static final UUID NEMS_MESSAGE_ID = UUID.fromString("ad9246ce-b337-4ba9-973f-e1284e1f79c7");
    private static final String NHS_NUMBER = "9896589658";

    @AfterEach
    public void tearDown() {
        purgeQueue(largeMessageFragmentsObservabilityQueueName);
        purgeQueue(smallEhrObservabilityQueueName);
        purgeQueue(largeEhrQueueName);
        purgeQueue(parsingDlqQueueName);
        purgeQueue(ehrCompleteQueueName);
        purgeQueue(ehrInUnhandledObservabilityQueueName);

        if(transferService.isInboundConversationPresent(COPC_INBOUND_CONVERSATION_ID)) {
            transferTrackerDbUtility.deleteItem(COPC_INBOUND_CONVERSATION_ID, CONVERSATION);
        }

        if(transferService.isInboundConversationPresent(EHR_CORE_INBOUND_CONVERSATION_ID)) {
            transferTrackerDbUtility.deleteItem(EHR_CORE_INBOUND_CONVERSATION_ID, CONVERSATION);
        }
    }

    @Test
    void shouldPublishCopcMessageToLargeMessageFragmentTopic() throws IOException {
        // given
        final RepoIncomingEvent repoIncomingEvent = createDefaultRepoIncomingEvent(COPC_INBOUND_CONVERSATION_ID);
        final String fragmentMessageBody = getTestDataAsString("large-ehr-fragment-with-ref");
        final SimpleAmqpQueue inboundQueueFromMhs = new SimpleAmqpQueue(inboundQueue);
        final String fragmentsQueueUrl = sqs.getQueueUrl(largeMessageFragmentsObservabilityQueueName).getQueueUrl();

        // when
        try {
            transferService.createConversationOrResetForRetry(repoIncomingEvent);
        } catch (ConversationIneligibleForRetryException e) {
            fail("Conversation should be new and eligible.");
        }

        inboundQueueFromMhs.sendMessage(fragmentMessageBody);

        // then
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            var receivedMessageHolder = checkMessageInRelatedQueue(fragmentsQueueUrl);
            Assertions.assertTrue(receivedMessageHolder.get(0).getBody().contains(fragmentMessageBody));
            Assertions.assertTrue(receivedMessageHolder.get(0).getMessageAttributes().containsKey("traceId"));
            Assertions.assertTrue(receivedMessageHolder.get(0).getMessageAttributes().containsKey("conversationId"));
        });
    }

    @Test
    void shouldPublishEhrCoreToSmallEhrObservabilityQueue() throws IOException {
        // given
        final RepoIncomingEvent repoIncomingEvent = createDefaultRepoIncomingEvent(EHR_CORE_INBOUND_CONVERSATION_ID);
        final String ehrCoreMessageBody = getTestDataAsString("small-ehr");
        final SimpleAmqpQueue inboundQueueFromMhs = new SimpleAmqpQueue(inboundQueue);
        final String smallEhrObservabilityQueueUrl = sqs.getQueueUrl(smallEhrObservabilityQueueName).getQueueUrl();

        // when
        try {
            transferService.createConversationOrResetForRetry(repoIncomingEvent);
        } catch (ConversationIneligibleForRetryException e) {
            fail("Conversation should be new and eligible.");
        }

        transferService.updateConversationTransferStatus(
            EHR_CORE_INBOUND_CONVERSATION_ID,
            INBOUND_REQUEST_SENT
        );

        inboundQueueFromMhs.sendMessage(ehrCoreMessageBody);

        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            var receivedMessageHolder = checkMessageInRelatedQueue(smallEhrObservabilityQueueUrl);
            Assertions.assertTrue(receivedMessageHolder.get(0).getBody().contains(ehrCoreMessageBody));
            Assertions.assertTrue(receivedMessageHolder.get(0).getMessageAttributes().containsKey("traceId"));
            Assertions.assertTrue(receivedMessageHolder.get(0).getMessageAttributes().containsKey("conversationId"));
        });
    }

    @Test
    void shouldPassCorrelationIdToBeSetAsTraceId() throws IOException {
        // given
        final RepoIncomingEvent repoIncomingEvent = createDefaultRepoIncomingEvent(EHR_CORE_INBOUND_CONVERSATION_ID);
        final String ehrCoreMessageBody = getTestDataAsString("small-ehr");
        final SimpleAmqpQueue inboundQueueFromMhs = new SimpleAmqpQueue(inboundQueue);
        final String smallEhrObservabilityQueueUrl = sqs.getQueueUrl(smallEhrObservabilityQueueName).getQueueUrl();
        final String correlationId = UUID.randomUUID().toString();

        // when
        try {
            transferService.createConversationOrResetForRetry(repoIncomingEvent);
        } catch (ConversationIneligibleForRetryException e) {
            fail("Conversation should be new and eligible.");
        }

        transferService.updateConversationTransferStatus(
            EHR_CORE_INBOUND_CONVERSATION_ID,
            INBOUND_REQUEST_SENT
        );

        inboundQueueFromMhs.sendMessage(ehrCoreMessageBody, correlationId);

        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            var receivedMessageHolder = checkMessageInRelatedQueue(smallEhrObservabilityQueueUrl);
            var message = receivedMessageHolder.get(0);
            Assertions.assertTrue(message.getBody().contains(ehrCoreMessageBody));
            Assertions.assertTrue(message.getMessageAttributes().containsKey("traceId"));
            Assertions.assertEquals(message.getMessageAttributes().get("traceId").getStringValue(), correlationId);
        });
    }

    @Test
    void shouldPublishInvalidMessageToDlq() {
        var wrongMessage = "something wrong";

        var inboundQueueFromMhs = new SimpleAmqpQueue(inboundQueue);
        inboundQueueFromMhs.sendMessage(wrongMessage);

        var parsingDqlQueueUrl = sqs.getQueueUrl(parsingDlqQueueName).getQueueUrl();

        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            var receivedMessageHolder = checkMessageInRelatedQueue(parsingDqlQueueUrl);
            Assertions.assertTrue(receivedMessageHolder.get(0).getBody().contains(wrongMessage));
        });
    }

    @Test
    void shouldPublishUnprocessableMessageToDlq() {
        var unprocessableMessage = "NO_ACTION:UNPROCESSABLE_MESSAGE_BODY";
        var inboundQueueFromMhs = new SimpleAmqpQueue(inboundQueue);
        inboundQueueFromMhs.sendUnprocessableAmqpMessage();

        var parsingDqlQueueUrl = sqs.getQueueUrl(parsingDlqQueueName).getQueueUrl();

        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            var receivedMessageHolder = checkMessageInRelatedQueue(parsingDqlQueueUrl);
            Assertions.assertTrue(receivedMessageHolder.get(0).getBody().contains(unprocessableMessage));
        });
    }

    private List<Message> checkMessageInRelatedQueue(String queueUrl) {
        System.out.println("checking sqs queue: " + queueUrl);

        var requestForMessagesWithAttributes
                = new ReceiveMessageRequest().withQueueUrl(queueUrl)
                .withMessageAttributeNames("All");
        var messages = sqs.receiveMessage(requestForMessagesWithAttributes).getMessages();
        System.out.println("messages in checkMessageInRelatedQueue: " + messages);
        assertThat(messages).hasSize(1);
        return messages;
    }

    private void purgeQueue(String queueName) {
        var queueUrl = sqs.getQueueUrl(queueName).getQueueUrl();
        sqs.purgeQueue(new PurgeQueueRequest(queueUrl));
    }

    private RepoIncomingEvent createDefaultRepoIncomingEvent(UUID inboundConversationId) {
        return RepoIncomingEvent.builder()
            .conversationId(inboundConversationId.toString().toUpperCase())
            .nhsNumber(NHS_NUMBER)
            .sourceGp(SOURCE_GP)
            .nemsMessageId(NEMS_MESSAGE_ID.toString())
            .build();
    }
}