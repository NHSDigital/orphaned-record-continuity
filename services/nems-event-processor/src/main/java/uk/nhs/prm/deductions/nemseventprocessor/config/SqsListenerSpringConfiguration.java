package uk.nhs.prm.deductions.nemseventprocessor.config;

import com.amazon.sqs.javamessaging.ProviderConfiguration;
import com.amazon.sqs.javamessaging.SQSConnection;
import com.amazon.sqs.javamessaging.SQSConnectionFactory;
import com.amazon.sqs.javamessaging.SQSSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.services.sqs.SqsClient;
import uk.nhs.prm.deductions.nemseventprocessor.nemsevents.NemsEventHandler;
import uk.nhs.prm.deductions.nemseventprocessor.nemsevents.NemsEventListener;

import jakarta.jms.JMSException;
import jakarta.jms.MessageConsumer;
import jakarta.jms.Session;

@Configuration
@RequiredArgsConstructor
@Slf4j
public class SqsListenerSpringConfiguration {

    @Value("${aws.nemsEventsQueueName}")
    private String nemsEventsQueueName;

    private final NemsEventHandler nemsEventHandler;
    private final Tracer tracer;

    @Bean
    public SqsClient amazonSQSAsync() {
        return SqsClient.create();
    }

    @Bean
    public SQSConnection createConnection(SqsClient amazonSQSAsync) throws JMSException {
        SQSConnectionFactory connectionFactory = new SQSConnectionFactory(new ProviderConfiguration(), amazonSQSAsync);
        return connectionFactory.createConnection();
    }

    @Bean
    public Session createListeners(SQSConnection connection) throws JMSException, InterruptedException {
        Session session = connection.createSession(false, SQSSession.UNORDERED_ACKNOWLEDGE);
        log.info("nems event queue name : {}", nemsEventsQueueName);
        MessageConsumer consumer = session.createConsumer(session.createQueue(nemsEventsQueueName));

        consumer.setMessageListener(new NemsEventListener(nemsEventHandler, tracer));

        connection.start();

        // TODO: check if we can get rid of this
        Thread.sleep(1000);

        return session;
    }
}
