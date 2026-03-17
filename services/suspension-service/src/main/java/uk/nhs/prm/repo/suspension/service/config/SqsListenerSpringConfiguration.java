package uk.nhs.prm.repo.suspension.service.config;

import com.amazon.sqs.javamessaging.ProviderConfiguration;
import com.amazon.sqs.javamessaging.SQSConnectionFactory;
import com.amazon.sqs.javamessaging.SQSSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.jms.config.DefaultJmsListenerContainerFactory;
import org.springframework.jms.config.SimpleJmsListenerEndpoint;
import org.springframework.jms.listener.DefaultMessageListenerContainer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;
import uk.nhs.prm.repo.suspension.service.suspensionsevents.SuspensionMessageProcessor;
import uk.nhs.prm.repo.suspension.service.suspensionsevents.SuspensionsEventListener;

@Configuration
@RequiredArgsConstructor
@Slf4j
public class SqsListenerSpringConfiguration {

    @Value("${aws.incomingQueueName}")
    private String suspensionsQueueName;

    @Value("${suspension.concurrency.min.max}")
    private String concurrencyMinMax;

    @Value("${suspension.concurrency.max.messages.per.task}")
    private Integer maxMessagesPerTask;

    @Value("${suspension.thread.core.pool.size}")
    private Integer threadCorePoolSize;

    @Value("${suspension.thread.max.pool.size}")
    private Integer threadMaxPoolSize;

    @Value("${AWS_REGION:eu-west-2}")
    private String awsRegion;

    private final SuspensionMessageProcessor suspensionsEventProcessor;
    private final Tracer tracer;

    @Bean
    public SqsClient sqsClient() {
        return SqsClient.builder()
                .region(Region.of(awsRegion))
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();
    }

    @Bean
    public DefaultMessageListenerContainer jmsListener(DefaultJmsListenerContainerFactory jmsListenerContainerFactory) {
        SimpleJmsListenerEndpoint simpleJmsListenerEndpoint = new SimpleJmsListenerEndpoint();
        simpleJmsListenerEndpoint.setMessageListener(new SuspensionsEventListener(suspensionsEventProcessor, tracer));
        simpleJmsListenerEndpoint.setDestination(suspensionsQueueName);
        return jmsListenerContainerFactory.createListenerContainer(simpleJmsListenerEndpoint);
    }

    @Bean
    public DefaultJmsListenerContainerFactory jmsListenerContainerFactory(SqsClient sqsClient) {
        ProviderConfiguration providerConfiguration = new ProviderConfiguration().withNumberOfMessagesToPrefetch(0);
        SQSConnectionFactory connectionFactory = new SQSConnectionFactory(providerConfiguration, sqsClient);
        DefaultJmsListenerContainerFactory factory = new DefaultJmsListenerContainerFactory();
        factory.setConcurrency(concurrencyMinMax);
        factory.setSessionAcknowledgeMode(SQSSession.UNORDERED_ACKNOWLEDGE);
        factory.setTaskExecutor(createDefaultTaskExecutor());
        factory.setMaxMessagesPerTask(maxMessagesPerTask);
        factory.setConnectionFactory(connectionFactory);
        return factory;
    }

    protected AsyncTaskExecutor createDefaultTaskExecutor() {
        ThreadPoolTaskExecutor threadPoolTaskExecutor = new ThreadPoolTaskExecutor();
        threadPoolTaskExecutor.setThreadNamePrefix("SQSExecutor - ");
        threadPoolTaskExecutor.setCorePoolSize(threadCorePoolSize);
        threadPoolTaskExecutor.setMaxPoolSize(threadMaxPoolSize);
        threadPoolTaskExecutor.afterPropertiesSet();
        return threadPoolTaskExecutor;
    }
}
