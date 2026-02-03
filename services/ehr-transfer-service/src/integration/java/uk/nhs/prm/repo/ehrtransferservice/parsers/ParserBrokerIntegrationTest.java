package uk.nhs.prm.repo.ehrtransferservice.parsers;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.annotation.DirtiesContext;
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
import uk.nhs.prm.repo.ehrtransferservice.services.PresignedUrl;
import uk.nhs.prm.repo.ehrtransferservice.services.ehr_repo.EhrRepoClient;
import uk.nhs.prm.repo.ehrtransferservice.services.ehr_repo.EhrRepoService;
import uk.nhs.prm.repo.ehrtransferservice.utils.TransferTrackerDbUtility;

import java.io.IOException;
import java.net.URL;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
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

    @MockBean
    private EhrRepoService ehrRepoService;

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

    @BeforeEach
    void configureMocks() throws Exception {
        purgeQueue(largeMessageFragmentsObservabilityQueueName);
        purgeQueue(smallEhrObservabilityQueueName);
    }

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
        final String fragmentsQueueUrl = getQueueUrl(largeMessageFragmentsObservabilityQueueName);

        // when
        try {
            transferService.createConversationOrResetForRetry(repoIncomingEvent);
        } catch (ConversationIneligibleForRetryException e) {
            fail("Conversation should be new and eligible.");
        }

        inboundQueueFromMhs.sendMessage(fragmentMessageBody);

        // then
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            List<Message> receivedMessageHolder = checkMessageInRelatedQueue(fragmentsQueueUrl);
            Assertions.assertTrue(receivedMessageHolder.getFirst().body().contains(fragmentMessageBody));
            Assertions.assertTrue(receivedMessageHolder.getFirst().messageAttributes().containsKey("traceId"));
            Assertions.assertTrue(receivedMessageHolder.getFirst().messageAttributes().containsKey("conversationId"));
        });
    }

    @Test
    void shouldPublishEhrCoreToSmallEhrObservabilityQueue() throws IOException {
        // given
        final RepoIncomingEvent repoIncomingEvent = createDefaultRepoIncomingEvent(EHR_CORE_INBOUND_CONVERSATION_ID);
        final String ehrCoreMessageBody = getTestDataAsString("small-ehr");
        final SimpleAmqpQueue inboundQueueFromMhs = new SimpleAmqpQueue(inboundQueue);
        final String smallEhrObservabilityQueueUrl = getQueueUrl(smallEhrObservabilityQueueName);

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
            List<Message> receivedMessageHolder = checkMessageInRelatedQueue(smallEhrObservabilityQueueUrl);
            Assertions.assertTrue(receivedMessageHolder.getFirst().body().contains(ehrCoreMessageBody));
            Assertions.assertTrue(receivedMessageHolder.getFirst().messageAttributes().containsKey("traceId"));
            Assertions.assertTrue(receivedMessageHolder.getFirst().messageAttributes().containsKey("conversationId"));
        });
    }

    @Test
    void shouldPassCorrelationIdToBeSetAsTraceId() throws IOException {
        // given
        final RepoIncomingEvent repoIncomingEvent = createDefaultRepoIncomingEvent(EHR_CORE_INBOUND_CONVERSATION_ID);
        final String ehrCoreMessageBody = getTestDataAsString("small-ehr");
        final SimpleAmqpQueue inboundQueueFromMhs = new SimpleAmqpQueue(inboundQueue);
        final String smallEhrObservabilityQueueUrl = getQueueUrl(smallEhrObservabilityQueueName);
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
            List<Message> receivedMessageHolder = checkMessageInRelatedQueue(smallEhrObservabilityQueueUrl);
            Message message = receivedMessageHolder.getFirst();
            Assertions.assertTrue(message.body().contains(ehrCoreMessageBody));
            Assertions.assertTrue(message.messageAttributes().containsKey("traceId"));
            Assertions.assertEquals(message.messageAttributes().get("traceId").stringValue(), correlationId);
        });
    }

    @Test
    void shouldPublishInvalidMessageToDlq() {
        String wrongMessage = "something wrong";

        SimpleAmqpQueue inboundQueueFromMhs = new SimpleAmqpQueue(inboundQueue);
        inboundQueueFromMhs.sendMessage(wrongMessage);

        String parsingDqlQueueUrl = getQueueUrl(parsingDlqQueueName);

        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            List<Message> receivedMessageHolder = checkMessageInRelatedQueue(parsingDqlQueueUrl);
            Assertions.assertTrue(receivedMessageHolder.getFirst().body().contains(wrongMessage));
        });
    }

    @Test
    void shouldPublishUnprocessableMessageToDlq() {
        String unprocessableMessage = "NO_ACTION:UNPROCESSABLE_MESSAGE_BODY";
        SimpleAmqpQueue inboundQueueFromMhs = new SimpleAmqpQueue(inboundQueue);
        inboundQueueFromMhs.sendUnprocessableAmqpMessage();

        String parsingDqlQueueUrl = getQueueUrl(parsingDlqQueueName);

        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            List<Message> receivedMessageHolder = checkMessageInRelatedQueue(parsingDqlQueueUrl);
            Assertions.assertTrue(receivedMessageHolder.getFirst().body().contains(unprocessableMessage));
        });
    }

    private List<Message> checkMessageInRelatedQueue(String queueUrl) {
        System.out.println("checking sqs queue: " + queueUrl);

        ReceiveMessageRequest requestForMessagesWithAttributes = ReceiveMessageRequest.builder()
                .queueUrl(queueUrl)
                .messageAttributeNames("All")
                .build();

        List<Message> messages = sqs.receiveMessage(requestForMessagesWithAttributes).messages();

        System.out.println("messages in checkMessageInRelatedQueue: " + messages);
        assertThat(messages).hasSize(1);
        return messages;
    }

    private void purgeQueue(String queueName) {
        String queueUrl = getQueueUrl(queueName);
        sqs.purgeQueue(PurgeQueueRequest.builder().queueUrl(queueUrl).build());

    }
    
    private String getQueueUrl(String queueName) {
        return sqs.getQueueUrl(builder -> builder.queueName(queueName)).queueUrl();
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