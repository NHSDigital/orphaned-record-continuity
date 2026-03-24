package uk.nhs.prm.repo.suspension.service.infra;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.cloudwatch.CloudWatchClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;
import software.amazon.awssdk.services.dynamodb.waiters.DynamoDbWaiter;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.CreateTopicRequest;
import software.amazon.awssdk.services.sns.model.CreateTopicResponse;
import software.amazon.awssdk.services.sns.model.SubscribeRequest;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.CreateQueueResponse;
import software.amazon.awssdk.services.sqs.model.GetQueueAttributesRequest;
import software.amazon.awssdk.services.sqs.model.QueueAttributeName;

import jakarta.annotation.PostConstruct;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@TestConfiguration
public class LocalStackAwsConfig {

    private static final Region LOCALSTACK_REGION = Region.EU_WEST_2;

    private static final StaticCredentialsProvider LOCALSTACK_CREDENTIALS =
            StaticCredentialsProvider.create(AwsBasicCredentials.create("LSIA5678901234567890", "LSIA5678901234567890"));

    @Autowired
    private SqsClient sqsClient;

    @Autowired
    private SnsClient snsClient;

    @Autowired
    private DynamoDbClient dynamoDbClient;

    @Value("${aws.incomingQueueName}")
    private String suspensionsQueueName;

    @Value("${aws.notSuspendedQueueName}")
    private String notSuspendedQueueName;

    @Value("${aws.mofUpdatedQueueName}")
    private String mofUpdatedQueueName;

    @Value("${aws.eventOutOfOrderQueueName}")
    private String eventOutOfOrderQueueName;

    @Value("${aws.invalidSuspensionAuditQueueName}")
    private String invalidSuspensionAuditQueueName;

    @Value("${aws.invalidSuspensionQueueName}")
    private String invalidSuspensionQueueName;

    @Value("${aws.suspensionDynamoDbTableName}")
    private String suspensionDynamoDbTableName;

    @Value("${aws.acknowledgementQueue}")
    private String ackQueueName;

    @Value("${aws.repoIncomingQueueName}")
    private String repoIncomingQueueName;

    @Value("${aws.activeSuspensionsQueueName}")
    private String activeSuspensionsQueueName;

    @Bean
    public static SqsClient sqsClient(@Value("${localstack.url}") String localstackUrl) {
        return SqsClient.builder()
                .endpointOverride(URI.create(localstackUrl))
                .region(LOCALSTACK_REGION)
                .credentialsProvider(LOCALSTACK_CREDENTIALS)
                .build();
    }

    @Bean
    public static SnsClient snsClient(@Value("${localstack.url}") String localstackUrl) {
        return SnsClient.builder()
                .endpointOverride(URI.create(localstackUrl))
                .region(LOCALSTACK_REGION)
                .credentialsProvider(LOCALSTACK_CREDENTIALS)
                .build();
    }

    @Bean
    public static DynamoDbClient dynamoDbClient(@Value("${localstack.url}") String localstackUrl) {
        return DynamoDbClient.builder()
                .endpointOverride(URI.create(localstackUrl))
                .region(LOCALSTACK_REGION)
                .credentialsProvider(LOCALSTACK_CREDENTIALS)
                .build();
    }

    @Bean
    @Primary
    public static CloudWatchClient cloudwatchClient(@Value("${localstack.url}") String localstackUrl) {
        return CloudWatchClient.builder()
                .endpointOverride(URI.create(localstackUrl))
                .region(LOCALSTACK_REGION)
                .credentialsProvider(LOCALSTACK_CREDENTIALS)
                .build();
    }

    @PostConstruct
    public void setupTestQueuesAndTopics() {
        sqsClient.createQueue(builder -> builder.queueName(suspensionsQueueName));
        sqsClient.createQueue(builder -> builder.queueName(ackQueueName));
        CreateQueueResponse notSuspendedQueue = sqsClient.createQueue(builder -> builder.queueName(notSuspendedQueueName));
        CreateQueueResponse mofUpdatedQueue = sqsClient.createQueue(builder -> builder.queueName(mofUpdatedQueueName));
        CreateQueueResponse eventOutOfOrderQueue = sqsClient.createQueue(builder -> builder.queueName(eventOutOfOrderQueueName));
        CreateQueueResponse invalidSuspensionQueue = sqsClient.createQueue(builder -> builder.queueName(invalidSuspensionQueueName));
        CreateQueueResponse invalidSuspensionAuditQueue = sqsClient.createQueue(builder -> builder.queueName(invalidSuspensionAuditQueueName));
        CreateQueueResponse incomingQueue = sqsClient.createQueue(builder -> builder.queueName(repoIncomingQueueName));
        // Queue created in re-registration service, only used here for checking that message is received on active-suspensions topic
        CreateQueueResponse activeSuspensionsQueue = sqsClient.createQueue(builder -> builder.queueName(activeSuspensionsQueueName));

        var topic = snsClient.createTopic(CreateTopicRequest.builder().name("test_not_suspended_topic").build());
        var mofUpdatedTopic = snsClient.createTopic(CreateTopicRequest.builder().name("mof_updated_sns_topic").build());
        var eventOutOfOrderTopic = snsClient.createTopic(CreateTopicRequest.builder().name("event_out_of_order_topic").build());
        var invalidSuspensionTopic = snsClient.createTopic(CreateTopicRequest.builder().name("invalid_suspension_topic").build());
        var nonSensitiveInvalidSuspensionTopic = snsClient.createTopic(CreateTopicRequest.builder().name("invalid_suspension_audit_topic").build());
        var repoIncomingTopic = snsClient.createTopic(CreateTopicRequest.builder().name("repo_incoming_sns_topic").build());
        var activeSuspensionsTopic = snsClient.createTopic(CreateTopicRequest.builder().name("active_suspensions_sns_topic").build());

        createSnsTestReceiverSubscription(topic, getQueueArn(notSuspendedQueue.queueUrl()));
        createSnsTestReceiverSubscription(mofUpdatedTopic, getQueueArn(mofUpdatedQueue.queueUrl()));
        createSnsTestReceiverSubscription(eventOutOfOrderTopic, getQueueArn(eventOutOfOrderQueue.queueUrl()));
        createSnsTestReceiverSubscription(invalidSuspensionTopic, getQueueArn(invalidSuspensionQueue.queueUrl()));
        createSnsTestReceiverSubscription(nonSensitiveInvalidSuspensionTopic, getQueueArn(invalidSuspensionAuditQueue.queueUrl()));
        createSnsTestReceiverSubscription(repoIncomingTopic, getQueueArn(incomingQueue.queueUrl()));
        createSnsTestReceiverSubscription(activeSuspensionsTopic, getQueueArn(activeSuspensionsQueue.queueUrl()));

        setupDbAndTable();
    }

    private void setupDbAndTable() {
        var waiter = dynamoDbClient.waiter();
        var tableRequest = DescribeTableRequest.builder()
                .tableName(suspensionDynamoDbTableName)
                .build();

        if (dynamoDbClient.listTables().tableNames().contains(suspensionDynamoDbTableName)) {
            resetTableForLocalEnvironment(waiter, tableRequest);
        }

        List<KeySchemaElement> keySchema = new ArrayList<>();
        keySchema.add(KeySchemaElement.builder()
                .keyType(KeyType.HASH)
                .attributeName("nhs_number")
                .build());

        var attributeDefinitions = new ArrayList<AttributeDefinition>();
        attributeDefinitions.add(AttributeDefinition.builder()
                .attributeType(ScalarAttributeType.S)
                .attributeName("nhs_number")
                .build());

        var createTableRequest = CreateTableRequest.builder()
                .tableName(suspensionDynamoDbTableName)
                .keySchema(keySchema)
                .attributeDefinitions(attributeDefinitions)
                .provisionedThroughput(ProvisionedThroughput.builder()
                        .readCapacityUnits(5L)
                        .writeCapacityUnits(5L)
                        .build())
                .build();

        dynamoDbClient.createTable(createTableRequest);
        waiter.waitUntilTableExists(tableRequest);
    }

    private void resetTableForLocalEnvironment(DynamoDbWaiter waiter, DescribeTableRequest tableRequest) {
        var deleteRequest = DeleteTableRequest.builder().tableName(suspensionDynamoDbTableName).build();
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
        var queueAttributes = sqsClient.getQueueAttributes(GetQueueAttributesRequest.builder()
                .queueUrl(queueUrl)
                .attributeNames(QueueAttributeName.QUEUE_ARN)
                .build());
        return queueAttributes.attributes().get(QueueAttributeName.QUEUE_ARN);
    }
}
