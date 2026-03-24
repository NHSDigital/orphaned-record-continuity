package uk.nhs.prm.deductions.nemseventprocessor.nemsevents;

import com.jcabi.xml.XML;
import com.jcabi.xml.XMLDocument;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.ZonedDateTime;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class NemsEventParser {
    private final NemsEventValidator validator;

    public NemsEventMessage parse(final String messageBody) {
        log.info("Parsing message");
        return tryToParse(messageBody);
    }

    public String extractNemsMessageIdFromStringBody(String messageBody) {
        log.info("Extracting nems message id to send to audit");
        final XML messageXml = parseMessageXML(messageBody);
        return extractNemsMessageId(messageXml);
    }

    private NemsEventMessage tryToParse(String messageBody) {
        final XML messageXml = parseMessageXML(messageBody);

        String nemsMessageId = extractNemsMessageId(messageXml);

        if (hasNoPatientEntry(messageXml)) {
            throw new NemsEventParseException("Cannot find Patient Details entry - invalid message");
        }
        if (hasNoGpEntry(messageXml)) {
            log.info("NEMS event has no current GP");
            final XML organizationXml = getOrganizationXml(messageXml);
            validator.validate(extractNhsNumber(messageXml), extractNhsNumberVerificationValue(messageXml), extractOdsCode(organizationXml));
            return createSuspensionMessage(messageXml, organizationXml, nemsMessageId);
        } else if (hasNoFinishedEpisodeOfCare(messageXml)) {
            log.info("NEMS event has no episode of care");
            final XML organizationXml = getNewGPOrganizationXml(messageXml);
            validator.validate(extractNhsNumber(messageXml), extractNhsNumberVerificationValue(messageXml), extractOdsCode(organizationXml));
            return createReRegistrationMessage(messageXml, organizationXml, nemsMessageId);
        }

        return NemsEventMessage.nonSuspension(nemsMessageId);
    }

    private XML getOrganizationXml(XML messageXml) {
        if (hasNoFinishedEpisodeOfCare(messageXml)) {
            throw new NemsEventParseException("Cannot find EpisodeOfCare with finished status");
        }
        final String previousGpReferenceUrl = extractPreviousGpUrl(messageXml);
        return findOrganizationByUrl(messageXml, previousGpReferenceUrl);
    }

    private XML getNewGPOrganizationXml(XML messageXml) {
        final String newGpReferenceUrl = extractNewGpUrl(messageXml);
        return findOrganizationByUrl(messageXml, newGpReferenceUrl);
    }

    private NemsEventMessage createSuspensionMessage(final XML messageXml, XML organizationXml, String nemsMessageId) {
        return NemsEventMessage.suspension(extractNhsNumber(messageXml),
                extractWhenLastUpdated(messageXml),
                extractOdsCode(organizationXml),nemsMessageId);
    }

    private NemsEventMessage createReRegistrationMessage(final XML messageXml, XML organizationXml, String nemsMessageId) {
        return NemsEventMessage.reRegistration(extractNhsNumber(messageXml),
                extractWhenLastUpdated(messageXml),
                extractOdsCode(organizationXml),nemsMessageId);
    }

    private boolean hasNoGpEntry(XML messageXml) {
        return messageXml.nodes("//fhir:Patient/fhir:generalPractitioner").isEmpty();
    }

    private boolean hasNoPatientEntry(XML messageXml) {
        return messageXml.nodes("//fhir:Patient").isEmpty();
    }

    private boolean hasNoFinishedEpisodeOfCare(XML messageXml) {
        return messageXml.nodes("//fhir:EpisodeOfCare[fhir:status/@value='finished']").isEmpty();
    }

    private String extractPreviousGpUrl(XML messageXml) {
        try {
            return query(messageXml, "//fhir:EpisodeOfCare[fhir:status/@value='finished']/fhir:managingOrganization/fhir:reference/@value");
        } catch (Exception e){
            throw new NemsEventParseException("Cannot extract previous GP URL Field from finished EpisodeOfCare");
        }
    }

    private String extractNewGpUrl(XML messageXml) {
        try {
            return query(messageXml, "//fhir:generalPractitioner/fhir:reference/@value");
        } catch (Exception e){
            throw new NemsEventParseException("Cannot extract new GP URL Field from generalPractitioner");
        }
    }

    private XML findOrganizationByUrl(XML messageXml, String organizationUrl) {
        try {
            return messageXml.nodes("//fhir:entry[fhir:fullUrl/@value='" + organizationUrl + "']/fhir:resource/fhir:Organization").get(0);
        } catch (Exception e){
            throw new NemsEventParseException("Cannot find entry for Organization with previous GP ODS code");
        }
    }

    private String extractOdsCode(XML organizationXml) {
        try {
            return query(organizationXml, "fhir:identifier[contains(fhir:system/@value,'ods-organization-code')]/fhir:value/@value");
        } catch (Exception e) {
            throw new NemsEventParseException("Cannot extract previous GP ODS Code");
        }
    }

    private String extractNhsNumber(XML messageXml) {
        try {
            return query(messageXml, "//fhir:Patient/fhir:identifier/fhir:value/@value");
        } catch (Exception e) {
            throw new NemsEventParseException("Cannot extract NHS Number from Patient Details Entry");
        }
    }

    private String extractNhsNumberVerificationValue(XML messageXml) {
        try {
            return query(messageXml, "//fhir:Patient/fhir:identifier/fhir:extension/fhir:valueCodeableConcept/fhir:coding/fhir:code/@value");
        } catch (Exception e){
            throw new NemsEventParseException("Cannot extract nhs number verification value from Patient Details Entry");
        }
    }

    private void validateLastUpdatedAsIso8601DateString(String lastUpdated) {
        ZonedDateTime.parse(lastUpdated);
    }

    private String extractWhenLastUpdated(XML messageXml) {
        try {
            var lastUpdated = query(messageXml, "//fhir:MessageHeader/fhir:meta/fhir:lastUpdated/@value");
            validateLastUpdatedAsIso8601DateString(lastUpdated);
            return lastUpdated;
        } catch (Exception e) {
            throw new NemsEventParseException("Cannot extract last updated field from Message Header Entry");
        }
    }

    private String extractNemsMessageId(XML messageXml) {
        try {
            return query(messageXml, "//fhir:MessageHeader/fhir:id/@value");
        } catch (Exception e) {
            throw new NemsEventParseException("Cannot extract nems message id from Message Header Entry");
        }
    }

    private XML parseMessageXML(String messageBody) {
        try {
            return new XMLDocument(messageBody).registerNs("fhir", "http://hl7.org/fhir");
        } catch (Exception exception) {
            throw new NemsEventParseException("Invalid/non XML message");
        }
    }

    private String query(XML messageXml, String query) {
        List<String> xpath = messageXml.xpath(query);
        if (xpath.size() > 1) {
            throw new NemsEventParseException("More than a single instance found of: " + query);
        }
        return xpath.get(0);
    }
}


