package uk.nhs.prm.repo.ehrtransferservice.message_publishers;

import com.google.gson.Gson;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.MessageAttributeValue;
import software.amazon.awssdk.services.sns.model.PublishRequest;
import software.amazon.awssdk.services.sns.model.PublishResponse;
import uk.nhs.prm.repo.ehrtransferservice.logging.Tracer;

import java.util.HashMap;
import java.util.Map;

@Component
@Slf4j
@RequiredArgsConstructor
public class MessagePublisher {
    private final SnsClient snsClient;
    private final Tracer tracer;

    public void sendMessage(String topicArn, String message) {
        sendMessage(topicArn, message, null);
    }

    public void sendMessage(String topicArn, String message, Map<String, String> attributes) {
        Map<String, MessageAttributeValue> messageAttributes = new HashMap<>();
        messageAttributes.put("traceId", getMessageAttributeValue(tracer.getTraceId()));
        if (attributes != null && attributes.size() > 0) {
            attributes.forEach((key, value) -> messageAttributes.put(key, getMessageAttributeValue(value)));
        }

        PublishRequest request = PublishRequest.builder()
                .message(message)
                .messageAttributes(messageAttributes)
                .topicArn(topicArn)
                .build();

        PublishResponse response = snsClient.publish(request);
        String[] topicAttributes = topicArn.split(":");
        log.info("PUBLISHED: message to {} topic. Published SNS message id: {}", topicAttributes[topicAttributes.length - 1], response.messageId());
    }

    public void sendJsonMessage(String topicArn, Object message, Map<String, String> attributes) {
        String jsonMessage = new Gson().toJson(message);
        sendMessage(topicArn, jsonMessage, attributes);
    }

    private MessageAttributeValue getMessageAttributeValue(String attributeValue) {
        return MessageAttributeValue.builder()
                .dataType("String")
                .stringValue(attributeValue)
                .build();
    }
}
