package uk.nhs.prm.repo.ehrtransferservice.configuration;

import com.amazon.sqs.javamessaging.AmazonSQSExtendedClient;
import com.amazon.sqs.javamessaging.ExtendedClientConfiguration;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.sns.AmazonSNS;
import com.amazonaws.services.sns.AmazonSNSClientBuilder;
import com.amazonaws.services.sqs.AmazonSQSAsync;
import com.amazonaws.services.sqs.AmazonSQSAsyncClientBuilder;
import jakarta.annotation.PostConstruct;
import jakarta.jms.ConnectionFactory;
import org.apache.activemq.ActiveMQConnectionFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
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
import software.amazon.payloadoffloading.S3BackedPayloadStore;
import software.amazon.payloadoffloading.S3Dao;
import software.amazon.sns.AmazonSNSExtendedClient;
import software.amazon.sns.SNSExtendedClientConfiguration;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;

import static javax.jms.Session.CLIENT_ACKNOWLEDGE;
import static uk.nhs.prm.repo.ehrtransferservice.database.enumeration.TransferTableAttribute.*;

@TestConfiguration
public class LocalStackAwsConfig {

    @Autowired
    private S3Client s3Client;

    @Autowired
    private AmazonSQSAsync amazonSQSAsync;

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
    public static SqsClient sqsClient(@Value("${localstack.url}") String localstackUrl) throws URISyntaxException {
        return SqsClient.builder()
                .credentialsProvider((()-> AwsBasicCredentials.create("LSIAQAAAAAAVNCBMPNSG", "LSIAQAAAAAAVNCBMPNSG")))
                .endpointOverride(new URI(localstackUrl))
                .build();
    }

    @Bean
    public JmsListenerContainerFactory<?> myFactory() {
        DefaultJmsListenerContainerFactory factory = new DefaultJmsListenerContainerFactory();
        factory.setSessionAcknowledgeMode(CLIENT_ACKNOWLEDGE);
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
    public AmazonS3 amazonS3(@Value("${localstack.url}") String localstackUrl) {
        return AmazonS3ClientBuilder.standard()
                .withCredentials(new AWSStaticCredentialsProvider(new BasicAWSCredentials("LSIAQAAAAAAVNCBMPNSG", "LSIAQAAAAAAVNCBMPNSG")))
                .withEndpointConfiguration(new AwsClientBuilder.EndpointConfiguration(localstackUrl, "eu-west-2"))
                .build();
    }

    // TODO: this S3Client bean is used to setup large message bucket only.
    // the real dependency used in code is the one above (AmazonS3 / v1).
    // Therefore: find a way to create the bucket - setting GrantFullControl using
    // the class above, then get rid of this S3Client / v2.
    @Bean
    public static S3Client s3Client(@Value("${localstack.url}") String localstackUrl) {
        return S3Client.builder()
                .endpointOverride(URI.create(localstackUrl))
                .region(Region.EU_WEST_2)
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
    public static AmazonSQSAsync amazonSQSAsync(@Value("${localstack.url}") String localstackUrl) {
        return AmazonSQSAsyncClientBuilder.standard()
                .withCredentials(new AWSStaticCredentialsProvider(new BasicAWSCredentials("LSIAQAAAAAAVNCBMPNSG", "LSIAQAAAAAAVNCBMPNSG")))
                .withEndpointConfiguration(new AwsClientBuilder.EndpointConfiguration(localstackUrl, "eu-west-2"))
                .build();
    }

    @Bean
    public static AmazonSQSExtendedClient s3SupportedSqsClient(AmazonSQSAsync sqsClient, AmazonS3 amazonS3, @Value("${aws.sqsLargeMessageBucketName}") String sqsLargeMessageBucketName) {
        return new AmazonSQSExtendedClient(sqsClient, new ExtendedClientConfiguration().withPayloadSupportEnabled(amazonS3, sqsLargeMessageBucketName, true));
    }

    @Bean
    public static AmazonSNS amazonSNS(@Value("${localstack.url}") String localstackUrl) {
        return AmazonSNSClientBuilder.standard()
                .withCredentials(new AWSStaticCredentialsProvider(new BasicAWSCredentials("LSIAQAAAAAAVNCBMPNSG", "LSIAQAAAAAAVNCBMPNSG")))
                .withEndpointConfiguration(new AwsClientBuilder.EndpointConfiguration(localstackUrl, "eu-west-2"))
                .build();
    }

    @Bean
    public static AmazonSNSExtendedClient s3SupportedSnsClient(AmazonSNS amazonSNS, AmazonS3 amazonS3, @Value("${aws.sqsLargeMessageBucketName}") String sqsLargeMessageBucketName) {
        return new AmazonSNSExtendedClient(amazonSNS, new SNSExtendedClientConfiguration(), new S3BackedPayloadStore(new S3Dao(amazonS3), sqsLargeMessageBucketName));
    }

    @Bean
    public static SnsClient snsClient(@Value("${localstack.url}") String localstackUrl) {
        return SnsClient.builder()
                .endpointOverride(URI.create(localstackUrl))
                .region(Region.EU_WEST_2)
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
    public static DynamoDbClient dynamoDbClient(@Value("${localstack.url}") String localstackUrl) {
        return DynamoDbClient.builder()
                .endpointOverride(URI.create(localstackUrl))
                .region(Region.EU_WEST_2)
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
        amazonSQSAsync.createQueue(repoIncomingQueueName);

        var fragmentQueue = amazonSQSAsync.createQueue(largeMessageFragmentsQueueName);
        var fragmentsTopic = snsClient.createTopic(CreateTopicRequest.builder().name("test_large_message_fragments_topic").build());
        createSnsTestReceiverSubscription(fragmentsTopic, getQueueArn(fragmentQueue.getQueueUrl()));

        var transferCompleteQueue = amazonSQSAsync.createQueue(transferCompleteQueueName);
        var transferCompleteTopic = snsClient.createTopic(CreateTopicRequest.builder().name("test_transfer_complete_topic").build());
        createSnsTestReceiverSubscription(transferCompleteTopic, getQueueArn(transferCompleteQueue.getQueueUrl()));

        var fragmentObservabilityQueue = amazonSQSAsync.createQueue(largeMessageFragmentsObservabilityQueueName);
        createSnsTestReceiverSubscription(fragmentsTopic, getQueueArn(fragmentObservabilityQueue.getQueueUrl()));

        var smallEhrQueue = amazonSQSAsync.createQueue(smallEhrQueueName);
        var smallEhrTopic = snsClient.createTopic(CreateTopicRequest.builder().name("test_small_ehr_topic").build());
        createSnsTestReceiverSubscription(smallEhrTopic, getQueueArn(smallEhrQueue.getQueueUrl()));

        var smallEhrObservabilityQueue = amazonSQSAsync.createQueue(smallEhrObservabilityQueueName);
        createSnsTestReceiverSubscription(smallEhrTopic, getQueueArn(smallEhrObservabilityQueue.getQueueUrl()));

        var largeEhrQueue = amazonSQSAsync.createQueue(largeEhrQueueName);
        var largeEhrTopic = snsClient.createTopic(CreateTopicRequest.builder().name("test_large_ehr_topic").build());
        createSnsTestReceiverSubscription(largeEhrTopic, getQueueArn(largeEhrQueue.getQueueUrl()));

        var positiveAcksTopic = snsClient.createTopic(CreateTopicRequest.builder().name("test_positive_acks_topic").build());
        var positiveAcksQueue = amazonSQSAsync.createQueue(positiveAcksQueueName);
        createSnsTestReceiverSubscription(positiveAcksTopic, getQueueArn(positiveAcksQueue.getQueueUrl()));

        var parsingDlqTopic = snsClient.createTopic(CreateTopicRequest.builder().name("test_dlq_topic").build());
        var parsingDlqQueue = amazonSQSAsync.createQueue(parsingDlqQueueName);
        createSnsTestReceiverSubscription(parsingDlqTopic, getQueueArn(parsingDlqQueue.getQueueUrl()));

        var ehrCompleteQueue = amazonSQSAsync.createQueue(ehrCompleteQueueName);
        var ehrCompleteTopic = snsClient.createTopic(CreateTopicRequest.builder().name("test_ehr_complete_topic").build());
        createSnsTestReceiverSubscription(ehrCompleteTopic, getQueueArn(ehrCompleteQueue.getQueueUrl()));

        var nackTopic = snsClient.createTopic(CreateTopicRequest.builder().name("test_negative_acks_topic").build());
        var nackQueue = amazonSQSAsync.createQueue(nackInternalQueueName);
        createSnsTestReceiverSubscription(nackTopic, getQueueArn(nackQueue.getQueueUrl()));

        var ehrInUnhandledTopic = snsClient.createTopic(CreateTopicRequest.builder().name("test_ehr_in_unhandled_topic").build());
        var ehrInUnhandledObservabilityQueue = amazonSQSAsync.createQueue("ehr_in_unhandled_queue");
        createSnsTestReceiverSubscription(ehrInUnhandledTopic, getQueueArn(ehrInUnhandledObservabilityQueue.getQueueUrl()));

        snsClient.createTopic(CreateTopicRequest.builder().name("test_splunk_uploader_topic").build());
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
        var queueAttributes = amazonSQSAsync.getQueueAttributes(queueUrl, List.of("QueueArn"));
        return queueAttributes.getAttributes().get("QueueArn");
    }
}
