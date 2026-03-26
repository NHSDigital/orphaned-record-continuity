package uk.nhs.prm.repo.suspension.service.repo.in;

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
import uk.nhs.prm.repo.suspension.service.suspensionsevents.SuspensionEventBuilder;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = { "toggle.canUpdateManagingOrganisationToRepo=true", "toggle.repoProcessOnlySafeListedOdsCodes=true" })
@ActiveProfiles("test")
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = LocalStackAwsConfig.class)
@DirtiesContext
public class MOFUpdateBasedOnOdsCodeToggle {
    private static final String AUTHORIZATION_HEADER = "Basic c3VzcGVuc2lvbi1zZXJ2aWNlOnRlc3Q=";

    @Autowired
    private SqsClient sqs;

    @Value("${aws.incomingQueueName}")
    private String suspensionsQueueName;

    @Value("${aws.mofUpdatedQueueName}")
    private String mofUpdatedQueueName;

    @Value("${aws.repoIncomingQueueName}")
    private String repoIncomingQueueName;

    private WireMockServer stubPdsAdaptor;

    private String suspensionQueueUrl;

    @BeforeEach
    public void setUp() {
        stubPdsAdaptor = initializeWebServer();
        configureFor("localhost", stubPdsAdaptor.port());
        suspensionQueueUrl = sqs.getQueueUrl(builder -> builder.queueName(suspensionsQueueName)).queueUrl();
    }

    @AfterEach
    public void tearDown() {
        stubPdsAdaptor.resetAll();
        stubPdsAdaptor.stop();
        purgeQueue(suspensionQueueUrl);
    }

    private WireMockServer initializeWebServer() {
        final WireMockServer wireMockServer = new WireMockServer(8080);
        wireMockServer.start();
        return wireMockServer;
    }

     @Test
     void shouldUpdateMofToPreviousGpAndSendMessageToMofUpdatedSNSTopicWhenOdsCodeNotInSafeList() {
         var nhsNumber = Long.toString(System.currentTimeMillis());
         stubForPdsAdaptor(nhsNumber, getSuspendedResponseWith(nhsNumber));

         var mofUpdatedQueueUrl = sqs.getQueueUrl(builder -> builder.queueName(mofUpdatedQueueName)).queueUrl();
         sqs.sendMessage(builder -> builder.queueUrl(suspensionQueueUrl).messageBody(getSuspensionEventWith(nhsNumber, "B85612")));

         await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
             List<Message> receivedMessageHolder = checkMessageInRelatedQueue(mofUpdatedQueueUrl);

             assertTrue(receivedMessageHolder.get(0).body().contains("ACTION:UPDATED_MANAGING_ORGANISATION"));
             assertTrue(receivedMessageHolder.get(0).body().contains("TEST-NEMS-ID"));
             assertTrue(receivedMessageHolder.get(0).body().contains("B85612"));
             assertTrue(receivedMessageHolder.get(0).messageAttributes().containsKey("traceId"));
         });
     }

    @Test
    void shouldSetMOFAsRepoOdsCodeWhenOdsCodeIsInSafeList(){
        var nhsNumber = Long.toString(System.currentTimeMillis());
        stubForPdsAdaptor(nhsNumber, getSuspendedResponseWithRepoOdsCode(nhsNumber));


        var repoIncomingQueueUrl = sqs.getQueueUrl(builder -> builder.queueName(repoIncomingQueueName)).queueUrl();
        purgeQueue(repoIncomingQueueUrl);
        sqs.sendMessage(builder -> builder.queueUrl(suspensionQueueUrl).messageBody(getSuspensionEventWith(nhsNumber, "tEsT21")));

        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            List<Message> receivedMessageHolder = checkMessageInRelatedQueue(repoIncomingQueueUrl);

            assertTrue(receivedMessageHolder.get(0).body().contains("TEST-NEMS-ID"));
            assertTrue(receivedMessageHolder.get(0).body().contains("A1234"));
            assertTrue(receivedMessageHolder.get(0).body().contains("nemsEventLastUpdated"));
            assertTrue(receivedMessageHolder.get(0).messageAttributes().containsKey("traceId"));
        });
    }

    private void stubForPdsAdaptor(String nhsNumber, String suspendedResponse) {
        stubPdsAdaptor.stubFor(get(urlMatching("/suspended-patient-status/" + nhsNumber))
                .withHeader("Authorization", matching(AUTHORIZATION_HEADER))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody(getSuspendedResponseWith(nhsNumber))));
        stubPdsAdaptor.stubFor(put(urlMatching("/suspended-patient-status/" + nhsNumber))
                .withHeader("Authorization", matching(AUTHORIZATION_HEADER))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody(suspendedResponse)));
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

    private String getSuspensionEventWith(String nhsNumber, String nemsMessageId, String odsCode) {
        return new SuspensionEventBuilder()
                .lastUpdated("2017-11-01T15:00:33+00:00")
                .previousOdsCode(odsCode)
                .eventType("SUSPENSION")
                .nhsNumber(nhsNumber)
                .nemsMessageId(nemsMessageId)
                .environment("local").buildJson();
    }

    private String getSuspensionEventWith(String nhsNumber, String odsCode) {
        return getSuspensionEventWith(nhsNumber, "TEST-NEMS-ID", odsCode);
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

    private String getSuspendedResponseWithRepoOdsCode(String nhsNumber) {
        return "{\n" +
                "    \"nhsNumber\": \"" + nhsNumber + "\",\n" +
                "    \"isSuspended\": true,\n" +
                "    \"currentOdsCode\": null,\n" +
                "    \"managingOrganisation\": \"A1234\",\n" +
                "    \"recordETag\": \"W/\\\"5\\\"\",\n" +
                "    \"isDeceased\":  false\n" +
                "}";
    }

    private void purgeQueue(String queueUrl) {
        System.out.println("Purging queue url: " + queueUrl);
        sqs.purgeQueue(PurgeQueueRequest.builder().queueUrl(queueUrl).build());
    }
}
