package uk.nhs.prm.repo.ehrtransferservice.gp2gp_message_models;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ParsedMessage {
    private SOAPEnvelope soapEnvelope;
    private MessageContent messageContent;
    private String messageBody;

    public ParsedMessage(SOAPEnvelope soapEnvelope, MessageContent messageContent, String messageBody) {
        this.soapEnvelope = soapEnvelope;
        this.messageContent = messageContent;
        this.messageBody = messageBody;
    }

    public String getMessageBody() {
        return messageBody;
    }

    public MessageContent getMessageContent() {
        return messageContent;
    }

    public SOAPEnvelope getSoapEnvelope() {
        return soapEnvelope;
    }

    public String getNhsNumber() {
        if (messageContent instanceof EhrExtractMessageWrapper) {
            return ((EhrExtractMessageWrapper) messageContent).getEhrExtract().getPatient().getNhsNumber();
        }

        if (messageContent instanceof EhrRequestMessageWrapper) {
            return ((EhrRequestMessageWrapper) messageContent).getEhrRequest().getPatient().getNhsNumber();
        }
        return null;
    }

    public String getInteractionId() {
        if (soapEnvelope.header == null || soapEnvelope.header.messageHeader == null) {
            return null;
        }
        return soapEnvelope.header.messageHeader.action;
    }

    public UUID getConversationId() {
        if (soapEnvelope.header == null || soapEnvelope.header.messageHeader == null) {
            return null;
        }
        return soapEnvelope.header.messageHeader.conversationId;
    }

    public UUID getMessageId() {
        if (soapEnvelope.header == null || soapEnvelope.header.messageHeader == null || soapEnvelope.header.messageHeader.messageData == null) {
            return null;
        }
        return soapEnvelope.header.messageHeader.messageData.messageId;
    }

    public String getEhrRequestId() {
        if (messageContent instanceof EhrRequestMessageWrapper) {
            return ((EhrRequestMessageWrapper) messageContent).getEhrRequest().getId();
        }
        return null;
    }

    public String getOdsCode() {
        if (messageContent instanceof EhrRequestMessageWrapper) {
            return ((EhrRequestMessageWrapper) messageContent).getEhrRequest().getRequestingPractice().getOdsCode();
        }
        return null;
    }

    public boolean isLargeMessage() {
        for (Reference reference : soapEnvelope.body.manifest) {
            if (reference.href.contains("mid")) {
                return true;
            }
        }
        return false;
    }

    public List<UUID> getFragmentMessageIds() {
        List<UUID> fragmentMessageIds = new ArrayList<>();

        soapEnvelope.body.manifest.forEach(reference -> {
            if (reference.href.contains("mid")) {
                String fragmentMessageId = reference.href.substring(4);
                fragmentMessageIds.add(UUID.fromString(fragmentMessageId));
            }
        });

        return fragmentMessageIds;
    }
}
