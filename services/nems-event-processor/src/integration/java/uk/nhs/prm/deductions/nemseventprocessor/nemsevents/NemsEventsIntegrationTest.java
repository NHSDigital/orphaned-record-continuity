package uk.nhs.prm.deductions.nemseventprocessor.nemsevents;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import software.amazon.awssdk.services.sqs.model.*;
import uk.nhs.prm.deductions.nemseventprocessor.audit.AuditMessage;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static uk.nhs.prm.deductions.nemseventprocessor.nemsevents.LocalStackAwsConfig.*;

@SpringBootTest()
@ActiveProfiles("test")
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = LocalStackAwsConfig.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class NemsEventsIntegrationTest {
    @Autowired
    private SqsClient sqsClient;

    @Value("${aws.nemsEventsQueueName}")
    private String nemsEventQueueName;

    private static final String NEMS_MESSAGE_ID = "3cfdf880-13e9-4f6b-8299-53e96ef5ec02";

    @Test
    void shouldSendNonSuspensionNemsEventMessageToUnhandledTopicAndAuditQueue() {
        String nonSuspensionMessageBody = "<Bundle xmlns=\"http://hl7.org/fhir\">\n" +
                "    <entry>\n" +
                "        <fullUrl value=\"3cfdf880-13e9-4f6b-8299-53e96ef5ec02\"/>\n" +
                "        <resource>\n" +
                "            <MessageHeader>\n" +
                "                <id value=\"" + NEMS_MESSAGE_ID + "\"/>\n" +
                "                <meta>\n" +
                "                    <lastUpdated value=\"2017-11-01T15:00:33+00:00\"/>\n" +
                "                </meta>\n" +
                "            </MessageHeader>\n" +
                "        </resource>\n" +
                "    </entry>\n" +
                "    <entry>\n" +
                "        <resource>\n" +
                "            <Patient>\n" +
                "                <generalPractitioner>\n" +
                "                    <reference value=\"urn:uuid:59a63170-b769-44f7-acb1-95cc3a0cb067\"/>\n" +
                "                    <display value=\"SHADWELL MEDICAL CENTRE\"/>\n" +
                "                </generalPractitioner>\n" +
                "            </Patient>\n" +
                "        </resource>\n" +
                "    </entry>\n" +
                "    <entry>\n" +
                "        <resource>\n" +
                "            <EpisodeOfCare>\n" +
                "                <status value=\"finished\"/>\n" +
                "                <managingOrganization>\n" +
                "                    <reference value=\"urn:uuid:e84bfc04-2d79-451e-84ef-a50116506088\"/>\n" +
                "                    <display value=\"LIVERSEDGE MEDICAL CENTRE\"/>\n" +
                "                </managingOrganization>\n" +
                "            </EpisodeOfCare>\n" +
                "        </resource>\n" +
                "    </entry>\n" +
                "</Bundle>";
        sendMessage(nemsEventQueueName, nonSuspensionMessageBody);

        validateAuditMessageReceived(nonSuspensionMessageBody);

        var receivingQueueUrl = getQueueUrl(UNHANDLED_EVENTS_TEST_RECEIVING_QUEUE);

        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            System.out.println("checking sqs queue: " + receivingQueueUrl);
            var receiveMessageRequest = ReceiveMessageRequest.builder()
                    .queueUrl(receivingQueueUrl)
                    .messageAttributeNames("traceId").build();
            var messages = sqsClient.receiveMessage(receiveMessageRequest).messages();
            System.out.println("messages: " + messages.size());
            assertThat(messages).hasSize(1);
            var receivedMessage = messages.get(0);

            System.out.println("message: " + receivedMessage.body());
            System.out.println("message attributes: " + receivedMessage.messageAttributes());
            System.out.println("message attributes empty: " + receivedMessage.messageAttributes().isEmpty());

            assertThat(receivedMessage.messageAttributes()).isNotEmpty();
            assertThat(receivedMessage.messageAttributes()).containsKey("traceId");
            assertThat(receivedMessage.body()).isEqualTo(
                    "{\"nemsMessageId\":\"" + NEMS_MESSAGE_ID + "\",\"messageStatus\":\"NO_ACTION:NON_SUSPENSION\"}");
        });

        purgeQueue(receivingQueueUrl);
    }

    @Test
    void shouldSendReRegistrationNemsEventMessageToReRegistrationSnsTopic() {

        String reRegistrationMessageBody = "<Bundle xmlns=\"http://hl7.org/fhir\">\n" +
                "    <entry>\n" +
                "        <fullUrl value=\"3cfdf880-13e9-4f6b-8299-53e96ef5ec02\"/>\n" +
                "        <resource>\n" +
                "            <MessageHeader>\n" +
                "                <id value=\"" + NEMS_MESSAGE_ID + "\"/>\n" +
                "                <meta>\n" +
                "                    <lastUpdated value=\"2017-11-01T15:00:33+00:00\"/>\n" +
                "                </meta>\n" +
                "            </MessageHeader>\n" +
                "        </resource>\n" +
                "    </entry>\n" +
                "    <entry>\n" +
                "        <resource>\n" +
                "            <Patient>\n" +
                "                <identifier>\n" +
                "                   <extension>\n" +
                "                        <valueCodeableConcept>\n" +
                "                            <coding>\n" +
                "                                <code value=\"01\"/>\n" +
                "                            </coding>\n" +
                "                        </valueCodeableConcept>\n" +
                "                    </extension>" +
                "                    <value value=\"9912003888\"/>\n" +
                "                </identifier>\n" +
                "                <generalPractitioner>\n" +
                "                    <reference value=\"urn:uuid:59a63170-b769-44f7-acb1-95cc3a0cb067\"/>\n" +
                "                    <display value=\"SHADWELL MEDICAL CENTRE\"/>\n" +
                "                </generalPractitioner>\n" +
                "            </Patient>\n" +
                "        </resource>\n" +
                "    </entry>\n" +
                "    <entry>\n" +
                "        <fullUrl value=\"urn:uuid:59a63170-b769-44f7-acb1-95cc3a0cb067\"/> \n" +
                "        <resource>\n" +
                "           <Organization>\n" +
                "               <id value=\"59a63170-b769-44f7-acb1-95cc3a0cb067\"/>\n" +
                "                <identifier>\n" +
                "                    <system value=\"https://fhir.nhs.uk/Id/ods-organization-code\"/>\n" +
                "                    <value value=\"B86056\"/>\n" +
                "                </identifier>\n" +
                "            </Organization>\n" +
                "        </resource>\n" +
                "    </entry>\n" +
                "</Bundle>";

        sendMessage(nemsEventQueueName, reRegistrationMessageBody);

        var receivingQueueUrl = getQueueUrl(RE_REGISTRATION_TEST_RECEIVING_QUEUE);

        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            System.out.println("checking sqs queue: " + receivingQueueUrl);
            var receiveMessageRequest = ReceiveMessageRequest.builder()
                    .queueUrl(receivingQueueUrl)
                    .messageAttributeNames("traceId", "nemsMessageId").build();
            var messages = sqsClient.receiveMessage(receiveMessageRequest).messages();

            System.out.println("messages: " + messages.size());
            assertThat(messages).hasSize(1);
            var receivedMessage = messages.get(0);

            System.out.println("message: " + receivedMessage.body());
            System.out.println("message attributes: " + receivedMessage.messageAttributes());
            System.out.println("message attributes empty: " + receivedMessage.messageAttributes().isEmpty());

            var expectedBody = "{\"nhsNumber\":\"9912003888\"," +
                    "\"newlyRegisteredOdsCode\":\"B86056\"," +
                    "\"nemsMessageId\":\"3cfdf880-13e9-4f6b-8299-53e96ef5ec02\"," +
                    "\"lastUpdated\":\"2017-11-01T15:00:33+00:00\"}";

            assertThat(receivedMessage.body()).contains(expectedBody);
            assertThat(receivedMessage.messageAttributes()).isNotEmpty();
            assertThat(receivedMessage.messageAttributes()).containsKey("traceId");
            assertThat(receivedMessage.messageAttributes()).containsKey("nemsMessageId");
        });

        purgeQueue(receivingQueueUrl);
    }

    @Test
    void shouldSendSuspensionNemsEventMessageToSuspensionsSnsTopic() {
        String suspensionMessageBody = "<Bundle xmlns=\"http://hl7.org/fhir\">\n" +
                "    <id value=\"236a1d4a-5d69-4fa9-9c7f-e72bf505aa5b\"/>\n" +
                "    <meta>\n" +
                "        <profile value=\"http://hl7.org/fhir/STU3/StructureDefinition/Bundle\"/>\n" +
                "    </meta>\n" +
                "    <type value=\"message\"/>\n" +
                "    <!--Entry for MessageHeader with id, timestamp, event type, source, responsible/publishing organization, communication-->\n" +
                "    <entry>\n" +
                "        <fullUrl value=\"3cfdf880-13e9-4f6b-8299-53e96ef5ec02\"/>\n" +
                "        <resource>\n" +
                "            <MessageHeader>\n" +
                "                    <id value=\"" + NEMS_MESSAGE_ID + "\"/>\n" +
                "                <meta>\n" +
                "                    <lastUpdated value=\"2017-11-01T15:00:33+00:00\"/>\n" +
                "                </meta>\n" +
                "            </MessageHeader>\n" +
                "        </resource>\n" +
                "    </entry>\n" +
                "    <entry>\n" +
                "        <resource>\n" +
                "            <Patient>\n" +
                "                <identifier>\n" +
                "                   <extension>\n" +
                "                        <valueCodeableConcept>\n" +
                "                            <coding>\n" +
                "                                <code value=\"01\"/>\n" +
                "                            </coding>\n" +
                "                        </valueCodeableConcept>\n" +
                "                    </extension>" +
                "                    <value value=\"9912003888\"/>\n" +
                "                </identifier>\n" +
                "            </Patient>\n" +
                "        </resource>\n" +
                "    </entry>\n" +
                "    <entry>\n" +
                "        <resource>\n" +
                "            <EpisodeOfCare>\n" +
                "                <status value=\"finished\"/>\n" +
                "                <managingOrganization>\n" +
                "                    <reference value=\"urn:uuid:e84bfc04-2d79-451e-84ef-a50116506088\"/>\n" +
                "                    <display value=\"LIVERSEDGE MEDICAL CENTRE\"/>\n" +
                "                </managingOrganization>\n" +
                "            </EpisodeOfCare>\n" +
                "        </resource>\n" +
                "    </entry>\n" +
                "    <entry>\n" +
                "        <fullUrl value=\"urn:uuid:e84bfc04-2d79-451e-84ef-a50116506088\"/>\n" +
                "        <resource>\n" +
                "            <Organization>\n" +
                "                <id value=\"e84bfc04-2d79-451e-84ef-a50116506088\"/>\n" +
                "                <identifier>\n" +
                "                    <system value=\"https://fhir.nhs.uk/Id/ods-organization-code\"/>\n" +
                "                    <value value=\"B85612\"/>\n" +
                "                </identifier>\n" +
                "            </Organization>\n" +
                "        </resource>\n" +
                "    </entry>\n" +
                "</Bundle>";

        sendMessage(nemsEventQueueName, suspensionMessageBody);

        var receivingQueueUrl = getQueueUrl(SUSPENSIONS_TEST_RECEIVING_QUEUE);

        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            System.out.println("checking sqs queue: " + receivingQueueUrl);
            var receiveMessageRequest = ReceiveMessageRequest.builder()
                    .queueUrl(receivingQueueUrl)
                    .messageAttributeNames("traceId", "nemsMessageId").build();
            var messages = sqsClient.receiveMessage(receiveMessageRequest).messages();

            System.out.println("messages: " + messages.size());
            assertThat(messages).hasSize(1);
            var receivedMessage = messages.get(0);

            System.out.println("message: " + receivedMessage.body());
            System.out.println("message attributes: " + receivedMessage.messageAttributes());
            System.out.println("message attributes empty: " + receivedMessage.messageAttributes().isEmpty());

            var expectedBody = "{\"nhsNumber\":\"9912003888\"," +
                    "\"lastUpdated\":\"2017-11-01T15:00:33+00:00\"," +
                    "\"previousOdsCode\":\"B85612\"," +
                    "\"nemsMessageId\":\"3cfdf880-13e9-4f6b-8299-53e96ef5ec02\"}";

            assertThat(receivedMessage.body()).contains(expectedBody);
            assertThat(receivedMessage.messageAttributes()).isNotEmpty();
            assertThat(receivedMessage.messageAttributes()).containsKey("traceId");
            assertThat(receivedMessage.messageAttributes()).containsKey("nemsMessageId");
        });

        validateAuditMessageReceived(suspensionMessageBody);
        purgeQueue(receivingQueueUrl);
    }

    private void validateAuditMessageReceived(String messageBody) {
        var auditReceivingQueueUrl = getQueueUrl(NEMS_EVENTS_AUDIT_TEST_RECEIVING_QUEUE);

        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            System.out.println("checking sqs queue: " + auditReceivingQueueUrl);

            var receiveMessageRequest = ReceiveMessageRequest.builder()
                    .queueUrl(auditReceivingQueueUrl)
                    .messageAttributeNames("traceId").build();
            var messages = sqsClient.receiveMessage(receiveMessageRequest).messages();

            System.out.println("messages: " + messages.size());
            assertThat(messages).hasSize(1);
            var receivedMessage = messages.get(0);

            System.out.println("message: " + receivedMessage.body());

            AuditMessage receivedAuditMessage = new ObjectMapper().readValue(receivedMessage.body(), AuditMessage.class);

            assertThat(receivedAuditMessage.getNemsMessageId()).isEqualTo(NEMS_MESSAGE_ID);
            assertThat(receivedAuditMessage.getMessageBody()).isEqualTo(messageBody);
        });

        purgeQueue(auditReceivingQueueUrl);
    }

    private void purgeQueue(String queueUrl) {
        System.out.println("Purging queue url: " + queueUrl);
        sqsClient.purgeQueue(PurgeQueueRequest.builder().queueUrl(queueUrl).build());
    }

    private void sendMessage(String queueName, String message) {
        sqsClient.sendMessage(SendMessageRequest.builder().queueUrl(getQueueUrl(queueName)).messageBody(message).build());
    }

    private String getQueueUrl(String queueName) {
        return sqsClient.getQueueUrl(GetQueueUrlRequest.builder().queueName(queueName).build()).queueUrl();
    }
}
