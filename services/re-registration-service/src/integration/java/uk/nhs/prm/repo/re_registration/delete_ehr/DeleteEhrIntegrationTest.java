package uk.nhs.prm.repo.re_registration.delete_ehr;

import com.github.tomakehurst.wiremock.WireMockServer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.delete;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest()
@ActiveProfiles("test")
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = LocalStackAwsConfig.class)
@DirtiesContext
public class DeleteEhrIntegrationTest {

    private static final Logger LOGGER = LogManager.getLogger(DeleteEhrIntegrationTest.class);
    public static final String NHS_NUMBER = "9999567890";

    @Autowired
    private SqsClient sqs;

    @Autowired
    ActiveSuspensionsDb activeSuspensionsDb;

    private static final String EXPECTED_PDS_AUTH_HEADER = "Basic dXNlcm5hbWU6dGVzdA==";
    private static final String EXPECTED_EHR_REPO_AUTH_HEADER = "test";

    @Value("${aws.reRegistrationsQueueName}")
    private String reRegistrationsQueueName;

    @Value("${aws.reRegistrationsAuditQueueName}")
    private String reRegistrationsAuditQueueName;

    private WireMockServer stubPdsAdaptor;
    private String reRegistrationsQueueUrl;
    private String reRegistrationsAuditUrl;

    @BeforeEach
    public void setUp() {
        stubPdsAdaptor = initializeWebServer();
        reRegistrationsQueueUrl = sqs.getQueueUrl(builder -> builder.queueName(reRegistrationsQueueName)).queueUrl();
        reRegistrationsAuditUrl = sqs.getQueueUrl(builder -> builder.queueName(reRegistrationsAuditQueueName)).queueUrl();
        stubResponses();
    }

    @AfterEach
    public void tearDown() {
        stubPdsAdaptor.resetAll();
        stubPdsAdaptor.stop();
        sqs.purgeQueue(PurgeQueueRequest.builder().queueUrl(reRegistrationsAuditUrl).build());
    }

    private WireMockServer initializeWebServer() {
        final WireMockServer wireMockServer = new WireMockServer(8080);
        wireMockServer.start();
        return wireMockServer;
    }

    @Test
    void shouldPutTheEHRDeleteAuditMessageOntoTheAuditQueueWhenActiveSuspensionExistsInDBAndPDSReturnsAStatusCode200() {
        activeSuspensionsDb.save(getActiveSuspensionsMessage());
        sqs.sendMessage(builder -> builder.queueUrl(reRegistrationsQueueUrl).messageBody(getReRegistrationEvent().toJsonString()));

        await().atMost(20, TimeUnit.SECONDS).untilAsserted(() -> {
            String messageBody = checkMessageInRelatedQueue(reRegistrationsAuditUrl).get(0).body();
            System.out.println("Found message - " + messageBody);

            assertThat(messageBody).contains("\"status\":\"ACTION:RE_REGISTRATION_EHR_DELETED\"");
            assertThat(messageBody).contains("\"nemsMessageId\":\"someNemsId\"");
            assertThat(messageBody).contains("\"conversationIds\":[\"2431d4ff-f760-4ab9-8cd8-a3fc47846762\",\"c184cc19-86e9-4a95-b5b5-2f156900bb3c\"]");
        });
    }

    @Test
    void shouldPutTheUnknownReRegistrationsAuditMessageOntoTheAuditQueueWhenActiveSuspensionDoesNotExistInDBAndPDSReturnsAStatusCode200() {
        sqs.sendMessage(builder -> builder.queueUrl(reRegistrationsQueueUrl).messageBody(getReRegistrationEvent().toJsonString()));

        await().atMost(20, TimeUnit.SECONDS).untilAsserted(() -> {
            String messageBody = checkMessageInRelatedQueue(reRegistrationsAuditUrl).get(0).body();
            System.out.println("Found message - " + messageBody);
            assertThat(messageBody).contains("\"status\":\"NO_ACTION:UNKNOWN_REGISTRATION_EVENT_RECEIVED\"");
        });
    }

    private void stubResponses() {
        setPds200SuccessState();
        ehrRepository200Response();
    }

    private void setPds200SuccessState() {
        stubPdsAdaptor.stubFor(get(urlEqualTo("/suspended-patient-status/" + NHS_NUMBER))
                .withHeader("Authorization", equalTo(EXPECTED_PDS_AUTH_HEADER))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(getPdsResponseString().getBody())));
    }

    private void ehrRepository200Response() {
        stubPdsAdaptor.stubFor(delete(urlEqualTo("/patients/" + NHS_NUMBER))
                .withHeader("Authorization", equalTo(EXPECTED_EHR_REPO_AUTH_HEADER))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\n" +
                                "  \"data\": {\n" +
                                "    \"type\": \"patients\",\n" +
                                "    \"id\": " + NHS_NUMBER + ",\n" +
                                "    \"conversationIds\":[\"2431d4ff-f760-4ab9-8cd8-a3fc47846762\"," + "\"c184cc19-86e9-4a95-b5b5-2f156900bb3c\"]}}")));
    }

    private ReRegistrationEvent getReRegistrationEvent() {
        String nemsMessageId = "someNemsId";
        return new ReRegistrationEvent(NHS_NUMBER, "ABC123", nemsMessageId, "2017-11-01T15:00:33+00:00");
    }

    private List<Message> checkMessageInRelatedQueue(String queueUrl) {
        LOGGER.info("Checking SQS Queue: {}", queueUrl);

        ReceiveMessageRequest requestForMessagesWithAttributes = ReceiveMessageRequest.builder()
                .queueUrl(queueUrl)
                .messageAttributeNames("traceId")
                .build();

        final List<Message> messages = sqs
                .receiveMessage(requestForMessagesWithAttributes)
                .messages();

        LOGGER.info("Found {} messages on queue: {}", messages.size(), queueUrl);
        assertThat(messages).hasSize(1);
        return messages;
    }

    private ResponseEntity<String> getPdsResponseString() {
        String pdsAdaptorResponseString = "{\"nhsNumber\":\"" + NHS_NUMBER + "\",\"isSuspended\":false,\"currentOdsCode\":\"currentOdsCode\",\"managingOrganisation\":\"managingOrganisation\",\"recordETag\":\"etag\",\"isDeceased\":false}";
        return new ResponseEntity<>(pdsAdaptorResponseString, HttpStatus.OK);
    }

    private ActiveSuspensionsMessage getActiveSuspensionsMessage() {
        return new ActiveSuspensionsMessage(NHS_NUMBER, "previous-ods-code", "2017-11-01T15:00:33+00:00");
    }
}
