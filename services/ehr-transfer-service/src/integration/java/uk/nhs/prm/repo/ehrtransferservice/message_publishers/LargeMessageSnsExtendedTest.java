package uk.nhs.prm.repo.ehrtransferservice.message_publishers;

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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import uk.nhs.prm.repo.ehrtransferservice.activemq.ForceXercesParserExtension;
import uk.nhs.prm.repo.ehrtransferservice.configuration.LocalStackAwsConfig;
import uk.nhs.prm.repo.ehrtransferservice.handlers.LargeEhrCoreMessageHandler;
import uk.nhs.prm.repo.ehrtransferservice.logging.Tracer;
import uk.nhs.prm.repo.ehrtransferservice.parsers.LargeSqsMessageParser;
import uk.nhs.prm.repo.ehrtransferservice.utils.SqsQueueUtility;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.ArgumentCaptor;

@SpringBootTest
@ActiveProfiles("test")
@ExtendWith(SpringExtension.class)
@ExtendWith(ForceXercesParserExtension.class)
@ContextConfiguration(classes = LocalStackAwsConfig.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
public class LargeMessageSnsExtendedTest {
    // SNS/SQS max message size is 256KB. This 300KB payload exceeds that to test the extended clients work as intended.
    private static final int LARGE_PAYLOAD_SIZE_BYTES = 300 * 1024; // 300KiB
    private static final int SMALL_PAYLOAD_SIZE_BYTES = 100 * 1024; // 100KiB

    @Autowired
    private MessagePublisher messagePublisher;

    @Autowired
    private SqsQueueUtility sqsQueueUtility;

    @MockitoBean
    private Tracer tracer;

    @MockitoBean
    private LargeSqsMessageParser largeSqsMessageParser;

    @MockitoBean
    private LargeEhrCoreMessageHandler largeEhrCoreMessageHandler;

    @Value("${aws.largeEhrTopicArn}")
    private String largeEhrTopicArn;

    @Value("${aws.largeEhrQueueName}")
    private String largeEhrQueueName;

    @BeforeEach
    void setUp() {
        when(tracer.getTraceId()).thenReturn("itest-trace-id-123");
        sqsQueueUtility.purgeQueue(largeEhrQueueName);
    }

    @AfterEach
    void afterEach() {
        sqsQueueUtility.purgeQueue(largeEhrQueueName);
    }

    @Test
    void shouldSuccessfullyPublishPayloadLargerThan256KBWithExtendedClient() {
        String payload = "A".repeat(LARGE_PAYLOAD_SIZE_BYTES);

        assertDoesNotThrow(() -> messagePublisher.sendMessage(largeEhrTopicArn, payload));

        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            verify(largeSqsMessageParser, atLeastOnce()).parse(messageCaptor.capture());
            String receivedPayload = messageCaptor.getValue();
            assertTrue(receivedPayload.contains(payload));
        });
    }

    @Test
    void shouldSuccessfullyPublishPayloadSmallerThan256KBWithExtendedClient() {
        String payload = "A".repeat(SMALL_PAYLOAD_SIZE_BYTES);

        assertDoesNotThrow(() -> messagePublisher.sendMessage(largeEhrTopicArn, payload));

        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            verify(largeSqsMessageParser, atLeastOnce()).parse(messageCaptor.capture());
            String receivedPayload = messageCaptor.getValue();
            assertTrue(receivedPayload.contains(payload));
        });
    }
}