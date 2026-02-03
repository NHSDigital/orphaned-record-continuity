package uk.nhs.prm.repo.ehrtransferservice.services;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import uk.nhs.prm.repo.ehrtransferservice.gp2gp_message_models.ParsedMessage;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.put;
import static com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Tag("unit")
class PresignedUrlTest {
    @RegisterExtension
    static WireMockExtension wireMock = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    @Test
    void shouldUploadMessageToS3() throws IOException, URISyntaxException, InterruptedException {
        URL url = new URL(wireMock.baseUrl());
        String messageBody = "test";
        ParsedMessage parsedMessage = new ParsedMessage(null, null, messageBody);
        wireMock.stubFor(put(urlEqualTo("/")).willReturn(aResponse().withStatus(200)));

        PresignedUrl presignedUrl = new PresignedUrl(url);
        presignedUrl.uploadMessage(parsedMessage);

        wireMock.verify(putRequestedFor(urlMatching("/"))
                .withRequestBody(equalTo(messageBody)));
    }

    @Test
    void shouldThrowErrorWhenCannotUploadMessageToS3() throws MalformedURLException {
        URL url = new URL(wireMock.baseUrl());
        String messageBody = "test";
        ParsedMessage parsedMessage = new ParsedMessage(null, null, messageBody);
        wireMock.stubFor(put(urlEqualTo("/")).willReturn(aResponse().withStatus(503)));

        PresignedUrl presignedUrl = new PresignedUrl(url);
        Exception expected = assertThrows(RuntimeException.class, () ->
                presignedUrl.uploadMessage(parsedMessage)
        );
        assertThat(expected, notNullValue());

        wireMock.verify(putRequestedFor(urlMatching("/"))
                .withRequestBody(equalTo(messageBody)));
    }
}
