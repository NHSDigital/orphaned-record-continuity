import { param, body } from 'express-validator';
import dateFormat from 'dateformat';
import { v4 as uuid } from 'uuid';
import generateEhrRequestQuery from '../../templates/ehr-request-template';
import { sendMessage } from '../../services/mhs/mhs-outbound-client';
import { logInfo, logWarning } from '../../middleware/logging';
import { setCurrentSpanAttributes } from '../../config/tracing';
import { initializeConfig } from '../../config';
import { getPracticeAsid } from '../../services/fhir/sds-fhir-client';

export const healthRecordRequestValidation = [
  param('nhsNumber').isNumeric().withMessage(`'nhsNumber' provided is not numeric`),
  param('nhsNumber')
    .isLength({ min: 10, max: 10 })
    .withMessage("'nhsNumber' provided is not 10 digits"),
  body('repositoryOdsCode').notEmpty().withMessage("'repositoryOdsCode' is not configured"),
  body('repositoryAsid').notEmpty().withMessage("'repositoryAsid' is not configured"),
  body('practiceOdsCode').notEmpty().withMessage("'practiceOdsCode' is not configured"),
  body('conversationId').isUUID('4').withMessage("'conversationId' provided is not of type UUIDv4"),
  body('conversationId').notEmpty().withMessage("'conversationId' is not configured")
];

export const healthRecordRequests = async (req, res) => {
  const interactionId = 'RCMR_IN010000UK05';
  const serviceId = `urn:nhs:names:services:gp2gp:${interactionId}`;
  const { nhsNumberPrefix, requestEhrOnlyForSafeListedOdsCodesToggle, safeListedOdsCodes } =
    initializeConfig();
  const { conversationId, practiceOdsCode } = req.body;
  const { nhsNumber } = req.params;

  setCurrentSpanAttributes({ conversationId });

  try {
    if (!checkNhsNumberPrefix(nhsNumberPrefix, nhsNumber)) {
      const notASyntheticPatientMessage =
        'The NHS number provided is not one for a synthetic patient';

      logWarning(notASyntheticPatientMessage);
      res.status(422).json({
        errors: notASyntheticPatientMessage
      });
      return;
    }

    if (
      !odsCodeInSafeList(
        practiceOdsCode,
        requestEhrOnlyForSafeListedOdsCodesToggle,
        safeListedOdsCodes
      )
    ) {
      const notASafeListedOdsCodeMessage = 'The ODS code provided is not safe listed.';

      logWarning(notASafeListedOdsCodeMessage);
      res.status(422).json({
        errors: notASafeListedOdsCodeMessage
      });
      return;
    }

    const asid = await getPracticeAsid(practiceOdsCode, serviceId);
    const message = await buildEhrRequest(req, conversationId, asid);

    await sendMessage({
      interactionId,
      conversationId,
      odsCode: practiceOdsCode,
      message
    });
    res.sendStatus(204);
    logInfo('EHR Request sent', { conversationId });
  } catch (err) {
    res.status(503).send({ errors: ['Sending EHR Request has failed', err.message] });
  }
};

export const buildEhrRequest = async (req, conversationId, practiceAsid) => {
  const timestamp = dateFormat(Date.now(), 'yyyymmddHHMMss');

  return generateEhrRequestQuery({
    id: conversationId,
    timestamp,
    receivingService: {
      asid: practiceAsid,
      odsCode: req.body.practiceOdsCode
    },
    sendingService: {
      asid: req.body.repositoryAsid,
      odsCode: req.body.repositoryOdsCode
    },
    patient: {
      nhsNumber: req.params.nhsNumber
    },
    ehrRequestId: uuid()
  });
};

const checkNhsNumberPrefix = (nhsNumberPrefix, nhsNumber) => {
  if (nhsNumberPrefix && !nhsNumber.startsWith(nhsNumberPrefix)) {
    logWarning(
      `Health record request failed as nhs number does not start with enforced prefix: ${nhsNumberPrefix}`
    );
    return false;
  }
  return true;
};

const odsCodeInSafeList = (
  practiceOdsCode,
  requestEhrOnlyForSafeListedOdsCodesToggle,
  safeListedOdsCodes
) => {
  logInfo(
    'process only safe listed ODS code toggle is: ' + requestEhrOnlyForSafeListedOdsCodesToggle
  );

  if (!requestEhrOnlyForSafeListedOdsCodesToggle) {
    return true;
  }

  const safelistedOdsCodesUppercased = safeListedOdsCodes.toUpperCase().split(',');
  return safelistedOdsCodesUppercased.includes(practiceOdsCode.toUpperCase());
};
