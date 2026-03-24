package uk.nhs.prm.deductions.nemseventprocessor.nemsevents;

import com.google.gson.Gson;
import lombok.Getter;

public class NemsEventMessage {
    private final NemsEventType eventType;
    @Getter
    private final String nhsNumber;
    @Getter
    private final String lastUpdated;
    @Getter
    private final String odsCode;
    @Getter
    private final String nemsMessageId;

    private NemsEventMessage(
            NemsEventType eventType,
            String nhsNumber,
            String lastUpdated,
            String previousOdsCode,
            String nemsMessageId
    ) {
        this.eventType = eventType;
        this.nhsNumber = nhsNumber;
        this.lastUpdated = lastUpdated;
        this.odsCode = previousOdsCode;
        this.nemsMessageId = nemsMessageId;
    }

    // TODO These static constructors should be subclasses and NemsEventMessage should be made abstract
    public static NemsEventMessage suspension(String nhsNumber, String lastUpdated, String odsCode, String nemsMessageId) {
        return new NemsEventMessage(NemsEventType.SUSPENSION, nhsNumber, lastUpdated, odsCode, nemsMessageId);
    }

    public static NemsEventMessage reRegistration(String nhsNumber, String lastUpdated, String odsCode, String nemsMessageId) {
        return new NemsEventMessage(NemsEventType.REREGISTRATION, nhsNumber, lastUpdated, odsCode, nemsMessageId);
    }

    public static NemsEventMessage nonSuspension(String nemsMessageId) {
        return new NemsEventMessage(NemsEventType.NON_SUSPENSION, null, null, null, nemsMessageId);
    }

    // TODO When converting to subclasses, these methods should be removed and replaced with instanceof checks
    public boolean isSuspension() {
        return eventType == NemsEventType.SUSPENSION;
    }

    public boolean isReRegistration() {
        return eventType == NemsEventType.REREGISTRATION;
    }

    public String toJsonString() {
        return new Gson().toJson(this);
    }
}
