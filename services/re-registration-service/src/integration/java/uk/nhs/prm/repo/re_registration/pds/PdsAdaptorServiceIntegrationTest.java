package uk.nhs.prm.repo.re_registration.pds;

import com.github.tomakehurst.wiremock.WireMockServer;
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
import software.amazon.awssdk.services.sqs.model.GetQueueAttributesRequest;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.PurgeQueueRequest;
import software.amazon.awssdk.services.sqs.model.QueueAttributeName;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import uk.nhs.prm.repo.re_registration.data.ActiveSuspensionsDb;
import uk.nhs.prm.repo.re_registration.infra.LocalStackAwsConfig;
import uk.nhs.prm.repo.re_registration.model.ActiveSuspensionsMessage;
import uk.nhs.prm.repo.re_registration.model.ReRegistrationEvent;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest()
@ActiveProfiles("test")
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = LocalStackAwsConfig.class)
@DirtiesContext
public class PdsAdaptorServiceIntegrationTest {

    public static final String NHS_NUMBER = "1234567890";
    public static final String STATUS_MESSAGE_FOR_WHEN_PATIENT_IS_STILL_SUSPENDED = "NO_ACTION:RE_REGISTRATION_FAILED_STILL_SUSPENDED";
    public static final String STATUS_MESSAGE_FOR_WHEN_PDS_RETURNS_4XX_ERROR = "NO_ACTION:RE_REGISTRATION_FAILED_PDS_ERROR";

    @Autowired
    private SqsClient sqs;

    @Value("${aws.reRegistrationsQueueName}")
    private String reRegistrationsQueueName;

    @Value("${aws.reRegistrationsAuditQueueName}")
    private String reRegistrationsAuditQueueName;

    private WireMockServer stubPdsAdaptor;
    private String reRegistrationsQueueUrl;
    private String reRegistrationsAuditUrl;
    private String nemsMessageId = "nemsMessageId";

    @Autowired
    ActiveSuspensionsDb activeSuspensionsDb;

    @BeforeEach
    public void setUp() {
        stubPdsAdaptor = initializeWebServer();
        reRegistrationsQueueUrl = sqs.getQueueUrl(builder -> builder.queueName(reRegistrationsQueueName)).queueUrl();
        reRegistrationsAuditUrl = sqs.getQueueUrl(builder -> builder.queueName(reRegistrationsAuditQueueName)).queueUrl();
        activeSuspensionsDb.save(getActiveSuspensionsMessage());
    }

    @AfterEach
    public void tearDown() {
        stubPdsAdaptor.resetAll();
        stubPdsAdaptor.stop();
        sqs.purgeQueue(PurgeQueueRequest.builder().queueUrl(reRegistrationsAuditUrl).build());
        sqs.purgeQueue(PurgeQueueRequest.builder().queueUrl(reRegistrationsQueueUrl).build());
    }

    private WireMockServer initializeWebServer() {
        final WireMockServer wireMockServer = new WireMockServer(8080);
        wireMockServer.start();
        return wireMockServer;
    }

    @Test
    void shouldRetryUpTo3TimesAndNotPutAnythingOnReRegistrationAuditTopicWhenPdsReturnsResponseWithStatusCode500() {
        setPdsRetryMessage(NHS_NUMBER);
        sqs.sendMessage(builder -> builder.queueUrl(reRegistrationsQueueUrl).messageBody(getReRegistrationEvent().toJsonString()));

        await().atMost(20, TimeUnit.SECONDS).untilAsserted(() -> assertThat(isQueueEmpty(reRegistrationsAuditUrl)).isTrue());
        await().atMost(20, TimeUnit.SECONDS).untilAsserted(() -> verify(3, getRequestedFor(urlMatching("/suspended-patient-status/" + NHS_NUMBER))));
    }

    @Test
    void shouldRetryWhenPdsReturnsResponseWithStatusCode500AndPublishOnReRegistrationAuditTopicOnce200IsReturned() {
        setPdsRetryMessageAndSucceed(NHS_NUMBER);
        sqs.sendMessage(builder -> builder.queueUrl(reRegistrationsQueueUrl).messageBody(getReRegistrationEvent().toJsonString()));

        await().atMost(20, TimeUnit.SECONDS).untilAsserted(() -> {
            String messageBody = checkMessageInRelatedQueue(reRegistrationsAuditUrl).get(0).body();
            assertThat(messageBody).contains(STATUS_MESSAGE_FOR_WHEN_PATIENT_IS_STILL_SUSPENDED);
            assertThat(messageBody).contains(nemsMessageId);
        });
    }

    @Test
    void shouldPutTheAuditStatusMessageOnAuditTopicWhenPdsReturnsResponseWithStatusCode200() {
        setPds200SuccessState(null, 1, NHS_NUMBER);
        sqs.sendMessage(builder -> builder.queueUrl(reRegistrationsQueueUrl).messageBody(getReRegistrationEvent().toJsonString()));

        await().atMost(20, TimeUnit.SECONDS).untilAsserted(() -> {
            String messageBody = checkMessageInRelatedQueue(reRegistrationsAuditUrl).get(0).body();
            assertThat(messageBody).contains(STATUS_MESSAGE_FOR_WHEN_PATIENT_IS_STILL_SUSPENDED);
            assertThat(messageBody).contains(nemsMessageId);
        });
    }

    @Test
    void shouldPutTheAuditStatusMessageOnAuditTopicWhenPdsReturnsResponseWithStatusCode400() {
        setPdsErrorState(null, null, 1, NHS_NUMBER, 400);
        sqs.sendMessage(builder -> builder.queueUrl(reRegistrationsQueueUrl).messageBody(getReRegistrationEvent().toJsonString()));

        await().atMost(20, TimeUnit.SECONDS).untilAsserted(() -> {
            String messageBody = checkMessageInRelatedQueue(reRegistrationsAuditUrl).get(0).body();
            assertThat(messageBody).contains(STATUS_MESSAGE_FOR_WHEN_PDS_RETURNS_4XX_ERROR);
            assertThat(messageBody).contains(nemsMessageId);
        });
    }

    private void setPds200SuccessState(String startingState, int priority, String nhsNumber) {
        stubPdsAdaptor.stubFor(get(urlEqualTo("/suspended-patient-status/" + nhsNumber)).atPriority(priority)
                .withHeader("Authorization", equalTo("Basic cmUtcmVnaXN0cmF0aW9uLXNlcnZpY2U6ZGVmYXVsdA=="))
                .inScenario("Retry Scenario")
                .whenScenarioStateIs(startingState)
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(getPdsResponseString().getBody())));
    }

    private void setPdsErrorState(String startingState, String finishedState, int priority, String nhsNumber, int statusCode) {
        stubPdsAdaptor.stubFor(get(urlEqualTo("/suspended-patient-status/" + nhsNumber)).atPriority(priority)
                .withHeader("Authorization", equalTo("Basic cmUtcmVnaXN0cmF0aW9uLXNlcnZpY2U6ZGVmYXVsdA=="))
                .inScenario("Retry Scenario")
                .whenScenarioStateIs(startingState)
                .willReturn(aResponse()
                        .withStatus(statusCode)
                        .withHeader("Content-Type", "application/json")
                        .withBody("<response>Some content</response>"))
                .willSetStateTo(finishedState));
    }

    private void setPdsRetryMessageAndSucceed(String nhsNumber) {
        setPdsErrorState(STARTED, "Cause Success", 1, nhsNumber, 500);
        setPdsErrorState("Cause Success", "Second Cause Success", 2, nhsNumber, 500);
        setPds200SuccessState("Second Cause Success", 3, nhsNumber);
    }

    private void setPdsRetryMessage(String nhsNumber) {
        setPdsErrorState(STARTED, "Cause Success", 1, nhsNumber, 500);
        setPdsErrorState("Cause Success", "Second Cause Success", 2, nhsNumber, 500);
        setPdsErrorState("Second Cause Success", null, 3, nhsNumber, 500);
    }

    private ReRegistrationEvent getReRegistrationEvent() {
        return new ReRegistrationEvent(NHS_NUMBER, "ABC123", nemsMessageId, "2017-11-01T15:00:33+00:00");
    }

    private boolean isQueueEmpty(String queueUrl) {
        var getQueueAttributesResponse = sqs.getQueueAttributes(GetQueueAttributesRequest.builder()
                .queueUrl(queueUrl)
                .attributeNames(
                        QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES_NOT_VISIBLE,
                        QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES
                )
                .build());

        int numberOfMessageNotVisible = Integer.parseInt(
                getQueueAttributesResponse.attributes().get(QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES_NOT_VISIBLE)
        );
        int numberOfMessageVisible = Integer.parseInt(
                getQueueAttributesResponse.attributes().get(QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES)
        );

        return numberOfMessageVisible == 0 && numberOfMessageNotVisible == 0;
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

    private ResponseEntity<String> getPdsResponseString() {
        var pdsResponseString = "{\"nhsNumber\":\"" + NHS_NUMBER + "\",\"isSuspended\":true,\"currentOdsCode\":\"currentOdsCode\",\"managingOrganisation\":\"managingOrganisation\",\"recordETag\":\"etag\",\"isDeceased\":false}";
        return new ResponseEntity<>(pdsResponseString, HttpStatus.OK);
    }

    private ActiveSuspensionsMessage getActiveSuspensionsMessage() {
        return new ActiveSuspensionsMessage(NHS_NUMBER, "previous-ods-code", "2017-11-01T15:00:33+00:00");
    }
}
