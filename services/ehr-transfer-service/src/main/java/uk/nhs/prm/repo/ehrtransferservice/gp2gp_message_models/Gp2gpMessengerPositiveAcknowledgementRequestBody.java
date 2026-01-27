package uk.nhs.prm.repo.ehrtransferservice.gp2gp_message_models;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper=true)
public class Gp2gpMessengerPositiveAcknowledgementRequestBody extends Gp2gpMessengerAcknowledgementRequestBody{
    public Gp2gpMessengerPositiveAcknowledgementRequestBody(
            String repositoryAsid,
            String odsCode,
            String conversationId,
            String messageId
    ) {
        super(repositoryAsid, odsCode, conversationId, messageId);
    }
}
