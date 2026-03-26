package uk.nhs.prm.repo.re_registration.ehr_repo;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import uk.nhs.prm.repo.re_registration.infra.LocalStackAwsConfig;
import uk.nhs.prm.repo.re_registration.model.ReRegistrationEvent;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;


@SpringBootTest()
@ActiveProfiles("test")
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = LocalStackAwsConfig.class)
@DirtiesContext
public class EhrRepoServiceIntegrationTest {
    @Autowired
    private EhrRepoService ehrRepoService;

    private WireMockServer stubEhrRepo;

    public static final String NHS_NUMBER = "1234567890";
    private static final String AUTHORIZATION_HEADER = "test";
    private static final String NEMS_MESSAGE_ID = "nemsMessageId";
    public static final String CONVERSATION_ID1 = "2431d4ff-f760-4ab9-8cd8-a3fc47846762";
    public static final String CONVERSATION_ID2 = "c184cc19-86e9-4a95-b5b5-2f156900bb3c";


    @BeforeEach
    public void setUp() {
        stubEhrRepo = initializeWebServer();
    }

    @AfterEach
    public void tearDown() {
        stubEhrRepo.resetAll();
        stubEhrRepo.stop();
    }

    private WireMockServer initializeWebServer() {
        final WireMockServer wireMockServer = new WireMockServer(8080);
        wireMockServer.start();
        return wireMockServer;
    }

    @Test
    void shouldSendMessageWithActionOnAuditTopicWhenEhrRepoReturns200() throws JsonProcessingException {
        ehrRepo200Response();
        var ehrResponse = ehrRepoService.deletePatientEhr(getReRegistrationEvent());
        var conversationIds = ehrResponse.getConversationIds();

        assertThat(conversationIds.size()).isEqualTo(2);
        assertThat(conversationIds).contains(CONVERSATION_ID1);
        assertThat(conversationIds).contains(CONVERSATION_ID2);
    }

    private void ehrRepo200Response() {
        stubFor(delete(urlMatching("/patients/" + NHS_NUMBER))
                .withHeader("Authorization", equalTo(AUTHORIZATION_HEADER))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withBody("{\n" +
                                "  \"data\": {\n" +
                                "    \"type\": \"patients\",\n" +
                                "    \"id\": " + NHS_NUMBER + ",\n" +
                                "    \"conversationIds\":[\"2431d4ff-f760-4ab9-8cd8-a3fc47846762\"," + "\"c184cc19-86e9-4a95-b5b5-2f156900bb3c\"]}}")
                        .withHeader("Content-Type", "application/json")));
    }

    private ReRegistrationEvent getReRegistrationEvent() {
        return new ReRegistrationEvent(NHS_NUMBER, "ABC123", NEMS_MESSAGE_ID, "2017-11-01T15:00:33+00:00");
    }

}
