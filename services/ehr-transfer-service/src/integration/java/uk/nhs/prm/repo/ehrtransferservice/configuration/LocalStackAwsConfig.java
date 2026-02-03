package uk.nhs.prm.repo.ehrtransferservice.configuration;

import jakarta.annotation.PostConstruct;
import jakarta.jms.ConnectionFactory;
import org.apache.activemq.ActiveMQConnectionFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.jms.DefaultJmsListenerContainerFactoryConfigurer;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.jms.config.DefaultJmsListenerContainerFactory;
import org.springframework.jms.config.JmsListenerContainerFactory;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;
import software.amazon.awssdk.services.dynamodb.waiters.DynamoDbWaiter;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.CreateTopicRequest;
import software.amazon.awssdk.services.sns.model.CreateTopicResponse;
import software.amazon.awssdk.services.sns.model.SubscribeRequest;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.CreateQueueRequest;
import software.amazon.awssdk.services.sqs.model.GetQueueAttributesRequest;
import software.amazon.awssdk.services.sqs.model.QueueAttributeName;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;

import static jakarta.jms.Session.CLIENT_ACKNOWLEDGE;
import static uk.nhs.prm.repo.ehrtransferservice.database.enumeration.TransferTableAttribute.*;

@TestConfiguration
public class LocalStackAwsConfig {

    @Autowired
    private S3Client s3Client;

    @Autowired
    private SqsClient sqsClient;

    @Autowired
    private DynamoDbClient dynamoDbClient;

    @Autowired
    private SnsClient snsClient;

    @Value("${aws.repoIncomingQueueName}")
    private String repoIncomingQueueName;

    @Value("${aws.transferTrackerDbTableName}")
    private String transferTrackerDbTableName;

    @Value("${aws.sqsLargeMessageBucketName}")
    private String sqsLargeMessageBucketName;

    @Value("${aws.largeMessageFragmentsQueueName}")
    private String largeMessageFragmentsQueueName;

    @Value("${aws.largeMessageFragmentsObservabilityQueueName}")
    private String largeMessageFragmentsObservabilityQueueName;

    @Value("${aws.smallEhrQueueName}")
    private String smallEhrQueueName;

    @Value("${aws.smallEhrObservabilityQueueName}")
    private String smallEhrObservabilityQueueName;

    @Value("${aws.largeEhrQueueName}")
    private String largeEhrQueueName;

    @Value("${aws.positiveAcksQueueName}")
    private String positiveAcksQueueName;

    @Value("${aws.parsingDlqQueueName}")
    private String parsingDlqQueueName;

    @Value("${aws.ehrCompleteQueueName}")
    private String ehrCompleteQueueName;

    @Value("${aws.nackQueueName}")
    private String nackInternalQueueName;

    @Value("${aws.transferCompleteQueueName}")
    private String transferCompleteQueueName;

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

    private static final long DYNAMO_READ_CAPACITY_UNITS = 5L;

    private static final long DYNAMO_WRITE_CAPACITY_UNITS = 5L;

    @Bean
    public static SqsClient sqsClient(
            @Value("${localstack.url}") String localstackUrl,
            @Value("${aws.region}") String region
    ) throws URISyntaxException {
        return SqsClient.builder()
                .credentialsProvider(() -> AwsBasicCredentials.create("LSIAQAAAAAAVNCBMPNSG", "LSIAQAAAAAAVNCBMPNSG"))
                .endpointOverride(new URI(localstackUrl))
                .region(Region.of(region))
                .build();
    }

    @Bean
    public JmsListenerContainerFactory<?> myFactory(
            ConnectionFactory connectionFactory,
            DefaultJmsListenerContainerFactoryConfigurer configurer
    ) {
        DefaultJmsListenerContainerFactory factory = new DefaultJmsListenerContainerFactory();
        factory.setSessionAcknowledgeMode(CLIENT_ACKNOWLEDGE);
        // This provides all boot's default to this factory, including the message converter
        configurer.configure(factory, connectionFactory);
        // You could still override some of Boot's default if necessary.
        return factory;
    }

    @Bean
    public ConnectionFactory connectionFactory() {
        ActiveMQConnectionFactory activeMQConnectionFactory = new ActiveMQConnectionFactory();
        activeMQConnectionFactory.setBrokerURL(failoverUrl());
        activeMQConnectionFactory.setPassword(brokerPassword);
        activeMQConnectionFactory.setUserName(brokerUsername);
        return activeMQConnectionFactory;
    }

    @Bean
    public static S3Client s3Client(
            @Value("${localstack.url}") String localstackUrl,
            @Value("${aws.region}") String region
    ) {
        return S3Client.builder()
                .endpointOverride(URI.create(localstackUrl))
                .region(Region.of(region))
                .credentialsProvider(StaticCredentialsProvider.create(new AwsCredentials() {
                    @Override
                    public String accessKeyId() {
                        return "LSIAQAAAAAAVNCBMPNSG";
                    }

                    @Override
                    public String secretAccessKey() {
                        return "LSIAQAAAAAAVNCBMPNSG";
                    }
                }))
                .serviceConfiguration(
                        S3Configuration.builder()
                                .pathStyleAccessEnabled(true)
                                .build())
                .build();
    }

    private String failoverUrl() {
        return String.format("failover:(%s,%s)%s", amqEndpoint1, amqEndpoint2, randomOption);
    }

    @Bean
    public static SnsClient snsClient(
            @Value("${localstack.url}") String localstackUrl,
            @Value("${aws.region}") String region
    ) {
        return SnsClient.builder()
                .endpointOverride(URI.create(localstackUrl))
                .region(Region.of(region))
                .credentialsProvider(StaticCredentialsProvider.create(new AwsCredentials() {
                    @Override
                    public String accessKeyId() {
                        return "LSIAQAAAAAAVNCBMPNSG";
                    }

                    @Override
                    public String secretAccessKey() {
                        return "LSIAQAAAAAAVNCBMPNSG";
                    }
                }))
                .build();
    }

    @Bean
    public static DynamoDbClient dynamoDbClient(
            @Value("${localstack.url}") String localstackUrl,
            @Value("${aws.region}") String region
    ) {
        return DynamoDbClient.builder()
                .endpointOverride(URI.create(localstackUrl))
                .region(Region.of(region))
                .credentialsProvider(
                        StaticCredentialsProvider.create(new AwsCredentials() {
                            @Override
                            public String accessKeyId() {
                                return "LSIAQAAAAAAVNCBMPNSG";
                            }

                            @Override
                            public String secretAccessKey() {
                                return "LSIAQAAAAAAVNCBMPNSG";
                            }
                        }))
                .build();
    }

    @PostConstruct
    public void setupTestQueuesAndTopics() {
        setupS3Bucket();
        setUpQueueAndTopics();
        createDynamoTable();
    }

    private void setUpQueueAndTopics() {
        createQueue(repoIncomingQueueName);

        createSnsTopic("test_splunk_uploader_topic");

        createQueueAndSnsReceiverSubscription(transferCompleteQueueName, "test_transfer_complete_topic");
        createQueueAndSnsReceiverSubscription(largeEhrQueueName, "test_large_ehr_topic");
        createQueueAndSnsReceiverSubscription(positiveAcksQueueName, "test_positive_acks_topic");
        createQueueAndSnsReceiverSubscription(parsingDlqQueueName, "test_dlq_topic");
        createQueueAndSnsReceiverSubscription(ehrCompleteQueueName, "test_ehr_complete_topic");
        createQueueAndSnsReceiverSubscription(nackInternalQueueName, "test_negative_acks_topic");
        createQueueAndSnsReceiverSubscription("ehr_in_unhandled_queue", "test_ehr_in_unhandled_topic");

        createQueueAndObservabilityQueueAndSnsReceiverSubscriptions(
                largeMessageFragmentsQueueName,
                largeMessageFragmentsObservabilityQueueName,
                "test_large_message_fragments_topic");

        createQueueAndObservabilityQueueAndSnsReceiverSubscriptions(
                smallEhrQueueName,
                smallEhrObservabilityQueueName,
                "test_small_ehr_topic"
        );
    }

    private void setupS3Bucket() {
        var createBucketRequest = CreateBucketRequest.builder()
                .bucket(sqsLargeMessageBucketName)
                .build();

        for (var bucket : s3Client.listBuckets().buckets()) {
            if (Objects.equals(bucket.name(), sqsLargeMessageBucketName)) {
                return;
            }
        }

        s3Client.createBucket(createBucketRequest);
        s3Client.waiter().waitUntilBucketExists(HeadBucketRequest.builder().bucket(sqsLargeMessageBucketName).build());
    }

    private void createDynamoTable() {
        final DynamoDbWaiter waiter = dynamoDbClient.waiter();
        final DescribeTableRequest tableRequest = DescribeTableRequest.builder()
                .tableName(transferTrackerDbTableName)
                .build();

        if (dynamoDbClient.listTables().tableNames().contains(transferTrackerDbTableName)) {
            deleteDynamoTable(waiter, tableRequest);
        }

        final List<KeySchemaElement> keySchema = new ArrayList<>();

        // Partition Key
        keySchema.add(KeySchemaElement.builder()
                .keyType(KeyType.HASH)
                .attributeName(INBOUND_CONVERSATION_ID.name)
                .build());

        // Sort Key
        keySchema.add(KeySchemaElement.builder()
                .keyType(KeyType.RANGE)
                .attributeName(LAYER.name)
                .build());

        final List<AttributeDefinition> attributeDefinitions = new ArrayList<>();

        attributeDefinitions.add(AttributeDefinition.builder()
                .attributeType(ScalarAttributeType.S)
                .attributeName(INBOUND_CONVERSATION_ID.name)
                .build());

        attributeDefinitions.add(AttributeDefinition.builder()
                .attributeType(ScalarAttributeType.S)
                .attributeName(LAYER.name)
                .build());

        attributeDefinitions.add(AttributeDefinition.builder()
                .attributeType(ScalarAttributeType.S)
                .attributeName(OUTBOUND_CONVERSATION_ID.name)
                .build());

        attributeDefinitions.add(AttributeDefinition.builder()
                .attributeType(ScalarAttributeType.S)
                .attributeName(NHS_NUMBER.name)
                .build());

        // NHS Number GSI
        final List<GlobalSecondaryIndex> globalSecondaryIndexes = List.of(
                GlobalSecondaryIndex.builder()
                        .indexName("NhsNumberSecondaryIndex")
                        .keySchema(KeySchemaElement.builder()
                                .keyType(KeyType.HASH)
                                .attributeName(NHS_NUMBER.name)
                                .build())
                        .projection(Projection.builder().projectionType(ProjectionType.ALL).build())
                        .provisionedThroughput(ProvisionedThroughput.builder()
                                .readCapacityUnits(DYNAMO_READ_CAPACITY_UNITS)
                                .writeCapacityUnits(DYNAMO_WRITE_CAPACITY_UNITS)
                                .build())
                        .build(),

                GlobalSecondaryIndex.builder()
                        .indexName("OutboundConversationIdSecondaryIndex")
                        .keySchema(KeySchemaElement.builder()
                                .keyType(KeyType.HASH)
                                .attributeName(OUTBOUND_CONVERSATION_ID.name)
                                .build())
                        .projection(Projection.builder().projectionType(ProjectionType.ALL).build())
                        .provisionedThroughput(ProvisionedThroughput.builder()
                                .readCapacityUnits(DYNAMO_READ_CAPACITY_UNITS)
                                .writeCapacityUnits(DYNAMO_WRITE_CAPACITY_UNITS)
                                .build())
                        .build()
        );

        final CreateTableRequest createTableRequest = CreateTableRequest.builder()
                .tableName(transferTrackerDbTableName)
                .keySchema(keySchema)
                .globalSecondaryIndexes(globalSecondaryIndexes)
                .attributeDefinitions(attributeDefinitions)
                .provisionedThroughput(ProvisionedThroughput.builder()
                        .readCapacityUnits(DYNAMO_READ_CAPACITY_UNITS)
                        .writeCapacityUnits(DYNAMO_WRITE_CAPACITY_UNITS)
                        .build()
                ).build();

        dynamoDbClient.createTable(createTableRequest);
        waiter.waitUntilTableExists(tableRequest);
    }

    private void deleteDynamoTable(DynamoDbWaiter waiter, DescribeTableRequest tableRequest) {
        final DeleteTableRequest deleteRequest = DeleteTableRequest.builder()
                .tableName(transferTrackerDbTableName)
                .build();

        dynamoDbClient.deleteTable(deleteRequest);
        waiter.waitUntilTableNotExists(tableRequest);
    }

    private void createQueueAndSnsReceiverSubscription(String queueName, String snsName) {
        String queueUrl = createQueue(queueName);
        CreateTopicResponse topic = createSnsTopic(snsName);
        createSnsTestReceiverSubscription(topic, getQueueArn(queueUrl));
    }

    private void createQueueAndObservabilityQueueAndSnsReceiverSubscriptions(
            String queueName,
            String observabilityQueueName,
            String snsName
    ) {
        String queueUrl = createQueue(queueName);
        String observabilityQueueUrl = createQueue(observabilityQueueName);

        CreateTopicResponse topic = createSnsTopic(snsName);

        createSnsTestReceiverSubscription(topic, getQueueArn(queueUrl));
        createSnsTestReceiverSubscription(topic, getQueueArn(observabilityQueueUrl));
    }

    private String createQueue(String queueName) {
        CreateQueueRequest createQueueRequest = CreateQueueRequest.builder()
                .queueName(queueName)
                .build();

        return sqsClient.createQueue(createQueueRequest).queueUrl();
    }

    private CreateTopicResponse createSnsTopic(String snsName) {
        CreateTopicRequest createTopicRequest = CreateTopicRequest.builder()
                .name(snsName)
                .build();

        return snsClient.createTopic(createTopicRequest);
    }

    private void createSnsTestReceiverSubscription(CreateTopicResponse topic, String queueArn) {
        final Map<String, String> attributes = new HashMap<>();
        attributes.put("RawMessageDelivery", "True");
        SubscribeRequest subscribeRequest = SubscribeRequest.builder()
                .topicArn(topic.topicArn())
                .protocol("sqs")
                .endpoint(queueArn)
                .attributes(attributes)
                .build();

        snsClient.subscribe(subscribeRequest);
    }
    private String getQueueArn(String queueUrl) {
        GetQueueAttributesRequest request = GetQueueAttributesRequest.builder()
                .queueUrl(queueUrl)
                .attributeNames(QueueAttributeName.QUEUE_ARN)
                .build();
        return sqsClient.getQueueAttributes(request).attributes().get(QueueAttributeName.QUEUE_ARN);
    }
}
