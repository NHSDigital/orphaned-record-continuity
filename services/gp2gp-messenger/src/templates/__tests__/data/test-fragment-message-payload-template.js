export function templateLargeEhrFragmentTestMessage(
  messageId,
  recipientAsid,
  sendingAsid,
  recipientOdsCode,
  sendingOdsCode
) {
  return `<COPC_IN000001UK01 xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:xs="http://www.w3.org/2001/XMLSchema" xmlns="urn:hl7-org:v3">
        <id root="${messageId}"/>
        <creationTime value="20220927112530"/>
        <versionCode code="3NPfIT7.2.02"/>
        <interactionId root="2.16.840.1.113883.2.1.3.2.4.12" extension="COPC_IN000001UK01"/>
        <processingCode code="P"/>
        <processingModeCode code="T"/>
        <acceptAckCode code="NE"/>
        <communicationFunctionRcv type="CommunicationFunction" typeCode="RCV">
                <device type="Device" classCode="DEV" determinerCode="INSTANCE">
                        <id root="1.2.826.0.1285.0.2.0.107" extension="${recipientAsid}"/>
                </device>
        </communicationFunctionRcv>
        <communicationFunctionSnd type="CommunicationFunction" typeCode="SND">
                <device type="Device" classCode="DEV" determinerCode="INSTANCE">
                        <id root="1.2.826.0.1285.0.2.0.107" extension="${sendingAsid}"/>
                </device>
        </communicationFunctionSnd>
        <ControlActEvent classCode="OBS" moodCode="EVN">
                <author1 type="Participation" typeCode="AUT">
                        <AgentSystemSDS type="RoleHeir" classCode="AGNT">
                                <agentSystemSDS type="Device" classCode="DEV" determinerCode="INSTANCE">
<!--                                Unsure whether below value is supposed to be sending or receiving practice asid ?? -->
<!--                                Updated to use recipient asid for testing, as emis example seemed to us the receiving practice asid-->
                                        <id root="1.2.826.0.1285.0.2.0.107" extension="${recipientAsid}"/> 
                                </agentSystemSDS>
                        </AgentSystemSDS>
                </author1>
                <subject typeCode="SUBJ" contextConductionInd="false">
                        <PayloadInformation classCode="OBS" moodCode="EVN">
                                <code code="GP2GPLMATTACHMENTINFO" codeSystem="2.16.840.1.113883.2.1.3.2.4.17.202" displayName="GP2GP Large Message Attachment Information"/>
                                <id root="${messageId}"/>
                                <messageType xmlns="NPFIT:HL7:Localisation" root="2.16.840.1.113883.2.1.3.2.4.18.17" extension="RCMR_MT000001GB01"/>
                                <value>
                                        <Gp2gpfragment xmlns:xsd="http://www.w3.org/2001/XMLSchema" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns="urn:nhs:names:services:gp2gp">
                                                <Version xsi:type="xsd:string">01</Version>
                                                <Recipients>
                                                        <Recipient>${recipientOdsCode}</Recipient>
                                                </Recipients>
                                                <From>${sendingOdsCode}</From>
                                                <subject>Attachment: 072FCF33-2FA9-4A3C-B335-F8EFE81D41BE_tree_0.jfif</subject>
                                                <message-id>${messageId}</message-id>
                                        </Gp2gpfragment>
                                </value>
                                <pertinentInformation typeCode="PERT">
                                        <sequenceNumber value="1"/>
                                        <pertinentPayloadBody classCode="OBS" moodCode="EVN">
                                                <code code="GP2GPLMATTACHMENT" codeSystem="2.16.840.1.113883.2.1.3.2.4.17.202" displayName="GP2GP Large Message Attachment"/>
                                                <id root="${messageId}"/>
                                                <value>
                                                        <reference value="file://localhost/072FCF33-2FA9-4A3C-B335-F8EFE81D41BE_tree_0.jfif"/>
                                                </value>
                                        </pertinentPayloadBody>
                                </pertinentInformation>
                        </PayloadInformation>
                </subject>
        </ControlActEvent>
</COPC_IN000001UK01>
`;
}
