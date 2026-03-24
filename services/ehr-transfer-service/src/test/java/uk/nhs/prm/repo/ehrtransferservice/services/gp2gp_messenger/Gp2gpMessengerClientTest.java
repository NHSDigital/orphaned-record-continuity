package uk.nhs.prm.repo.ehrtransferservice.services.gp2gp_messenger;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.google.gson.Gson;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import uk.nhs.prm.repo.ehrtransferservice.logging.Tracer;
import uk.nhs.prm.repo.ehrtransferservice.exceptions.HttpException;
import uk.nhs.prm.repo.ehrtransferservice.gp2gp_message_models.Gp2gpMessengerContinueMessageRequestBody;
import uk.nhs.prm.repo.ehrtransferservice.gp2gp_message_models.Gp2gpMessengerEhrRequestBody;
import uk.nhs.prm.repo.ehrtransferservice.gp2gp_message_models.Gp2gpMessengerPositiveAcknowledgementRequestBody;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.matching;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Tag("unit")
class Gp2gpMessengerClientTest {
    Tracer tracer = new Tracer();

    @RegisterExtension
    static WireMockExtension wireMock = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    @Test
    void shouldCallGP2GpMessengerEHRRequest() throws IOException, URISyntaxException, InterruptedException, HttpException {

        Gp2gpMessengerEhrRequestBody requestBody = new Gp2gpMessengerEhrRequestBody("", "", "", "");

        String jsonPayloadString = new Gson().toJson(requestBody);

        wireMock.stubFor(post(urlEqualTo("/health-record-requests/1234567890"))
                .withHeader("Authorization", matching("secret"))
                .willReturn(aResponse()
                        .withStatus(204)
                        .withBody(jsonPayloadString)
                        .withHeader("Content-Type", "application/json")));

        tracer.directlyUpdateTraceIdButNotConversationId("some-trace-id");

        Gp2gpMessengerClient gp2gpMessengerClient = new Gp2gpMessengerClient(wireMock.baseUrl(), "secret", tracer);
        gp2gpMessengerClient.sendGp2gpMessengerEhrRequest("1234567890", requestBody);

        wireMock.verify(postRequestedFor(urlMatching("/health-record-requests/1234567890"))
                .withRequestBody(equalToJson((jsonPayloadString)))
                .withHeader("Content-Type", matching("application/json"))
                .withHeader("Authorization", matching("secret")));

    }

    @Test
    void shouldCallGP2GpMessengerPositiveRequest() throws IOException, URISyntaxException, InterruptedException, HttpException {

        Gp2gpMessengerPositiveAcknowledgementRequestBody requestBody = new Gp2gpMessengerPositiveAcknowledgementRequestBody("", "", "", "");

        String jsonPayloadString = new Gson().toJson(requestBody);

        wireMock.stubFor(post(urlEqualTo("/health-record-requests/1234567890/acknowledgement"))
                .withHeader("Authorization", matching("secret"))
                .withRequestBody(equalTo(jsonPayloadString))
                .willReturn(aResponse()
                        .withStatus(204)
                        .withHeader("Content-Type", "application/json")));

        tracer.directlyUpdateTraceIdButNotConversationId("some-trace-id");

        Gp2gpMessengerClient gp2gpMessengerClient = new Gp2gpMessengerClient(wireMock.baseUrl(), "secret", tracer);
        gp2gpMessengerClient.sendGp2gpMessengerAcknowledgement("1234567890", requestBody);

        wireMock.verify(postRequestedFor(urlMatching("/health-record-requests/1234567890/acknowledgement"))
                .withRequestBody(equalToJson((jsonPayloadString)))
                .withHeader("Content-Type", matching("application/json"))
                .withHeader("Authorization", matching("secret")));
    }

    @Test
    void shouldThrowHTTPExceptionWhenWeGotAnyStatusCodeButNot204ForPositiveAcknowledgement() throws IOException {
        Gp2gpMessengerPositiveAcknowledgementRequestBody requestBody = new Gp2gpMessengerPositiveAcknowledgementRequestBody("", "", "", "");

        String jsonPayloadString = new Gson().toJson(requestBody);

        wireMock.stubFor(post(urlEqualTo("/health-record-requests/1234567890/acknowledgement"))
                .withHeader("Authorization", matching("secret"))
                .withRequestBody(equalTo(jsonPayloadString))
                .willReturn(aResponse()
                        .withStatus(500)
                        .withHeader("Content-Type", "application/json")));

        tracer.directlyUpdateTraceIdButNotConversationId("some-trace-id");

        Gp2gpMessengerClient gp2gpMessengerClient = new Gp2gpMessengerClient(wireMock.baseUrl(), "secret", tracer);
        assertThrows(HttpException.class, () -> gp2gpMessengerClient.sendGp2gpMessengerAcknowledgement("1234567890", requestBody));
    }

    @Test
    void shouldCallRequestBuilderWithExpectedRequestBody() throws IOException, HttpException, URISyntaxException, InterruptedException {
        UUID conversationId = UUID.randomUUID();
        UUID ehrExtractMessageId = UUID.randomUUID();
        Gp2gpMessengerContinueMessageRequestBody continueMessageRequestBody = new Gp2gpMessengerContinueMessageRequestBody(conversationId, "gp-ods-code", ehrExtractMessageId);
        var jsonPayloadString = new Gson().toJson(continueMessageRequestBody);
        wireMock.stubFor(post(urlEqualTo("/health-record-requests/continue-message"))
                .withHeader("Authorization", matching("secret"))
                .willReturn(aResponse()
                        .withStatus(204)
                        .withBody(jsonPayloadString)
                        .withHeader("Content-Type", "application/json")));

        tracer.directlyUpdateTraceIdButNotConversationId("some-trace-id");

        var gp2gpMessengerClient = new Gp2gpMessengerClient(wireMock.baseUrl(), "secret", tracer);
        gp2gpMessengerClient.sendContinueMessage(continueMessageRequestBody);

        wireMock.verify(postRequestedFor(urlMatching("/health-record-requests/continue-message"))
                .withRequestBody(equalToJson((jsonPayloadString)))
                .withHeader("Content-Type", matching("application/json"))
                .withHeader("Authorization", matching("secret")));
    }

    @Test
    void shouldThrowAnExceptionWhenResponseStatusCodeIsNot204() throws IOException {
        UUID conversationId = UUID.randomUUID();
        UUID ehrExtractMessageId = UUID.randomUUID();
        Gp2gpMessengerContinueMessageRequestBody continueMessageRequestBody = new Gp2gpMessengerContinueMessageRequestBody(conversationId, "gp-ods-code", ehrExtractMessageId);
        var jsonPayloadString = new Gson().toJson(continueMessageRequestBody);
        wireMock.stubFor(post(urlEqualTo("/health-record-requests/continue-message"))
                .withHeader("Authorization", matching("secret"))
                .willReturn(aResponse()
                        .withStatus(500)
                        .withBody(jsonPayloadString)
                        .withHeader("Content-Type", "application/json")));

        tracer.directlyUpdateTraceIdButNotConversationId("some-trace-id");

        var gp2gpMessengerClient = new Gp2gpMessengerClient(wireMock.baseUrl(), "secret", tracer);
        assertThrows(HttpException.class, () -> gp2gpMessengerClient.sendContinueMessage(continueMessageRequestBody));
    }
}
