package uk.nhs.prm.repo.suspension.service.suspensionsevents;

import com.github.tomakehurst.wiremock.WireMockServer;
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
import uk.nhs.prm.repo.suspension.service.infra.LocalStackAwsConfig;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest()
@ActiveProfiles("test")
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = LocalStackAwsConfig.class)
@DirtiesContext
public class SuspensionsIntegrationTest {

    @Autowired
    private SqsClient sqs;

    @Value("${aws.incomingQueueName}")
    private String suspensionsQueueName;

    @Value("${aws.notSuspendedQueueName}")
    private String notSuspendedQueueName;

    @Value("${aws.mofUpdatedQueueName}")
    private String mofUpdatedQueueName;

    @Value("${aws.eventOutOfOrderQueueName}")
    private String eventOutOfOrderQueueName;

    @Value("${aws.invalidSuspensionAuditQueueName}")
    private String invalidSuspensionAuditQueueName;

    @Value("${aws.invalidSuspensionQueueName}")
    private String invalidSuspensionQueueName;

    @Value("${aws.activeSuspensionsQueueName}")
    private String activeSuspensionsQueueName;

    private WireMockServer stubPdsAdaptor;

    private String suspensionQueueUrl;

    @BeforeEach
    public void setUp() {
        stubPdsAdaptor = initializeWebServer();
        configureFor("localhost", stubPdsAdaptor.port());
        suspensionQueueUrl = sqs.getQueueUrl(builder -> builder.queueName(suspensionsQueueName)).queueUrl();
        purgeQueue(suspensionQueueUrl);
    }


    @AfterEach
    public void tearDown() {
        stubPdsAdaptor.resetAll();
        stubPdsAdaptor.stop();
        suspensionQueueUrl = sqs.getQueueUrl(builder -> builder.queueName(suspensionsQueueName)).queueUrl();
        purgeQueue(suspensionQueueUrl);
    }

    private WireMockServer initializeWebServer() {
        final WireMockServer wireMockServer = new WireMockServer(8080);
        wireMockServer.start();
        return wireMockServer;
    }

    @Test
    void shouldSendSuspensionMessageToNotSuspendedSNSTopicIfNoLongerSuspendedInPDS() {
        var nhsNumber = Long.toString(System.currentTimeMillis());
        stubFor(get(urlMatching("/suspended-patient-status/" + nhsNumber))
                .withHeader("Authorization", matching("Basic c3VzcGVuc2lvbi1zZXJ2aWNlOiJ0ZXN0Ig=="))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody(getNotSuspendedResponseWith(nhsNumber))));

        var queueUrl = sqs.getQueueUrl(builder -> builder.queueName(suspensionsQueueName)).queueUrl();
        var notSuspendedQueueUrl = sqs.getQueueUrl(builder -> builder.queueName(notSuspendedQueueName)).queueUrl();
        sqs.sendMessage(builder -> builder.queueUrl(queueUrl).messageBody(getSuspensionEventWith(nhsNumber)));

        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            List<Message> receivedMessageHolder = checkMessageInRelatedQueue(notSuspendedQueueUrl);
            assertTrue(receivedMessageHolder.get(0).body().contains("NO_ACTION:NO_LONGER_SUSPENDED_ON_PDS"));
            assertTrue(receivedMessageHolder.get(0).body().contains("nemsMessageId"));
            assertTrue(receivedMessageHolder.get(0).messageAttributes().containsKey("traceId"));
        });
        purgeQueue(notSuspendedQueueUrl);
    }

    @Test
    void shouldUpdateManagingOrganisationAndSendMessageToMofUpdatedSNSTopicForSuspendedPatient() {
        var nhsNumber = Long.toString(System.currentTimeMillis());
        stubFor(get(urlMatching("/suspended-patient-status/" + nhsNumber))
                .withHeader("Authorization", matching("Basic c3VzcGVuc2lvbi1zZXJ2aWNlOiJ0ZXN0Ig=="))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody(getSuspendedResponseWith(nhsNumber))));
        stubFor(put(urlMatching("/suspended-patient-status/" + nhsNumber))
                .withHeader("Authorization", matching("Basic c3VzcGVuc2lvbi1zZXJ2aWNlOiJ0ZXN0Ig=="))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody(getSuspendedResponseWith(nhsNumber))));

        var queueUrl = sqs.getQueueUrl(builder -> builder.queueName(suspensionsQueueName)).queueUrl();
        var mofUpdatedQueueUrl = sqs.getQueueUrl(builder -> builder.queueName(mofUpdatedQueueName)).queueUrl();
        var activeSuspensionsQueueUrl = sqs.getQueueUrl(builder -> builder.queueName(activeSuspensionsQueueName)).queueUrl();
        sqs.sendMessage(builder -> builder.queueUrl(queueUrl).messageBody(getSuspensionEventWith(nhsNumber)));

        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            List<Message> receivedMessageHolder = checkMessageInRelatedQueue(mofUpdatedQueueUrl);

            assertTrue(receivedMessageHolder.get(0).body().contains("ACTION:UPDATED_MANAGING_ORGANISATION"));
            assertTrue(receivedMessageHolder.get(0).body().contains("TEST-NEMS-ID"));
            assertTrue(receivedMessageHolder.get(0).body().contains("B85612"));
            assertTrue(receivedMessageHolder.get(0).messageAttributes().containsKey("traceId"));
        });

        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            List<Message> receivedMessageHolder = checkMessageInRelatedQueue(activeSuspensionsQueueUrl);

            assertTrue(receivedMessageHolder.get(0).body().contains("nhsNumber"));
            assertTrue(receivedMessageHolder.get(0).body().contains("B85612"));
            assertTrue(receivedMessageHolder.get(0).body().contains("2017-11-01T15:00:33+00:00"));
            assertTrue(receivedMessageHolder.get(0).messageAttributes().containsKey("traceId"));
        });

        purgeQueue(mofUpdatedQueueUrl);
        purgeQueue(activeSuspensionsQueueUrl);
    }

    @Test
    void shouldPutEventOutOfOrderInRelevantQueues() {
        var nhsNumber = Long.toString(System.currentTimeMillis());

        stubFor(get(urlMatching("/suspended-patient-status/" + nhsNumber))
                .withHeader("Authorization", matching("Basic c3VzcGVuc2lvbi1zZXJ2aWNlOiJ0ZXN0Ig=="))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody(getSuspendedResponseWith(nhsNumber))));
        stubFor(put(urlMatching("/suspended-patient-status/" + nhsNumber))
                .withHeader("Authorization", matching("Basic c3VzcGVuc2lvbi1zZXJ2aWNlOiJ0ZXN0Ig=="))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody(getSuspendedResponseWith(nhsNumber))));

        var queueUrl = sqs.getQueueUrl(builder -> builder.queueName(suspensionsQueueName)).queueUrl();
        var mofUpdatedQueueUrl = sqs.getQueueUrl(builder -> builder.queueName(mofUpdatedQueueName)).queueUrl();
        var eventOutOfOrderQueue = sqs.getQueueUrl(builder -> builder.queueName(eventOutOfOrderQueueName)).queueUrl();

        var suspensionEvent = getSuspensionEventWith(nhsNumber);
        sqs.sendMessage(builder -> builder.queueUrl(queueUrl).messageBody(suspensionEvent));

        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            var receivedMessageHolder = checkMessageInRelatedQueue(mofUpdatedQueueUrl);
            assertTrue(receivedMessageHolder.get(0).body().contains("B85612"));
        });

        var nemsMessageId = "OUT-OF-ORDER-ID";
        var secondSuspensionEvent = getSuspensionEventWith(nhsNumber, nemsMessageId);

        sqs.sendMessage(builder -> builder.queueUrl(queueUrl).messageBody(secondSuspensionEvent));

        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            var receivedMessageInObservabilityQueue = checkMessageInRelatedQueue(eventOutOfOrderQueue);
            assertTrue(receivedMessageInObservabilityQueue.get(0).body().contains(nemsMessageId));
        });

        purgeQueue(mofUpdatedQueueUrl);
        purgeQueue(eventOutOfOrderQueue);
    }

    @Test
    void shouldPutDLQsWhenPdsAdaptorReturn400() {
        var nhsNumber = Long.toString(System.currentTimeMillis());
        stubFor(get(urlMatching("/suspended-patient-status/" + nhsNumber))
                .withHeader("Authorization", matching("Basic c3VzcGVuc2lvbi1zZXJ2aWNlOiJ0ZXN0Ig=="))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody(getSuspendedResponseWith(nhsNumber))));
        stubFor(put(urlMatching("/suspended-patient-status/" + nhsNumber))
                .withHeader("Authorization", matching("Basic c3VzcGVuc2lvbi1zZXJ2aWNlOiJ0ZXN0Ig=="))
                .willReturn(aResponse()
                        .withStatus(400)
                        .withHeader("Content-Type", "application/json")));

        var queueUrl = sqs.getQueueUrl(builder -> builder.queueName(suspensionsQueueName)).queueUrl();
        var invalidSuspensionQueueUrl = sqs.getQueueUrl(builder -> builder.queueName(invalidSuspensionQueueName)).queueUrl();
        var nonSensitiveInvalidSuspensionQueueUrl = sqs.getQueueUrl(builder -> builder.queueName(invalidSuspensionAuditQueueName)).queueUrl();
        sqs.sendMessage(builder -> builder.queueUrl(queueUrl).messageBody(getSuspensionEventWith(nhsNumber)));

        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            List<Message> receivedMessageHolderForInvalidSuspensions = checkMessageInRelatedQueue(invalidSuspensionQueueUrl);

            assertTrue(receivedMessageHolderForInvalidSuspensions.get(0).body().contains("nhsNumber"));
            assertTrue(receivedMessageHolderForInvalidSuspensions.get(0).body().contains(nhsNumber));
            assertTrue(receivedMessageHolderForInvalidSuspensions.get(0).body().contains("B85612"));
            assertTrue(receivedMessageHolderForInvalidSuspensions.get(0).body().contains("TEST-NEMS-ID"));
            assertTrue(receivedMessageHolderForInvalidSuspensions.get(0).messageAttributes().containsKey("traceId"));

        });

        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            List<Message> receivedMessageHolderForNonSensitiveInvalidSuspensions = checkMessageInRelatedQueue(nonSensitiveInvalidSuspensionQueueUrl);

            assertTrue(receivedMessageHolderForNonSensitiveInvalidSuspensions.get(0).body().contains("NO_ACTION:INVALID_SUSPENSION"));
            assertTrue(receivedMessageHolderForNonSensitiveInvalidSuspensions.get(0).body().contains("TEST-NEMS-ID"));
        });

        purgeQueue(invalidSuspensionQueueUrl);
        purgeQueue(nonSensitiveInvalidSuspensionQueueUrl);

    }

    private List<Message> checkMessageInRelatedQueue(String queueUrl) {
        System.out.println("checking sqs queue: " + queueUrl);

        var requestForMessagesWithAttributes = ReceiveMessageRequest.builder()
                .queueUrl(queueUrl)
                .messageAttributeNames("traceId")
                .build();
        List<Message> messages = sqs.receiveMessage(requestForMessagesWithAttributes).messages();
        System.out.printf("Found %s messages on queue: %s%n", messages.size(), queueUrl);
        assertThat(messages).hasSize(1);
        return messages;
    }

    private String getSuspensionEventWith(String nhsNumber, String nemsMessageId) {
        return new SuspensionEventBuilder()
                .lastUpdated("2017-11-01T15:00:33+00:00")
                .previousOdsCode("B85612")
                .eventType("SUSPENSION")
                .nhsNumber(nhsNumber)
                .nemsMessageId(nemsMessageId)
                .environment("local").buildJson();
    }

    private String getSuspensionEventWith(String nhsNumber) {
        return getSuspensionEventWith(nhsNumber, "TEST-NEMS-ID");
    }

    private String getNotSuspendedResponseWith(String nhsNumber) {
        return "{\n" +
                "    \"nhsNumber\": \"" + nhsNumber + "\",\n" +
                "    \"isSuspended\": false,\n" +
                "    \"currentOdsCode\": \"N85027\",\n" +
                "    \"isDeceased\":  false\n" +
                "}";
    }

    private String getSuspendedResponseWith(String nhsNumber) {
        return "{\n" +
                "    \"nhsNumber\": \"" + nhsNumber + "\",\n" +
                "    \"isSuspended\": true,\n" +
                "    \"currentOdsCode\": null,\n" +
                "    \"managingOrganisation\": \"B1234\",\n" +
                "    \"recordETag\": \"W/\\\"5\\\"\",\n" +
                "    \"isDeceased\":  false\n" +
                "}";
    }

    private void purgeQueue(String queueUrl) {
        System.out.println("Purging queue url: " + queueUrl);
        sqs.purgeQueue(PurgeQueueRequest.builder().queueUrl(queueUrl).build());
    }
}
