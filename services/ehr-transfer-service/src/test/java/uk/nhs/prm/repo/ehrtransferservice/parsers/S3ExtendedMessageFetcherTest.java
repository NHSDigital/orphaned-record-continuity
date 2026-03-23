package uk.nhs.prm.repo.ehrtransferservice.parsers;

import com.amazon.sqs.javamessaging.message.SQSTextMessage;
import com.google.common.base.Charsets;
import com.google.common.io.ByteSource;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.http.AbortableInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import uk.nhs.prm.repo.ehrtransferservice.models.ParsingResult;
import uk.nhs.prm.repo.ehrtransferservice.models.S3PointerMessage;
import uk.nhs.prm.repo.ehrtransferservice.models.enums.Status;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class S3ExtendedMessageFetcherTest {
    @Mock
    private S3Client s3Client;
    @Mock
    private S3PointerMessageParser s3PointerMessageParser;

    @Mock
    private LargeSqsMessageParser largeSqsMessageParser;

    @InjectMocks
    private S3ExtendedMessageFetcher s3ExtendedMessageFetcher;

    @Test
    void shouldCallS3ClientToParseMessageReturnedFromS3() throws Exception {
        mockS3GetObjectResponseToReturnContentFrom("RCMR_IN030000UK06");
        s3ExtendedMessageFetcher.retrieveMessageFromS3(getStaticS3PointerMessage());
        verify(s3Client).getObject(GetObjectRequest.builder().bucket("s3-bucket-name").key("s3-key-value").build());
    }

    @Test
    void shouldThrowExceptionWhenS3ClientFails() {
        when(s3Client.getObject(any(GetObjectRequest.class))).then(invocation -> {
            throw new Exception("woops");
        });
        assertThrows(Exception.class, () -> s3ExtendedMessageFetcher.retrieveMessageFromS3(getStaticS3PointerMessage()));
    }

    @Test
    void shouldCallS3PointerMessageParserWithS3PointerPayLoad() throws Exception {
        var payload = "{\"s3BucketName\":\"s3-bucket-name\",\"s3Key\":\"s3-key-value\"}";
        mockS3GetObjectResponseToReturnContentFrom("RCMR_IN030000UK06");
        var s3PointerOk = new ParsingResult<>(getStaticS3PointerMessage(), Status.OK);
        when(s3PointerMessageParser.parse(any())).thenReturn(s3PointerOk);

        s3ExtendedMessageFetcher.fetchAndParse(new SQSTextMessage(payload));

        verify(s3PointerMessageParser).parse(payload);
        verify(largeSqsMessageParser, never()).parse(payload);
    }

    @Test
    void shouldCallLargeSqsMessageParserWithoutS3PointerPayLoad() throws Exception {
        var byteSource = new ByteSource() {
            @Override
            public InputStream openStream() throws IOException {
                return readResourceFile("RCMR_IN030000UK06");
            }
        };
        var payload = byteSource.asCharSource(Charsets.UTF_8).read();

        var s3PointerKo = new ParsingResult<>(getStaticS3PointerMessage(), Status.KO);
        when(s3PointerMessageParser.parse(any())).thenReturn(s3PointerKo);

        s3ExtendedMessageFetcher.fetchAndParse(new SQSTextMessage(payload));

        verify(s3PointerMessageParser).parse(payload);
        verify(largeSqsMessageParser).parse(payload);
    }

    private S3PointerMessage getStaticS3PointerMessage() {
        var validMessage = "{\"s3BucketName\":\"s3-bucket-name\",\"s3Key\":\"s3-key-value\"}";
        var json = (JsonObject) JsonParser.parseString(validMessage);
        return new S3PointerMessage(json);
    }

    private void mockS3GetObjectResponseToReturnContentFrom(String resourceFileName) {
        when(s3Client.getObject(any(GetObjectRequest.class))).then(invocation -> {
            GetObjectRequest getObjectRequest = invocation.getArgument(0);
            assertEquals("s3-bucket-name", getObjectRequest.bucket());
            assertEquals("s3-key-value", getObjectRequest.key());

            return new ResponseInputStream<>(GetObjectResponse.builder().build(), AbortableInputStream.create(readResourceFile(resourceFileName)));
        });
    }

    private InputStream readResourceFile(String resourceFileName) throws FileNotFoundException {
        return new FileInputStream("src/test/resources/data/" + resourceFileName);
    }
}