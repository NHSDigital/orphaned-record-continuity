package uk.nhs.prm.repo.ehrtransferservice.models;

import java.util.UUID;

public class EhrExtractMessage {

    public UUID messageId;

    public EhrExtractMessage(UUID messageId) {
        this.messageId = messageId;
    }
}
