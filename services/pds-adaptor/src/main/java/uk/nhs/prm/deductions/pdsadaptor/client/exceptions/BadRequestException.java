package uk.nhs.prm.deductions.pdsadaptor.client.exceptions;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.client.HttpStatusCodeException;

@Slf4j
@ResponseStatus(value = HttpStatus.BAD_REQUEST)
public class BadRequestException extends RuntimeException {
    public BadRequestException(HttpStatusCodeException exception) {
        super("Received " + exception.getStatusCode().value() + " error from PDS FHIR: error: " + exception.getMessage());
        log.info("Received " + exception.getStatusCode().value() + " error from PDS FHIR: error: " + exception.getMessage());
    }
}