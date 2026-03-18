package uk.nhs.prm.repo.suspension.service.pds;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import uk.nhs.prm.repo.suspension.service.model.PdsAdaptorSuspensionStatusResponse;

@Component
@Slf4j
@AllArgsConstructor
public class PdsAdaptorSuspensionStatusResponseParser {
    public PdsAdaptorSuspensionStatusResponse parse(String responseBody) {
        if (null == responseBody) {
            throw new UnexpectedPdsAdaptorResponseException("Response body was null attempting parse PDS status response");
        }
        try {
            ObjectMapper mapper = new ObjectMapper();
            return mapper.readValue(responseBody, PdsAdaptorSuspensionStatusResponse.class);
        }
        catch (JacksonException e) {
            log.error("Got an exception while parsing PDS lookup response.");
            throw new UnexpectedPdsAdaptorResponseException("JSON parsing error attempting to parse PDS status response");
        }
    }
}
