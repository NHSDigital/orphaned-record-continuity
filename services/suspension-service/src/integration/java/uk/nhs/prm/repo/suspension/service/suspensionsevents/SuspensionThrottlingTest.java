package uk.nhs.prm.repo.suspension.service.suspensionsevents;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
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
import software.amazon.awssdk.services.sqs.model.GetQueueAttributesRequest;
import software.amazon.awssdk.services.sqs.model.PurgeQueueRequest;
import software.amazon.awssdk.services.sqs.model.QueueAttributeName;
import software.amazon.awssdk.services.sqs.model.SendMessageBatchRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageBatchRequestEntry;
import uk.nhs.prm.repo.suspension.service.infra.LocalStackAwsConfig;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = LocalStackAwsConfig.class)
@DirtiesContext
public class SuspensionThrottlingTest {

    @Autowired
    private SqsClient sqs;

    @Value("${aws.incomingQueueName}")
    private String suspensionsQueueName;

    @Value("${aws.mofUpdatedQueueName}")
    private String mofUpdatedQueueName;

    private WireMockServer stubPdsAdaptor;

    private String mofUpdatedQueueUrl;

    private String suspensionQueueUrl;

    @BeforeEach
    public void setUp() {
        stubPdsAdaptor = initializeWebServer();
        configureFor("localhost", stubPdsAdaptor.port());
        mofUpdatedQueueUrl = sqs.getQueueUrl(builder -> builder.queueName(mofUpdatedQueueName)).queueUrl();
        suspensionQueueUrl = sqs.getQueueUrl(builder -> builder.queueName(suspensionsQueueName)).queueUrl();
        purgeQueues(suspensionQueueUrl, mofUpdatedQueueUrl);
    }

    @AfterEach
    public void tearDown() {
        stubPdsAdaptor.resetAll();
        stubPdsAdaptor.stop();
        purgeQueues(suspensionQueueUrl, mofUpdatedQueueUrl);
    }

    private WireMockServer initializeWebServer() {
        final WireMockServer wireMockServer = new WireMockServer(8080);
        wireMockServer.start();
        return wireMockServer;
    }

    @Test
    void shouldProcess50MessagesIn25Seconds() {
        stubbinForGenericPdsResponses(200, 800);

        var startingTime = Instant.now();

        sendMultipleBatchesOf10Messages(suspensionQueueUrl, 2);

        await().atMost(60, TimeUnit.SECONDS).untilAsserted(() -> assertTrue(isQueueEmpty(suspensionQueueUrl)));

        var finishTime = Instant.now();
        var timeElapsed = Duration.between(startingTime, finishTime);

        System.out.println("Total time taken: " + timeElapsed);

        assertThat(timeElapsed).isCloseTo(Duration.ofSeconds(10), Duration.ofSeconds(5));
    }

    @Test
    void shouldThrottlePdsAdaptorToPreventUpdatesMoreThan2PerSecond() {
        stubbinForGenericPdsResponses(0, 0);

        var startingTime = Instant.now();

        sendMultipleBatchesOf10Messages(suspensionQueueUrl, 2);

        await().atMost(60, TimeUnit.SECONDS).untilAsserted(() -> assertTrue(isQueueEmpty(suspensionQueueUrl)));

        var finishTime = Instant.now();
        var timeElapsed = Duration.between(startingTime, finishTime);

        System.out.println("Total time taken: " + timeElapsed);

        assertThat(timeElapsed).isCloseTo(Duration.ofSeconds(7), Duration.ofSeconds(5));
    }

    @Test
    void shouldApplyBackOffDelay() {
        var nhsNumber = randomNhsNumber();

        setPdsRetryMessage(nhsNumber);
        stubbinForGenericPdsResponses(0, 0);
        var startingTime = Instant.now();

        sqs.sendMessage(builder -> builder.queueUrl(suspensionQueueUrl).messageBody(getSuspensionEvent(nhsNumber)));

        await().atMost(20, TimeUnit.SECONDS).untilAsserted(() -> assertTrue(isQueueEmpty(suspensionQueueUrl)));

        var finishTime = Instant.now();
        var timeElapsed = Duration.between(startingTime, finishTime);

        System.out.println("Total time taken: " + timeElapsed);

        assertThat(timeElapsed).isCloseTo(Duration.ofSeconds(8), Duration.ofSeconds(1));
        verify(4, getRequestedFor(urlEqualTo("/suspended-patient-status/" + nhsNumber)));
    }

    @Test
    void shouldRetry3TimesWithExponentialBackOffPeriod() {
        var nhsNumber = randomNhsNumber();
        stubbinForGenericPdsResponses(200, 800);
        setPdsRetryMessage(nhsNumber);

        var startingTime = Instant.now();

        sendMultipleBatchesOf10Messages(suspensionQueueUrl, 2);
        sqs.sendMessage(builder -> builder.queueUrl(suspensionQueueUrl).messageBody(getSuspensionEvent(nhsNumber)));

        await().atMost(120, TimeUnit.SECONDS).untilAsserted(() -> assertTrue(isQueueEmpty(suspensionQueueUrl)));

        var finishTime = Instant.now();
        var timeElapsed = Duration.between(startingTime, finishTime);

        System.out.println("Total time taken: " + timeElapsed);

        assertThat(timeElapsed).isCloseTo(Duration.ofSeconds(15), Duration.ofSeconds(10));
    }

    private void setPdsRetryMessage(String nhsNumber) {
        setPdsErrorState(STARTED, "Cause Success", 1, nhsNumber);
        setPdsErrorState("Cause Success", "Second Cause Success", 2, nhsNumber);
        setPdsErrorState("Second Cause Success", "Third Cause Success", 3, nhsNumber);

        stubFor(get(urlEqualTo("/suspended-patient-status/" + nhsNumber)).atPriority(4)
                .withHeader("Authorization", matching("Basic c3VzcGVuc2lvbi1zZXJ2aWNlOiJ0ZXN0Ig=="))
                .inScenario("Retry Scenario")
                .whenScenarioStateIs("Third Cause Success")
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(getSuspendedResponse())));
    }

    private void setPdsErrorState(String startingState, String finishedState, int priority, String nhsNumber) {
        stubFor(get(urlMatching("/suspended-patient-status/" + nhsNumber)).atPriority(priority)
                .withHeader("Authorization", matching("Basic c3VzcGVuc2lvbi1zZXJ2aWNlOiJ0ZXN0Ig=="))
                .inScenario("Retry Scenario")
                .whenScenarioStateIs(startingState)
                .willReturn(aResponse()
                        .withStatus(500)
                        .withHeader("Content-Type", "text/xml")
                        .withBody("<response>Some content</response>"))
                .willSetStateTo(finishedState));
    }

    private void sendMultipleBatchesOf10Messages(String queueUrl, int numberOfBatches) {
        for (int i = 1; i <= numberOfBatches; i++) {
            sqs.sendMessageBatch(createBatchOfTenRequest(queueUrl));
        }
    }

    private void stubbinForGenericPdsResponses(int getRequestDelay, int putRequestDelay) {
        var anyNhsNumber = ".*";
        stubFor(get(urlMatching("/suspended-patient-status/" + anyNhsNumber))
                .withHeader("Authorization", matching("Basic c3VzcGVuc2lvbi1zZXJ2aWNlOiJ0ZXN0Ig=="))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withFixedDelay(getRequestDelay)
                        .withHeader("Content-Type", "application/json")
                        .withBody(getSuspendedResponse())));
        stubFor(put(urlMatching("/suspended-patient-status/" + anyNhsNumber))
                .withHeader("Authorization", matching("Basic c3VzcGVuc2lvbi1zZXJ2aWNlOiJ0ZXN0Ig=="))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withFixedDelay(putRequestDelay)
                        .withBody(getSuspendedResponse())
                        .withHeader("Content-Type", "application/json")));
    }

    private boolean isQueueEmpty(String queueUrl) {
        var getQueueAttributesResult = sqs.getQueueAttributes(GetQueueAttributesRequest.builder()
                .queueUrl(queueUrl)
                .attributeNames(
                        QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES_NOT_VISIBLE,
                        QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES)
                .build());

        var numberOfMessageNotVisible = Integer.parseInt(
                getQueueAttributesResult.attributes().get(QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES_NOT_VISIBLE));
        var numberOfMessageVisible = Integer.parseInt(
                getQueueAttributesResult.attributes().get(QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES));

        return (numberOfMessageVisible == 0 && numberOfMessageNotVisible == 0);
    }

    private SendMessageBatchRequest createBatchOfTenRequest(String queueUrl) {
        return SendMessageBatchRequest.builder()
                .queueUrl(queueUrl)
                .entries(generateSendMessageBatchRequestEntryList())
                .build();
    }

    private List<SendMessageBatchRequestEntry> generateSendMessageBatchRequestEntryList() {
        List<SendMessageBatchRequestEntry> requestEntries = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            requestEntries.add(SendMessageBatchRequestEntry.builder()
                    .id(UUID.randomUUID().toString())
                    .messageBody(getSuspensionEvent())
                    .build());
        }
        return requestEntries;
    }

    private String getSuspensionEvent(String nhsNumber) {
        return new SuspensionEventBuilder()
                .lastUpdated("2017-11-01T15:00:33+00:00")
                .previousOdsCode("B85612")
                .eventType("SUSPENSION")
                .nhsNumber(nhsNumber)
                .nemsMessageId("TEST-NEMS-ID-BACK-OFF")
                .environment("local").buildJson();
    }

    private String getSuspensionEvent() {
        var nhsNumber = randomNhsNumber();
        System.out.println("Nhs Number generated: " + nhsNumber);
        return new SuspensionEventBuilder()
                .lastUpdated("2017-11-01T15:00:33+00:00")
                .previousOdsCode("B85612")
                .eventType("SUSPENSION")
                .nhsNumber(nhsNumber)
                .nemsMessageId("TEST-NEMS-ID")
                .environment("local").buildJson();
    }

    private String getSuspendedResponse() {
        return "{\n" +
                "    \"nhsNumber\": \"1231231231\",\n" +
                "    \"isSuspended\": true,\n" +
                "    \"currentOdsCode\": null,\n" +
                "    \"managingOrganisation\": \"B1234\",\n" +
                "    \"recordETag\": \"W/\\\"5\\\"\"\n" +
                "}";
    }

    private String randomNhsNumber() {
        return Long.toString(System.currentTimeMillis());
    }

    private void purgeQueues(String queueUrl, String mofUpdatedQueueUrl) {
        sqs.purgeQueue(PurgeQueueRequest.builder().queueUrl(queueUrl).build());
        sqs.purgeQueue(PurgeQueueRequest.builder().queueUrl(mofUpdatedQueueUrl).build());
    }
}
