package uk.nhs.prm.repo.ehrtransferservice.services.ehr_repo;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import uk.nhs.prm.repo.ehrtransferservice.logging.Tracer;
import uk.nhs.prm.repo.ehrtransferservice.exceptions.DuplicateMessageException;
import uk.nhs.prm.repo.ehrtransferservice.exceptions.HttpException;
import uk.nhs.prm.repo.ehrtransferservice.gp2gp_message_models.EhrExtract;
import uk.nhs.prm.repo.ehrtransferservice.gp2gp_message_models.EhrExtractMessageWrapper;
import uk.nhs.prm.repo.ehrtransferservice.gp2gp_message_models.Identifier;
import uk.nhs.prm.repo.ehrtransferservice.gp2gp_message_models.MessageData;
import uk.nhs.prm.repo.ehrtransferservice.gp2gp_message_models.MessageHeader;
import uk.nhs.prm.repo.ehrtransferservice.gp2gp_message_models.ParsedMessage;
import uk.nhs.prm.repo.ehrtransferservice.gp2gp_message_models.Patient;
import uk.nhs.prm.repo.ehrtransferservice.gp2gp_message_models.Reference;
import uk.nhs.prm.repo.ehrtransferservice.gp2gp_message_models.SOAPBody;
import uk.nhs.prm.repo.ehrtransferservice.gp2gp_message_models.SOAPEnvelope;
import uk.nhs.prm.repo.ehrtransferservice.gp2gp_message_models.SOAPHeader;
import uk.nhs.prm.repo.ehrtransferservice.services.PresignedUrl;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.matching;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@Tag("unit")
class EhrRepoClientTest {
    static Tracer tracer = mock(Tracer.class);
    static UUID traceId = UUID.randomUUID();

    @RegisterExtension
    static WireMockExtension wireMock = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    @BeforeEach
    public void setUp() {
        when(tracer.getTraceId()).thenReturn(String.valueOf(traceId));
    }

    @Test
    void shouldFetchStorageUrlFromEhrRepo() throws Exception {
        UUID conversationId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();
        String presignedUrl = "https://fake-presigned-url";

        wireMock.stubFor(get(urlEqualTo("/messages/" + conversationId.toString().toUpperCase() + "/" + messageId.toString().toUpperCase()))
                .withHeader("Authorization", equalTo("secret"))
                .withHeader("traceId", equalTo(String.valueOf(traceId)))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withBody(presignedUrl)
                        .withHeader("Content-Type", "application/json")));

        EhrRepoClient ehrRepoClient = new EhrRepoClient(wireMock.baseUrl(), "secret", tracer);
        PresignedUrl response = ehrRepoClient.fetchStorageUrl(conversationId, messageId);

        wireMock.verify(getRequestedFor(urlMatching("/messages/" + conversationId.toString().toUpperCase() + "/" + messageId.toString().toUpperCase()))
                .withHeader("Content-Type", matching("application/json"))
                .withHeader("Authorization", matching("secret"))
                .withHeader("traceId", matching(String.valueOf(traceId))));

        assertThat(response.getUrl(), Matchers.equalTo(new URL("https://fake-presigned-url")));
    }

    @Test
    void shouldThrowErrorWhenCannotFetchStorageUrlFromEhrRepo() throws MalformedURLException {
        UUID conversationId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();

        wireMock.stubFor(get(urlEqualTo("/messages/" + conversationId.toString().toUpperCase() + "/" + messageId.toString().toUpperCase()))
                .withHeader("Authorization", equalTo("secret"))
                .willReturn(aResponse()
                        .withStatus(503)
                        .withHeader("Content-Type", "application/json")));

        EhrRepoClient ehrRepoClient = new EhrRepoClient(wireMock.baseUrl(), "secret", tracer);
        Exception expected = assertThrows(Exception.class, () ->
                ehrRepoClient.fetchStorageUrl(conversationId, messageId)
        );
        assertThat(expected, notNullValue());

        wireMock.verify(getRequestedFor(urlMatching("/messages/" + conversationId.toString().toUpperCase() + "/" + messageId.toString().toUpperCase()))
                .withHeader("Content-Type", matching("application/json"))
                .withHeader("Authorization", matching("secret")));
    }

    @Test
    void shouldThrowDuplicateMessageExceptionWhenReceiving409() throws MalformedURLException {
        UUID conversationId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();
        String presignedUrl = "https://fake-presigned-url";

        wireMock.stubFor(get(urlEqualTo("/messages/" + conversationId.toString().toUpperCase() + "/" + messageId.toString().toUpperCase()))
                .withHeader("Authorization", equalTo("secret"))
                .willReturn(aResponse()
                        .withStatus(409)
                        .withHeader("Content-Type", "application/json")));

        EhrRepoClient ehrRepoClient = new EhrRepoClient(wireMock.baseUrl(), "secret", tracer);
        Exception expected = assertThrows(DuplicateMessageException.class, () ->
                ehrRepoClient.fetchStorageUrl(conversationId, messageId)
        );
        assertThat(expected, notNullValue());
    }

    @Test
    void shouldThrowErrorWhenCannotStoreMessageInEhrRepo() throws MalformedURLException {
        UUID conversationId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();

        wireMock.stubFor(post(urlEqualTo("/messages"))
                .withHeader("Authorization", equalTo("secret"))
                .willReturn(aResponse()
                        .withStatus(503)
                        .withHeader("Content-Type", "application/json")));

        ParsedMessage mockParsedMessage = mock(ParsedMessage.class);
        when(mockParsedMessage.getNhsNumber()).thenReturn("0123456789");
        when(mockParsedMessage.getConversationId()).thenReturn(conversationId);
        when(mockParsedMessage.getMessageId()).thenReturn(messageId);
        when(mockParsedMessage.getInteractionId()).thenReturn("RCMR_IN030000UK06");
        when(mockParsedMessage.getFragmentMessageIds()).thenReturn(Collections.emptyList());

        EhrRepoClient ehrRepoClient = new EhrRepoClient(wireMock.baseUrl(), "secret", tracer);
        Exception expected = assertThrows(HttpException.class, () ->
                ehrRepoClient.confirmMessageStored(mockParsedMessage)
        );
        assertThat(expected, notNullValue());
    }

    @Test
    void shouldConfirmMessageStoredInEhrRepo() throws Exception {
        // Setup
        UUID conversationId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();
        UUID fragmentId = UUID.randomUUID();
        String nhsNumber = "1234567890";
        String messageType = "ehrExtract";
        String interactionId = "RCMR_IN030000UK06";
        String requestBody = "{\"data\":{\"type\":\"messages\",\"id\":\"" + messageId + "\",\"attributes\":{\"conversationId\":\"" + conversationId + "\",\"messageType\":\"" + messageType + "\",\"nhsNumber\":\"" + nhsNumber + "\",\"fragmentMessageIds\":[\"" + fragmentId + "\"]}}}";

        //TODO: Refactor the below json body
        String responseBody = "{\"healthRecordStatus\":\"complete\"}";
        // Mock request
        wireMock.stubFor(post(urlEqualTo("/messages"))
                .withHeader("Authorization", equalTo("secret"))
                .withHeader("traceId", equalTo(String.valueOf(traceId)))
                .willReturn(aResponse()
                        .withStatus(201)
                        .withHeader("Content-Type", "application/json").withBody(responseBody)));

        EhrRepoClient ehrRepoClient = new EhrRepoClient(wireMock.baseUrl(), "secret", tracer);

        // Create parsed message to store
        SOAPEnvelope envelope = getSoapEnvelope(conversationId, messageId, fragmentId, interactionId);
        EhrExtractMessageWrapper ehrExtractMessageWrapper = getMessageContent(nhsNumber);
        ParsedMessage parsedMessage = new ParsedMessage(envelope, ehrExtractMessageWrapper, null);

        // Store
        var response = ehrRepoClient.confirmMessageStored(parsedMessage);

        wireMock.verify(postRequestedFor(urlMatching("/messages"))
                .withRequestBody(equalToJson((requestBody)))
                .withHeader("Content-Type", matching("application/json"))
                .withHeader("Authorization", matching("secret"))
                .withHeader("traceId", matching(String.valueOf(traceId))));
        assertEquals("complete", response.getHealthRecordStatus());

    }

    private EhrExtractMessageWrapper getMessageContent(String nhsNumber) {
        EhrExtractMessageWrapper ehrExtractMessageWrapper = new EhrExtractMessageWrapper();
        ehrExtractMessageWrapper.controlActEvent = new EhrExtractMessageWrapper.ControlActEvent();
        ehrExtractMessageWrapper.controlActEvent.subject = new EhrExtractMessageWrapper.ControlActEvent.Subject();
        ehrExtractMessageWrapper.controlActEvent.subject.ehrExtract = new EhrExtract();
        ehrExtractMessageWrapper.controlActEvent.subject.ehrExtract.recordTarget = new EhrExtract.RecordTarget();
        ehrExtractMessageWrapper.controlActEvent.subject.ehrExtract.recordTarget.patient = new Patient();
        ehrExtractMessageWrapper.controlActEvent.subject.ehrExtract.recordTarget.patient.id = new Identifier();
        ehrExtractMessageWrapper.controlActEvent.subject.ehrExtract.recordTarget.patient.id.extension = nhsNumber;
        return ehrExtractMessageWrapper;
    }

    private SOAPEnvelope getSoapEnvelope(UUID conversationId, UUID messageId, UUID fragmentId, String interactionId) {
        SOAPEnvelope envelope = new SOAPEnvelope();
        Reference reference = new Reference();
        reference.href = "mid:" + fragmentId;
        envelope.body = new SOAPBody();
        envelope.body.manifest = new ArrayList<>();
        envelope.body.manifest.add(reference);
        envelope.header = new SOAPHeader();
        envelope.header.messageHeader = new MessageHeader();
        envelope.header.messageHeader.action = interactionId;
        envelope.header.messageHeader.conversationId = conversationId;
        envelope.header.messageHeader.messageData = new MessageData();
        envelope.header.messageHeader.messageData.messageId = messageId;
        return envelope;
    }
}
