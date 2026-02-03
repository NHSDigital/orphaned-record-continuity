package uk.nhs.prm.repo.ehrtransferservice.config;

import com.amazon.sqs.javamessaging.ProviderConfiguration;
import com.amazon.sqs.javamessaging.SQSConnection;
import com.amazon.sqs.javamessaging.SQSConnectionFactory;
import com.amazon.sqs.javamessaging.SQSSession;
import software.amazon.awssdk.services.sqs.SqsClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import uk.nhs.prm.repo.ehrtransferservice.gp2gp_message_models.ParsedMessage;
import uk.nhs.prm.repo.ehrtransferservice.handlers.LargeEhrCoreMessageHandler;
import uk.nhs.prm.repo.ehrtransferservice.handlers.LargeMessageFragmentHandler;
import uk.nhs.prm.repo.ehrtransferservice.handlers.MessageHandler;
import uk.nhs.prm.repo.ehrtransferservice.handlers.NegativeAcknowledgementHandler;
import uk.nhs.prm.repo.ehrtransferservice.handlers.SmallEhrMessageHandler;
import uk.nhs.prm.repo.ehrtransferservice.listeners.NegativeAcknowledgementListener;
import uk.nhs.prm.repo.ehrtransferservice.listeners.S3ExtendedMessageListener;
import uk.nhs.prm.repo.ehrtransferservice.logging.Tracer;
import uk.nhs.prm.repo.ehrtransferservice.parsers.Parser;
import uk.nhs.prm.repo.ehrtransferservice.parsers.S3ExtendedMessageFetcher;
import uk.nhs.prm.repo.ehrtransferservice.repo_incoming.RepoIncomingEventListener;
import uk.nhs.prm.repo.ehrtransferservice.repo_incoming.RepoIncomingEventParser;
import uk.nhs.prm.repo.ehrtransferservice.repo_incoming.RepoIncomingService;

import jakarta.jms.JMSException;
import jakarta.jms.Session;

@Configuration
@RequiredArgsConstructor
@Slf4j
public class SqsListenerSpringConfiguration {
    private final Tracer tracer;
    private final RepoIncomingService repoIncomingService;
    private final RepoIncomingEventParser repoIncomingEventParser;
    private final SmallEhrMessageHandler smallEhrMessageHandler;
    private final LargeEhrCoreMessageHandler largeEhrCoreMessageHandler;
    private final Parser parser;
    private final S3ExtendedMessageFetcher s3ExtendedMessageFetcher;
    private final LargeMessageFragmentHandler largeMessageFragmentHandler;
    private final NegativeAcknowledgementHandler negativeAcknowledgementHandler;

    @Value("${aws.repoIncomingQueueName}")
    private String repoIncomingQueueName;

    @Value("${aws.smallEhrQueueName}")
    private String smallEhrQueueName;

    @Value("${aws.largeEhrQueueName}")
    private String largeEhrCoreQueueName;

    @Value("${aws.largeMessageFragmentsQueueName}")
    private String largeEhrMessageFragmentQueueName;

    @Value("${aws.nackQueueName}")
    private String negativeAckQueueName;

    @Bean
    public SqsClient sqsClient() {
        return SqsClient.create();
    }

    @Bean
    public SQSConnection createConnection(SqsClient sqsClient) throws JMSException {
        SQSConnectionFactory connectionFactory = new SQSConnectionFactory(new ProviderConfiguration(), sqsClient);
        return connectionFactory.createConnection();
    }

    @Bean
    public Session createRepoIncomingQueueListener(SQSConnection connection) throws JMSException {
        Session session = getSession(connection);

        log.info("repo incoming queue name : {}", repoIncomingQueueName);
        var incomingQueueConsumer = session.createConsumer(session.createQueue(repoIncomingQueueName));
        incomingQueueConsumer.setMessageListener(new RepoIncomingEventListener(tracer, repoIncomingService, repoIncomingEventParser));

        connection.start();

        return session;
    }

    @Bean
    public Session createSmallEhrQueueListener(SQSConnection connection) throws JMSException {
        return createS3ExtendedSqsListener(connection, "small-ehr", smallEhrQueueName, smallEhrMessageHandler);
    }

    @Bean
    public Session createLargeEhrQueueListener(SQSConnection connection) throws JMSException {
        return createS3ExtendedSqsListener(connection, "large-ehr-core", largeEhrCoreQueueName, largeEhrCoreMessageHandler);
    }

    @Bean
    public Session createLargeEhrFragmentsQueueListener(SQSConnection connection) throws JMSException {
        return createS3ExtendedSqsListener(connection, "large-ehr-fragment", largeEhrMessageFragmentQueueName, largeMessageFragmentHandler);
    }

    @Bean
    public Session createNegativeAckQueueListener(SQSConnection connection) throws JMSException {
        Session session = getSession(connection);

        log.info("nack queue name : {}", negativeAckQueueName);
        var nackConsumer = session.createConsumer(session.createQueue(negativeAckQueueName));
        nackConsumer.setMessageListener(new NegativeAcknowledgementListener(tracer, parser, negativeAcknowledgementHandler));

        connection.start();

        return session;
    }

    private Session createS3ExtendedSqsListener(SQSConnection connection,
                                                String messageTypeDescription,
                                                String queueName,
                                                MessageHandler<ParsedMessage> messageHandler) throws JMSException {
        Session session = getSession(connection);

        log.info("{} queue name: {}", messageTypeDescription, queueName);
        var ehrSmallConsumer = session.createConsumer(session.createQueue(queueName));
        ehrSmallConsumer.setMessageListener(new S3ExtendedMessageListener(messageTypeDescription, tracer, s3ExtendedMessageFetcher, messageHandler));

        connection.start();

        return session;
    }

    private Session getSession(SQSConnection connection) throws JMSException {
        return connection.createSession(false, SQSSession.UNORDERED_ACKNOWLEDGE);
    }
}
