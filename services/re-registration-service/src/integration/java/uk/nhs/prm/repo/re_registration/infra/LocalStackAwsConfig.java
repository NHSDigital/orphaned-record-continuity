package uk.nhs.prm.repo.re_registration.infra;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeDefinition;
import software.amazon.awssdk.services.dynamodb.model.CreateTableRequest;
import software.amazon.awssdk.services.dynamodb.model.DeleteTableRequest;
import software.amazon.awssdk.services.dynamodb.model.DescribeTableRequest;
import software.amazon.awssdk.services.dynamodb.model.KeySchemaElement;
import software.amazon.awssdk.services.dynamodb.model.KeyType;
import software.amazon.awssdk.services.dynamodb.model.ProvisionedThroughput;
import software.amazon.awssdk.services.dynamodb.model.ScalarAttributeType;
import software.amazon.awssdk.services.dynamodb.waiters.DynamoDbWaiter;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.CreateTopicRequest;
import software.amazon.awssdk.services.sns.model.CreateTopicResponse;
import software.amazon.awssdk.services.sns.model.SubscribeRequest;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.CreateQueueRequest;
import software.amazon.awssdk.services.sqs.model.GetQueueAttributesRequest;
import software.amazon.awssdk.services.sqs.model.QueueAttributeName;
import software.amazon.awssdk.services.sqs.model.QueueDoesNotExistException;
import software.amazon.awssdk.services.ssm.SsmClient;
import software.amazon.awssdk.services.ssm.model.ParameterType;
import software.amazon.awssdk.services.ssm.model.PutParameterRequest;

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

    @Value("${aws.reRegistrationsQueueName}")
    private String reRegistrationsQueueName;

    @Value("${aws.activeSuspensionsQueueName}")
    private String activeSuspensionsQueueName;

    @Autowired
    private SnsClient snsClient;

    @Value("${aws.reRegistrationsAuditQueueName}")
    private String reRegistrationsAuditQueueName;

    @Value("${aws.region}")
    private String awsRegion;

    @Autowired
    private SqsClient sqsClient;

    @Autowired
    private SsmClient ssmClient;

    @Autowired
    private DynamoDbClient dynamoDbClient;

    @Value("${aws.activeSuspensionsDynamoDbTableName}")
    private String activeSuspensionsDynamoDbTableName;

    @Value("${pdsAdaptor.authPasswordSsmParameterName}")
    private String authPasswordSsmParameterName;

    @Value("${ehrRepoAuthKeySsmParameterName}")
    private String ehrRepoAuthKeySsmParameterName;

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
    public static SsmClient ssmClient(@Value("${localstack.url}") String localstackUrl) {
        return SsmClient.builder()
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

    @PostConstruct
    public void setupTestQueuesTopicsAndDb() {
        recreateReRegistrationsQueue();
        var reRegistrationsAuditQueue = sqsClient.createQueue(
                CreateQueueRequest.builder().queueName(reRegistrationsAuditQueueName).build()
        );
        var topic = snsClient.createTopic(CreateTopicRequest.builder().name("re_registration_audit_sns_topic").build());

        createSnsTestReceiverSubscription(topic, getQueueArn(reRegistrationsAuditQueue.queueUrl()));

        setupSsmParameters();

        setupDbAndTable();
    }

    private void setupSsmParameters() {
        ssmClient.putParameter(PutParameterRequest.builder()
                .name(authPasswordSsmParameterName)
                .value("test")
                .type(ParameterType.SECURE_STRING)
                .overwrite(true)
                .build());

        ssmClient.putParameter(PutParameterRequest.builder()
                .name(ehrRepoAuthKeySsmParameterName)
                .value("test")
                .type(ParameterType.SECURE_STRING)
                .overwrite(true)
                .build());
    }

    private void setupDbAndTable() {

        var waiter = dynamoDbClient.waiter();
        var tableRequest = DescribeTableRequest.builder()
                .tableName(activeSuspensionsDynamoDbTableName)
                .build();

        if (dynamoDbClient.listTables().tableNames().contains(activeSuspensionsDynamoDbTableName)) {
            resetTableForLocalEnvironment(waiter, tableRequest);
        }


        List<KeySchemaElement> keySchema = new ArrayList<>();
        keySchema.add(KeySchemaElement.builder()
                .keyType(KeyType.HASH)
                .attributeName("nhs_number")
                .build());

        List<AttributeDefinition> attributeDefinitions = new ArrayList<AttributeDefinition>();
        attributeDefinitions.add(AttributeDefinition.builder()
                .attributeType(ScalarAttributeType.S)
                .attributeName("nhs_number")
                .build());

        var createTableRequest = CreateTableRequest.builder()
                .tableName(activeSuspensionsDynamoDbTableName)
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
        var deleteRequest = DeleteTableRequest.builder().tableName(activeSuspensionsDynamoDbTableName).build();
        dynamoDbClient.deleteTable(deleteRequest);
        waiter.waitUntilTableNotExists(tableRequest);
    }

    private void recreateReRegistrationsQueue() {
        ensureQueueDeleted(reRegistrationsQueueName);
        ensureQueueDeleted(activeSuspensionsQueueName);
        createQueue(reRegistrationsQueueName);
        createQueue(activeSuspensionsQueueName);
    }

    private void createQueue(String queueName) {
        sqsClient.createQueue(CreateQueueRequest.builder().queueName(queueName).build());
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

    private void ensureQueueDeleted(String queueName) {
        try {
            deleteQueue(queueName);
        } catch (QueueDoesNotExistException e) {
            // no biggie
        }
    }

    private void deleteQueue(String queueName) {
        var queueUrl = sqsClient.getQueueUrl(builder -> builder.queueName(queueName)).queueUrl();
        sqsClient.deleteQueue(builder -> builder.queueUrl(queueUrl));
    }

    private String getQueueArn(String queueUrl) {
        var queueAttributes = sqsClient.getQueueAttributes(
                GetQueueAttributesRequest.builder()
                        .queueUrl(queueUrl)
                        .attributeNames(QueueAttributeName.QUEUE_ARN)
                        .build()
        );
        return queueAttributes.attributes().get(QueueAttributeName.QUEUE_ARN);
    }

}
