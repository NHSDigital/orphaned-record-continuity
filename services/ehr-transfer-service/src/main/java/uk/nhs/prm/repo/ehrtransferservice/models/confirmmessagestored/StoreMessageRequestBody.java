package uk.nhs.prm.repo.ehrtransferservice.models.confirmmessagestored;

import java.util.List;
import java.util.UUID;

public class StoreMessageRequestBody {

    public StoreMessageData data;

    public StoreMessageRequestBody(UUID messageId, UUID conversationId, String nhsNumber, String messageType, List<UUID> fragmentMessageIds) {
        this.data = new StoreMessageData(messageId, conversationId, nhsNumber, messageType, fragmentMessageIds);
    }
}
