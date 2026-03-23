package uk.nhs.prm.deductions.nemseventprocessor.nemsevents;

// TODO when converting NemsEventMessage to subclasses, this enum should be removed and the type should be determined by instanceof checks instead
public enum NemsEventType {
    SUSPENSION,
    REREGISTRATION,
    NON_SUSPENSION;
}
