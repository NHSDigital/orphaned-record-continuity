package uk.nhs.prm.repo.suspension.service.suspensionsevents;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import uk.nhs.prm.repo.suspension.service.config.MessageProcessProperties;
import uk.nhs.prm.repo.suspension.service.data.LastUpdatedEventService;
import uk.nhs.prm.repo.suspension.service.model.PdsAdaptorSuspensionStatusResponse;
import uk.nhs.prm.repo.suspension.service.pds.InvalidPdsRequestException;
import uk.nhs.prm.repo.suspension.service.pds.PdsService;
import uk.nhs.prm.repo.suspension.service.publishers.MessagePublisherBroker;

@Service
@Slf4j
@RequiredArgsConstructor
public class MessageProcessExecution {
    private final MessagePublisherBroker messagePublisherBroker;
    private final PdsService pdsService;
    private final LastUpdatedEventService lastUpdatedEventService;
    private final ManagingOrganisationService managingOrganisationService;
    private final MessageProcessProperties config;

    private final SuspensionEventParser parser;
    private final ConcurrentThreadLock threadLock;

    public void run(String suspensionMessage) {
        var suspensionEvent = getSuspensionEvent(suspensionMessage);
        try {
            threadLock.lock(suspensionEvent.nhsNumber());

            // event out of order block
            if (lastUpdatedEventService.isOutOfOrder(suspensionEvent.nhsNumber(), suspensionEvent.lastUpdated())) {
                log.info("Event is out of order");
                messagePublisherBroker.eventOutOfOrderMessage(suspensionEvent.nemsMessageId());
                return;
            }

            // pds adaptor block
            var patientStatus = getPdsAdaptorSuspensionStatusResponse(suspensionMessage, suspensionEvent);

            // patient is deceased block
            if (Boolean.TRUE.equals(patientStatus.getIsDeceased())) {
                log.info("Patient is deceased");
                messagePublisherBroker.deceasedPatientMessage(suspensionEvent.nemsMessageId());
                return;
            }


            // synthetic patient block
            if (processingOnlySyntheticPatients() && patientIsNonSynthetic(suspensionEvent)) {
                    messagePublisherBroker.notSyntheticMessage(suspensionEvent.nemsMessageId());
                    return;
            }

            if (Boolean.TRUE.equals(patientStatus.getIsSuspended())) {
                log.info("Patient is Suspended");
                managingOrganisationService.processMofUpdate(suspensionMessage, suspensionEvent, patientStatus);
                lastUpdatedEventService.save(suspensionEvent.nhsNumber(), suspensionEvent.lastUpdated());
            } else {
                messagePublisherBroker.notSuspendedMessage(suspensionEvent.nemsMessageId());
            }
        } finally {
            threadLock.unlock(suspensionEvent.nhsNumber());
        }
    }


    private PdsAdaptorSuspensionStatusResponse getPdsAdaptorSuspensionStatusResponse(String suspensionMessage, SuspensionEvent suspensionEvent) {
        try {
            log.info("Checking patient's suspension status on PDS");
            var response = pdsService.isSuspended(suspensionEvent.nhsNumber());
            if (nhsNumberIsSuperseded(suspensionEvent.nhsNumber(), response.getNhsNumber())) {
                log.info("Processing a superseded record");
                var supersededNhsNumber = response.getNhsNumber();
                response = pdsService.isSuspended(supersededNhsNumber);
            }
            return response;
        } catch (InvalidPdsRequestException invalidPdsRequestException) {
            messagePublisherBroker.invalidMessage(suspensionMessage, suspensionEvent.getNemsMessageId());
            throw invalidPdsRequestException;
        }
    }

    private SuspensionEvent getSuspensionEvent(String suspensionMessage) {
        try {
            return parser.parse(suspensionMessage);
        } catch (JacksonException e) {
            log.error("Got an exception while parsing suspensions message");
            messagePublisherBroker.invalidMessage(suspensionMessage, null);
            throw new InvalidSuspensionMessageException("Encountered an invalid message", e);
        }
    }

    private boolean patientIsNonSynthetic(SuspensionEvent suspensionEvent) {
        boolean isNonSynthetic = !suspensionEvent.nhsNumber().startsWith(this.config.getSyntheticPatientPrefix());
        log.info(isNonSynthetic ? "Processing Non-Synthetic Patient" : "Processing Synthetic Patient");
        return isNonSynthetic;
    }

    private boolean processingOnlySyntheticPatients() {
        log.info("Process only synthetic patients: " + this.config.getProcessOnlySyntheticPatients());
        return Boolean.parseBoolean(this.config.getProcessOnlySyntheticPatients());
    }

    private boolean nhsNumberIsSuperseded(String nemsEventNhsNumber, String pdsNhsNumber) {
        return !nemsEventNhsNumber.equals(pdsNhsNumber);
    }

}