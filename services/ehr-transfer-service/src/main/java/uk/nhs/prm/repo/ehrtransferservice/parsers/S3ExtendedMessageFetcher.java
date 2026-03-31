package uk.nhs.prm.repo.ehrtransferservice.parsers;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import uk.nhs.prm.repo.ehrtransferservice.gp2gp_message_models.ParsedMessage;
import uk.nhs.prm.repo.ehrtransferservice.models.LargeSqsMessage;
import uk.nhs.prm.repo.ehrtransferservice.models.S3PointerMessage;
import uk.nhs.prm.repo.ehrtransferservice.models.enums.Status;

import javax.jms.Message;
import javax.jms.TextMessage;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class S3ExtendedMessageFetcher {
    private final S3Client s3Client;
    private final S3PointerMessageParser s3PointerMessageParser;
    private final LargeSqsMessageParser largeSqsMessageParser;

    public ParsedMessage fetchAndParse(Message message) throws Exception {
        var sqsMessagePayload = ((TextMessage) message).getText();

        var parsingResult = s3PointerMessageParser.parse(sqsMessagePayload);
        if (parsingResult.getStatus() == Status.OK) {
            return retrieveMessageFromS3(parsingResult.getMessage());
        }

        log.info("Not a message to be retrieved from s3, parsing directly");
        return largeSqsMessageParser.parse(sqsMessagePayload);
    }

    public LargeSqsMessage retrieveMessageFromS3(S3PointerMessage sqsMessagePayload) throws Exception {
        var getObjectRequest = GetObjectRequest.builder()
                .bucket(sqsMessagePayload.getS3BucketName())
                .key(sqsMessagePayload.getS3Key()).build();

        var messageBody = new BufferedReader(
                new InputStreamReader(s3Client.getObject(getObjectRequest), StandardCharsets.UTF_8))
                .lines()
                .collect(Collectors.joining("\n"));

        return largeSqsMessageParser.parse(messageBody);
    }
}