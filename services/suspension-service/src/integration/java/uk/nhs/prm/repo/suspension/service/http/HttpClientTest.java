package uk.nhs.prm.repo.suspension.service.http;

import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.web.client.HttpServerErrorException;
import uk.nhs.prm.repo.suspension.service.config.HttpClientConfig;
import uk.nhs.prm.repo.suspension.service.config.RestClientSpringConfiguration;
import uk.nhs.prm.repo.suspension.service.config.Tracer;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = {HttpServiceClient.class, RestClientSpringConfiguration.class, HttpClientConfig.class, Tracer.class})
public class HttpClientTest {

    public static final String BOB_BANANA_AUTH_TOKEN = "Ym9iOmJhbmFuYQ==";
    private WireMockServer wireMockServer;

    @Autowired
    private HttpServiceClient httpServiceClient;

    @BeforeEach
    public void setUp() {
        wireMockServer = initializeWebServer();
        configureFor("localhost", wireMockServer.port());

    }

    @Test
    void shouldPerformPutWithAuthAndReturnBodyAndResponseCode() {
        stubFor(put(urlMatching("/put-path"))
                .withHeader("Authorization", matching("Basic " + BOB_BANANA_AUTH_TOKEN))
                .withRequestBody(equalTo("some request payload"))
                .willReturn(aResponse()
                        .withStatus(203)
                        .withHeader("Content-Type", "application/json")
                        .withHeader("Connection", "close")
                        .withBody("some body")));

        ResponseEntity<String> responsePayload = httpServiceClient.putWithStatusCode(
                "http://localhost:8080/put-path",
                "bob",
                "banana",
                "some request payload");

        assertThat(responsePayload.getBody()).isEqualTo("some body");
        assertThat(responsePayload.getStatusCodeValue()).isEqualTo(203);
    }

    @Test
    void shouldReturnResponseWithStatusCode() {
        stubFor(get(urlMatching("/get-path"))
                .withHeader("Authorization", matching("Basic " + BOB_BANANA_AUTH_TOKEN))
                .willReturn(aResponse()
                        .withStatus(202)
                        .withHeader("Content-Type", "application/json")
                        .withHeader("Connection", "close")
                        .withBody("some body")));

        ResponseEntity<String> response = httpServiceClient.getWithStatusCode(
                "http://localhost:8080/get-path",
                "bob",
                "banana");

        assertThat(response.getStatusCodeValue()).isEqualTo(202);
    }


    @Test
    void shouldThrowException() {
        stubFor(get(urlMatching("/get-path"))
                .withHeader("Authorization", matching("Basic " + BOB_BANANA_AUTH_TOKEN))
                .willReturn(aResponse()
                        .withStatus(502)
                        .withHeader("Content-Type", "application/json")
                        .withHeader("Connection", "close")
                        .withBody("some body")));

        Assertions.assertThrows(HttpServerErrorException.class, () ->
                httpServiceClient.getWithStatusCode(
                        "http://localhost:8080/get-path",
                        "bob",
                        "banana"));
    }

    @AfterEach
    public void tearDown() {
        wireMockServer.stop();
    }

    private WireMockServer initializeWebServer() {
        WireMockServer wireMockServer = new WireMockServer(8080);
        wireMockServer.start();

        return wireMockServer;
    }
}
