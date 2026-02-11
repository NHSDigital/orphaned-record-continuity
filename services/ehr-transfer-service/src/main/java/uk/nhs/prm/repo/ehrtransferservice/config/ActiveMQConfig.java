package uk.nhs.prm.repo.ehrtransferservice.config;

import jakarta.jms.ConnectionFactory;
import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.activemq.ActiveMQSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jms.config.DefaultJmsListenerContainerFactory;

import java.util.Collections;

@Configuration
public class ActiveMQConfig {

    @Value("${activemq.openwireEndpoint1}")
    private String amqEndpoint1;

    @Value("${activemq.openwireEndpoint2}")
    private String amqEndpoint2;

    @Value("${activemq.userName}")
    private String brokerUsername;

    @Value("${activemq.password}")
    private String brokerPassword;

    @Value("${activemq.randomOption}")
    private String randomOption;

    @Bean
    public DefaultJmsListenerContainerFactory myFactory(ConnectionFactory connectionFactory) {
        DefaultJmsListenerContainerFactory factory = new DefaultJmsListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setSessionAcknowledgeMode(ActiveMQSession.INDIVIDUAL_ACKNOWLEDGE);
        return factory;
    }

    @Bean
    public ConnectionFactory connectionFactory() {
        ActiveMQConnectionFactory activeMQConnectionFactory = new ActiveMQConnectionFactory();
        activeMQConnectionFactory.setTrustedPackages(Collections.singletonList("uk.nhs.prm.repo.ehrtransferservice"));
        activeMQConnectionFactory.setBrokerURL(failoverUrl());
        activeMQConnectionFactory.setPassword(brokerPassword);
        activeMQConnectionFactory.setUserName(brokerUsername);
        return activeMQConnectionFactory;
    }

    private String failoverUrl() {
        return String.format("failover:(%s,%s)%s", amqEndpoint1, amqEndpoint2, randomOption);
    }
}